# MiniChat — a free ChatGPT-style AI you run yourself, with no API key

A complete, self-hosted chat app with a familiar ChatGPT-style interface: streaming answers,
markdown + syntax-highlighted code, chat history, light/dark themes, image attachments.

**You can use it without signing up for anything.** On first run it asks where you want the
model to run, and two of the three options need no account and no API key:

| Where the model runs | Sign-up? | Cost | Quality | Best for |
| --- | --- | --- | --- | --- |
| **In your browser** (WebGPU) | none | free | 1–8B open models | "just let me chat, install nothing" |
| **On your machine** (Ollama) | none | free forever, offline | scales with your RAM — 3B → 70B | best free quality, private |
| **Free API key** (Gemini/Groq/OpenRouter) | free signup | free tier | strongest models available free | best answers, rate limited |

All three are wired in and switchable from Settings. The first two send your prompts nowhere.

> ### The honest bit about "as good as Claude"
> A frontier model (Claude, GPT-5, Gemini Pro) cannot be run for free — it needs a paid API or a
> server farm. What you *can* get for free, with no key:
> - **8B model** (browser or laptop): a fast, helpful assistant — great at drafting, explaining,
>   simple code. Noticeably below Claude on hard reasoning.
> - **70B model** on a big machine (~48 GB RAM): genuinely strong, useful for real work, still a
>   tier under frontier models.
> - **Free API tiers** (Gemini 2.5 Flash/Pro, Groq): closest to the frontier, but rate limited and
>   your prompts go to someone else's computer.
>
> MiniChat makes all of those one click apart, so you can pick your trade-off instead of being
> locked into one.

---

## Quick start

```bash
node server.js          # needs Node 18+ (uses built-in fetch)
# open http://localhost:3000
```

Pick one of the three options in the first-run dialog and you're chatting. No config files, no
`.env`, no build step, no runtime dependencies.

### Option A — in your browser (nothing to install)

Choose **"Run it in your browser"**, pick a model, send a message. The model downloads once
(1–6 GB) and is then cached by your browser; from then on it runs on your GPU, offline, and
nothing leaves your machine.

- Needs **Chrome or Edge 113+** (or Safari 26+) — these have WebGPU.
- Rough guide: 8 GB RAM → Qwen3 4B; 16 GB → Qwen3 8B / Llama 3.1 8B; weak machine → Qwen3 1.7B.

### Option B — on your machine (best free quality)

```bash
# 1. install Ollama: https://ollama.com/download
ollama serve                    # usually already running
ollama pull llama3.2:3b         # 4 GB RAM  — fast
ollama pull qwen2.5:7b          # 8 GB RAM  — strong all-rounder
ollama pull deepseek-r1:8b      # 8 GB RAM  — reasoning / maths / code
ollama pull llama3.3:70b        # 48 GB RAM — best free quality
```

In MiniChat choose **"Run it on your machine"** → **Detect**. It lists what you have installed,
and shows one-click `↓ Install` buttons (with a progress bar) for everything else — it drives
`ollama pull` for you. Free, offline, private, no account, no key.

### Option C — free API key (strongest models)

