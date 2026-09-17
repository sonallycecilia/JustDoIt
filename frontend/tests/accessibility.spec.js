import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import AxeBuilder from '@axe-core/playwright';
import { expect, test } from 'playwright/test';
import { AUDIT_TASK_ID, AUDITABLE_ROUTES } from '../src/appRoutes.js';
import { evidenceMetadata, readQualityContext } from '../scripts/quality-context.mjs';
import { accessibilityIncompleteAllowlist } from './accessibility-incomplete-allowlist.js';

const require = createRequire(import.meta.url);
const axeVersion = require('axe-core/package.json').version;

function mockedBody(url) {
  if (url.pathname === '/auth/me') {
    return { id: AUDIT_TASK_ID, name: 'Auditoria', email: 'auditoria@example.com', profile: 'USER' };
  }
  if (url.pathname === `/tasks/${AUDIT_TASK_ID}`) {
    return { id: AUDIT_TASK_ID, title: 'Tarefa de auditoria', status: 'PENDING', priority: 'MEDIUM' };
  }
  if (url.pathname === '/tasks/report') {
    return { byDay: [], byCategory: [], taskPerformance: [], totalTasks: 0, dueTasksCompleted: 0, totalEstimatedMinutes: 0, totalActualSeconds: 0 };
  }
  if (url.pathname === '/analytics/overall') return { reports: [], timeBlocks: [] };
  return [];
}

async function mockBackend(page) {
  await page.route(/^http:\/\/localhost:808[0-3]\//, async (route) => {
    const url = new URL(route.request().url());
    const nullable = /\/(note|module-config|cycle-config|timer|timers\/active|focus-sessions)$/.test(url.pathname)
      || url.pathname === '/weekly-plans';
    await route.fulfill({
      status: nullable ? 404 : 200,
      contentType: 'application/json',
      headers: { 'Access-Control-Allow-Origin': '*' },
      body: JSON.stringify(nullable ? { message: 'Não configurado' } : mockedBody(url)),
    });
  });
}

function axeRule(rule) {
  return {
    id: rule.id,
    impact: rule.impact,
    help: rule.help,
    nodes: rule.nodes.length,
    details: rule.nodes.map((node) => ({
      target: node.target,
      html: node.html,
      failureSummary: node.failureSummary,
      any: node.any?.map((check) => check.message),
      all: node.all?.map((check) => check.message),
      none: node.none?.map((check) => check.message),
    })),
  };
}

function allowanceKey(surface, rule) {
  return `${surface}:${rule}`;
}

