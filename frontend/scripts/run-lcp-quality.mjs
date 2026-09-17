import { spawn, spawnSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';

const REPORT_DIR = resolve('.lighthouseci');
const TARGET_URL = new URL(process.env.LCP_TARGET_URL ?? 'http://127.0.0.1:4173/');
const EXPECTED_RUNS = Number(process.env.LCP_EXPECTED_RUNS ?? 4);

if (!Number.isInteger(EXPECTED_RUNS) || EXPECTED_RUNS <= 0) {
  throw new Error(`LCP_EXPECTED_RUNS deve ser um inteiro positivo; recebido: ${process.env.LCP_EXPECTED_RUNS}`);
}

function runNode(script, args = []) {
  return spawnSync(process.execPath, [resolve(script), ...args], { stdio: 'inherit' });
}

async function waitForServer(url, timeoutMs = 30_000) {
  const deadline = Date.now() + timeoutMs;
  let lastError;

  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (response.ok) return;
      lastError = new Error(`Servidor respondeu HTTP ${response.status}.`);
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolveWait) => setTimeout(resolveWait, 250));
  }

  throw new Error(`O preview não ficou disponível em ${url} após ${timeoutMs} ms: ${lastError?.message ?? 'erro desconhecido'}`);
}

async function stopServer(server) {
  if (server.exitCode !== null || server.signalCode !== null) return;
  server.kill();
  await Promise.race([
    new Promise((resolveExit) => server.once('exit', resolveExit)),
    new Promise((resolveTimeout) => setTimeout(resolveTimeout, 5_000)),
  ]);
}

async function collectLcpReports() {
  if (TARGET_URL.protocol !== 'http:') {
    throw new Error(`LCP_TARGET_URL deve usar HTTP local; recebido: ${TARGET_URL.href}`);
  }

  const server = spawn(process.execPath, [
    resolve('node_modules/vite/bin/vite.js'),
    'preview',
    '--host', TARGET_URL.hostname,
    '--port', TARGET_URL.port || '80',
    '--strictPort',
  ], { stdio: 'inherit' });
  let browser;

  try {
    await waitForServer(TARGET_URL.href);
    browser = await chromium.launch({ headless: true });
    await mkdir(REPORT_DIR, { recursive: true });

    for (let run = 1; run <= EXPECTED_RUNS; run += 1) {
      const context = await browser.newContext({ viewport: { width: 1350, height: 940 } });
      const page = await context.newPage();
      await page.addInitScript(() => {
        window.__lastLcpEntry = null;
        new PerformanceObserver((list) => {
          const entries = list.getEntries();
          window.__lastLcpEntry = entries.at(-1) ?? window.__lastLcpEntry;
        }).observe({ type: 'largest-contentful-paint', buffered: true });
      });

      const response = await page.goto(TARGET_URL.href, { waitUntil: 'networkidle', timeout: 30_000 });
      if (!response?.ok()) {
        throw new Error(`Execução ${run}: a página respondeu HTTP ${response?.status() ?? 'desconhecido'}.`);
      }
      await page.waitForTimeout(1_000);

      const measurement = await page.evaluate(() => {
        const buffered = performance.getEntriesByType('largest-contentful-paint').at(-1);
        const entry = window.__lastLcpEntry ?? buffered;
        return {
          lcp: entry?.startTime ?? null,
          userAgent: navigator.userAgent,
        };
      });
      if (!Number.isFinite(measurement.lcp)) {
        throw new Error(`Execução ${run}: o navegador não produziu uma amostra de LCP.`);
      }

      const report = {
        requestedUrl: TARGET_URL.href,
        finalUrl: new URL(page.url()).href,
        fetchTime: new Date().toISOString(),
        collector: 'playwright-performance-observer',
        userAgent: measurement.userAgent,
        audits: {
          'largest-contentful-paint': { numericValue: measurement.lcp },
        },
      };
      await writeFile(
        resolve(REPORT_DIR, `lhr-playwright-${run}.json`),
        `${JSON.stringify(report, null, 2)}\n`,
        'utf8',
      );
      console.log(`[LCP ${run}/${EXPECTED_RUNS}] ${Math.round(measurement.lcp)} ms`);
      await context.close();
    }
  } finally {
    await browser?.close();
    await stopServer(server);
  }
}

const build = runNode('node_modules/vite/bin/vite.js', ['build']);
let collectionStatus = 1;
if (build.status === 0) {
  try {
    await collectLcpReports();
    collectionStatus = 0;
  } catch (error) {
    console.error(`[COLETA LCP] ${error.stack ?? error.message}`);
  }
}

// O agregador roda mesmo quando build/coleta falham para registrar zero ou as
// amostras parciais e explicar por que o gate foi reprovado.
const assertion = runNode('scripts/assert-lcp-p75.mjs');
process.exitCode = build.status || collectionStatus || assertion.status || 0;
