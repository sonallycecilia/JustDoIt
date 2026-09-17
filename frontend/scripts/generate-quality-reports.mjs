import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { readQualityContext } from './quality-context.mjs';

const outputDir = resolve('docs/quality');
const evidenceDir = resolve('quality-reports');
await mkdir(outputDir, { recursive: true });
await mkdir(evidenceDir, { recursive: true });

const context = await readQualityContext();
const commit = context.commit;
const timestamp = process.env.QUALITY_TIMESTAMP ?? new Date().toISOString();
const environment = context.environment;
const rejectedEvidence = [];

async function readEvidence(path) {
  if (!existsSync(path)) return null;
  const evidence = JSON.parse(await readFile(path, 'utf8'));
  const reasons = [];
  if (evidence.evidence?.runId !== context.runId) reasons.push('runId divergente');
  if (evidence.evidence?.commit !== context.commit) reasons.push('commit divergente');
  if (Date.parse(evidence.evidence?.measuredAt) < Date.parse(context.startedAt)) {
    reasons.push('evidência anterior ao início da execução');
  }
  if (reasons.length) {
    rejectedEvidence.push({ path, reasons });
    return null;
  }
  return evidence;
}

let tests = { status: 'NÃO EXECUTADA', total: '—', passed: '—', failed: '—' };
const junitPath = resolve(evidenceDir, 'vitest-junit.xml');
if (existsSync(junitPath)) {
  const junitStat = await stat(junitPath);
  if (junitStat.mtimeMs >= Date.parse(context.startedAt) - 1_000) {
    const xml = await readFile(junitPath, 'utf8');
    const suites = [...xml.matchAll(/<testsuite\b[^>]*\btests="(\d+)"[^>]*\bfailures="(\d+)"/g)];
    if (suites.length) {
      const total = suites.reduce((sum, match) => sum + Number(match[1]), 0);
      const failed = suites.reduce((sum, match) => sum + Number(match[2]), 0);
      tests = { status: failed === 0 ? 'APROVADA' : 'REPROVADA', total, passed: total - failed, failed };
    }
  }
}

const lcp = await readEvidence(resolve(evidenceDir, 'lcp-p75.json'));
const accessibility = await readEvidence(resolve(evidenceDir, 'accessibility.json'));
const sessionProtection = await readEvidence(resolve(evidenceDir, 'session-protection.json'));
const lcpStatus = lcp ? (lcp.passed ? 'APROVADA' : 'REPROVADA') : 'NÃO EXECUTADA';
const lcpSamples = lcp ? lcp.samplesMs.join(', ') : '—';
const lcpValue = lcp ? `${lcp.valueMs} ms` : '—';
const lcpLimit = lcp ? `${lcp.limitMs} ms` : '2500 ms';
const accessibilityStatus = accessibility
  ? accessibility.status
  : 'NÃO EXECUTADA';
const accessibilityDenominator = accessibility?.denominator ?? '—';
const accessibilityResult = accessibility ? `${accessibility.numerator}/${accessibility.denominator} (${accessibility.percentage}%)` : '—';
const sessionStatus = sessionProtection
  ? (sessionProtection.passed ? 'IMPLEMENTADA / APROVADA' : 'IMPLEMENTADA / REPROVADA')
  : 'NÃO EXECUTADA';
const sessionDenominator = sessionProtection?.denominator ?? 11;
const sessionResult = sessionProtection
  ? `${sessionProtection.numerator}/${sessionProtection.denominator} (${sessionProtection.percentage}%)`
  : '—';

const header = `> Gerado automaticamente.
>
> Commit: \`${commit}\`
>
> Árvore de trabalho: ${context.worktreeDirty ? 'com alterações não commitadas' : 'limpa'}
>
> Execução: \`${context.runId}\`
>
> Data UTC: \`${timestamp}\`
>
> Ambiente: ${environment}`;

