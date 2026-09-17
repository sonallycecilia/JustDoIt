import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

const vitest = spawnSync(process.execPath, [
  resolve('node_modules/vitest/vitest.mjs'),
  'run',
  'src/api/sessionLifecycle.metric.test.js',
  '--reporter=junit',
  '--outputFile=quality-reports/session-lifecycle-junit.xml',
], { stdio: 'inherit' });

const assertion = spawnSync(process.execPath, [resolve('scripts/assert-session-protection.mjs')], {
  stdio: 'inherit',
});

process.exitCode = vitest.status || assertion.status || 0;