test('métrica WCAG automatizada em todas as rotas do manifesto', async ({ browser, page }) => {
  const context = await readQualityContext();
  await mockBackend(page);
  const results = [];

  for (const surface of AUDITABLE_ROUTES) {
    try {
      await page.goto('/');
      await page.evaluate(({ key, value }) => {
        [...Array(localStorage.length).keys()]
          .map((index) => localStorage.key(index))
          .filter((storageKey) => storageKey?.startsWith('jdi.sessao'))
          .forEach((storageKey) => localStorage.removeItem(storageKey));
        [...Array(sessionStorage.length).keys()]
          .map((index) => sessionStorage.key(index))
          .filter((storageKey) => storageKey?.startsWith('jdi.sessao'))
          .forEach((storageKey) => sessionStorage.removeItem(storageKey));
        if (value) localStorage.setItem(key, value);
      }, {
        key: 'jdi.sessao',
        value: surface.private
          ? JSON.stringify({ accessToken: 'access-auditoria', refreshToken: 'refresh-auditoria', name: 'Auditoria' })
          : null,
      });

      await page.goto(surface.path, { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(750);
      const finalPath = new URL(page.url()).pathname;
      if (finalPath !== surface.path) {
        throw new Error(`${surface.name} redirecionou para ${finalPath}`);
      }

      const axe = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
        .analyze();

      results.push({
        surface: surface.name,
        path: surface.path,
        passedRules: axe.passes.map((rule) => rule.id),
        violations: axe.violations.map(axeRule),
        incompleteRules: axe.incomplete.map(axeRule),
        error: null,
      });
    } catch (error) {
      results.push({
        surface: surface.name,
        path: surface.path,
        passedRules: [],
        violations: [],
        incompleteRules: [],
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  const allowanceByKey = new Map(accessibilityIncompleteAllowlist.map((entry) => (
    [allowanceKey(entry.surface, entry.rule), entry]
  )));
  const observedIncompleteKeys = new Set();
  const justifiedIncomplete = [];
  const unjustifiedIncomplete = [];
  const today = new Date().toISOString().slice(0, 10);

  for (const result of results) {
    for (const rule of result.incompleteRules) {
      const key = allowanceKey(result.surface, rule.id);
      observedIncompleteKeys.add(key);
      const allowance = allowanceByKey.get(key);
      const valid = allowance?.justification?.trim()
        && allowance?.reviewedAt
        && allowance?.expiresAt >= today;
      const item = { surface: result.surface, rule: rule.id, details: rule, allowance: allowance ?? null };
      (valid ? justifiedIncomplete : unjustifiedIncomplete).push(item);
    }
  }

  const staleJustifications = accessibilityIncompleteAllowlist.filter((entry) => (
    !observedIncompleteKeys.has(allowanceKey(entry.surface, entry.rule))
  ));
  const approved = results.reduce((sum, result) => {
    const violated = new Set(result.violations.map((rule) => rule.id));
    const incomplete = new Set(result.incompleteRules.map((rule) => rule.id));
    return sum + new Set(result.passedRules.filter((rule) => !violated.has(rule) && !incomplete.has(rule))).size;
  }, 0);
  const violations = results.reduce((sum, result) => sum + result.violations.length, 0);
  const incomplete = results.reduce((sum, result) => sum + result.incompleteRules.length, 0);
  const denominator = approved + violations + incomplete;
  const percentage = denominator ? (approved / denominator) * 100 : 0;
  const navigationErrors = results.filter((result) => result.error);
  const passed = results.length === AUDITABLE_ROUTES.length
    && navigationErrors.length === 0
    && violations === 0
    && unjustifiedIncomplete.length === 0
    && staleJustifications.length === 0;
  const report = {
    metric: 'Cobertura de acessibilidade automatizada',
    standard: 'WCAG 2 A/AA, WCAG 2.1 A/AA e WCAG 2.2 AA cobertas pelo axe-core',
    formula: 'regras-página aprovadas / (aprovadas + violadas + inconclusivas)',
    evidence: evidenceMetadata(context, {
      axeCoreVersion: axeVersion,
      playwrightVersion: require('playwright/package.json').version,
      browser: `Chromium ${browser.version()}`,
    }),
    surfacesExpected: AUDITABLE_ROUTES.length,
    surfacesAudited: results.length - navigationErrors.length,
    numerator: approved,
    denominator,
    percentage: Number(percentage.toFixed(2)),
    violations,
    incomplete,
    justifiedIncomplete,
    unjustifiedIncomplete,
    staleJustifications,
    navigationErrors,
    passed,
    status: passed
      ? (justifiedIncomplete.length ? 'APROVADA COM REVISÃO MANUAL' : 'APROVADA')
      : 'REPROVADA',
    results,
  };

  await mkdir(resolve('quality-reports'), { recursive: true });
  await writeFile(resolve('quality-reports/accessibility.json'), `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  expect(navigationErrors, JSON.stringify(navigationErrors, null, 2)).toEqual([]);
  expect(results.flatMap((result) => result.violations), JSON.stringify(report.results, null, 2)).toEqual([]);
  expect(unjustifiedIncomplete, JSON.stringify(unjustifiedIncomplete, null, 2)).toEqual([]);
  expect(staleJustifications, JSON.stringify(staleJustifications, null, 2)).toEqual([]);
});
