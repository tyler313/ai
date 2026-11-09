# MiniChat — a free ChatGPT-style AI you run yourself

A complete, self-hosted chat app with a familiar ChatGPT-style interface: streaming answers,
markdown + syntax-highlighted code, chat history, light/dark themes, image attachments.

**Open it and chat.** On first run it offers three ways to get a model behind the UI — and the
default one needs no account, no API key and no install:

| Option | Sign-up | Quality | Trade-off |
| --- | --- | --- | --- |
| **Keyless** (default) | **none** | strong open models — DeepSeek V4 Pro, GLM-5.2, MiniMax-M3, Gemma 4 | community endpoints; rate limited, model list rotates |
| **Free API key** | free, ~30 s | closest to frontier (Gemini 2.5 Pro/Flash, Groq) | prompts go to that provider; free tiers are rate limited |
| **In your browser** | **none** | up to 8B (Qwen3 8B, Llama 3.1 8B) | private & offline, but the models are smaller |

All three are one click apart in Settings. Nothing needs installing on your machine apart from
Node, and there is no build step and no runtime dependencies.

> ### The honest bit about "as good as Claude"
> Nobody hands out a frontier model for free — running one costs real money per message. So:
> - **Keyless endpoints** are the best "no signup" deal: they serve today's strong open models
>   (DeepSeek V4 Pro, GLM-5.2, MiniMax-M3, Gemma 4 31B) with 30–60 requests/minute and no key.
>   Quality is good — well above a small local model — but a tier below Claude/GPT-5, and these
>   are community-run, so they come and go.
> - **A free Gemini key** (no card) gets you Gemini 2.5 Pro/Flash, which is the closest you can
>   legally get to frontier quality for £0. Rate limited, and Google can use free-tier content.
> - **In-browser models** are private and offline but capped at ~8B, so they're the weakest here.
>
> The app says this on the first-run screen rather than overselling it, and switching is one click.

---

## Quick start

```bash
node server.js          # needs Node 18+ (uses built-in fetch)
# open http://localhost:3000
```

Pick **"Start chatting — no signup"** and you're talking to a model in about two seconds.

### How the keyless option works

Keyless gateways are community projects that expose an OpenAI-compatible API with no
authentication (e.g. [LLM7.io](https://api.llm7.io/v1),
[Pollinations](https://text.pollinations.ai/openai)). Because they rotate models and sometimes
go down, MiniChat doesn't hard-code one:

- the server probes a list of endpoints and uses **whichever answers right now**
  (`GET /api/public/discover`), and reports each one's health (`GET /api/public/status`,
  shown by the **Diagnose** button in Settings);
- the UI shows **"Find a working endpoint"** — press it any time to re-probe and switch;
- the model dropdown is filled from that endpoint's live `/v1/models`, best-scoring model first;
- if one 429s, 5xxes or dies mid-chat, the server automatically fails over to the next
  endpoint (re-picking a model that exists there), and the UI re-probes and retries once;
- if every endpoint is unreachable, the error says so and suggests a free API key.

Set your own list with `PUBLIC_ENDPOINTS="https://a/v1,https://b/v1"`.

### Switching to the best free quality

Settings → **Free API key** → pick a provider → paste a key → **Fetch models** → Save:

| Provider | Endpoint | Free tier | Card? | Get a key |
| --- | --- | --- | --- | --- |
| **Google Gemini** | `https://generativelanguage.googleapis.com/v1beta/openai/` | Generous, multimodal | No | [aistudio.google.com/apikey](https://aistudio.google.com/apikey) |
| **Groq** | `https://api.groq.com/openai/v1` | Very fast open models | No | [console.groq.com/keys](https://console.groq.com/keys) |
| **OpenRouter** | `https://openrouter.ai/api/v1` | Dozens of `:free` models | No | [openrouter.ai/keys](https://openrouter.ai/keys) |
| **Custom** | any `/v1/chat/completions` | — | — | — |

### Private / offline

Settings → **In your browser** → pick a model (Qwen3 8B, Llama 3.1 8B, Qwen2.5 Coder 7B…).
Weights download once from Hugging Face, then run on your GPU via WebGPU — nothing leaves the
machine. Needs Chrome/Edge 113+ or Safari 26+.

### Try it instantly (fake backend)

```bash
node tools/demo-model.js &                                   # fake provider on :4010
PUBLIC_ENDPOINTS=http://localhost:4010/v1 node server.js     # pretend it's a keyless endpoint
```

Now the keyless flow, discovery and streaming all work against a canned model — useful for
seeing the UI before you connect anything real.

---
## Features

- **Keyless by default** — no account, no key: community endpoints are probed and the best model
  auto-selected, with one-click re-discovery when one goes down.
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
| `GET /api/public/discover` | Probes the keyless endpoints, returns the first that answers plus its model list ranked best-first |
| `GET /api/public/status` | Health of every keyless endpoint (used by **Diagnose**) |
| `POST /api/chat` with `failover: true` | Tries the requested endpoint, then the other keyless endpoints (and alternate models) before giving up |
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
  demo-model.js        fake OpenAI-compatible server (no-key demo backend)
  hljs-entry.js        source for the highlight.js bundle
  smoke-test.mjs       headless jsdom test of the real UI (31 checks)
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
node tools/demo-model.js &
PUBLIC_ENDPOINTS=http://localhost:4010/v1 node server.js &
node tools/smoke-test.mjs
```

Boots the real UI in jsdom and drives it: first-run wizard → keyless discovery → model
auto-selection → streamed markdown answer with highlighted code → in-browser engine (asserts it
fails gracefully without WebGPU) → free-API-key engine → theme toggle and history. 31 checks.

---

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| "No endpoint configured" | Settings → choose a provider, model, Save |
| `401 Rejected` | Key is wrong/expired, or pasted with a space |
| `404 Not found` | Check the endpoint URL and model name (*Fetch models* shows valid names) |
| `429 Rate limited` | Free tiers cap requests/minute & day — wait, or switch provider |
| "Couldn't connect" (LM Studio / llama.cpp) | The local server isn't running, or the port differs |
| Keyless endpoint keeps failing | Press **Diagnose** to see which endpoints answer, then **Find a working endpoint**; or switch to a free Gemini/Groq key |
| `Server responded 502` | The gateway refused the request. Keyless mode now retries other endpoints automatically; if it still fails, your network may block those hosts — use a free API key |
| Model answers but text isn't highlighted | Only bundled languages highlight; it still renders as plain code |

---

## License

MIT — see [LICENSE](LICENSE). Vendored libraries keep their own licenses (marked, highlight.js, DOMPurify).
