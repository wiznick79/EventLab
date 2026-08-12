[CmdletBinding()]
param(
    [ValidateRange(1, 25)]
    [int] $WorkflowCount = 5,
    [ValidateRange(10, 120)]
    [int] $RecoveryTimeoutSeconds = 45
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$paymentJar = Join-Path $repositoryRoot `
    'services/payment-service/target/payment-service-0.1.0-SNAPSHOT.jar'
$paymentPort = 8082
$consoleUrl = 'http://localhost:8080'
$paymentProcess = $null
$originalPaymentStopped = $false

function Wait-ForHealth {
    param(
        [Parameter(Mandatory)]
        [string] $Url,
        [int] $TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            if ((Invoke-WebRequest -UseBasicParsing -TimeoutSec 2 $Url).StatusCode -eq 200) {
                return
            }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    } while ((Get-Date) -lt $deadline)

    throw "Service did not become healthy at $Url"
}

function Start-PaymentService {
    $previousMessagingSetting = $env:EVENTLAB_MESSAGING_ENABLED
    try {
        $env:EVENTLAB_MESSAGING_ENABLED = 'true'
        return Start-Process java -ArgumentList @('-jar', $paymentJar) `
            -WorkingDirectory $repositoryRoot -WindowStyle Hidden -PassThru
    } finally {
        $env:EVENTLAB_MESSAGING_ENABLED = $previousMessagingSetting
    }
}

if (-not (Test-Path $paymentJar)) {
    throw "Package the backend first; payment JAR not found at $paymentJar"
}

Wait-ForHealth -Url "$consoleUrl/actuator/health" -TimeoutSeconds 5
Wait-ForHealth -Url "http://localhost:$paymentPort/actuator/health" -TimeoutSeconds 5

$listener = Get-NetTCPConnection -State Listen -LocalPort $paymentPort -ErrorAction Stop
$originalProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($listener.OwningProcess)"
if ($originalProcess.Name -ne 'java.exe' `
        -or $originalProcess.CommandLine -notlike '*payment-service*.jar*') {
    throw "Port $paymentPort is not owned by the EventLab payment-service JAR"
}

try {
    Stop-Process -Id $listener.OwningProcess -Force
    Wait-Process -Id $listener.OwningProcess -Timeout 10 -ErrorAction SilentlyContinue
    $originalPaymentStopped = $true

    $request = @{
        scenarioId = 'happy-path'
        amount = 129.90
        currency = 'EUR'
    } | ConvertTo-Json
    $workflowIds = @(
        1..$WorkflowCount | ForEach-Object {
            $response = Invoke-RestMethod -Method Post -ContentType 'application/json' `
                -Body $request -TimeoutSec 10 "$consoleUrl/api/v1/runs"
            [string] $response.workflowId
        }
    )

    Start-Sleep -Seconds 2
    foreach ($workflowId in $workflowIds) {
        $timeline = @(Invoke-RestMethod -TimeoutSec 5 `
                "$consoleUrl/api/v1/runs/$workflowId/timeline")
        if ($timeline.state -contains 'PAYMENT_AUTHORIZED' -or $timeline.state -contains 'COMPLETED') {
            throw "Workflow $workflowId advanced through Payment while payment-service was stopped"
        }
    }

    $paymentProcess = Start-PaymentService
    Wait-ForHealth -Url "http://localhost:$paymentPort/actuator/health"

    $deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    $results = @{}
    do {
        foreach ($workflowId in $workflowIds) {
        $results[$workflowId] = @(Invoke-RestMethod -TimeoutSec 5 `
                    "$consoleUrl/api/v1/runs/$workflowId/timeline")
        }
        $completed = @($workflowIds | Where-Object {
                @($results[$_] | Where-Object { $_.state -eq 'COMPLETED' }).Count -eq 1
            }).Count
        if ($completed -eq $WorkflowCount) {
            break
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    $failures = @($workflowIds | Where-Object {
            $timeline = @($results[$_])
            @($timeline | Where-Object { $_.eventType -eq 'payment.authorized' }).Count -ne 1 `
                -or @($timeline | Where-Object { $_.state -eq 'COMPLETED' }).Count -ne 1
        })
    if ($failures.Count -gt 0) {
        throw "Recovery invariant failed for workflow(s): $($failures -join ', ')"
    }

    [pscustomobject]@{
        interruptedService = 'payment-service'
        acceptedWhileOffline = $WorkflowCount
        recovered = $WorkflowCount
        paymentEvents = @($workflowIds | ForEach-Object {
                @($results[$_] | Where-Object { $_.eventType -eq 'payment.authorized' }).Count
            } | Measure-Object -Sum).Sum
        completions = @($workflowIds | ForEach-Object {
                @($results[$_] | Where-Object { $_.state -eq 'COMPLETED' }).Count
            } | Measure-Object -Sum).Sum
        workflowIds = $workflowIds
    } | ConvertTo-Json -Depth 4
} finally {
    if ($originalPaymentStopped -and (-not $paymentProcess -or $paymentProcess.HasExited)) {
        $paymentProcess = Start-PaymentService
        Wait-ForHealth -Url "http://localhost:$paymentPort/actuator/health"
    }
}
