/**
 * MiniChat server
 * ---------------
 * Zero-dependency Node HTTP server that does two things:
 *   1. serves the static chat UI in ./public
 *   2. proxies chat requests to any OpenAI-compatible endpoint (streaming)
 *
 * Why a proxy? It keeps API keys off the page, dodges browser CORS problems,
 * and lets the same UI talk to Gemini, Groq, OpenRouter, Ollama, LM Studio,
 * llama.cpp, vLLM ... anything that speaks /v1/chat/completions.
 *
 * Run:  node server.js            (then open http://localhost:3000)
 * Env:  PORT, HOST, AI_BASE_URL, AI_API_KEY, AI_MODEL
 */

import http from 'node:http';
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC_DIR = path.join(__dirname, 'public');
const PORT = Number(process.env.PORT || 3000);
const HOST = process.env.HOST || '0.0.0.0';

/** Free-tier OpenAI-compatible providers, offered as presets in the UI. */
const PROVIDERS = [
  {
    id: 'gemini',
    name: 'Google Gemini',
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai/',
    model: 'gemini-2.5-flash',
    keyUrl: 'https://aistudio.google.com/apikey',
    note: 'Free tier, no credit card. Generous limits, multimodal.',
  },
  {
    id: 'groq',
    name: 'Groq',
    baseUrl: 'https://api.groq.com/openai/v1',
    model: 'llama-3.3-70b-versatile',
    keyUrl: 'https://console.groq.com/keys',
    note: 'Free plan, extremely fast output on open models.',
  },
  {
    id: 'openrouter',
    name: 'OpenRouter',
    baseUrl: 'https://openrouter.ai/api/v1',
    model: 'deepseek/deepseek-chat-v3-0324:free',
    keyUrl: 'https://openrouter.ai/keys',
    note: 'One key, dozens of ":free" models. 50 req/day on the free tier.',
  },
  {
    id: 'ollama',
    name: 'Ollama (local)',
    baseUrl: 'http://localhost:11434/v1',
    model: 'llama3.2',
    keyUrl: 'https://ollama.com/download',
    note: 'Runs on your own machine. 100% free, no key, works offline.',
  },
  {
    id: 'lmstudio',
    name: 'LM Studio (local)',
    baseUrl: 'http://localhost:1234/v1',
    model: 'local-model',
    keyUrl: 'https://lmstudio.ai/',
    note: 'Local model server, OpenAI-compatible.',
  },
  {
    id: 'openai',
    name: 'OpenAI',
    baseUrl: 'https://api.openai.com/v1',
    model: 'gpt-4o-mini',
    keyUrl: 'https://platform.openai.com/api-keys',
    note: 'Paid — bring your own key.',
  },
  {
    id: 'custom',
    name: 'Custom (OpenAI-compatible)',
    baseUrl: '',
    model: '',
    keyUrl: '',
    note: 'Any endpoint that speaks /v1/chat/completions.',
  },
];

// ---------------------------------------------------------------- utilities

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
  '.map': 'application/json; charset=utf-8',
};

function json(res, status, data) {
  const body = JSON.stringify(data);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': Buffer.byteLength(body),
    'cache-control': 'no-store',
  });
  res.end(body);
}

function readBody(req, limit = 8 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > limit) {
        reject(Object.assign(new Error('Request body too large'), { status: 413 }));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => {
      if (!chunks.length) return resolve({});
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')));
      } catch (err) {
        reject(Object.assign(new Error('Invalid JSON body'), { status: 400 }));
      }
    });
    req.on('error', reject);
  });
}

function resolveBaseUrl(raw) {
  const url = String(raw || '').trim().replace(/\/+$/, '');
  if (!url) throw Object.assign(new Error('No endpoint configured. Open Settings and pick a provider.'), { status: 400 });
  let parsed;
  try {
    parsed = new URL(url);
  } catch {
    throw Object.assign(new Error(`Not a valid URL: ${url}`), { status: 400 });
  }
  if (!/^https?:$/.test(parsed.protocol)) {
    throw Object.assign(new Error('Only http/https endpoints are supported.'), { status: 400 });
  }
  return url;
}

