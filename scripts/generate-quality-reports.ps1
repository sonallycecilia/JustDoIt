param(
    [string]$Commit = $(if ($env:QUALITY_COMMIT) { $env:QUALITY_COMMIT } else { git rev-parse HEAD }),
    [string]$Timestamp = $([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')),
    [string]$Environment = $(if ($env:QUALITY_ENVIRONMENT) { $env:QUALITY_ENVIRONMENT } else { "Local / $([System.Environment]::OSVersion.VersionString) / Java $((java -version 2>&1 | Select-Object -First 1))" }),
    [string]$TestLog = 'quality-test.log',
    [string]$RunContext = 'quality-reports/run-context.json',
    [switch]$EnforceGates
)

$ErrorActionPreference = 'Stop'
$outputDir = Join-Path $PSScriptRoot '..\docs\quality'
$evidenceDir = Join-Path $PSScriptRoot '..\quality-reports'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

if (-not (Test-Path -LiteralPath $RunContext)) {
    throw "Contexto de qualidade ausente. Execute ./scripts/prepare-quality-run.ps1 antes dos testes."
}
$context = Get-Content -Raw -LiteralPath $RunContext | ConvertFrom-Json
if ($context.commit -ne $Commit.Trim()) {
    throw "Commit do contexto ($($context.commit)) diverge do relatório ($Commit)."
}

$startedAt = [DateTimeOffset]::Parse($context.startedAt)
$worktreeDirty = if ($null -ne $context.worktreeDirty) { [bool]$context.worktreeDirty } else { [bool]((git status --porcelain | Out-String).Trim()) }
$logIsFresh = (Test-Path -LiteralPath $TestLog) -and ((Get-Item -LiteralPath $TestLog).LastWriteTimeUtc -ge $startedAt.UtcDateTime.AddSeconds(-1))
$log = if ($logIsFresh) { Get-Content -Raw -LiteralPath $TestLog } else { '' }

function MetricResult([string]$pattern, [int]$expectedDenominator, [string]$limit) {
    $match = [regex]::Match($log, $pattern)
    if (-not $match.Success) {
        return [pscustomobject]@{ Status = 'NÃO EXECUTADA'; Numerator = '—'; Denominator = $expectedDenominator; Result = '—'; Limit = $limit; Passed = $false }
    }
    $a = [int]$match.Groups['a'].Value
    $b = [int]$match.Groups['b'].Value
    $value = if ($b -eq 0) { 0 } else { $a / $b }
    $passed = $b -eq $expectedDenominator -and $a -eq $b
    $status = if ($passed) { 'APROVADA' } else { 'REPROVADA' }
    [pscustomobject]@{ Status = $status; Numerator = $a; Denominator = $b; Result = ('{0:P2}' -f $value); Limit = $limit; Passed = $passed }
}

$timer = MetricResult 'CRON.METRO CONCORRENTE\]\s+A=(?<a>\d+).*?B=(?<b>\d+)' 130 '100%'
$executedTime = MetricResult 'TEMPO EXECUTADO\]\s+A=(?<a>\d+)s.*?B=(?<b>\d+)s' 12000 '100%'
$access = MetricResult 'M.TRICA SEGURAN.A\]\s+A=(?<a>\d+).*?B=(?<b>\d+)' 36 '100%'
$session = MetricResult 'CICLO DE SESS.O BACKEND\]\s+A=(?<a>\d+).*?B=(?<b>\d+)' 5 '100%'
$inputMatches = [regex]::Matches($log, 'M.TRICA SEGURAN.A - VALIDA..O DE ENTRADA\]\s+A=(?<a>\d+).*?B=(?<b>\d+)')
$inputA = 0; $inputB = 0
foreach ($match in $inputMatches) { $inputA += [int]$match.Groups['a'].Value; $inputB += [int]$match.Groups['b'].Value }
$inputStatus = if ($inputB -eq 144 -and $inputA -eq $inputB) { 'APROVADA' } elseif ($inputB -eq 0) { 'NÃO EXECUTADA' } else { 'REPROVADA' }
$inputResult = if ($inputB -eq 0) { '—' } else { '{0:P2}' -f ($inputA / $inputB) }

$sessionEvidence = [ordered]@{
    metric = 'Taxa de Proteção do Ciclo de Sessão'
    formula = 'TPS = cenários tratados corretamente / cenários testados * 100'
    component = 'backend'
    evidence = [ordered]@{
        schemaVersion = 1
        runId = $context.runId
        commit = $context.commit
        worktreeDirty = $worktreeDirty
        environment = $context.environment
        runStartedAt = $context.startedAt
        measuredAt = $Timestamp
        source = $TestLog
        logFresh = $logIsFresh
    }
    numerator = if ($session.Numerator -eq '—') { $null } else { $session.Numerator }
    denominator = if ($session.Numerator -eq '—') { 0 } else { $session.Denominator }
    expectedDenominator = 5
    percentage = if ($session.Numerator -eq '—') { 0 } else { [math]::Round(([double]$session.Numerator / [double]$session.Denominator) * 100, 2) }
    targetPercentage = 100
    passed = $session.Passed
    systemicContract = [ordered]@{
        backendScenarios = 5
        frontendScenarios = 11
        totalScenarios = 16
        approvalRule = '5/5 no backend e 11/11 no frontend; ambos os gates devem estar verdes'
        systemicPassed = $null
        status = 'NÃO AGREGADA: a evidência do frontend pertence ao pipeline complementar'
    }
}
$sessionEvidence | ConvertTo-Json -Depth 8 | Set-Content -Encoding utf8 (Join-Path $evidenceDir 'backend-session-protection.json')

$header = @"
> Gerado automaticamente.
>
> Commit: $([char]96)$Commit$([char]96)
>
> Árvore de trabalho: $(if ($worktreeDirty) { 'com alterações não commitadas' } else { 'limpa' })
>
> Execução: $([char]96)$($context.runId)$([char]96)
>
> Data UTC: $([char]96)$Timestamp$([char]96)
>
> Ambiente: $Environment
"@

@"
# Usabilidade

$header

| Métrica | Situação | Denominador | Resultado | Limite/meta |
|---|---|---:|---:|---:|
| Taxa de conclusão de tarefas | NÃO IMPLEMENTADA | Jornadas iniciadas (não coletadas) | — | Não definida |
| Tempo para concluir uma operação | NÃO IMPLEMENTADA | Operações concluídas (não coletadas) | — | Não definido |
| Conformidade de acessibilidade | PARCIAL / NÃO MENSURADA | Critérios WCAG aplicáveis (não definidos) | — | Não definido |

Os testes HTTP do backend não medem jornadas de usuário nem conformidade WCAG. Essas métricas dependem de instrumentação e testes no frontend.
"@ | Set-Content -Encoding utf8 (Join-Path $outputDir 'usabilidade.md')

@"
# Desempenho

$header

| Métrica | Situação | Numerador | Denominador | Resultado | Limite/meta |
|---|---|---:|---:|---:|---:|
| Latência P95 da API | NÃO IMPLEMENTADA | — | Requisições medidas (não coletadas) | — | Não definido |
| Bloqueio de cronômetro concorrente | $($timer.Status) | $($timer.Numerator) bloqueios corretos | $($timer.Denominator) disputas esperadas | $($timer.Result) | $($timer.Limit) |

O teste do cronômetro executa 3 cenários, 10 acionamentos por cenário e 5 repetições. O denominador esperado é `(9 + 9 + 8) × 5 = 130`. O ambiente usa `MockMvc` e H2 em modo MySQL, não uma implantação produtiva.
"@ | Set-Content -Encoding utf8 (Join-Path $outputDir 'desempenho.md')

@"
# Correção funcional

$header

| Métrica | Situação | Numerador | Denominador | Resultado | Limite/meta |
|---|---|---:|---:|---:|---:|
| Exatidão do tempo executado | $($executedTime.Status) | $($executedTime.Numerator) segundos corretos | $($executedTime.Denominator) segundos registrados | $($executedTime.Result) | $($executedTime.Limit) |

A métrica compara o total de segundos calculado pelo sistema com o total conhecido registrado pelos cenários do teste. O denominador esperado da suíte atual é 12.000 segundos.
"@ | Set-Content -Encoding utf8 (Join-Path $outputDir 'correcao-funcional.md')

@"
# Segurança

$header

| Métrica | Situação | Numerador | Denominador | Resultado | Limite/meta |
|---|---|---:|---:|---:|---:|
| Bloqueio de acesso não autorizado | $($access.Status) | $($access.Numerator) bloqueios | $($access.Denominator) requisições inválidas | $($access.Result) | $($access.Limit) |
| Validação do corpus malicioso | $inputStatus | $(if ($inputB) { $inputA } else { '—' }) rejeições | $(if ($inputB) { $inputB } else { 144 }) casos esperados | $inputResult | 100% |
| Proteção do ciclo de sessão (backend) | $(if ($session.Passed) { 'IMPLEMENTADA / APROVADA' } else { $session.Status }) | $($session.Numerator) cenários corretos | $($session.Denominator) cenários obrigatórios | $($session.Result) | $($session.Limit) |
| TPS sistêmica | NÃO AGREGADA | — | 16 cenários esperados | — | 5/5 backend e 11/11 frontend |

O denominador de acesso é `4 endpoints × 9 condições sem credencial válida = 36`. O corpus de entrada esperado soma `36 Auth + 84 Task + 24 Notification = 144`.

A TPS usa `cenários corretos ÷ cenários testados × 100`. O backend exige 5/5: JWT expirado, rotação do refresh token, detecção de reutilização, logout e rate limiting. O frontend exige 11/11 no workflow próprio. O contrato esperado é 16/16, mas não é declarado aprovado sem uma execução sistêmica que agregue os dois artefatos.
"@ | Set-Content -Encoding utf8 (Join-Path $outputDir 'seguranca.md')

Write-Host "Relatórios gerados em $outputDir"

if ($EnforceGates -and -not $session.Passed) {
    throw "Gate TPS backend reprovado: esperado 5/5; obtido $($session.Numerator)/$($session.Denominator)."
}
