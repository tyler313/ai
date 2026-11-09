/* =========================================================================
   MiniChat — ChatGPT-style UI, any OpenAI-compatible backend, no build step
   ========================================================================= */

import { marked } from './vendor/marked.esm.js';
import hljs from './vendor/hljs.js';
import DOMPurify from './vendor/purify.esm.js';
const $ = (id) => document.getElementById(id);
const STORE_KEY = 'minichat.v1';
const uid = () => Math.random().toString(36).slice(2, 10) + Date.now().toString(36).slice(-4);

const SUGGESTIONS = [
  { title: 'Write code', body: 'Write a Python script that renames files in a folder by date' },
  { title: 'Explain something', body: 'Explain how a CPU cache works, simply, with an analogy' },
  { title: 'Draft a message', body: 'Draft a short, friendly email asking my landlord about a leaking tap' },
  { title: 'Plan a trip', body: 'Plan a 3-day weekend in Ballarat with a mix of food and history' },
];

const HLJS_ALIAS = {
  js: 'javascript', jsx: 'javascript', mjs: 'javascript', cjs: 'javascript', node: 'javascript',
  ts: 'typescript', tsx: 'typescript', py: 'python', py3: 'python', rb: 'ruby',
  sh: 'shell', zsh: 'shell', console: 'shell', shell_session: 'shell', bash: 'bash',
  yml: 'yaml', md: 'markdown', mdx: 'markdown', html: 'xml', xml: 'xml', vue: 'xml', svelte: 'xml',
  golang: 'go', rs: 'rust', docker: 'dockerfile', dockerfile: 'dockerfile', toml: 'ini',
  'c++': 'cpp', cc: 'cpp', h: 'c', cs: 'csharp', ps1: 'powershell', objc: 'objectivec',
  jsonc: 'json', http: 'plaintext', text: 'plaintext', txt: 'plaintext',
};

// ───────────────────────────────── state ──────────────────────────────────

const state = load() || {
  conversations: {},
  order: [],
  activeId: null,
  search: '',
  settings: {
    provider: 'gemini',
    baseUrl: '',
    apiKey: '',
    model: '',
    systemPrompt: 'You are a helpful, concise assistant. Format answers with markdown when it helps.',
    temperature: 0.7,
    maxTokens: 0,
    theme: matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark',
    sidebarCollapsed: false,
    models: {}, // baseUrl -> [modelIds]
  },
};

let providers = [];
let streaming = null; // { convId, msgId, controller }
let pendingAttachments = [];
let editingMsgId = null;

function load() {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object' || !parsed.settings) return null;
    parsed.settings = { models: {}, ...parsed.settings };
    return parsed;
  } catch {
    return null;
  }
}

let saveTimer = null;
function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(state));
    } catch (err) {
      toast('Could not save locally: ' + err.message);
    }
  }, 250);
}

const settings = () => state.settings;
const activeConv = () => state.conversations[state.activeId] || null;
const isConfigured = () => Boolean(settings().baseUrl && settings().model);

// ─────────────────────────────── markdown ─────────────────────────────────

marked.setOptions({ gfm: true, breaks: false, headerIds: false, mangle: false });

