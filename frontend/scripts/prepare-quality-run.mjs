import { mkdir, rm, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import {
  QUALITY_CONTEXT_PATH,
  QUALITY_REPORT_DIR,
  createQualityContext,
} from './quality-context.mjs';

const generatedDirectories = [
  resolve('.lighthouseci'),
  QUALITY_REPORT_DIR,
  resolve('playwright-report'),
  resolve('test-results'),
];

for (const directory of generatedDirectories) {
  await rm(directory, { recursive: true, force: true });
}

const context = createQualityContext();
await mkdir(QUALITY_REPORT_DIR, { recursive: true });
await writeFile(QUALITY_CONTEXT_PATH, `${JSON.stringify(context, null, 2)}\n`, 'utf8');

console.log(`[QUALIDADE] execução=${context.runId} commit=${context.commit}`);
