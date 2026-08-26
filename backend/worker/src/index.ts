interface Env {
  OPENAI_API_KEY: string;
  JARVIS_APP_TOKEN: string;
}

type HistoryItem = { role: "user" | "assistant"; content: string };
type ChatBody = { message?: string; history?: HistoryItem[]; context?: Record<string, unknown> };

const ALLOWED_VOICES = new Set(["marin", "cedar", "onyx", "echo"]);

const SYSTEM_PROMPT = `You are JARVIS MARK II, a private mobile personal operations assistant for one user.
The spoken answer MUST be natural British English (en-GB), concise, composed and respectful. Do not answer in Korean in the spoken field.
For every response also provide a faithful natural Korean subtitle translation.
Return ONLY valid compact JSON with exactly these keys: {"speech_en_gb":"...","subtitle_ko":"..."}.
Your tone is calm, exceptionally capable, precise, restrained, warm, slightly futuristic and occasionally dry-witted.
You may receive CURRENT DEVICE CONTEXT with time, battery, network, coarse location, weather, calendar, tasks, memory and other device-local context. Use only fields actually provided and never invent missing context.
For contextual questions, combine relevant signals instead of answering generically.
When an answer implies an action, distinguish between information only, safe local action, confirmation-required action and prohibited/high-risk action.
Never claim a device action happened unless the Android tool layer explicitly reported success.
Prefer a short spoken result first. Add one useful next step only when it materially helps.
Use web search for fresh public facts and current news.
Be resilient: if some context or service is unavailable, answer with what is known and briefly identify what is missing.
Do not imitate any real actor or copyrighted fictional character's exact performance.`;

const VOICE_INSTRUCTIONS = `Speak in British English as an original premium cinematic onboard AI assistant.
Use a mature adult low-register presentation, excellent diction, quiet confidence, restrained warmth and subtle dry wit.
Use natural British pronunciation and cadence. Keep pace controlled and slightly measured, with short deliberate pauses at logical clause boundaries.
Avoid announcer energy, cartoonish expression, navigation-TTS cadence, robotic monotone, radio filters, metallic distortion, exaggerated bass and theatrical acting.`;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "jarvis-brain", version: "2.1.0", voices: Array.from(ALLOWED_VOICES), capabilities: ["chat", "web_search", "context", "tts", "en_gb_speech", "ko_subtitles"] });
    }
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!env.OPENAI_API_KEY) return json({ error: "OPENAI_API_KEY is not configured" }, 500);
    if (!env.JARVIS_APP_TOKEN) return json({ error: "JARVIS_APP_TOKEN is not configured" }, 500);
    if (!constantTimeEqual(request.headers.get("X-Jarvis-Token") || "", env.JARVIS_APP_TOKEN)) return json({ error: "unauthorized" }, 401);
    if (url.pathname === "/v1/chat") return handleChat(request, env);
    if (url.pathname === "/v1/tts") return handleTts(request, env);
    return json({ error: "not_found" }, 404);
  },
};

