package com.choivoo.jarvis.vision

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.choivoo.jarvis.overlay.JarvisSubtitleService
import com.choivoo.jarvis.voice.VoiceController
import kotlinx.coroutines.launch

class ShareVisionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri? = if (intent.action == Intent.ACTION_SEND) {
            if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            }
        } else null
        if (uri == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            val bitmap = runCatching { decode(uri) }.getOrNull()
            if (bitmap == null) {
                JarvisSubtitleService.show(this@ShareVisionActivity, "공유된 화면 이미지를 읽지 못했습니다.")
                finish()
                return@launch
            }
            val reply = VisionClient().analyze(
                bitmap,
                "This is a screenshot shared by the user. Explain the visible screen, important text or controls, what appears to be happening, and the most useful next action. Do not infer anything not visible."
            )
            JarvisSubtitleService.show(this@ShareVisionActivity, reply.subtitle)
            val voice = VoiceController(
                context = this@ShareVisionActivity,
                enableRecognition = false,
                onListeningStarted = {}, onPartialText = {}, onFinalText = {}, onError = {},
                onSpeakingStarted = {}, onSpeakingFinished = { finish() }
            )
            voice.speak(reply.subtitle)
        }
    }

    private fun decode(uri: Uri): Bitmap = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(2)
        }
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Images.Media.getBitmap(contentResolver, uri)
    }
}
