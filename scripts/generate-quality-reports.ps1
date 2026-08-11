param(
    [string]$Commit = $(git rev-parse HEAD),
    [string]$Timestamp = $([DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')),
    [string]$Environment = "Local / $([System.Environment]::OSVersion.VersionString) / Java $((java -version 2>&1 | Select-Object -First 1))",
    [string]$TestLog = 'quality-test.log'
)

$ErrorActionPreference = 'Stop'
$outputDir = Join-Path $PSScriptRoot '..\docs\quality'
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$log = if (Test-Path $TestLog) { Get-Content -Raw $TestLog } else { '' }

function MetricResult([string]$pattern, [int]$expectedDenominator, [string]$limit) {
    $match = [regex]::Match($log, $pattern)
    if (-not $match.Success) {
        return [pscustomobject]@{ Status = 'NÃO EXECUTADA'; Numerator = '—'; Denominator = $expectedDenominator; Result = '—'; Limit = $limit }
    }
    $a = [int]$match.Groups['a'].Value
    $b = [int]$match.Groups['b'].Value
    $value = if ($b -eq 0) { 0 } else { $a / $b }
    $status = if ($b -eq $expectedDenominator -and $value -ge 1) { 'APROVADA' } else { 'REPROVADA' }
    [pscustomobject]@{ Status = $status; Numerator = $a; Denominator = $b; Result = ('{0:P2}' -f $value); Limit = $limit }
}

$timer = MetricResult 'CRON.METRO CONCORRENTE\]\s+A=(?<a>\d+).*?B=(?<b>\d+)' 130 '100%'
$executedTime = MetricResult 'TEMPO EXECUTADO\]\s+A=(?<a>\d+)s.*?B=(?<b>\d+)s' 12000 '100%'
$access = MetricResult 'M.TRICA SEGURAN.A\]\s+A=(?<a>\d+).*?B=(?<b>\d+)' 36 '100%'
$inputMatches = [regex]::Matches($log, 'M.TRICA SEGURAN.A - VALIDA..O DE ENTRADA\]\s+A=(?<a>\d+).*?B=(?<b>\d+)')
$inputA = 0; $inputB = 0
foreach ($match in $inputMatches) { $inputA += [int]$match.Groups['a'].Value; $inputB += [int]$match.Groups['b'].Value }
$inputStatus = if ($inputB -eq 144 -and $inputA -eq $inputB) { 'APROVADA' } elseif ($inputB -eq 0) { 'NÃO EXECUTADA' } else { 'REPROVADA' }
$inputResult = if ($inputB -eq 0) { '—' } else { '{0:P2}' -f ($inputA / $inputB) }

$header = @"
> Gerado automaticamente.  
> Commit: $([char]96)$Commit$([char]96)  
> Data UTC: $([char]96)$Timestamp$([char]96)  
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
| Proteção do ciclo de sessão | PARCIAL / NÃO AGREGADA | — | Cenários ainda não formalizados | — | Não definido |

O denominador de acesso é `4 endpoints × 9 condições sem credencial válida = 36`. O corpus de entrada esperado soma `36 Auth + 84 Task + 24 Notification = 144`. A proteção de sessão possui testes individuais, mas ainda não dispõe de fórmula agregadora estável.
"@ | Set-Content -Encoding utf8 (Join-Path $outputDir 'seguranca.md')

Write-Host "Relatórios gerados em $outputDir"
