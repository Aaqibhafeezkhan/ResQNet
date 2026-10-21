/**
 * Runs a static server, executes a scenario script against it, then shuts down.
 * Keeps the browser checks reproducible from a single command.
 */
import { spawn } from 'node:child_process';
import { setTimeout as delay } from 'node:timers/promises';

const [scriptArg, ...rest] = process.argv.slice(2);
if (!scriptArg) {
  console.error('usage: node scripts/with-server.mjs <script.mjs> [args...]');
  process.exit(1);
}

const server = spawn(
  process.execPath,
  ['scripts/static-server.mjs', 'dist', '4173'],
  { stdio: ['ignore', 'pipe', 'pipe'] },
);
server.stdout.on('data', () => {});
server.stderr.on('data', (chunk) => process.stderr.write(chunk));

await delay(700);

try {
  const child = spawn(process.execPath, [scriptArg, ...rest], {
    stdio: 'inherit',
    env: { ...process.env, RESQNET_BASE_URL: 'http://localhost:4173' },
  });
  const code = await new Promise((resolve) => child.on('exit', resolve));
  process.exit(code ?? 0);
} finally {
  server.kill();
}