/** Turn non-2xx upstream responses into readable messages. */
async function describeHttpError(url, res) {
  const text = await res.text().catch(() => '');
  let detail = text.slice(0, 800);
  try {
    const parsed = JSON.parse(detail);
    detail = parsed?.error?.message || parsed?.message || detail;
  } catch {
    /* keep raw text */
  }
  const hints = {
    401: 'Rejected (401) — the API key looks wrong, expired, or missing.',
    403: 'Forbidden (403) — the key is valid but this model/endpoint is not available to it.',
    404: 'Not found (404) — check the endpoint URL and model name.',
    429: 'Rate limited (429) — free tiers cap requests per minute/day. Wait a bit or switch provider.',
    500: 'Upstream server error (500).',
    502: 'Bad gateway (502) — the provider is having a moment. Try again.',
    503: 'Service unavailable (503) — provider overloaded or model still loading (Ollama does this).',
  };
  const prefix = hints[res.status] || `Request failed (${res.status})`;
  return `${prefix}${detail ? `\n\n${detail}` : ''}\n\n${new URL(url).host}`;
}

const TIMEOUT_MS = Number(process.env.REQUEST_TIMEOUT_MS || 5 * 60 * 1000);

/** GET helper with a timeout; returns parsed JSON or throws a friendly Error. */
async function getJson(url, headers) {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  try {
    const res = await fetch(url, { headers, signal: ctrl.signal });
    if (!res.ok) throw new Error(await describeHttpError(url, res));
    return await res.json();
  } catch (err) {
    if (err.name === 'AbortError') throw new Error('Request timed out.');
    throw err;
  } finally {
    clearTimeout(timer);
  }
}

// ------------------------------------------------------------------- routes

/** GET /api/providers — preset list + whether the server has its own key. */
function handleProviders(res) {
  json(res, 200, {
    providers: PROVIDERS,
    serverDefaults: {
      baseUrl: process.env.AI_BASE_URL || '',
      model: process.env.AI_MODEL || '',
      hasKey: Boolean(process.env.AI_API_KEY),
    },
  });
}

/** POST /api/models { baseUrl, apiKey } -> { models: [{id, ...}] } */
async function handleModels(req, res) {
  const { baseUrl, apiKey } = await readBody(req);
  const base = resolveBaseUrl(baseUrl);
  const headers = {};
  const key = apiKey || process.env.AI_API_KEY || '';
  if (key) headers.authorization = `Bearer ${key}`;
  const data = await getJson(`${base}/models`, headers);
  let models = Array.isArray(data?.data) ? data.data : Array.isArray(data) ? data : [];
  models = models
    .map((m) => (typeof m === 'string' ? { id: m } : m))
    .filter((m) => m && m.id)
    .map((m) => ({ id: m.id, name: m.name || m.id, owned_by: m.owned_by || '', context: m.context_length || 0 }));
  models.sort((a, b) => a.id.localeCompare(b.id));
  json(res, 200, { models });
}

/** `http://host:11434/v1` -> `http://host:11434` */
function originOf(baseUrl) {
  return String(baseUrl).trim().replace(/\/+$/, '').replace(/\/v1(beta)?$/, '');
}

