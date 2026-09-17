import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

function runNode(script, args = []) {
  return spawnSync(process.execPath, [resolve(script), ...args], { stdio: 'inherit' });
}

const build = runNode('node_modules/vite/bin/vite.js', ['build']);
const collection = build.status === 0
  ? runNode('node_modules/@lhci/cli/src/cli.js', ['collect'])
  : { status: 1 };

// O agregador roda mesmo quando build/coleta falham para registrar zero ou as
// amostras parciais e explicar por que o gate foi reprovado.
const assertion = runNode('scripts/assert-lcp-p75.mjs');
process.exitCode = build.status || collection.status || assertion.status || 0;
