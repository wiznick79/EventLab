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

try {
    foreach ($definition in @(
        @('workflow-service', 18081), @('payment-service', 18082), @('fulfilment-service', 18083))) {
        $service = $definition[0]
        $processes += Start-Process java -ArgumentList @(
            '-jar', "services/$service/target/$service-0.1.0-SNAPSHOT.jar",
            "--server.port=$($definition[1])") `
            -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru
    }
    $processes += Start-Process java -ArgumentList @(
        '-jar', 'services/lab-console/target/lab-console-0.1.0-SNAPSHOT.jar', '--server.port=18080',
        '--eventlab.workflow-base-url=http://localhost:18081',
        '--eventlab.fulfilment-base-url=http://localhost:18083') `
        -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru
    Wait-ForHealth @(18080, 18081, 18082, 18083)

    $request = @{ scenarioId = 'out-of-order-event'; amount = 129.90; currency = 'EUR' } | ConvertTo-Json
    $run = (Invoke-WebRequest -UseBasicParsing -Method Post -ContentType application/json `
        -Body $request http://localhost:18080/api/v1/runs).Content | ConvertFrom-Json
    $deadline = (Get-Date).AddSeconds(40)
    do {
        Start-Sleep -Milliseconds 500
        $parsed = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 `
            "http://localhost:18080/api/v1/runs/$($run.workflowId)/timeline").Content | ConvertFrom-Json
        $timeline = @($parsed | ForEach-Object { $_ })
    } while (-not ($timeline.state -contains 'STALE_IGNORED') -and (Get-Date) -lt $deadline)

    $workflow = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 `
        "http://localhost:18081/api/v1/workflows/$($run.workflowId)").Content | ConvertFrom-Json
    if ($workflow.state -ne 'COMPLETED' -or -not ($timeline.state -contains 'LATE_UPDATE_OBSERVED') `
            -or -not ($timeline.state -contains 'STALE_IGNORED') `
            -or $timeline.state -contains 'COMPENSATED') {
        throw 'Ordering invariant failed'
    }
    [pscustomobject]@{
        workflowId = $run.workflowId
        persistedState = $workflow.state
        states = @($timeline.state)
    } | ConvertTo-Json -Depth 4
} finally {
    $processes | Where-Object { $_ -and -not $_.HasExited } | Stop-Process -Force
}