function renderMarkdown(src) {
  let html;
  try {
    html = marked.parse(src || '');
  } catch {
    html = escapeHtml(src || '');
  }
  return DOMPurify.sanitize(html, { ADD_ATTR: ['target', 'rel', 'class'], FORBID_TAGS: ['style', 'form'] });
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/** Highlight + wrap code blocks with a copy-able header. */
function decorateCode(root) {
  root.querySelectorAll('pre').forEach((pre) => {
    if (pre.parentElement?.classList.contains('code-block')) return;
    const code = pre.querySelector('code') || pre;
    let lang = ((code.className || '').match(/language-([\w+#.-]+)/) || [])[1] || '';
    const resolved = HLJS_ALIAS[lang.toLowerCase()] || lang.toLowerCase();
    if (resolved && hljs.getLanguage(resolved)) {
      code.classList.add('language-' + resolved);
      try {
        hljs.highlightElement(code);
      } catch {
        /* keep plain text */
      }
    } else {
      try {
        hljs.highlightElement(code);
      } catch {
        /* noop */
      }
    }
    code.classList.add('hljs');

    const block = document.createElement('div');
    block.className = 'code-block';
    pre.parentNode.insertBefore(block, pre);
    block.appendChild(pre);

    const head = document.createElement('div');
    head.className = 'code-head';
    const label = document.createElement('span');
    label.textContent = lang || 'code';
    const copy = document.createElement('button');
    copy.type = 'button';
    copy.innerHTML = copyIcon() + '<span>Copy</span>';
    copy.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(code.textContent);
        copy.querySelector('span').textContent = 'Copied';
        setTimeout(() => (copy.querySelector('span').textContent = 'Copy'), 1400);
      } catch {
        toast('Clipboard blocked by the browser');
      }
    });
    head.append(label, copy);
    block.prepend(head);
  });
}

const copyIcon = () =>
  '<svg viewBox="0 0 24 24" width="13" height="13"><path d="M9 3h9a2 2 0 012 2v9h-2V5H9V3zm-4 4h9a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9a2 2 0 012-2zm0 2v9h9V9H5z"/></svg>';

// ────────────────────────────── icons / ui ────────────────────────────────

const ICON = {
  copy: copyIcon(),
  check: '<svg viewBox="0 0 24 24" width="15" height="15"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z"/></svg>',
  pencil: '<svg viewBox="0 0 24 24" width="15" height="15"><path d="M3 17.25V21h3.75L17.8 9.94l-3.75-3.75L3 17.25zM20.7 7.04a1 1 0 000-1.41l-2.34-2.34a1 1 0 00-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>',
  trash: '<svg viewBox="0 0 24 24" width="15" height="15"><path d="M6 19a2 2 0 002 2h8a2 2 0 002-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>',
  refresh: '<svg viewBox="0 0 24 24" width="15" height="15"><path d="M12 5V1L7 6l5 5V7a5 5 0 11-5 5H5a7 7 0 107-7z"/></svg>',
};

function toast(msg) {
  const el = $('toast');
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toast.t);
  toast.t = setTimeout(() => (el.hidden = true), 2600);
}

// ───────────────────────────── theme / layout ─────────────────────────────

function applyTheme() {
  const theme = settings().theme || 'dark';
  document.documentElement.dataset.theme = theme;
  $('hljs-dark').disabled = theme !== 'dark';
  $('hljs-light').disabled = theme !== 'light';
}

function applySidebar() {
  const sb = $('sidebar');
  sb.classList.toggle('collapsed', Boolean(settings().sidebarCollapsed) && window.innerWidth > 768);
}

// ─────────────────────────── conversations ────────────────────────────────

function newConversation(makeActive = true) {
  const conv = { id: uid(), title: 'New chat', messages: [], createdAt: Date.now(), updatedAt: Date.now() };
  state.conversations[conv.id] = conv;
  state.order.unshift(conv.id);
  if (makeActive) state.activeId = conv.id;
  save();
  return conv;
}

function ensureConversation() {
  if (activeConv()) return activeConv();
  const existing = state.order.map((id) => state.conversations[id]).filter(Boolean)[0];
  if (existing) {
    state.activeId = existing.id;
    return existing;
  }
  return newConversation();
}

function touch(conv) {
  conv.updatedAt = Date.now();
  const i = state.order.indexOf(conv.id);
  if (i > 0) {
    state.order.splice(i, 1);
    state.order.unshift(conv.id);
  }
  save();
}

function deleteConversation(id) {
  delete state.conversations[id];
  state.order = state.order.filter((x) => x !== id);
  if (state.activeId === id) state.activeId = state.order[0] || null;
  save();
  renderSidebar();
  renderThread();
}

function groupFor(ts) {
  const now = new Date();
  const d = new Date(ts);
  const startOfToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  if (ts >= startOfToday) return 'Today';
  if (ts >= startOfToday - 864e5) return 'Yesterday';
  if (ts >= startOfToday - 7 * 864e5) return 'Previous 7 days';
  if (ts >= startOfToday - 30 * 864e5) return 'Previous 30 days';
  return d.toLocaleString(undefined, { month: 'long' });
}

function renderSidebar() {
  const list = $('chatList');
  const q = (state.search || '').trim().toLowerCase();
  const items = state.order
    .map((id) => state.conversations[id])
    .filter(Boolean)
    .filter((c) => !q || c.title.toLowerCase().includes(q) || c.messages.some((m) => String(m.content || '').toLowerCase().includes(q)));

  list.innerHTML = '';
  if (!items.length) {
    const p = document.createElement('div');
    p.className = 'group-label';
    p.textContent = q ? 'No chats match' : 'No chats yet';
    list.appendChild(p);
    return;
  }

  const groups = new Map();
  for (const c of items) {
    const g = q ? 'Results' : groupFor(c.updatedAt);
    if (!groups.has(g)) groups.set(g, []);
    groups.get(g).push(c);
  }

  for (const [label, convs] of groups) {
    const h = document.createElement('div');
    h.className = 'group-label';
    h.textContent = label;
    list.appendChild(h);
    for (const c of convs) {
      const row = document.createElement('div');
      row.className = 'chat-item' + (c.id === state.activeId ? ' active' : '');
      row.innerHTML = `<span class="title"></span>
        <span class="mini">
          <button data-act="rename" title="Rename">${ICON.pencil}</button>
          <button data-act="delete" title="Delete">${ICON.trash}</button>
        </span>`;
      row.querySelector('.title').textContent = c.title || 'New chat';
      row.addEventListener('click', (e) => {
        const act = e.target.closest('button')?.dataset.act;
        if (act === 'delete') {
          deleteConversation(c.id);
          return;
        }
        if (act === 'rename') {
          e.stopPropagation();
          const next = prompt('Rename chat', c.title);
          if (next !== null) {
            c.title = next.trim() || 'New chat';
            save();
            renderSidebar();
          }
          return;
        }
        state.activeId = c.id;
        save();
        renderSidebar();
        renderThread();
        closeSidebarMobile();
      });
      list.appendChild(row);
    }
  }

  const badge = $('modelBadge');
  badge.textContent = isConfigured() ? `${settings().model} · ${hostOf(settings().baseUrl)}` : 'not configured';
  badge.title = badge.textContent;
}

const hostOf = (url) => {
  try {
    return new URL(url).host;
  } catch {
    return url || '—';
  }
};

// ──────────────────────────────── thread ──────────────────────────────────

function renderThread() {
  const thread = $('thread');
  thread.innerHTML = '';
  const conv = activeConv();

  // a stream that was interrupted (e.g. you switched chats) must not stay "typing"
  if (conv) {
    for (const m of conv.messages) {
      if (m.streaming && (!streaming || streaming.msgId !== m.id)) m.streaming = false;
    }
  }

  if (!conv || !conv.messages.length) {
    thread.appendChild(heroNode());
    return;
  }

  for (const msg of conv.messages) thread.appendChild(messageNode(msg, conv));

  const last = conv.messages[conv.messages.length - 1];
  if (last?.role === 'assistant' && last.streaming) attachCaret(thread, last);
  scrollToBottom(true);
}

function heroNode() {
  const wrap = document.createElement('div');
  wrap.className = 'hero';
  const hour = new Date().getHours();
  const greet = hour < 5 ? 'Still up?' : hour < 12 ? 'Good morning' : hour < 18 ? 'Good afternoon' : 'Good evening';
  const configured = isConfigured();

  wrap.innerHTML = `
    <h1>${configured ? 'What can I help with?' : `${greet}. Let's connect a model.`}</h1>
    ${
      configured
        ? ''
        : `<div class="error-box" style="max-width:520px;margin:0 auto 24px;text-align:left">
             <b>No model connected yet.</b><br />MiniChat is a free chat UI — it needs an endpoint to talk to.
             Pick a free provider (Google Gemini, Groq, OpenRouter all have free tiers) or point it at a local
             Ollama install, which is free forever and needs no key.
             <div class="row"><button class="btn primary" id="heroSetup">Connect a model</button></div>
           </div>`
    }
    <div class="suggestions">
      ${SUGGESTIONS.map(
        (s) => `<button class="suggestion" data-prompt="${escapeHtml(s.body)}"><b>${escapeHtml(s.title)}</b>${escapeHtml(s.body)}</button>`
      ).join('')}
    </div>`;

  wrap.querySelectorAll('.suggestion').forEach((b) =>
    b.addEventListener('click', () => {
      $('input').value = b.dataset.prompt;
      autoGrow();
      $('input').focus();
    })
  );
  wrap.querySelector('#heroSetup')?.addEventListener('click', () => openModal());
  return wrap;
}

function messageNode(msg, conv) {
  const el = document.createElement('div');
  el.className = 'msg ' + (msg.role === 'user' ? 'user' : 'assistant');
  el.dataset.id = msg.id;

  const row = document.createElement('div');
  row.className = 'msg-row';

  if (msg.role === 'assistant') {
    const av = document.createElement('div');
    av.className = 'avatar assistant';
    av.textContent = 'AI';
    row.appendChild(av);
  }

  const body = document.createElement('div');
  body.className = 'body';

  if (msg.role === 'user') {
    const bubble = document.createElement('div');
    bubble.className = 'bubble';
    bubble.textContent = msg.content || '';
    if (msg.attachments?.length) {
      const gal = document.createElement('div');
      gal.className = 'attachments';
      msg.attachments.forEach((a) => {
        const img = document.createElement('img');
        img.src = a.dataUrl;
        img.alt = a.name || 'attachment';
        img.style.maxHeight = '180px';
        gal.appendChild(img);
      });
      bubble.appendChild(gal);
    }
    if (editingMsgId === msg.id) {
      const ta = document.createElement('textarea');
      ta.className = 'edit-box';
      ta.style.cssText = 'width:100%;background:var(--bg);color:var(--text);border:1px solid var(--border-strong);border-radius:10px;padding:10px;font:inherit;font-size:15px;';
      ta.rows = 3;
      ta.value = msg.content;
      const acts = document.createElement('div');
      acts.className = 'row';
      acts.style.marginTop = '8px';
      acts.innerHTML = '<button class="btn primary" data-edit="save">Save</button><button class="btn ghost" data-edit="cancel">Cancel</button>';
      const box = document.createElement('div');
      box.style.width = '100%';
      box.append(ta, acts);
      bubble.replaceChildren(box);
      setTimeout(() => {
        ta.focus();
        ta.setSelectionRange(ta.value.length, ta.value.length);
      }, 0);
      acts.addEventListener('click', (e) => {
        const act = e.target.closest('button')?.dataset.edit;
        if (act === 'save' && ta.value.trim()) {
          msg.content = ta.value.trim();
          editingMsgId = null;
          const idx = conv.messages.findIndex((m) => m.id === msg.id);
          conv.messages = conv.messages.slice(0, idx + 1);
          touch(conv);
          renderThread();
          generate();
        } else {
          editingMsgId = null;
          renderThread();
        }
      });
    }
    row.appendChild(bubble);
    el.appendChild(row);
    el.appendChild(actionBar(msg, conv, { edit: true, copy: true }));
    return el;
  }

  // assistant
  if (msg.reasoning) {
    const det = document.createElement('details');
    det.className = 'reasoning';
    det.open = false;
    det.innerHTML = `<summary>Thinking…</summary><div class="reasoning-body">${escapeHtml(msg.reasoning)}</div>`;
    body.appendChild(det);
  }

  if (msg.error) {
    const err = document.createElement('div');
    err.className = 'error-box';
    err.textContent = msg.error;
    const acts = document.createElement('div');
    acts.className = 'row';
    acts.innerHTML =
      '<button class="btn ghost" data-act="retry">Retry</button><button class="btn ghost" data-act="settings">Settings</button>';
    acts.addEventListener('click', (e) => {
      const act = e.target.closest('button')?.dataset.act;
      if (act === 'settings') openModal();
      if (act === 'retry') {
        conv.messages = conv.messages.filter((m) => m.id !== msg.id);
        touch(conv);
        renderThread();
        generate();
      }
    });
    err.appendChild(acts);
    body.appendChild(err);
  } else if (msg.content) {
    const md = document.createElement('div');
    md.className = 'md';
    md.innerHTML = renderMarkdown(msg.content);
    decorateCode(md);
    body.appendChild(md);
  } else if (msg.streaming) {
    const dots = document.createElement('div');
    dots.className = 'thinking';
    dots.innerHTML = '<span></span><span></span><span></span>';
    body.appendChild(dots);
  }

  row.appendChild(body);
  el.appendChild(row);
  el.appendChild(actionBar(msg, conv, { copy: !msg.error && Boolean(msg.content), retry: true }));
  return el;
}

function actionBar(msg, conv, opts = {}) {
  const bar = document.createElement('div');
  bar.className = 'actions';
  if (msg.role === 'user') bar.style.justifyContent = 'flex-end';

  const add = (icon, title, fn) => {
    const b = document.createElement('button');
    b.innerHTML = icon;
    b.title = title;
    b.addEventListener('click', fn);
    bar.appendChild(b);
    return b;
  };

  if (opts.copy) {
    add(ICON.copy, 'Copy', async () => {
      try {
        await navigator.clipboard.writeText(msg.content || '');
        toast('Copied');
      } catch {
        toast('Clipboard blocked by the browser');
      }
    });
  }
  if (opts.edit && !streaming) add(ICON.pencil, 'Edit message', () => {
    editingMsgId = msg.id;
    renderThread();
  });
  if (opts.retry && !streaming) {
    add(ICON.refresh, 'Regenerate', () => {
      const idx = conv.messages.findIndex((m) => m.id === msg.id);
      if (idx > 0) {
        conv.messages = conv.messages.slice(0, idx);
        touch(conv);
        renderThread();
        generate();
      }
    });
  }
  return bar;
}

function attachCaret(thread, msg) {
  const nodes = thread.querySelectorAll('.msg');
  const last = nodes[nodes.length - 1];
  if (!last) return;
  const md = last.querySelector('.md');
  if (!md) return;
  const caret = document.createElement('span');
  caret.className = 'caret';
  caret.dataset.caret = '1';
  (md.lastElementChild || md).appendChild(caret);
  void msg;
}

function scrollToBottom(force = false) {
  const sc = $('threadScroll');
  const near = sc.scrollHeight - sc.scrollTop - sc.clientHeight < 120;
  if (force || near) sc.scrollTop = sc.scrollHeight;
}

function autoGrow() {
  const ta = $('input');
  ta.style.height = 'auto';
  ta.style.height = Math.min(ta.scrollHeight, 200) + 'px';
}

// ─────────────────────────────── generation ───────────────────────────────

function buildPayload(conv) {
  const s = settings();
  const history = conv.messages
    .filter((m) => !m.error && (m.content || m.attachments?.length))
    .slice(-40)
    .map((m) => ({
      role: m.role === 'user' ? 'user' : 'assistant',
      content: m.content || '',
      attachments: m.role === 'user' ? m.attachments || [] : [],
    }));
  if (s.systemPrompt?.trim()) history.unshift({ role: 'system', content: s.systemPrompt.trim() });
  return history;
}

async function generate() {
  const conv = activeConv();
  if (!conv || streaming) return;
  if (!isConfigured()) {
    openModal();
    return;
  }

  const s = settings();
  const msg = { id: uid(), role: 'assistant', content: '', reasoning: '', streaming: true, createdAt: Date.now(), model: s.model };
  conv.messages.push(msg);
  conv.model = s.model;
  touch(conv);
  renderThread();
  setGenerating(true);
  scrollToBottom(true);

  const controller = new AbortController();
  streaming = { convId: conv.id, msgId: msg.id, controller };

  try {
    const res = await fetch('/api/chat', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      signal: controller.signal,
      body: JSON.stringify({
        baseUrl: s.baseUrl,
        apiKey: s.apiKey,
        model: s.model,
        temperature: Number(s.temperature),
        maxTokens: Number(s.maxTokens) || 0,
        messages: buildPayload(conv),
      }),
    });

    if (!res.ok || !res.body) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error || `Server responded ${res.status}`);
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buf = '';
    let raf = null;

    const paint = () => {
      raf = null;
      const node = $('thread').querySelector(`.msg[data-id="${msg.id}"]`);
      if (node) {
        const container = node.querySelector('.body');
        let md = container.querySelector('.md');
        if (!md) {
          container.querySelector('.thinking')?.remove();
          md = document.createElement('div');
          md.className = 'md';
          container.appendChild(md);
        }
        md.innerHTML = renderMarkdown(msg.content);
        decorateCode(md);
        const caret = document.createElement('span');
        caret.className = 'caret';
        md.lastElementChild?.appendChild(caret) ?? md.appendChild(caret);
        scrollToBottom();
      }
    };
    const schedule = () => {
      if (raf === null) raf = requestAnimationFrame(paint);
    };

    const handleEvent = (event, data) => {
      if (event === 'delta') {
        msg.content += data.text || '';
        schedule();
      } else if (event === 'reasoning') {
        msg.reasoning = (msg.reasoning || '') + (data.text || '');
        const node = $('thread').querySelector(`.msg[data-id="${msg.id}"] .reasoning-body`);
        if (node) node.textContent = msg.reasoning;
      } else if (event === 'error') {
        msg.error = data.message || 'Something went wrong.';
      } else if (event === 'done') {
        msg.finishReason = data.finish_reason;
        if (data.usage) msg.usage = data.usage;
      }
    };

    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buf.indexOf('\n\n')) !== -1) {
        const raw = buf.slice(0, idx);
        buf = buf.slice(idx + 2);
        let event = 'message';
        let data = '';
        for (const line of raw.split('\n')) {
          if (line.startsWith('event:')) event = line.slice(6).trim();
          else if (line.startsWith('data:')) data += line.slice(5).trim();
        }
        if (!data) continue;
        try {
          handleEvent(event, JSON.parse(data));
        } catch {
          /* ignore malformed frame */
        }
      }
    }
    if (raf !== null) cancelAnimationFrame(raf);
    paint();
  } catch (err) {
    if (err.name === 'AbortError') {
      if (!msg.content) msg.error = null;
    } else {
      msg.error = friendlyError(err);
    }
  } finally {
    msg.streaming = false;
    streaming = null;
    setGenerating(false);
    const convNow = state.conversations[conv.id];
    if (convNow && !msg.error) autoTitle(convNow);
    touch(conv);
    save();
    renderThread();
    renderSidebar();
  }
}

function friendlyError(err) {
  const m = String(err?.message || err);
  if (/Failed to fetch|NetworkError|Load failed/i.test(m)) return "Couldn't reach the server. Is `node server.js` still running?";
  if (/No endpoint configured/i.test(m)) return m;
  return m;
}

function autoTitle(conv) {
  const firstUser = conv.messages.find((m) => m.role === 'user' && m.content);
  if (!firstUser) return;
  if (conv.title && conv.title !== 'New chat') return;
  conv.title = firstUser.content.trim().replace(/\s+/g, ' ').slice(0, 48) || 'New chat';
}

function setGenerating(on) {
  $('stopBtn').hidden = !on;
  $('sendBtn').hidden = on;
  $('input').disabled = on;
  $('composerHint').textContent = on ? 'Generating… (Esc or Stop to cancel)' : 'Enter to send · Shift + Enter for a new line';
}

function abortGeneration() {
  streaming?.controller.abort();
}

// ─────────────────────────────── composer ─────────────────────────────────

function sendCurrent() {
  const input = $('input');
  const text = input.value.trim();
  if (!text && !pendingAttachments.length) return;
  if (streaming) return;
  if (!isConfigured()) {
    openModal();
    return;
  }

  const conv = ensureConversation();
  conv.messages.push({
    id: uid(),
    role: 'user',
    content: text,
    attachments: pendingAttachments.slice(),
    createdAt: Date.now(),
  });
  if (conv.messages.filter((m) => m.role === 'user').length === 1) autoTitle(conv);
  touch(conv);
  save();

  input.value = '';
  autoGrow();
  pendingAttachments = [];
  renderAttachments();
  renderSidebar();
  renderThread();
  generate();
}

function renderAttachments() {
  const wrap = $('attachments');
  wrap.innerHTML = '';
  pendingAttachments.forEach((att, i) => {
    const d = document.createElement('div');
    d.className = 'attachment';
    const img = document.createElement('img');
    img.src = att.dataUrl;
    img.alt = att.name;
    const x = document.createElement('button');
    x.type = 'button';
    x.textContent = '×';
    x.title = 'Remove';
    x.addEventListener('click', () => {
      pendingAttachments.splice(i, 1);
      renderAttachments();
    });
    d.append(img, x);
    wrap.appendChild(d);
  });
}

async function addFiles(files) {
  for (const file of files) {
    if (!file.type.startsWith('image/')) {
      toast('Only image attachments are supported');
      continue;
    }
    if (file.size > 5 * 1024 * 1024) {
      toast(`${file.name} is larger than 5 MB`);
      continue;
    }
    const dataUrl = await new Promise((resolve, reject) => {
      const fr = new FileReader();
      fr.onload = () => resolve(fr.result);
      fr.onerror = () => reject(new Error('Could not read file'));
      fr.readAsDataURL(file);
    });
    pendingAttachments.push({ name: file.name, dataUrl });
  }
  renderAttachments();
}

// ─────────────────────────────── settings ─────────────────────────────────

async function loadProviders() {
  try {
    const res = await fetch('/api/providers');
    const data = await res.json();
    providers = data.providers || [];
    // A server can ship its own endpoint/model via AI_BASE_URL / AI_MODEL / AI_API_KEY.
    // Those act as defaults only — they never overwrite what you typed in Settings.
    const d = data.serverDefaults || {};
    let changed = false;
    if (d.baseUrl && !settings().baseUrl) {
      settings().baseUrl = d.baseUrl;
      changed = true;
    }
    if (d.model && !settings().model) {
      settings().model = d.model;
      changed = true;
    }
    if (d.hasKey && !settings().apiKey) {
      settings().apiKey = '';
      $('apiKey').placeholder = 'using the key configured on the server';
    }
    if (changed) save();
  } catch {
    providers = [];
  }
  renderProviderSelect();
}

function renderProviderSelect() {
  const sel = $('providerSelect');
  sel.innerHTML = '';
  for (const p of providers) {
    const o = document.createElement('option');
    o.value = p.id;
    o.textContent = p.name;
    sel.appendChild(o);
  }
  const cur = providers.find((p) => p.id === settings().provider);
  if (cur) sel.value = cur.id;
  else if (providers.length) {
    const o = document.createElement('option');
    o.value = settings().provider || 'custom';
    o.textContent = 'Custom';
    sel.appendChild(o);
    sel.value = o.value;
  }
  updateProviderNote();
}

function updateProviderNote() {
  const p = providers.find((x) => x.id === $('providerSelect').value);
  $('providerNote').innerHTML = p?.note ? `${escapeHtml(p.note)}${p.keyUrl ? ` <a href="${p.keyUrl}" target="_blank" rel="noreferrer">Get a key →</a>` : ''}` : '';
  $('endpointHint').textContent = p?.id === 'ollama' || p?.id === 'lmstudio' ? 'Runs locally — no API key needed (leave the key blank).' : '';
  $('keyLink').innerHTML = p?.keyUrl ? `<a href="${p.keyUrl}" target="_blank" rel="noreferrer">Get a free key →</a>` : '';
}

function fillSettingsForm() {
  const s = settings();
  $('baseUrl').value = s.baseUrl || '';
  $('apiKey').value = s.apiKey || '';
  $('modelInput').value = s.model || '';
  $('systemPrompt').value = s.systemPrompt || '';
  $('temperature').value = s.temperature ?? 0.7;
  $('tempVal').textContent = Number(s.temperature ?? 0.7).toFixed(2);
  $('maxTokens').value = s.maxTokens ?? 0;
  $('maxVal').textContent = String(s.maxTokens ?? 0);
  renderModelList();
  updateProviderNote();
}

function renderModelList() {
  const models = settings().models?.[settings().baseUrl] || [];
  const dl = $('modelList');
  dl.innerHTML = '';
  for (const m of models) {
    const o = document.createElement('option');
    o.value = m;
    dl.appendChild(o);
  }
  renderModelSelect(models);
}

function renderModelSelect(models = settings().models?.[settings().baseUrl] || []) {
  const sel = $('modelSelect');
  const cur = settings().model;
  sel.innerHTML = '';
  const known = new Set(models);
  if (cur) known.add(cur);
  if (!known.size) {
    const o = document.createElement('option');
    o.value = '';
    o.textContent = 'Select a model…';
    sel.appendChild(o);
  }
  for (const m of known) {
    const o = document.createElement('option');
    o.value = m;
    o.textContent = m;
    sel.appendChild(o);
  }
  const cfg = document.createElement('option');
  cfg.value = '__settings__';
  cfg.textContent = models.length ? 'Manage models…' : 'Configure a model…';
  sel.appendChild(cfg);
  sel.value = known.has(cur) ? cur : '__settings__';
}

function openModal() {
  fillSettingsForm();
  $('modal').hidden = false;
  $('testResult').textContent = '';
  $('testResult').className = 'test-result';
  setTimeout(() => $('baseUrl').focus(), 30);
}

function closeModal() {
  $('modal').hidden = true;
}

function saveSettingsForm() {
  const s = settings();
  s.provider = $('providerSelect').value;
  s.baseUrl = $('baseUrl').value.trim();
  s.apiKey = $('apiKey').value.trim();
  s.model = $('modelInput').value.trim();
  s.systemPrompt = $('systemPrompt').value;
  s.temperature = Number($('temperature').value);
  s.maxTokens = Number($('maxTokens').value) || 0;
  save();
  renderModelSelect();
  renderSidebar();
  if (!s.model) renderThread();
  return s;
}

async function testConnection() {
  const s = saveSettingsForm();
  const out = $('testResult');
  out.className = 'test-result';
  out.textContent = 'Checking…';
  try {
    const res = await fetch('/api/models', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ baseUrl: s.baseUrl, apiKey: s.apiKey }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
    const n = data.models.length;
    s.models[s.baseUrl] = data.models.map((m) => m.id);
    save();
    renderModelList();
    if (!s.model && n) {
      const pick = data.models.find((m) => /flash|lite|mini|8b|7b|small/i.test(m.id)) || data.models[0];
      s.model = pick.id;
      $('modelInput').value = pick.id;
      save();
      renderModelSelect();
    }
    out.className = 'test-result ok';
    out.textContent = n ? `Connected — ${n} models available.` : 'Connected (endpoint returned no model list).';
  } catch (err) {
    out.className = 'test-result bad';
    out.textContent = err.message;
  }
}

// ───────────────────────────── data import ────────────────────────────────

function exportAll() {
  const blob = new Blob([JSON.stringify(state, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = `minichat-export-${new Date().toISOString().slice(0, 10)}.json`;
  a.click();
  URL.revokeObjectURL(a.href);
  toast('Exported');
}

function importAll(file) {
  const fr = new FileReader();
  fr.onload = () => {
    try {
      const data = JSON.parse(fr.result);
      if (!data?.conversations) throw new Error('Not a MiniChat export');
      state.conversations = { ...data.conversations, ...state.conversations };
      state.order = [...new Set([...data.order, ...state.order])].filter((id) => state.conversations[id]);
      save();
      renderSidebar();
      renderThread();
      toast('Imported');
    } catch (err) {
      toast('Import failed: ' + err.message);
    }
  };
  fr.readAsText(file);
}

// ─────────────────────────────── wiring ───────────────────────────────────

function closeSidebarMobile() {
  if (window.innerWidth <= 768) {
    $('sidebar').classList.remove('open');
    $('scrim').classList.remove('show');
  }
}

function bind() {
  $('newChat').addEventListener('click', () => {
    if (streaming) abortGeneration();
    newConversation();
    renderSidebar();
    renderThread();
    $('input').focus();
    closeSidebarMobile();
  });

  $('toggleSidebar').addEventListener('click', () => {
    settings().sidebarCollapsed = true;
    save();
    applySidebar();
  });
  $('openSidebar').addEventListener('click', () => {
    $('sidebar').classList.add('open');
    $('scrim').classList.add('show');
  });
  $('scrim').addEventListener('click', closeSidebarMobile);

  $('searchBtn').addEventListener('click', () => {
    const wrap = $('searchWrap');
    wrap.classList.toggle('hidden');
    if (!wrap.classList.contains('hidden')) $('searchInput').focus();
    else {
      state.search = '';
      $('searchInput').value = '';
      renderSidebar();
    }
  });
  $('searchInput').addEventListener('input', (e) => {
    state.search = e.target.value;
    renderSidebar();
  });

  $('themeToggle').addEventListener('click', () => {
    settings().theme = settings().theme === 'dark' ? 'light' : 'dark';
    applyTheme();
    save();
  });

  $('openSettings').addEventListener('click', openModal);
  $('topSettings').addEventListener('click', openModal);
  $('closeModal').addEventListener('click', closeModal);
  $('modal').addEventListener('click', (e) => {
    if (e.target === $('modal')) closeModal();
  });

  $('providerSelect').addEventListener('change', (e) => {
    const p = providers.find((x) => x.id === e.target.value);
    if (p) {
      if (p.baseUrl) $('baseUrl').value = p.baseUrl;
      if (p.model && !$('modelInput').value) $('modelInput').value = p.model;
      settings().provider = p.id;
    }
    updateProviderNote();
  });
  $('baseUrl').addEventListener('change', () => {
    const models = settings().models?.[$('baseUrl').value.trim()];
    if (models?.length) renderModelSelect(models);
  });

  $('toggleKey').addEventListener('click', () => {
    const k = $('apiKey');
    const show = k.type === 'password';
    k.type = show ? 'text' : 'password';
    $('toggleKey').textContent = show ? 'Hide' : 'Show';
  });

  $('fetchModels').addEventListener('click', testConnection);
  $('testConn').addEventListener('click', testConnection);
  $('saveSettings').addEventListener('click', () => {
    saveSettingsForm();
    closeModal();
    toast(isConfigured() ? 'Settings saved' : 'Saved — pick a model to start');
  });

  $('temperature').addEventListener('input', (e) => ($('tempVal').textContent = Number(e.target.value).toFixed(2)));
  $('maxTokens').addEventListener('input', (e) => ($('maxVal').textContent = e.target.value));

  $('exportAll').addEventListener('click', exportAll);
  $('importBtn').addEventListener('click', () => $('importFile').click());
  $('importFile').addEventListener('change', (e) => e.target.files[0] && importAll(e.target.files[0]));
  $('wipeBtn').addEventListener('click', () => {
    if (!confirm('Delete every chat in this browser?')) return;
    state.conversations = {};
    state.order = [];
    state.activeId = null;
    save();
    renderSidebar();
    renderThread();
  });

  $('modelSelect').addEventListener('change', (e) => {
    if (e.target.value === '__settings__') {
      openModal();
      renderModelSelect();
      return;
    }
    settings().model = e.target.value;
    save();
    renderSidebar();
  });

  $('composer').addEventListener('submit', (e) => {
    e.preventDefault();
    sendCurrent();
  });
  $('input').addEventListener('input', autoGrow);
  $('input').addEventListener('keydown', (e) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
      e.preventDefault();
      sendCurrent();
    }
  });
  $('sendBtn').addEventListener('click', () => sendCurrent());
  $('stopBtn').addEventListener('click', abortGeneration);

  $('attachBtn').addEventListener('click', () => $('fileInput').click());
  $('fileInput').addEventListener('change', (e) => {
    addFiles([...e.target.files]);
    e.target.value = '';
  });

  // paste images
  $('input').addEventListener('paste', (e) => {
    const files = [...(e.clipboardData?.files || [])].filter((f) => f.type.startsWith('image/'));
    if (files.length) {
      e.preventDefault();
      addFiles(files);
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      if (!$('modal').hidden) closeModal();
      else if (streaming) abortGeneration();
    }
    if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      $('searchBtn').click();
    }
    if ((e.metaKey || e.ctrlKey) && e.shiftKey && e.key.toLowerCase() === 'o') {
      e.preventDefault();
      $('newChat').click();
    }
  });

  window.addEventListener('resize', applySidebar);
}

// ──────────────────────────────── boot ────────────────────────────────────

async function boot() {
  applyTheme();
  applySidebar();
  bind();
  await loadProviders();
  if (!state.order.length) newConversation();
  if (!state.activeId) state.activeId = state.order[0];
  renderSidebar();
  renderThread();
  renderModelSelect();
  autoGrow();
  $('input').focus();
  if (!isConfigured()) setTimeout(openModal, 400);
}

boot();
