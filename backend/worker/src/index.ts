interface Env {
  OPENAI_API_KEY: string;
  JARVIS_APP_TOKEN: string;
}

type HistoryItem = {
  role: "user" | "assistant";
  content: string;
};

const SYSTEM_PROMPT = `You are JARVIS, a private mobile AI assistant for one user.
Speak in Korean by default unless the user asks otherwise.
Your personality is calm, intelligent, concise, warm, fast, and slightly futuristic.
Avoid stiff phrases like '요청을 처리하였습니다'. Prefer natural Korean such as '알겠어', '지금 확인해볼게', and '이렇게 하면 돼'.
Never claim that you opened an app, changed a device setting, sent a message, created an alarm, or performed another device action unless the Android app explicitly reports that it did so.
For device-local actions, explain that the mobile tool layer should perform the action.
Use web search when the answer depends on fresh public information.
Keep most spoken answers short enough to sound natural aloud, but provide more detail when the user clearly asks for it.
Do not imitate any real actor or copyrighted fictional character's exact voice or performance.`;

const VOICE_INSTRUCTIONS = `Speak Korean in a distinctive original cinematic AI-assistant voice.
Use a low, resonant, composed adult male timbre with polished diction, controlled pacing, quiet confidence, and restrained warmth.
Keep Korean pronunciation natural and clear. Pronounce English names with refined British-style diction when appropriate.
Use subtle pauses and a faintly dry, intelligent delivery. Avoid exaggerated robot effects, radio distortion, growling, theatrical acting, or imitation of any actor or fictional character.
The result should feel premium, calm, futuristic, precise, and trustworthy.`;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "jarvis-brain", version: "0.6.0" });
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
        .slice(-12)
    : [];

  const input = [
    ...safeHistory.map((item) => ({ role: item.role, content: item.content.slice(0, 4000) })),
    { role: "user" as const, content: message.slice(0, 8000) },
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
      max_output_tokens: 1000,
    }),
  });

  if (!response.ok) {
    const errorText = await response.text();
    return json({ error: "openai_chat_failed", status: response.status, detail: errorText.slice(0, 1200) }, 502);
  }

  const data: any = await response.json();
  const reply = extractOutputText(data);
  return json({ reply: reply || "지금은 답을 만들지 못했어. 다시 한 번 말해줘." });
}

async function handleTts(request: Request, env: Env): Promise<Response> {
  let body: { text?: string };
  try {
    body = await request.json();
  } catch {
    return json({ error: "invalid_json" }, 400);
  }

  const text = body.text?.trim();
  if (!text) return json({ error: "text_required" }, 400);

  const response = await fetch("https://api.openai.com/v1/audio/speech", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${env.OPENAI_API_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      model: "gpt-4o-mini-tts",
      voice: "cedar",
      input: text.slice(0, 4096),
      instructions: VOICE_INSTRUCTIONS,
      response_format: "mp3",
      speed: 0.96,
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
