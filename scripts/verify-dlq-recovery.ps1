$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$processes = @()
$env:EVENTLAB_MESSAGING_ENABLED = 'true'

function Wait-ForHealth([int[]] $Ports) {
    $deadline = (Get-Date).AddSeconds(60)
    do {
        Start-Sleep -Milliseconds 750
        $healthy = @($Ports | Where-Object {
            try {
                (Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 `
                    "http://localhost:$($_)/actuator/health").StatusCode -eq 200
            } catch { $false }
        }).Count
    } while ($healthy -lt $Ports.Count -and (Get-Date) -lt $deadline)
    if ($healthy -lt $Ports.Count) { throw "Only $healthy/$($Ports.Count) services became healthy" }
}

function Get-Timeline([string] $WorkflowId) {
    $parsed = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 `
        "http://localhost:38080/api/v1/runs/$WorkflowId/timeline").Content | ConvertFrom-Json
    return @($parsed | ForEach-Object { $_ })
}

try {
    foreach ($definition in @(
        @('workflow-service', 38081),
        @('payment-service', 38082),
        @('fulfilment-service', 38083))) {
        $service = $definition[0]
        $processes += Start-Process java -ArgumentList @(
            '-jar', "services/$service/target/$service-0.1.0-SNAPSHOT.jar",
            "--server.port=$($definition[1])") `
            -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru
    }
    $processes += Start-Process java -ArgumentList @(
        '-jar', 'services/lab-console/target/lab-console-0.1.0-SNAPSHOT.jar',
        '--server.port=38080',
        '--eventlab.workflow-base-url=http://localhost:38081',
        '--eventlab.fulfilment-base-url=http://localhost:38083') `
        -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru
    Wait-ForHealth @(38080, 38081, 38082, 38083)

    $request = @{
        scenarioId = 'fulfilment-unavailable'
        amount = 129.90
        currency = 'EUR'
    } | ConvertTo-Json
    $run = (Invoke-WebRequest -UseBasicParsing -Method Post -ContentType application/json `
        -Body $request http://localhost:38080/api/v1/runs).Content | ConvertFrom-Json

    $deadline = (Get-Date).AddSeconds(40)
    do {
        Start-Sleep -Milliseconds 500
        $timeline = Get-Timeline $run.workflowId
    } while (-not ($timeline.state -contains 'DEAD_LETTERED') -and (Get-Date) -lt $deadline)
    if (@($timeline | Where-Object state -eq 'RETRY_SCHEDULED').Count -ne 4) {
        throw 'Expected four failed fulfilment attempts before dead-lettering'
    }

    Invoke-WebRequest -UseBasicParsing -Method Post -TimeoutSec 20 `
        "http://localhost:38080/api/v1/runs/$($run.workflowId)/recover" | Out-Null
    $deadline = (Get-Date).AddSeconds(40)
    do {
        Start-Sleep -Milliseconds 500
        $timeline = Get-Timeline $run.workflowId
    } while (-not ($timeline.state -contains 'COMPLETED') -and (Get-Date) -lt $deadline)

    if (-not ($timeline.state -contains 'RECOVERY_REQUESTED') `
            -or -not ($timeline.state -contains 'FULFILLED') `
            -or @($timeline | Where-Object state -eq 'COMPLETED').Count -ne 1) {
        throw 'Recovery invariant failed'
    }
    [pscustomobject]@{
        workflowId = $run.workflowId
        failedAttempts = @($timeline | Where-Object state -eq 'RETRY_SCHEDULED').Count
        completions = @($timeline | Where-Object state -eq 'COMPLETED').Count
        states = @($timeline.state)
    } | ConvertTo-Json -Depth 4
} finally {
    $processes | Where-Object { $_ -and -not $_.HasExited } | Stop-Process -Force
}
