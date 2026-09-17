import { randomUUID } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

export const QUALITY_REPORT_DIR = resolve('quality-reports');
export const QUALITY_CONTEXT_PATH = resolve(QUALITY_REPORT_DIR, 'run-context.json');

export function currentCommit() {
  return process.env.QUALITY_COMMIT
    ?? execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
}

function worktreeDirty() {
  return execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8' }).trim().length > 0;
}

export function createQualityContext() {
  return {
    schemaVersion: 1,
    runId: process.env.QUALITY_RUN_ID ?? randomUUID(),
    commit: currentCommit(),
    worktreeDirty: worktreeDirty(),
    startedAt: new Date().toISOString(),
    environment: process.env.QUALITY_ENVIRONMENT
      ?? `${process.platform} ${process.arch} / Node ${process.version}`,
    nodeVersion: process.version,
  };
}

export async function readQualityContext() {
  try {
    const context = JSON.parse(await readFile(QUALITY_CONTEXT_PATH, 'utf8'));
    return {
      ...context,
      worktreeDirty: context.worktreeDirty ?? worktreeDirty(),
    };
  } catch (error) {
    if (error?.code === 'ENOENT') {
      throw new Error('Contexto de qualidade ausente. Execute `npm run quality:prepare` antes dos gates.');
    }
    throw error;
  }
}

export function evidenceMetadata(context, extra = {}) {
  return {
    schemaVersion: 1,
    runId: context.runId,
    commit: context.commit,
    worktreeDirty: context.worktreeDirty,
    environment: context.environment,
    runStartedAt: context.startedAt,
    measuredAt: new Date().toISOString(),
    ...extra,
  };
}
