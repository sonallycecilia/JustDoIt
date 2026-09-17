import { createRequire } from 'node:module';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { evidenceMetadata, readQualityContext } from './quality-context.mjs';

const expectedFrontendScenarios = 11;
const expectedBackendScenarios = 5;
const evidenceDir = resolve('quality-reports');
const junitPath = resolve(evidenceDir, 'session-lifecycle-junit.xml');
const outputPath = resolve(evidenceDir, 'session-protection.json');
const require = createRequire(import.meta.url);

await mkdir(evidenceDir, { recursive: true });

const context = await readQualityContext();
const junitStat = await stat(junitPath);
if (junitStat.mtimeMs < Date.parse(context.startedAt) - 1_000) {
  throw new Error(`JUnit anterior à execução ${context.runId}: ${junitPath}`);
}
const xml = await readFile(junitPath, 'utf8');
const suite = /<testsuite\b([^>]*)>/.exec(xml);
if (!suite) {
  throw new Error(`JUnit sem testsuite em ${junitPath}`);
}

function attribute(name) {
  const match = new RegExp(`${name}="(\\d+)"`).exec(suite[1]);
  return match ? Number(match[1]) : 0;
}

const denominator = attribute('tests');
const failures = attribute('failures') + attribute('errors');
const skipped = attribute('skipped');
const numerator = denominator - failures - skipped;
const percentage = denominator === 0 ? 0 : Number(((numerator / denominator) * 100).toFixed(2));
const passed = denominator === expectedFrontendScenarios
  && numerator === expectedFrontendScenarios
  && percentage === 100;

const result = {
  metric: 'Taxa de Proteção do Ciclo de Sessão',
  formula: 'TPS = cenários tratados corretamente / cenários testados * 100',
  component: 'frontend',
  evidence: evidenceMetadata(context, {
    source: 'quality-reports/session-lifecycle-junit.xml',
    vitestVersion: require('vitest/package.json').version,
  }),
  numerator,
  denominator,
  percentage,
  targetPercentage: 100,
  passed,
  systemicContract: {
    backendScenarios: expectedBackendScenarios,
    frontendScenarios: expectedFrontendScenarios,
    totalScenarios: expectedBackendScenarios + expectedFrontendScenarios,
    approvalRule: '5/5 no backend e 11/11 no frontend; ambos os gates devem estar verdes',
    systemicPassed: null,
    status: 'NÃO AGREGADA: a evidência do backend pertence ao pipeline complementar',
  },
};

await writeFile(outputPath, `${JSON.stringify(result, null, 2)}\n`, 'utf8');

console.log(
  `[MÉTRICA SEGURANÇA - CICLO DE SESSÃO FRONTEND] `
    + `A=${numerator} cenários corretos / B=${denominator} cenários testados -> TPS = ${percentage.toFixed(2)}%`,
);

if (!passed) {
  throw new Error(
    `TPS reprovada: esperado ${expectedFrontendScenarios}/${expectedFrontendScenarios} (100%), `
      + `obtido ${numerator}/${denominator} (${percentage}%)`,
  );
}