const fmtBytes = (n) => {
  const b = Number(n) || 0;
  if (!b) return '';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.min(units.length - 1, Math.floor(Math.log(b) / Math.log(1024)));
  return `${(b / 1024 ** i).toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
};

/**
 * POST /api/local { baseUrl }
 * Probes a *local* model server (Ollama, LM Studio, llama.cpp, vLLM…).
 * Browser pages on https cannot call http://localhost directly (mixed content),
 * so this runs server-side, on the same machine as the browser.
 */
async function handleLocalProbe(req, res) {
  const { baseUrl } = await readBody(req);
  const base = resolveBaseUrl(baseUrl);
  const origin = originOf(base);

  // 1. Ollama native API: /api/tags and /api/ps
  try {
    const tags = await getJson(`${origin}/api/tags`, {});
    const models = Array.isArray(tags?.models) ? tags.models : [];
    if (models.length || Array.isArray(tags?.models)) {
      let running = [];
      try {
        const ps = await getJson(`${origin}/api/ps`, {});
        running = (ps?.models || []).map((m) => m.name);
      } catch {
        /* older Ollama builds */
      }
      return json(res, 200, {
        kind: 'ollama',
        origin,
        models: models.map((m) => ({
          id: m.name,
          label: m.name,
          size: m.size || 0,
          sizeText: fmtBytes(m.size),
          params: m.details?.parameter_size || '',
          quant: m.details?.quantization_level || '',
          family: m.details?.family || '',
          running: running.includes(m.name),
        })),
      });
    }
  } catch {
    /* not Ollama — fall through */
  }

  // 2. Plain OpenAI-compatible server
  const data = await getJson(`${base}/models`, {});
  const models = Array.isArray(data?.data) ? data.data : Array.isArray(data) ? data : [];
  json(res, 200, {
    kind: 'openai',
    origin,
    models: models.map((m) => ({
      id: typeof m === 'string' ? m : m.id,
      label: typeof m === 'string' ? m : m.id,
      size: 0,
      sizeText: '',
      params: '',
      quant: '',
      family: '',
      running: false,
    })),
  });
}

/**
 * POST /api/local/pull { baseUrl, model }
 * Streams Ollama's `ollama pull` progress back as SSE so the UI can show a bar:
 *   event: progress  data: { status, completed, total, percent }
 */
async function handleLocalPull(req, res) {
  const { baseUrl, model } = await readBody(req);
  const origin = originOf(resolveBaseUrl(baseUrl));
  if (!model) throw Object.assign(new Error('No model name given'), { status: 400 });

  const ctrl = new AbortController();
  let upstream;
  try {
    upstream = await fetch(`${origin}/api/pull`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ name: model, stream: true }),
      signal: ctrl.signal,
    });
  } catch (err) {
    throw new Error(`Could not reach ${origin}. Is Ollama running? (${err.message})`);
  }
  if (!upstream.ok) throw new Error(await describeHttpError(`${origin}/api/pull`, upstream));

  res.writeHead(200, {
    'content-type': 'text/event-stream; charset=utf-8',
    'cache-control': 'no-cache, no-transform',
    connection: 'keep-alive',
    'x-accel-buffering': 'no',
  });
  const send = (event, data) => res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  req.on('close', () => ctrl.abort());

  const decoder = new TextDecoder();
  let buf = '';
  try {
    for await (const chunk of upstream.body) {
      buf += decoder.decode(chunk, { stream: true });
      let idx;
      while ((idx = buf.indexOf('\n')) !== -1) {
        const line = buf.slice(0, idx).trim();
        buf = buf.slice(idx + 1);
        if (!line) continue;
        let evt;
        try {
          evt = JSON.parse(line);
        } catch {
          continue;
        }
        if (evt.error) {
          send('error', { message: evt.error });
          return res.end();
        }
        const total = Number(evt.total) || 0;
        const completed = Number(evt.completed) || 0;
        send('progress', {
          status: evt.status || '',
          digest: evt.digest || '',
          total,
          completed,
          percent: total ? Math.min(100, Math.round((completed / total) * 100)) : 0,
        });
      }
    }
    send('done', { ok: true });
  } catch (err) {
    if (err.name !== 'AbortError') send('error', { message: err.message });
  }
  res.end();
}

/**
 * POST /api/chat — streams the upstream SSE response back as a normalized SSE
 * stream: `delta` (text), `reasoning` (chain-of-thought), `error`, `done`.
 */
async function handleChat(req, res) {
  const body = await readBody(req);
  const base = resolveBaseUrl(body.baseUrl || process.env.AI_BASE_URL);
  const apiKey = body.apiKey || process.env.AI_API_KEY || '';

  const messages = Array.isArray(body.messages) ? body.messages : [];
  if (!messages.length) throw Object.assign(new Error('No messages to send.'), { status: 400 });

  const payload = {
    model: body.model || process.env.AI_MODEL,
    messages: messages.map((m) => ({
      role: m.role,
      content:
        Array.isArray(m.attachments) && m.attachments.length
          ? [{ type: 'text', text: String(m.content || '') }].concat(
              m.attachments
                .filter((a) => a?.dataUrl)
                .map((a) => ({ type: 'image_url', image_url: { url: a.dataUrl } }))
            )
          : String(m.content ?? ''),
    })),
    stream: true,
  };
  if (!payload.model) throw Object.assign(new Error('No model selected. Open Settings and pick one.'), { status: 400 });
  if (typeof body.temperature === 'number') payload.temperature = body.temperature;
  if (typeof body.maxTokens === 'number' && body.maxTokens > 0) payload.max_tokens = body.maxTokens;

  const headers = { 'content-type': 'application/json', accept: 'text/event-stream' };
  if (apiKey) headers.authorization = `Bearer ${apiKey}`;
  if (body.extraHeaders && typeof body.extraHeaders === 'object') Object.assign(headers, body.extraHeaders);

  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  let upstream;
  try {
    upstream = await fetch(`${base}/chat/completions`, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
      signal: ctrl.signal,
    });
  } catch (err) {
    clearTimeout(timer);
    const friendly =
      err.name === 'AbortError'
        ? 'Request timed out.'
        : err.cause?.code === 'ECONNREFUSED' || /ECONNREFUSED/.test(String(err.message))
          ? `Could not connect to ${base}. If this is a local server (Ollama, LM Studio) make sure it is running.`
          : err.cause?.code === 'ENOTFOUND' || /ENOTFOUND|getaddrinfo/.test(String(err.message))
            ? `Could not resolve ${new URL(base).host}. Check the endpoint URL and your internet connection.`
            : `Network error: ${err.message}`;
    throw new Error(friendly);
  }

  if (!upstream.ok) {
    clearTimeout(timer);
    throw new Error(await describeHttpError(`${base}/chat/completions`, upstream));
  }

  res.writeHead(200, {
    'content-type': 'text/event-stream; charset=utf-8',
    'cache-control': 'no-cache, no-transform',
    connection: 'keep-alive',
    'x-accel-buffering': 'no',
  });

  const send = (event, data) => res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  const sendError = (message) => send('error', { message });
  const finish = () => {
    clearTimeout(timer);
    try {
      res.end();
    } catch {
      /* already closed */
    }
  };
  req.on('close', () => ctrl.abort());

  const contentType = upstream.headers.get('content-type') || '';

  // Some servers (a few Ollama proxies) ignore stream:true and return plain JSON.
  if (!contentType.includes('event-stream')) {
    try {
      const data = await upstream.json();
      const text = data?.choices?.[0]?.message?.content || data?.message?.content || '';
      if (text) send('delta', { text });
      send('done', { finish_reason: data?.choices?.[0]?.finish_reason || 'stop', usage: data?.usage || null });
    } catch (err) {
      sendError(`Unexpected response from provider: ${err.message}`);
    }
    return finish();
  }

  const decoder = new TextDecoder();
  let buffer = '';
  let usage = null;
  let finishReason = null;

  try {
    for await (const chunk of upstream.body) {
      buffer += decoder.decode(chunk, { stream: true });
      let idx;
      while ((idx = buffer.indexOf('\n\n')) !== -1) {
        const raw = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        const line = raw.split('\n').find((l) => l.startsWith('data:'));
        if (!line) continue;
        const data = line.slice(5).trim();
        if (!data || data === '[DONE]') continue;
        let evt;
        try {
          evt = JSON.parse(data);
        } catch {
          continue;
        }
        if (evt.usage) usage = evt.usage;
        const choice = evt.choices?.[0];
        if (!choice) continue;
        if (choice.finish_reason) finishReason = choice.finish_reason;
        const delta = choice.delta || {};
        const text = typeof delta.content === 'string' ? delta.content : Array.isArray(delta.content) ? delta.content.map((p) => (typeof p === 'string' ? p : p.text || '')).join('') : '';
        if (text) send('delta', { text });
        const reasoning = delta.reasoning_content || delta.reasoning;
        if (typeof reasoning === 'string' && reasoning) send('reasoning', { text: reasoning });
      }
    }
    // flush any trailing event that arrived without a blank line
    if (buffer.trim()) {
      const line = buffer.split('\n').find((l) => l.startsWith('data:'));
      if (line && line.slice(5).trim() && line.slice(5).trim() !== '[DONE]') {
        try {
          const evt = JSON.parse(line.slice(5).trim());
          const delta = evt.choices?.[0]?.delta || {};
          if (delta.content) send('delta', { text: delta.content });
        } catch {
          /* ignore */
        }
      }
    }
    send('done', { finish_reason: finishReason || 'stop', usage });
  } catch (err) {
    if (err.name !== 'AbortError') sendError(`Stream interrupted: ${err.message}`);
    send('done', { finish_reason: 'error' });
  }
  finish();
}

// ------------------------------------------------------------ static server

async function serveStatic(req, res, pathname) {
  let rel = decodeURIComponent(pathname);
  if (rel === '/' || rel === '') rel = '/index.html';
  const filePath = path.join(PUBLIC_DIR, path.normalize(rel).replace(/^(\.\.[/\\])+/, ''));
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403).end('Forbidden');
    return;
  }
  try {
    const stat = await fsp.stat(filePath);
    if (stat.isDirectory()) throw new Error('dir');
    res.writeHead(200, {
      'content-type': MIME[path.extname(filePath).toLowerCase()] || 'application/octet-stream',
      'content-length': stat.size,
      'cache-control': 'no-cache',
    });
    if (req.method === 'HEAD') return res.end();
    fs.createReadStream(filePath).pipe(res);
  } catch {
    res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' }).end('Not found');
  }
}

// ------------------------------------------------------------------- server

const server = http.createServer(async (req, res) => {
  const { pathname } = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  try {
    if (pathname === '/api/providers' && req.method === 'GET') return handleProviders(res);
    if (pathname === '/api/models' && req.method === 'POST') return await handleModels(req, res);
    if (pathname === '/api/local' && req.method === 'POST') return await handleLocalProbe(req, res);
    if (pathname === '/api/local/pull' && req.method === 'POST') return await handleLocalPull(req, res);
    if (pathname === '/api/chat' && req.method === 'POST') return await handleChat(req, res);
    if (pathname === '/api/health') return json(res, 200, { ok: true, uptime: process.uptime() });
    if (pathname.startsWith('/api/')) return json(res, 404, { error: 'Unknown endpoint' });
    if (req.method === 'GET' || req.method === 'HEAD') return await serveStatic(req, res, pathname);
    json(res, 405, { error: 'Method not allowed' });
  } catch (err) {
    const status = err.status || 502;
    const message = err.message || 'Unexpected server error';
    if (res.headersSent) {
      try {
        res.write(`event: error\ndata: ${JSON.stringify({ message })}\n\n`);
        res.end();
      } catch {
        /* socket gone */
      }
    } else {
      json(res, status, { error: message });
    }
  }
});

server.listen(PORT, HOST, () => {
  const shown = HOST === '0.0.0.0' ? 'localhost' : HOST;
  console.log(`\n  MiniChat running →  http://${shown}:${PORT}`);
  if (process.env.AI_BASE_URL) console.log(`  Server-side endpoint: ${process.env.AI_BASE_URL}${process.env.AI_MODEL ? ` (${process.env.AI_MODEL})` : ''}`);
  console.log(`  Pick a provider + add a free API key in Settings (gear icon).\n`);
});

process.on('SIGINT', () => {
  server.close(() => process.exit(0));
});
