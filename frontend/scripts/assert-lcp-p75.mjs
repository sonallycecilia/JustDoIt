import { mkdir, readdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { evidenceMetadata, readQualityContext } from './quality-context.mjs';

const REPORT_DIR = resolve('.lighthouseci');
const OUTPUT_DIR = resolve('quality-reports');
const OUTPUT_PATH = resolve(OUTPUT_DIR, 'lcp-p75.json');
const EXPECTED_URL = new URL(process.env.LCP_TARGET_URL ?? 'http://127.0.0.1:4173/').href;
const EXPECTED_RUNS = Number(process.env.LCP_EXPECTED_RUNS ?? 4);
const LIMIT_MS = Number(process.env.LCP_P75_LIMIT_MS ?? 2500);

if (!Number.isInteger(EXPECTED_RUNS) || EXPECTED_RUNS <= 0) {
  throw new Error(`LCP_EXPECTED_RUNS deve ser um inteiro positivo; recebido: ${process.env.LCP_EXPECTED_RUNS}`);
}
if (!Number.isFinite(LIMIT_MS) || LIMIT_MS <= 0) {
  throw new Error(`LCP_P75_LIMIT_MS deve ser um número positivo e finito; recebido: ${process.env.LCP_P75_LIMIT_MS}`);
}

function percentileNearestRank(values, percentile) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const rank = Math.ceil(percentile * sorted.length);
  return sorted[rank - 1];
}

function normalizedUrl(value) {
  try {
    return new URL(value).href;
  } catch {
    return null;
  }
}

const context = await readQualityContext();
const validationErrors = [];
let reportFiles = [];
try {
  reportFiles = (await readdir(REPORT_DIR))
    .filter((file) => /^lhr-.*\.json$/.test(file))
    .sort();
} catch (error) {
  if (error?.code !== 'ENOENT') throw error;
}

if (reportFiles.length !== EXPECTED_RUNS) {
  validationErrors.push(`Esperados exatamente ${EXPECTED_RUNS} relatórios LHR; encontrados ${reportFiles.length}.`);
}

const reports = [];
const runStartedAt = Date.parse(context.startedAt);
for (const file of reportFiles) {
  try {
    const report = JSON.parse(await readFile(resolve(REPORT_DIR, file), 'utf8'));
    const lcp = report.audits?.['largest-contentful-paint']?.numericValue;
    const requestedUrl = normalizedUrl(report.requestedUrl);
    const finalUrl = normalizedUrl(report.finalUrl);
    const fetchTime = Date.parse(report.fetchTime);
    const errors = [];

    if (requestedUrl !== EXPECTED_URL || finalUrl !== EXPECTED_URL) {
      errors.push(`URL divergente: requested=${requestedUrl}; final=${finalUrl}; esperada=${EXPECTED_URL}`);
    }
    if (!Number.isFinite(lcp)) errors.push('LCP ausente ou inválido.');
    if (report.runtimeError) errors.push(`Erro do Lighthouse: ${report.runtimeError.message ?? report.runtimeError.code}`);
    if (!Number.isFinite(fetchTime) || fetchTime < runStartedAt - 5_000) {
      errors.push(`Relatório anterior ao início da execução (${context.startedAt}).`);
    }

    if (errors.length) validationErrors.push(`${file}: ${errors.join(' ')}`);
    reports.push({
      file,
      requestedUrl,
      finalUrl,
      fetchTime: report.fetchTime ?? null,
      lcpMs: Number.isFinite(lcp) ? Math.round(lcp) : null,
      lighthouseVersion: report.lighthouseVersion ?? null,
      browserUserAgent: report.userAgent ?? report.environment?.hostUserAgent ?? null,
      errors,
    });
  } catch (error) {
    validationErrors.push(`${file}: JSON inválido (${error.message}).`);
  }
}

const samples = reports.map((report) => report.lcpMs).filter(Number.isFinite);
const lcpP75 = percentileNearestRank(samples, 0.75);
const passed = validationErrors.length === 0
  && samples.length === EXPECTED_RUNS
  && lcpP75 <= LIMIT_MS;
const result = {
  metric: 'Largest Contentful Paint (LCP) no P75',
  formula: 'P75 = valor na posição ceil(0,75 × N) das amostras ordenadas',
  evidence: evidenceMetadata(context, {
    targetUrl: EXPECTED_URL,
    expectedRuns: EXPECTED_RUNS,
    lighthouseVersions: [...new Set(reports.map((report) => report.lighthouseVersion).filter(Boolean))],
    browserUserAgents: [...new Set(reports.map((report) => report.browserUserAgent).filter(Boolean))],
  }),
  samplesMs: [...samples].sort((a, b) => a - b),
  reports,
  percentile: 75,
  valueMs: lcpP75,
  limitMs: LIMIT_MS,
  validationErrors,
  passed,
};

await mkdir(OUTPUT_DIR, { recursive: true });
await writeFile(OUTPUT_PATH, `${JSON.stringify(result, null, 2)}\n`, 'utf8');

console.log(
  `[MÉTRICA DESEMPENHO - LCP P75] amostras=${result.samplesMs.join(',')} ms`
  + ` -> P75=${lcpP75 ?? '—'} ms; limite=${LIMIT_MS} ms; ${passed ? 'APROVADA' : 'REPROVADA'}`,
);

if (!passed) {
  console.error(validationErrors.join('\n') || `LCP P75 ${lcpP75} ms excedeu ${LIMIT_MS} ms.`);
  process.exitCode = 1;
}
