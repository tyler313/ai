/**
 * Tiny fake OpenAI-compatible server used for demoing / smoke-testing MiniChat
 * without any API key. Streams a canned markdown answer back, token by token.
 *
 *   node tools/demo-model.js        # listens on http://localhost:4010/v1
 *
 * Not part of the app — delete it if you don't want it.
 */

import http from 'node:http';

const PORT = Number(process.env.DEMO_PORT || 4010);
const MODEL = 'demo-model';

const REPLIES = [
  (q) => `You asked: **${q}**

I'm \`demo-model\` — a fake model that ships with MiniChat so you can see the UI working (streaming, markdown, syntax highlighting) before you plug in a real API key.

Here's what a real response looks like:

1. Text streams in token by token
2. Markdown renders as it arrives
3. Code blocks get a copy button and highlighting

\`\`\`python
def answer(question: str) -> str:
    """Replace this with a real provider call."""
    return llm.complete(question)
\`\`\`

| Provider | Free tier | Card needed |
| --- | --- | --- |
| Google Gemini | yes | no |
| Groq | yes | no |
| OpenRouter | yes | no |
| Ollama (local) | always free | no |

To switch: open **Settings** (gear icon), pick a provider, paste your free key, hit *Fetch models*, choose one, Save.`,
];

const sse = (obj) => `data: ${JSON.stringify(obj)}\n\n`;

function replyFor(messages) {
  const last = [...messages].reverse().find((m) => m.role === 'user');
  const text = typeof last?.content === 'string' ? last.content : last?.content?.find?.((p) => p.type === 'text')?.text || '';
  return REPLIES[0]((text || 'hello').slice(0, 120));
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost');

  if (url.pathname === '/v1/models') {
    res.writeHead(200, { 'content-type': 'application/json' });
    return res.end(JSON.stringify({ object: 'list', data: [{ id: MODEL, object: 'model', owned_by: 'minichat' }] }));
  }

  if (url.pathname === '/v1/chat/completions' && req.method === 'POST') {
    let body = '';
    for await (const chunk of req) body += chunk;
    let payload = {};
    try {
      payload = JSON.parse(body);
    } catch {
      /* ignore */
    }

    const text = replyFor(payload.messages || []);

    if (payload.stream === false) {
      res.writeHead(200, { 'content-type': 'application/json' });
      return res.end(
        JSON.stringify({
          id: 'demo',
          object: 'chat.completion',
          created: Math.floor(Date.now() / 1000),
          model: MODEL,
          choices: [{ index: 0, message: { role: 'assistant', content: text }, finish_reason: 'stop' }],
          usage: { prompt_tokens: 10, completion_tokens: text.split(' ').length, total_tokens: 10 + text.split(' ').length },
        })
      );
    }

    res.writeHead(200, {
      'content-type': 'text/event-stream',
      'cache-control': 'no-cache',
      connection: 'keep-alive',
    });

    const tokens = text.match(/\s*\S+/g) || [text];
    for (const token of tokens) {
      res.write(sse({ id: 'demo', object: 'chat.completion.chunk', created: Math.floor(Date.now() / 1000), model: MODEL, choices: [{ index: 0, delta: { content: token }, finish_reason: null }] }));
      await new Promise((r) => setTimeout(r, 18));
    }
    res.write(
      sse({
        id: 'demo',
        object: 'chat.completion.chunk',
        created: Math.floor(Date.now() / 1000),
        model: MODEL,
        choices: [{ index: 0, delta: {}, finish_reason: 'stop' }],
        usage: { prompt_tokens: 10, completion_tokens: tokens.length, total_tokens: 10 + tokens.length },
      })
    );
    res.write('data: [DONE]\n\n');
    return res.end();
  }

  res.writeHead(404, { 'content-type': 'application/json' }).end(JSON.stringify({ error: { message: 'not found' } }));
});

server.listen(PORT, '127.0.0.1', () => console.log(`demo model on http://localhost:${PORT}/v1`));