await writeFile(resolve(outputDir, 'usabilidade.md'), `# Usabilidade

${header}

| Métrica | Situação | Denominador | Resultado | Limite/meta |
|---|---|---:|---:|---:|
| Taxa de conclusão de tarefas | NÃO IMPLEMENTADA | Jornadas iniciadas (não coletadas) | — | Não definida |
| Tempo para concluir uma operação | NÃO IMPLEMENTADA | Operações concluídas (não coletadas) | — | Não definido |
| Cobertura de acessibilidade automatizada | ${accessibilityStatus} | ${accessibilityDenominator} verificações regra-página | ${accessibilityResult} | 0 violações; 0 inconclusivos sem justificativa |

## Evidência auxiliar

| Verificação | Situação | Denominador | Resultado | Limite |
|---|---|---:|---:|---:|
| Suíte Vitest | ${tests.status} | ${tests.total} casos executados | ${tests.passed} passaram; ${tests.failed} falharam | 0 falhas |

Os testes de componentes e hooks dão suporte aos fluxos, mas não medem jornadas reais. A acessibilidade usa axe-core em Chromium sobre ${accessibility?.surfacesAudited ?? 0}/${accessibility?.surfacesExpected ?? 13} rotas do manifesto da aplicação. O denominador inclui regras aprovadas, violadas e inconclusivas; por isso não é publicado 100% enquanto houver revisão manual. Inconclusivos justificados: ${accessibility?.justifiedIncomplete?.length ?? '—'}; sem justificativa: ${accessibility?.unjustifiedIncomplete?.length ?? '—'}. Navegação por teclado dos diálogos é testada no Vitest. Leitores de tela continuam como verificação manual.
`, 'utf8');

await writeFile(resolve(outputDir, 'desempenho.md'), `# Desempenho

${header}

| Métrica | Situação | Denominador | Amostras | Resultado | Limite/meta |
|---|---|---:|---|---:|---:|
| LCP no percentil 75 | ${lcpStatus} | ${lcp ? `${lcp.samplesMs.length}/${lcp.evidence.expectedRuns}` : '0/4'} execuções válidas | ${lcpSamples} ms | ${lcpValue} | ${lcpLimit} |

O P75 usa nearest rank: posição \`ceil(0,75 × N)\` das amostras ordenadas. O gate exige exatamente quatro relatórios da URL configurada, gerados depois do início desta execução. A coleta usa build de produção, Lighthouse desktop e somente a página inicial servida localmente.
`, 'utf8');

await writeFile(resolve(outputDir, 'correcao-funcional.md'), `# Correção funcional

${header}

| Verificação | Situação | Numerador | Denominador | Resultado | Limite/meta |
|---|---|---:|---:|---:|---:|
| Suíte automatizada Vitest | ${tests.status} | ${tests.passed} casos aprovados | ${tests.total} casos executados | ${tests.total === '—' ? '—' : `${tests.passed}/${tests.total}`} | 0 falhas |

A suíte cobre componentes, hooks e regras funcionais. Ela é evidência de regressão automatizada, não uma medição E2E de jornadas reais.
`, 'utf8');

await writeFile(resolve(outputDir, 'seguranca.md'), `# Segurança

${header}

| Métrica | Situação | Denominador | Resultado | Limite/meta |
|---|---|---:|---:|---:|
| Proteção do ciclo de sessão (frontend) | ${sessionStatus} | ${sessionDenominator} cenários obrigatórios | ${sessionResult} | 100% exatos |

A TPS usa \`cenários corretos ÷ cenários testados × 100\`. O cliente testa 11/11 cenários: renovações concorrentes, token atualizado por outra aba, rotação preservando o storage escolhido, refresh 401, 429, 5xx e falha de rede, rejeição após rotação, respostas 403 de sessão e de regra de negócio, além de sessão legada sem refresh token. O backend possui gate complementar de 5/5 para JWT expirado, rotação, reutilização, logout e rate limiting. O contrato sistêmico esperado é 16/16, mas permanece NÃO AGREGADO enquanto os dois artefatos não forem validados na mesma execução sistêmica.

O risco de access token e refresh token em Web Storage está registrado em \`docs/security/session-storage-risk.md\`; a migração para cookies HttpOnly está planejada no ticket \`docs/backlog/SEC-001-http-only-session.md\`.
`, 'utf8');

console.log(`Relatórios gerados em ${outputDir}`);

await writeFile(resolve(evidenceDir, 'evidence-index.json'), `${JSON.stringify({
  context,
  generatedAt: timestamp,
  accepted: {
    lcp: Boolean(lcp),
    accessibility: Boolean(accessibility),
    sessionProtection: Boolean(sessionProtection),
  },
  rejectedEvidence,
}, null, 2)}\n`, 'utf8');