| Provider | Endpoint (paste into Settings) | Free tier | Card? | Get a key |
| --- | --- | --- | --- | --- |
| **Google Gemini** | `https://generativelanguage.googleapis.com/v1beta/openai/` | Generous daily limits, multimodal | No | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) |
| **Groq** | `https://api.groq.com/openai/v1` | Fast open models, daily caps | No | [console.groq.com/keys](https://console.groq.com/keys) |
| **OpenRouter** | `https://openrouter.ai/api/v1` | Dozens of `:free` models | No | [openrouter.ai/keys](https://openrouter.ai/keys) |
| **LM Studio / llama.cpp / vLLM** | `http://localhost:1234/v1` | Free (local) | — | — |
| **OpenAI** | `https://api.openai.com/v1` | Paid | Yes | [platform.openai.com](https://platform.openai.com/api-keys) |

Anything that speaks `/v1/chat/completions` works — pick **Custom** and paste the base URL.

### Try it instantly (no key, no model, no install)

```bash
node tools/demo-model.js &                                             # fake provider on :4010
node tools/mock-ollama.js &                                            # fake Ollama on :11434
node server.js
```

The first is a fake API, the second a fake local server, so you can see streaming, markdown and
the local-model installer working before you connect anything real.

---
## Features

- **Streaming answers** — token-by-token, like ChatGPT, with a blinking caret and a Stop button.
- **Markdown** — headings, lists, tables, links, blockquotes, inline code.
- **Code blocks** — syntax highlighting (35 languages), one-click copy, language label.
- **Chat history** — saved in your browser, grouped by date, searchable, rename/delete,
  import/export as JSON.
- **Light & dark themes** — follows your system setting, toggle in the header.
- **Image attachments** — drag-free attach or paste; sent to vision models
  (Gemini Flash, Llama 4, etc.).
- **Regenerate / edit** — retry any answer, or edit your prompt and re-run from there.
- **Reasoning models** — chain-of-thought from models like DeepSeek-R1 shows in a collapsible
  "Thinking…" block.
- **Settings** — system prompt, temperature, max tokens, custom endpoint, model picker.
- **Keyboard** — `Enter` send, `Shift+Enter` newline, `Esc` stop, `Cmd/Ctrl+K` search,
  `Cmd/Ctrl+Shift+O` new chat.
- **Responsive** — collapses into a drawer on phones.

---

## Configuration

### In the UI (Settings)

| Field | Notes |
| --- | --- |
| Provider | Preset that fills in the endpoint, or **Custom** |
| API endpoint | Any OpenAI-compatible base URL |
| API key | Stored in `localStorage`; blank for local servers |
| Model | Type it, or press **Fetch models** to list what your key can use |
| System prompt / temperature / max tokens | Optional tuning |

### Server-side (optional)

```bash
PORT=3000 \
AI_BASE_URL=https://api.groq.com/openai/v1 \
AI_MODEL=llama-3.3-70b-versatile \
AI_API_KEY=gsk_… \
node server.js
```

Server values act as **defaults** — whatever you set in the UI wins. Handy if you want everyone
using your deployment to share one key.

---

## How it works

```
browser (public/)  ──POST /api/chat──▶  server.js  ──streaming SSE──▶  provider
                   ◀── SSE (delta / reasoning / done) ───────────────────
```

The Node server is ~350 lines with **zero dependencies**. It serves the static UI and proxies
chat requests, which keeps API keys off the page, avoids CORS issues, and lets one UI talk to
a dozen different backends.

| Route | Purpose |
| --- | --- |
| `GET /api/providers` | Provider presets + server-side defaults |
| `POST /api/models` | Lists models available at an endpoint (used by *Fetch models* / *Test connection*) |
| `POST /api/local` | Probes a **local** server (Ollama `/api/tags`, else `/v1/models`) — the browser can't call `http://localhost` from an https page, so the server does it |
| `POST /api/local/pull` | Streams `ollama pull` progress to the UI as SSE |
| `POST /api/chat` | Streams a chat completion as normalized SSE (`delta`, `reasoning`, `error`, `done`) |
| `GET /api/health` | Liveness check |

### Project layout

```
server.js              zero-dep HTTP server + streaming proxy
public/
  index.html           markup
  styles.css           ChatGPT-style theme (light + dark)
  app.js               state, rendering, streaming, settings
  vendor/              marked, highlight.js, DOMPurify (bundled, works offline)
tools/
  demo-model.js        fake OpenAI-compatible server (no-key demo of the API engine)
  mock-ollama.js       fake Ollama: /api/tags, /api/ps, /api/pull, /v1/chat/completions
  hljs-entry.js        source for the highlight.js bundle
  smoke-test.mjs       headless jsdom test of the real UI (29 checks)
```

### Vendored libraries

Rebuild the syntax highlighter bundle after editing `tools/hljs-entry.js`:

```bash
npm install     # dev-only: esbuild, marked, highlight.js, dompurify, jsdom
npm run vendor
```

The app itself has **no runtime dependencies**.

---

## Tests

```bash
node server.js &
node tools/demo-model.js &
node tools/mock-ollama.js &
node tools/smoke-test.mjs
```

Boots the real UI in jsdom and drives it: first-run wizard → local engine → Ollama detection →
one-click model install with progress → streamed markdown answer → in-browser engine (asserts it
fails gracefully without WebGPU) → free-API engine → theme toggle and history. 29 checks.

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| "No endpoint configured" | Settings → choose a provider, model, Save |
| `401 Rejected` | Key is wrong/expired, or pasted with a space |
| `404 Not found` | Check the endpoint URL and model name (*Fetch models* shows valid names) |
| `429 Rate limited` | Free tiers cap requests/minute & day — wait, or switch provider |
| "Couldn't connect" (Ollama/LM Studio) | The local server isn't running, or the port differs |
| Model answers but text isn't highlighted | Only bundled languages highlight; it still renders as plain code |

---

## License

MIT — see [LICENSE](LICENSE). Vendored libraries keep their own licenses (marked, highlight.js, DOMPurify).
