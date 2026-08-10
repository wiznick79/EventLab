$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$processes = @()
$env:EVENTLAB_MESSAGING_ENABLED = 'true'

function Wait-ForHealth {
    param([int[]] $Ports)

    $deadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Milliseconds 750
        $healthy = @($Ports | Where-Object {
            try {
                (Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 `
                    "http://localhost:$($_)/actuator/health").StatusCode -eq 200
            } catch {
                $false
            }
        }).Count
    } while ($healthy -lt $Ports.Count -and (Get-Date) -lt $deadline)

    if ($healthy -lt $Ports.Count) {
        throw "Only $healthy/$($Ports.Count) services became healthy"
    }
}

try {
    $processes += Start-Process java -ArgumentList @(
        '-jar', 'services/workflow-service/target/workflow-service-0.1.0-SNAPSHOT.jar',
        '--server.port=18081') -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru
    $processes += Start-Process java -ArgumentList @(
        '-jar', 'services/payment-service/target/payment-service-0.1.0-SNAPSHOT.jar',
        '--server.port=18082') -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru
    $processes += Start-Process java -ArgumentList @(
        '-jar', 'services/fulfilment-service/target/fulfilment-service-0.1.0-SNAPSHOT.jar',
        '--server.port=18083') -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru
    $processes += Start-Process java -ArgumentList @(
        '-jar', 'services/lab-console/target/lab-console-0.1.0-SNAPSHOT.jar',
        '--server.port=18080',
        '--eventlab.workflow-base-url=http://localhost:18081',
        '--eventlab.fulfilment-base-url=http://localhost:18083') `
        -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru

    Wait-ForHealth -Ports @(18081, 18082, 18083, 18080)

    $request = @{
        scenarioId = 'duplicate-payment-result'
        amount = 129.90
        currency = 'EUR'
    } | ConvertTo-Json
    $run = (Invoke-WebRequest -UseBasicParsing -Method Post -ContentType 'application/json' `
        -Body $request -TimeoutSec 10 http://localhost:18080/api/v1/runs).Content | ConvertFrom-Json

    $deadline = (Get-Date).AddSeconds(40)
    do {
        Start-Sleep -Milliseconds 500
        $parsed = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 `
            "http://localhost:18080/api/v1/runs/$($run.workflowId)/timeline").Content | ConvertFrom-Json
        $timeline = @($parsed | ForEach-Object { $_ })
        $duplicates = @($timeline | Where-Object duplicateDelivery).Count
        $completions = @($timeline | Where-Object state -eq 'COMPLETED').Count
    } while (($duplicates -lt 1 -or $completions -lt 1) -and (Get-Date) -lt $deadline)

    $paymentDeliveries = @($timeline | Where-Object eventType -eq 'payment.authorized').Count
    if ($paymentDeliveries -ne 2 -or $duplicates -ne 1 -or $completions -ne 1) {
        throw "Invariant failed: payment deliveries=$paymentDeliveries, duplicates=$duplicates, completions=$completions"
    }

    [pscustomobject]@{
        workflowId = $run.workflowId
        paymentDeliveries = $paymentDeliveries
        duplicateObservations = $duplicates
        workflowCompletions = $completions
        states = @($timeline.state)
    } | ConvertTo-Json -Depth 4
} finally {
    $processes | Where-Object { $_ -and -not $_.HasExited } | Stop-Process -Force
}
