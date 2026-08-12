param(
    [string] $BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'

$request = @{
    scenarioId = 'custom-plan'
    experimentPlan = @{
        paymentResultDeliveries = 2
        fulfilmentBehavior = 'BUSINESS_REJECTION'
    }
    amount = 129.90
    currency = 'EUR'
} | ConvertTo-Json

$run = (Invoke-WebRequest -UseBasicParsing -Method Post -ContentType application/json `
    -Body $request "$BaseUrl/api/v1/runs").Content | ConvertFrom-Json
if (-not $run.experimentPlanId) { throw 'The run response did not expose an experiment plan ID' }

$deadline = (Get-Date).AddSeconds(45)
do {
    Start-Sleep -Milliseconds 500
    $parsed = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 `
        "$BaseUrl/api/v1/runs/$($run.workflowId)/timeline").Content | ConvertFrom-Json
    $timeline = @($parsed | ForEach-Object { $_ })
} while (-not ($timeline.state -contains 'COMPENSATED') -and (Get-Date) -lt $deadline)

$paymentDeliveries = @($timeline | Where-Object eventType -eq 'payment.authorized').Count
$duplicateDecisions = @($timeline | Where-Object state -eq 'DUPLICATE_IGNORED').Count
$compensated = @($timeline | Where-Object state -eq 'COMPENSATED').Count
$completed = @($timeline | Where-Object state -eq 'COMPLETED').Count
if ($paymentDeliveries -ne 2 -or $duplicateDecisions -ne 1 `
        -or $compensated -ne 1 -or $completed -ne 0) {
    throw "Custom invariant failed: deliveries=$paymentDeliveries, duplicates=$duplicateDecisions, compensated=$compensated, completed=$completed"
}

[pscustomobject]@{
    workflowId = $run.workflowId
    experimentPlanId = $run.experimentPlanId
    paymentAuthorizedDeliveries = $paymentDeliveries
    duplicateDecisions = $duplicateDecisions
    compensated = $compensated
    completed = $completed
    states = @($timeline.state)
} | ConvertTo-Json -Depth 4
