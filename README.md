# MiniChat — a free ChatGPT-style AI chat you run yourself

A complete, self-hosted chat app with a familiar ChatGPT-style interface: streaming answers,
markdown + syntax-highlighted code, chat history, light/dark themes, image attachments.

It is **free** because it doesn't ship a model — it talks to any **OpenAI-compatible API**,
including the ones with genuinely free tiers (Google Gemini, Groq, OpenRouter, Mistral) or a
model running on your own machine via **Ollama** (free forever, no key, works offline).

No build step. No runtime dependencies. One `node server.js` and you're chatting.

---

## Quick start

```bash
node server.js          # needs Node 18+ (uses built-in fetch)
# open http://localhost:3000
```

1. Click the **gear icon** (Settings).
2. Pick a provider, paste your API key, press **Fetch models**, choose a model, **Save**.
3. Type a message. That's it.

> The first run opens Settings automatically. Your key is kept in your browser's
> `localStorage` (or in server env vars) and is only ever sent to the endpoint you chose.

### Try it instantly (no key at all)

```bash
node tools/demo-model.js &                                             # fake model on :4010
AI_BASE_URL=http://localhost:4010/v1 AI_MODEL=demo-model node server.js
```

That runs a tiny fake "model" so you can see streaming, markdown and code highlighting work
before you connect a real one.

---

## Free providers

| Provider | Endpoint (paste into Settings) | Free tier | Card? | Get a key |
| --- | --- | --- | --- | --- |
| **Google Gemini** | `https://generativelanguage.googleapis.com/v1beta/openai/` | Generous daily limits, multimodal | No | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) |
| **Groq** | `https://api.groq.com/openai/v1` | Fast open models, daily caps | No | [console.groq.com/keys](https://console.groq.com/keys) |
| **OpenRouter** | `https://openrouter.ai/api/v1` | Dozens of `:free` models | No | [openrouter.ai/keys](https://openrouter.ai/keys) |
| **Ollama (local)** | `http://localhost:11434/v1` | Unlimited — runs on your machine | No | install [ollama.com](https://ollama.com), then `ollama pull llama3.2` |
| **Mistral** | `https://api.mistral.ai/v1` | "Free mode" on some models | No | [console.mistral.ai](https://console.mistral.ai/api-keys) |
| **LM Studio / llama.cpp / vLLM** | `http://localhost:1234/v1` | Free (local) | — | — |
| **OpenAI** | `https://api.openai.com/v1` | Paid | Yes | [platform.openai.com](https://platform.openai.com/api-keys) |

Anything that speaks `/v1/chat/completions` works — pick **Custom** and paste the base URL.

### Ollama (100% free, private, offline)

```bash
ollama pull llama3.2          # or gemma3, qwen2.5, phi4, deepseek-r1…
ollama serve                  # already running on most installs
```

In Settings: provider **Ollama (local)** → endpoint `http://localhost:11434/v1` → **Fetch models**
→ pick your model → leave the API key empty → Save.

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
  demo-model.js        fake OpenAI-compatible server for demos
  hljs-entry.js        source for the highlight.js bundle
  smoke-test.mjs       headless jsdom test of the real UI
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
node tools/smoke-test.mjs
```

Boots the real UI in jsdom, sends a message to the demo model and asserts the streamed markdown,
code highlighting, history and chat switching all behave.

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
