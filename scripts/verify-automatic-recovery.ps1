param(
    [string] $BaseUrl = 'http://localhost:38080'
)

$ErrorActionPreference = 'Stop'
$request = @{
    scenarioId = 'custom-plan'
    experimentPlan = @{
        paymentResultDeliveries = 1
        fulfilmentBehavior = 'TEMPORARY_UNAVAILABLE'
        fulfilmentMaxAttempts = 3
        recoveryMode = 'AUTOMATIC'
    }
    amount = 129.90
    currency = 'EUR'
} | ConvertTo-Json

$run = (Invoke-WebRequest -UseBasicParsing -Method Post -ContentType application/json `
    -Body $request "$BaseUrl/api/v1/runs").Content | ConvertFrom-Json
$deadline = (Get-Date).AddSeconds(60)
do {
    Start-Sleep -Milliseconds 500
    $details = (Invoke-WebRequest -UseBasicParsing `
        "$BaseUrl/api/v1/runs/$($run.workflowId)").Content | ConvertFrom-Json
} while ($details.state -ne 'COMPLETED' -and (Get-Date) -lt $deadline)

$attempts = @($details.timeline | Where-Object eventType -eq 'fulfilment.attempt-failed').Count
$deadLetters = @($details.timeline | Where-Object state -eq 'DEAD_LETTERED').Count
$recoveries = @($details.timeline | Where-Object state -eq 'RECOVERY_REQUESTED')
if ($details.state -ne 'COMPLETED' -or $attempts -ne 3 -or $deadLetters -ne 1 `
        -or $recoveries.Count -ne 1 -or $recoveries[0].payload.initiatedBy -ne 'automatic-policy') {
    throw 'Automatic recovery invariant failed'
}

[pscustomobject]@{
    workflowId = $run.workflowId
    failedAttempts = $attempts
    deadLetters = $deadLetters
    recoveryInitiator = $recoveries[0].payload.initiatedBy
    terminalState = $details.state
    states = @($details.timeline.state)
} | ConvertTo-Json -Depth 4
