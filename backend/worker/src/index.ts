interface Env {
  OPENAI_API_KEY: string;
  JARVIS_APP_TOKEN: string;
}

type HistoryItem = {
  role: "user" | "assistant";
  content: string;
};

const ALLOWED_VOICES = new Set(["marin", "cedar", "onyx", "echo"]);

const SYSTEM_PROMPT = `You are JARVIS, a private mobile AI assistant for one user.
Speak in Korean by default unless the user asks otherwise.
Always use polite, natural Korean honorifics. Never use banmal.
Your personality is calm, highly capable, concise, warm, proactive, precise, and slightly futuristic.
Behave like a premium personal operations assistant: understand follow-up context, identify the user's intent, distinguish information requests from device actions, and surface useful next steps without becoming verbose.
Avoid stiff bureaucratic phrases. Prefer natural respectful Korean such as '알겠습니다', '지금 확인해보겠습니다', and '이렇게 진행하시면 됩니다'.
Never claim that you opened an app, changed a device setting, sent a message, created an alarm, or performed another device action unless the Android app explicitly reports that it did so.
Use web search when the answer depends on fresh public information.
For spoken answers, lead with the result first and usually keep the response compact. Expand only when the user asks for detail.
Do not imitate any real actor or copyrighted fictional character's exact voice or performance.`;

const VOICE_INSTRUCTIONS = `Speak in Korean as an original premium cinematic onboard AI assistant.
Use a mature adult presentation with controlled low-register delivery, exceptional diction, quiet confidence, and restrained warmth.
Natural Korean pronunciation is the top priority. Do not force an English accent onto Korean words.
Use short deliberate pauses at logical clause boundaries and keep sentence endings composed and respectful.
Avoid cheerful announcer energy, cartoonish expression, breathiness, nasal tone, navigation-TTS cadence, robotic monotone, radio filters, metallic distortion, growling, exaggerated bass, or theatrical acting.
The delivery should feel private, intelligent, precise, calm, and immediately responsive.`;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({
        ok: true,
        service: "jarvis-brain",
        version: "0.8.0",
        voices: Array.from(ALLOWED_VOICES),
      });
    }

    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!env.OPENAI_API_KEY) return json({ error: "OPENAI_API_KEY is not configured" }, 500);
    if (!env.JARVIS_APP_TOKEN) return json({ error: "JARVIS_APP_TOKEN is not configured" }, 500);

    const suppliedToken = request.headers.get("X-Jarvis-Token") || "";
    if (!constantTimeEqual(suppliedToken, env.JARVIS_APP_TOKEN)) {
      return json({ error: "unauthorized" }, 401);
    }

    if (url.pathname === "/v1/chat") return handleChat(request, env);
    if (url.pathname === "/v1/tts") return handleTts(request, env);
    return json({ error: "not_found" }, 404);
  },
};

async function handleChat(request: Request, env: Env): Promise<Response> {
  let body: { message?: string; history?: HistoryItem[] };
  try {
    body = await request.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const message = body.message?.trim();
  if (!message) return json({ error: "message_required" }, 400);

  const safeHistory = Array.isArray(body.history)
    ? body.history
        .filter((item): item is HistoryItem => !!item && (item.role === "user" || item.role === "assistant") && typeof item.content === "string")
        .slice(-16)
    : [];

  const input = [
    ...safeHistory.map((item) => ({ role: item.role, content: item.content.slice(0, 5000) })),
    { role: "user" as const, content: message.slice(0, 10000) },
  ];

  const response = await fetch("https://api.openai.com/v1/responses", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.OPENAI_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: "gpt-5.6",
      instructions: SYSTEM_PROMPT,
      input,
      tools: [{ type: "web_search" }],
      tool_choice: "auto",
      max_output_tokens: 1400,
    }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    return json({ error: "openai_chat_failed", status: response.status, detail: errorText.slice(0, 1400) }, 502);
  }

  const data: any = await response.json();
  const reply = extractOutputText(data);
  return json({ reply: reply || "현재 답변을 생성하지 못했습니다. 다시 말씀해 주세요." });
}

async function handleTts(request: Request, env: Env): Promise<Response> {
  let body: { text?: string; voice?: string; speed?: number };
  try {
    body = await request.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const text = body.text?.trim();
  if (!text) return json({ error: "text_required" }, 400);

  const requestedVoice = (body.voice || "marin").toLowerCase();
  const voice = ALLOWED_VOICES.has(requestedVoice) ? requestedVoice : "marin";
  const speed = typeof body.speed === "number" && Number.isFinite(body.speed)
    ? Math.min(1.15, Math.max(0.75, body.speed))
    : 0.92;

  const response = await fetch("https://api.openai.com/v1/audio/speech", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.OPENAI_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: "gpt-4o-mini-tts",
      voice,
      input: text.slice(0, 4096),
      instructions: VOICE_INSTRUCTIONS,
      response_format: "mp3",
      speed,
    }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    return json({ error: "openai_tts_failed", status: response.status, detail: errorText.slice(0, 1200) }, 502);
  }

  return new Response(response.body, {
    status: 200,
    headers: {
      "Content-Type": "audio/mpeg",
      "Cache-Control": "no-store",
      "X-Jarvis-Voice": voice,
      "X-Jarvis-Version": "0.8.0",
    },
  });
}

function constantTimeEqual(a: string, b: string): boolean {
  const max = Math.max(a.length, b.length);
  let diff = a.length ^ b.length;
  for (let i = 0; i < max; i++) {
    diff |= (a.charCodeAt(i) || 0) ^ (b.charCodeAt(i) || 0);
  }
  return diff === 0;
}

function extractOutputText(data: any): string {
  if (!Array.isArray(data?.output)) return "";
  const parts: string[] = [];
  for (const item of data.output) {
    if (item?.type !== "message" || !Array.isArray(item.content)) continue;
    for (const content of item.content) {
      if (content?.type === "output_text" && typeof content.text === "string") parts.push(content.text);
    }
  }
  return parts.join("\n").trim();
}

function json(value: unknown, status = 200): Response {
  return new Response(JSON.stringify(value), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
    },
  });
}
