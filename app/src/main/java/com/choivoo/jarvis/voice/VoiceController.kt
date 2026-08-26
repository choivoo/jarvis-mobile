package com.choivoo.jarvis.voice

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.*
import android.speech.*
import android.speech.tts.*
import com.choivoo.jarvis.config.JarvisConfig
import com.choivoo.jarvis.overlay.JarvisSubtitleService
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.concurrent.thread

class VoiceController(private val context: Context, private val onListeningStarted:()->Unit, private val onPartialText:(String)->Unit, private val onFinalText:(String)->Unit, private val onError:(String)->Unit, private val onSpeakingStarted:()->Unit, private val onSpeakingFinished:()->Unit) {
    private var recognizer: SpeechRecognizer?=null; private var tts:TextToSpeech?=null; private var ready=false; private var player:MediaPlayer?=null
    private val main=Handler(Looper.getMainLooper()); private val prefs=VoicePreferences(context); private val cache=File(context.cacheDir,"jarvis_voice_cache").apply{mkdirs()}
    init { initRecognizer(); initTts() }
    private fun initRecognizer(){ if(!SpeechRecognizer.isRecognitionAvailable(context)) return; recognizer=SpeechRecognizer.createSpeechRecognizer(context).apply{setRecognitionListener(object:RecognitionListener{
        override fun onReadyForSpeech(p:Bundle?)=onListeningStarted(); override fun onBeginningOfSpeech(){}; override fun onRmsChanged(v:Float){}; override fun onBufferReceived(b:ByteArray?){}; override fun onEndOfSpeech(){}
        override fun onError(e:Int)=onError("음성 인식 오류: $e"); override fun onResults(b:Bundle?){val s=b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty(); if(s.isNotBlank())onFinalText(s)}
        override fun onPartialResults(b:Bundle?){b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onPartialText)}; override fun onEvent(t:Int,p:Bundle?){}
    })}}
    private fun initTts(){tts=TextToSpeech(context){s->if(s==TextToSpeech.SUCCESS){val e=tts?:return@TextToSpeech; val r=e.setLanguage(Locale.UK); ready=r!=TextToSpeech.LANG_MISSING_DATA&&r!=TextToSpeech.LANG_NOT_SUPPORTED; e.setSpeechRate(.91f);e.setPitch(.86f); val vs=e.voices.orEmpty().filter{it.locale.language=="en"&&(it.locale.country=="GB"||it.locale==Locale.UK)}; if(vs.isNotEmpty())runCatching{e.voice=vs.sortedByDescending{it.quality}.first()}; e.setOnUtteranceProgressListener(object:UtteranceProgressListener(){override fun onStart(id:String?){};override fun onDone(id:String?){main.post(onSpeakingFinished)};@Deprecated("Deprecated") override fun onError(id:String?){main.post(onSpeakingFinished)}})}}}
    fun startListening(){stopSpeaking();recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true)})}
    fun stopListening(){recognizer?.stopListening()}; fun cancelListening(){recognizer?.cancel()}
    fun speak(text:String){ if(text.isBlank()){onSpeakingFinished();return}; stopSpeaking();onSpeakingStarted(); requestBritishSpeechPackage(text) }
    /* The brain returns separate speech/subtitle fields. This compatibility path sends Korean source text and asks the server to translate speech to en-GB while preserving Korean subtitles. */
    private fun requestBritishSpeechPackage(source:String){ thread(name="jarvis-voice-v2") { try { if(!JarvisConfig.cloudEnabled) throw IllegalStateException("offline")
        val c=(URL("${JarvisConfig.API_BASE_URL}/v1/voice-package").openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=10000;readTimeout=50000;doOutput=true;setRequestProperty("Content-Type","application/json");setRequestProperty("X-Jarvis-Token",JarvisConfig.APP_TOKEN)}
        c.outputStream.use{it.write(JSONObject().put("text",source).toString().toByteArray())}; if(c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}"); val j=JSONObject(c.inputStream.bufferedReader().readText()); val speech=j.optString("speech",source);val subtitle=j.optString("subtitle",source); main.post{JarvisSubtitleService.show(context,subtitle); speakCloudEnglish(speech,subtitle,true)}
    } catch(_:Exception){ main.post{JarvisSubtitleService.show(context,source); speakLocal(source,"local-offline")} } } }
    private fun speakCloudEnglish(speech:String,subtitle:String,fallback:Boolean){val voice=prefs.getVoice();val f=File(cache,"${sha256("uk|$voice|$speech")}.mp3"); if(f.exists()&&f.length()>256){play(f,speech,subtitle);return};thread{name="jarvis-cloud-tts";try{val c=(URL("${JarvisConfig.API_BASE_URL}/v1/tts").openConnection() as HttpURLConnection).apply{requestMethod="POST";doOutput=true;connectTimeout=10000;readTimeout=50000;setRequestProperty("Content-Type","application/json");setRequestProperty("X-Jarvis-Token",JarvisConfig.APP_TOKEN)};c.outputStream.use{it.write(JSONObject().put("text",speech).put("voice",voice).put("speed",.91).put("locale","en-GB").toString().toByteArray())};if(c.responseCode !in 200..299)throw IllegalStateException();c.inputStream.use{i->f.outputStream().use{i.copyTo(it)}};main.post{play(f,speech,subtitle)}}catch(_:Exception){main.post{if(fallback)speakLocal(speech,"local-fallback") else onSpeakingFinished()}}}}
    private fun play(f:File,speech:String,subtitle:String){JarvisSubtitleService.show(context,subtitle);player=MediaPlayer().apply{setDataSource(f.absolutePath);setOnPreparedListener{it.start()};setOnCompletionListener{it.release();player=null;onSpeakingFinished()};setOnErrorListener{p,_,_->p.release();player=null;speakLocal(speech,"local-playback");true};prepareAsync()}}
    private fun speakLocal(text:String,label:String){prefs.recordProvider(label);if(!ready){onError("영국 영어 로컬 음성을 사용할 수 없습니다.");onSpeakingFinished();return};tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"jarvis-uk")}
    fun isNeuralReady()=ready;fun isNeuralInstalled()=true
    fun stopSpeaking(){runCatching{player?.stop()};player?.release();player=null;tts?.stop()};fun destroy(){recognizer?.destroy();stopSpeaking();tts?.shutdown()}
    private fun sha256(v:String)=MessageDigest.getInstance("SHA-256").digest(v.toByteArray()).joinToString(""){"%02x".format(it)}
}
