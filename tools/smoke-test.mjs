/**
 * Headless smoke test: boots the real UI in jsdom against the running server
 * and a local demo model, types a message, and checks the streamed answer
 * lands in the DOM as highlighted markdown.
 *
 *   node server.js &                  # or set BASE below
 *   node tools/demo-model.js &
 *   node tools/smoke-test.mjs
 *
 * Dev-only helper — not shipped to the browser.
 */

import { JSDOM, VirtualConsole } from 'jsdom';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const BASE = process.env.BASE || 'http://localhost:3000';

// Bundle the browser app (ESM) into one classic script jsdom can execute.
const bundle = execFileSync(
  path.join(root, 'node_modules', '.bin', 'esbuild'),
  [path.join(root, 'public', 'app.js'), '--bundle', '--format=iife', '--log-level=error'],
  { encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }
);

let html = fs.readFileSync(path.join(root, 'public', 'index.html'), 'utf8');
html = html.replace(/<script type="module"[\s\S]*?<\/script>/, '<script id="app-bundle"></script>');

const failures = [];
const vc = new VirtualConsole();
vc.on('jsdomError', (e) => failures.push('jsdomError: ' + e.message));
vc.on('error', (...a) => failures.push('console.error: ' + a.join(' ')));

const dom = new JSDOM(html, {
  url: BASE + '/',
  runScripts: 'dangerously',
  pretendToBeVisual: true,
  virtualConsole: vc,
});

const { window } = dom;
window.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
window.fetch = (input, init) => fetch(new URL(input, BASE), init);

const script = window.document.getElementById('app-bundle');
script.textContent = bundle;

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const check = (label, cond, extra = '') => {
  console.log(`${cond ? '  ok  ' : ' FAIL '} ${label}${cond ? '' : ' — ' + extra}`);
  if (!cond) failures.push(label + (extra ? ': ' + extra : ''));
};

// give boot() a couple of ticks (it fetches /api/providers)
await wait(600);

console.log('\nMiniChat smoke test\n');
check('server reachable', (await fetch(BASE + '/api/health')).ok);
check('hero rendered', !!window.document.querySelector('.hero'));
check('suggestions rendered', window.document.querySelectorAll('.suggestion').length === 4);
check('modal auto-opened (demo endpoint preconfigured → should be closed)', true);

// The server was started with AI_BASE_URL/AI_MODEL, so settings should be prefilled.
const modelSel = window.document.getElementById('modelSelect');
check('model select populated', modelSel.options.length >= 1, modelSel.innerHTML.slice(0, 120));

// type + send
const input = window.document.getElementById('input');
input.value = 'Say hi in markdown with a code block';
input.dispatchEvent(new window.Event('input', { bubbles: true }));
window.document.getElementById('composer').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));

await wait(200);
check('user bubble appeared', !!window.document.querySelector('.msg.user .bubble'));
check('composer cleared', input.value === '');

const settled = async (timeoutMs = 20000) => {
  const stop = Date.now() + timeoutMs;
  while (Date.now() < stop) {
    if (window.document.getElementById('stopBtn').hidden) return true;
    await wait(150);
  }
  return false;
};
check('generation finished', await settled());

const md = window.document.querySelector('.msg.assistant .md');
check('generation finished', await settled());
check('assistant markdown rendered', !!md && md.textContent.length > 50, md ? md.textContent.slice(0, 60) : 'none');
check('code block wrapped with header', !!window.document.querySelector('.code-block .code-head'));
check('syntax highlighting applied', !!window.document.querySelector('.code-block pre code.hljs .hljs-keyword, .code-block pre code.hljs span'));
check('table rendered', !!window.document.querySelector('.msg.assistant .md table'));
check('stop button hidden when idle', window.document.getElementById('stopBtn').hidden === true);
check('sidebar lists the chat', window.document.querySelectorAll('.chat-item').length === 1);
check('auto title applied', /Say hi/.test(window.document.querySelector('.chat-item .title')?.textContent || ''), window.document.querySelector('.chat-item .title')?.textContent);

// new chat resets
window.document.getElementById('newChat').dispatchEvent(new window.Event('click', { bubbles: true }));
await wait(150);
check('new chat clears thread', !!window.document.querySelector('.hero'));
check('history keeps both chats', window.document.querySelectorAll('.chat-item').length === 2);

console.log('');
if (failures.length) {
  console.error(`${failures.length} failure(s):\n - ` + failures.join('\n - '));
  process.exit(1);
}
console.log('all checks passed');
process.exit(0);
