/**
 * One command that always works, with no network and no API key:
 * starts a fake model backend and points MiniChat at it.
 *
 *   npm run demo            # http://localhost:3000
 *   PORT=4000 npm run demo
 *
 * Dev-only convenience — not used in production.
 */

import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const PORT = process.env.PORT || process.env.DEMO_APP_PORT || '3000';
const MODEL_PORT = process.env.DEMO_PORT || '4010';

const children = [];

function run(label, args, env) {
  const child = spawn(process.execPath, args, {
    cwd: root,
    env: { ...process.env, ...env },
    stdio: ['ignore', 'pipe', 'pipe'],
  });
  const tag = (line) => process.stdout.write(`[${label}] ${line}`);
  child.stdout.on('data', (d) => tag(d));
  child.stderr.on('data', (d) => tag(d));
  child.on('exit', (code) => {
    if (code !== 0 && code !== null) process.stdout.write(`[${label}] exited with ${code}\n`);
  });
  children.push(child);
  return child;
}

process.stdout.write('\n  Starting MiniChat in demo mode (fake model, no key needed)…\n');
run('model', [path.join('tools', 'demo-model.js')], { DEMO_PORT: MODEL_PORT });
run('app', ['server.js'], { PORT, PUBLIC_ENDPOINTS: `http://localhost:${MODEL_PORT}/v1` });

process.stdout.write(`\n  Open http://localhost:${PORT} → pick "Start chatting — no signup"\n\n`);

const stop = () => {
  for (const c of children) if (!c.killed) c.kill('SIGTERM');
  setTimeout(() => process.exit(0), 200);
};
process.on('SIGINT', stop);
process.on('SIGTERM', stop);
