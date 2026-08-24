# REPLOG Backend

Backend for the RepLog Android app: accounts, cloud sync, and the AI coach (chat + food-photo vision).

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/api/health` | — | Status + whether AI is configured |
| POST | `/api/auth/register` | — | Create account `{email, password, name}` |
| POST | `/api/auth/login` | — | Get JWT `{email, password}` |
| POST | `/api/sync` | Bearer JWT | Push food/workout/weight entries (upsert by client id) |
| POST | `/api/ai/chat` | — | LLM coach chat `{messages, context}` |
| POST | `/api/ai/vision` | — | Food photo analysis `{image_base64, prompt}` |

## Setup

Requires Node.js 20+.

```bash
cd backend
npm install
```

Configure by creating a `.env` file (see `.env.example`). The only optional-but-important one is `OPENAI_API_KEY`, which enables the AI endpoints.

```bash
npm start        # production
npm run dev      # auto-reload on change
```

Server listens on port `4000` by default.

## Connecting the Android app

- **Emulator**: the app's default `http://10.0.2.2:4000/` already points at your PC.
- **Physical phone**: set your PC's LAN IP in `gradle.properties` of the app project, then rebuild:

```properties
replog.apiUrl=http://192.168.1.XXX:4000/
```

(Phone and PC must be on the same Wi-Fi network.)

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `PORT` | `4000` | HTTP port |
| `DB_PATH` | `backend/data/replog.db` | SQLite database file |
| `JWT_SECRET` | random per start | HMAC secret for tokens — **set this in production** or tokens invalidate on restart |
| `TOKEN_TTL_SECONDS` | `2592000` (30 days) | Token lifetime |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Any OpenAI-compatible API (OpenAI, Gemini via compat layer, OpenRouter, Ollama…) |
| `OPENAI_API_KEY` | empty | Enables `/api/ai/chat` and `/api/ai/vision` when set |
| `CHAT_MODEL` | `gpt-4o-mini` | Model for coach chat |
| `VISION_MODEL` | `gpt-4o-mini` | Model for food photos |

## Production notes

- Set `JWT_SECRET` to a long random string, otherwise sessions reset on every deploy.
- Data is stored in SQLite (`data/replog.db`) — back that file up.
- The AI endpoints are unauthenticated by design (matches the current app client). If you expose this server publicly, put it behind a reverse proxy with rate limiting.
