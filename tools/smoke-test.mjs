/**
 * Headless smoke test: boots the real UI in jsdom against the running server
 * and exercises the keyless engines (local/Ollama, in-browser) plus the
 * free-API path.
 *
 *   node server.js &                  # or set BASE below
 *   node tools/demo-model.js &        # fake provider on :4010
 *   node tools/mock-ollama.js &       # fake Ollama on :11434
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
  { encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 }
);

let html = fs.readFileSync(path.join(root, 'public', 'index.html'), 'utf8');
html = html.replace(/<script type="module"[\s\S]*?<\/script>/, '<script id="app-bundle"></script>');

const failures = [];
const vc = new VirtualConsole();
vc.on('jsdomError', (e) => failures.push('jsdomError: ' + e.message));
vc.on('error', (...a) => failures.push('console.error: ' + a.join(' ')));

const dom = new JSDOM(html, { url: BASE + '/', runScripts: 'dangerously', pretendToBeVisual: true, virtualConsole: vc });
const { window } = dom;
const doc = window.document;
window.matchMedia = () => ({ matches: false, addEventListener() {}, removeEventListener() {} });
window.fetch = (input, init) => fetch(new URL(input, BASE), init);

doc.getElementById('app-bundle').textContent = bundle;

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const check = (label, cond, extra = '') => {
  console.log(`${cond ? '  ok  ' : ' FAIL '} ${label}${cond ? '' : ' — ' + extra}`);
  if (!cond) failures.push(label + (extra ? ': ' + extra : ''));
};
const $ = (id) => doc.getElementById(id);
const q = (sel) => doc.querySelector(sel);
const settled = async (timeoutMs = 30000) => {
  const stop = Date.now() + timeoutMs;
  while (Date.now() < stop) {
    if ($('stopBtn').hidden) return true;
    await wait(120);
  }
  return false;
};
const click = (el) => el.dispatchEvent(new window.Event('click', { bubbles: true }));

console.log('\nMiniChat smoke test — keyless engines\n');
await wait(700);

// ── 1. first run ────────────────────────────────────────────────────────────
check('server reachable', (await fetch(BASE + '/api/health')).ok);
check('first-run wizard shown', !$('wizard').hidden);
check('wizard offers 3 ways to run', doc.querySelectorAll('.wiz-card').length === 3);
check('keyless option needs no key', /no key/i.test(doc.querySelector('.wiz-card[data-engine="keyless"]').textContent));

// ── 2. keyless engine (no signup) ───────────────────────────────────────────
click(doc.querySelector('.wiz-card[data-engine="keyless"]'));
await wait(1200); // discovery runs on selection
check('wizard closed, settings opened', $('wizard').hidden && !$('modal').hidden);
check('keyless endpoint discovered', /Connected to/.test($('endpointHint').textContent), $('endpointHint').textContent);
check('model auto-selected', $('modelSelect').value === 'demo-model', $('modelSelect').value);
check('API key field hidden for keyless', $('keyField').hidden === true);
check('find-endpoint button visible', $('findEndpoint').hidden === false);
check('diagnose button visible', $('diagnose').hidden === false);

// diagnostics report per-endpoint health
click($('diagnose'));
await wait(900);
check('diagnostics list endpoint health', doc.querySelectorAll('#diagBox .diag-row').length >= 1, $('diagBox').textContent.slice(0, 80));
check('diagnostics found a live endpoint', /up/.test($('diagBox').textContent), $('diagBox').textContent.slice(0, 120));

click($('closeModal'));
$('input').value = 'Explain why keyless endpoints are handy';
$('input').dispatchEvent(new window.Event('input', { bubbles: true }));
$('composer').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
await wait(200);
check('user bubble rendered', !!q('.msg.user .bubble'));
check('generation finished (keyless engine)', await settled());
const md = q('.msg.assistant .md');
check('answer streamed as markdown', !!md && md.textContent.length > 80, md?.textContent.slice(0, 60));
check('code block highlighted', !!q('.code-block pre code.hljs span'));
check('table rendered', !!q('.msg.assistant .md table'));
check('sidebar says keyless', /keyless/.test($('modelBadge').textContent), $('modelBadge').textContent);
check('history kept', doc.querySelectorAll('.chat-item').length === 1);

// ── 3. in-browser engine (WebGPU) ──────────────────────────────────────────
click($('openSettings'));
await wait(150);
click(doc.querySelector('.engine-card[data-engine="browser"]'));
await wait(200);
check('browser panel visible', !$('browserPanel').hidden && $('apiPanel').hidden);
check('browser models listed', $('browserModel').options.length >= 6, String($('browserModel').options.length));
check('no WebGPU in jsdom is reported', /no WebGPU/i.test($('dlText').textContent), $('dlText').textContent);
click($('closeModal'));

$('input').value = 'hi';
$('composer').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
await wait(600);
check('browser engine fails gracefully', /WebGPU/.test(q('.error-box')?.textContent || ''), q('.error-box')?.textContent?.slice(0, 80));
check('no crash: composer usable again', $('stopBtn').hidden === true);
// clean the failed message so later checks see a fresh thread
click($('newChat'));
await wait(150);

// ── 4. free-API-key engine ──────────────────────────────────────────
click($('openSettings'));
await wait(120);
click(doc.querySelector('.engine-card[data-engine="api"]'));
await wait(120);
check('api panel visible', !$('apiPanel').hidden && $('browserPanel').hidden);
check('engine cards offer 3 options', doc.querySelectorAll('.engine-card').length === 3);
check('API key field shown when a key is needed', $('keyField').hidden === false);
check('providers loaded', $('providerSelect').options.length >= 6, String($('providerSelect').options.length));
$('baseUrl').value = 'http://localhost:4010/v1';
$('modelInput').value = 'demo-model';
click($('saveSettings'));
await wait(150);

$('input').value = 'Say hi in markdown';
$('composer').dispatchEvent(new window.Event('submit', { bubbles: true, cancelable: true }));
await wait(200);
check('api engine streams answer', await settled(), 'stream did not finish');
check('api answer rendered', (q('.msg.assistant .md')?.textContent || '').length > 80);
check('theme toggle works', (() => {
  const before = doc.documentElement.dataset.theme;
  click($('themeToggle'));
  const after = doc.documentElement.dataset.theme;
  click($('themeToggle'));
  return before !== after;
})());
check('two chats in history', doc.querySelectorAll('.chat-item').length === 2, String(doc.querySelectorAll('.chat-item').length));

console.log('');
if (failures.length) {
  console.error(`${failures.length} failure(s):\n - ` + failures.join('\n - '));
  process.exit(1);
}
console.log('all checks passed');
process.exit(0);