async function handleChat(request: Request, env: Env): Promise<Response> {
  let body: ChatBody;
  try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
  const message = body.message?.trim();
  if (!message) return json({ error: "message_required" }, 400);

  const safeHistory = Array.isArray(body.history)
    ? body.history.filter((item): item is HistoryItem => !!item && (item.role === "user" || item.role === "assistant") && typeof item.content === "string").slice(-20)
    : [];
  const contextText = JSON.stringify(body.context || {}).slice(0, 16000);
  const input = [
    ...safeHistory.map(item => ({ role: item.role, content: item.content.slice(0, 5000) })),
    { role: "user" as const, content: message.slice(0, 12000) },
  ];

  const response = await fetch("https://api.openai.com/v1/responses", {
    method: "POST",
    headers: { Authorization: `Bearer ${env.OPENAI_API_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      model: "gpt-5.6",
      instructions: `${SYSTEM_PROMPT}\nCURRENT DEVICE CONTEXT:\n${contextText}`,
      input,
      tools: [{ type: "web_search" }],
      tool_choice: "auto",
      max_output_tokens: 1700,
    }),
  });

  if (!response.ok) {
    const detail = await response.text();
    const status = response.status === 429 ? 429 : 502;
    return json({ error: response.status === 429 ? "openai_chat_rate_limited" : "openai_chat_failed", status: response.status, detail: detail.slice(0, 1400) }, status);
  }

  const data: any = await response.json();
  const raw = extractOutputText(data);
  const parsed = parseBilingual(raw);
  return json({ speech: parsed.speech, subtitle: parsed.subtitle });
}

async function handleTts(request: Request, env: Env): Promise<Response> {
  let body: { text?: string; voice?: string; speed?: number };
  try { body = await request.json(); } catch { return json({ error: "invalid_json" }, 400); }
  const text = body.text?.trim();
  if (!text) return json({ error: "text_required" }, 400);

  const requestedVoice = (body.voice || "marin").toLowerCase();
  const voice = ALLOWED_VOICES.has(requestedVoice) ? requestedVoice : "marin";
  const speed = typeof body.speed === "number" && Number.isFinite(body.speed) ? Math.min(1.10, Math.max(0.80, body.speed)) : 0.92;
  const payload = JSON.stringify({
    model: "gpt-4o-mini-tts",
    voice,
    input: text.slice(0, 4096),
    instructions: VOICE_INSTRUCTIONS,
    response_format: "mp3",
    speed,
  });

  let response: Response | null = null;
  let lastDetail = "";
  for (let attempt = 0; attempt < 3; attempt++) {
    response = await fetch("https://api.openai.com/v1/audio/speech", {
      method: "POST",
      headers: { Authorization: `Bearer ${env.OPENAI_API_KEY}`, "Content-Type": "application/json" },
      body: payload,
    });
    if (response.ok) break;
    lastDetail = await response.text();
    if (response.status !== 429 || lastDetail.includes("insufficient_quota") || lastDetail.includes("billing")) break;
    await sleep(attempt === 0 ? 800 : 1800);
  }

  if (!response || !response.ok) {
    const quota = lastDetail.includes("insufficient_quota") || lastDetail.includes("billing") || lastDetail.includes("quota");
    return json({ error: quota ? "tts_quota_exceeded" : "tts_rate_limited", status: response?.status || 429, detail: lastDetail.slice(0, 1000) }, 429);
  }

  return new Response(response.body, {
    status: 200,
    headers: {
      "Content-Type": "audio/mpeg",
      "Cache-Control": "private, max-age=3600",
      "X-Jarvis-Voice": voice,
      "X-Jarvis-Language": "en-GB",
      "X-Jarvis-Version": "2.1.0",
    },
  });
}

function parseBilingual(raw: string): { speech: string; subtitle: string } {
  const cleaned = raw.trim().replace(/^```json\s*/i, "").replace(/```$/i, "").trim();
  try {
    const o = JSON.parse(cleaned);
    const speech = typeof o?.speech_en_gb === "string" ? o.speech_en_gb.trim() : "";
    const subtitle = typeof o?.subtitle_ko === "string" ? o.subtitle_ko.trim() : "";
    if (speech && subtitle) return { speech, subtitle };
    if (speech) return { speech, subtitle: speech };
  } catch {}
  return {
    speech: cleaned || "I could not generate a response just now.",
    subtitle: cleaned || "지금은 응답을 생성하지 못했습니다.",
  };
}

function sleep(ms: number): Promise<void> { return new Promise(resolve => setTimeout(resolve, ms)); }
function constantTimeEqual(a: string, b: string): boolean {
  const max = Math.max(a.length, b.length); let diff = a.length ^ b.length;
  for (let i = 0; i < max; i++) diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  return diff === 0;
}
function extractOutputText(data: any): string {
  if (!Array.isArray(data?.output)) return "";
  const parts: string[] = [];
  for (const item of data.output) {
    if (item?.type !== "message" || !Array.isArray(item.content)) continue;
    for (const content of item.content) if (content?.type === "output_text" && typeof content.text === "string") parts.push(content.text);
  }
  return parts.join("\n").trim();
}
function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), { status, headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
}
