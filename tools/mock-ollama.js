/**
 * Fake Ollama server for developing/testing MiniChat's keyless "local" engine.
 * Implements /api/tags, /api/ps, /api/pull (streamed) and /v1/chat/completions.
 *
 *   node tools/mock-ollama.js      # listens on :11434
 *
 * Dev-only. Not used by the app at runtime.
 */

import http from 'node:http';

const PORT = Number(process.env.MOCK_OLLAMA_PORT || 11434);
let INSTALLED = [
  { name: 'llama3.2:3b', size: 2_000_000_000, digest: 'abc123', details: { parameter_size: '3.2B', quantization_level: 'Q4_K_M', family: 'llama' } },
];

const ANSWER = (q) => `You asked: **${q}**

I'm a local model running through Ollama — no API key, no account, no cost.
Anything you type here stays on this machine.

Here's a quick list of what that gets you:

- Free forever, works offline
- Private by default
- Quality scales with the model you install

\`\`\`bash
ollama pull llama3.1:8b    # bigger model, better answers
\`\`\`

| Model | RAM | Notes |
| --- | --- | --- |
| llama3.2:3b | 4 GB | fast |
| qwen2.5:7b | 8 GB | strong all-rounder |
| llama3.3:70b | 48 GB | best free quality |
`;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');

  if (url.pathname === '/api/tags') {
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ models: INSTALLED }));
  }

  if (url.pathname === '/api/ps') {
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ models: [{ name: INSTALLED[0]?.name || '' }] }));
  }

  if (url.pathname === '/api/pull' && req.method === 'POST') {
    let body = '';
    for await (const c of req) body += c;
    const { name } = JSON.parse(body || '{}');
    res.writeHead(200, { 'content-type': 'application/x-ndjson' });
    const total = 1_000_000_000;
    for (const status of ['pulling manifest', 'pulling layers', 'verifying sha256 digest']) {
      res.write(JSON.stringify({ status }) + '\n');
      await sleep(120);
    }
    for (let i = 1; i <= 10; i++) {
      res.write(JSON.stringify({ status: 'downloading', digest: 'sha256:deadbeef', total, completed: (total / 10) * i }) + '\n');
      await sleep(60);
    }
    res.write(JSON.stringify({ status: 'writing manifest' }) + '\n');
    await sleep(60);
    res.write(JSON.stringify({ status: 'success' }) + '\n');
    if (name && !INSTALLED.some((m) => m.name === name)) {
      INSTALLED.push({ name, size: total, digest: 'sha256:new', details: { parameter_size: '7B', quantization_level: 'Q4_K_M', family: 'qwen2' } });
    }
    return res.end();
  }

  if (url.pathname === '/v1/models') {
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ object: 'list', data: INSTALLED.map((m) => ({ id: m.name, object: 'model', owned_by: 'ollama' })) }));
  }

  if (url.pathname === '/v1/chat/completions' && req.method === 'POST') {
    let body = '';
    for await (const c of req) body += c;
    const payload = JSON.parse(body || '{}');
    const last = [...(payload.messages || [])].reverse().find((m) => m.role === 'user');
    const text = ANSWER((typeof last?.content === 'string' ? last.content : 'hello').slice(0, 120));
    const model = payload.model || INSTALLED[0]?.name || 'llama3.2:3b';

    if (payload.stream === false) {
      res.writeHead(200, { 'content-type': 'application/json' });
      return res.end(JSON.stringify({ id: 'mock', object: 'chat.completion', created: Date.now(), model, choices: [{ index: 0, message: { role: 'assistant', content: text }, finish_reason: 'stop' }] }));
    }

    res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache' });
    for (const tok of text.match(/\s*\S+/g) || [text]) {
      res.write(`data: ${JSON.stringify({ id: 'mock', object: 'chat.completion.chunk', created: Date.now(), model, choices: [{ index: 0, delta: { content: tok }, finish_reason: null }] })}\n\n`);
      await sleep(12);
    }
    res.write(`data: ${JSON.stringify({ id: 'mock', object: 'chat.completion.chunk', created: Date.now(), model, choices: [{ index: 0, delta: {}, finish_reason: 'stop' }] })}\n\n`);
    res.write('data: [DONE]\n\n');
    return res.end();
  }

  res.writeHead(404, { 'content-type': 'application/json' }).end(JSON.stringify({ error: 'not found' }));
});

server.listen(PORT, '127.0.0.1', () => console.log(`mock ollama on http://localhost:${PORT}`));
