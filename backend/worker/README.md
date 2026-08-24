# JARVIS Brain Worker V0.6

Cloud backend for JARVIS Mobile.

## Endpoints

- `GET /health` — health check
- `POST /v1/chat` — AI Brain using OpenAI Responses API
- `POST /v1/tts` — cinematic original JARVIS-style TTS

## Mobile-first deploy from GitHub Codespaces

```bash
cd /workspaces/jarvis-mobile/backend/worker
npm install
npx wrangler login
npx wrangler secret put OPENAI_API_KEY
npm run deploy
```

After deployment, Wrangler prints a URL similar to:

```text
https://jarvis-brain.<your-subdomain>.workers.dev
```

Set that URL in:

```text
app/src/main/java/com/choivoo/jarvis/config/JarvisConfig.kt
```

Do not commit the OpenAI API key. The API key belongs only in the Cloudflare Worker secret store.
