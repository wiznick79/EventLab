$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$paymentJar = Join-Path $repositoryRoot `
    'services/payment-service/target/payment-service-0.1.0-SNAPSHOT.jar'
$paymentPort = 38082
$consoleUrl = 'http://localhost:38080'
$testPaymentProcess = $null
$originalPaymentStopped = $false

function Wait-ForHealth {
    param([string] $Url, [int] $TimeoutSeconds = 60)

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
    param([switch] $InjectPostSendFailure)

    $previousMessagingSetting = $env:EVENTLAB_MESSAGING_ENABLED
    try {
        $env:EVENTLAB_MESSAGING_ENABLED = 'true'
        $arguments = @('-jar', $paymentJar)
        if ($InjectPostSendFailure) {
            $arguments += '--eventlab.messaging.fail-once-after-send=true'
        }
        return Start-Process java -ArgumentList $arguments -WorkingDirectory $repositoryRoot `
            -WindowStyle Hidden -PassThru
    } finally {
        $env:EVENTLAB_MESSAGING_ENABLED = $previousMessagingSetting
    }
}

function Stop-PaymentService {
    param([System.Diagnostics.Process] $Process)

    if ($Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        Wait-Process -Id $Process.Id -Timeout 10 -ErrorAction SilentlyContinue
    }
}

function Get-Timeline {
    param([string] $WorkflowId)

    $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 5 `
        "$consoleUrl/api/v1/runs/$WorkflowId/timeline"
    $parsed = $response.Content | ConvertFrom-Json
    foreach ($event in $parsed) {
        Write-Output $event
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

    $testPaymentProcess = Start-PaymentService -InjectPostSendFailure
    Wait-ForHealth -Url "http://localhost:$paymentPort/actuator/health"

    $request = @{
        scenarioId = 'happy-path'
        amount = 129.90
        currency = 'EUR'
    } | ConvertTo-Json
    $run = Invoke-RestMethod -Method Post -ContentType 'application/json' -Body $request `
        -TimeoutSec 10 "$consoleUrl/api/v1/runs"

    $deadline = (Get-Date).AddSeconds(45)
    do {
        Start-Sleep -Milliseconds 500
        $timeline = @(Get-Timeline -WorkflowId $run.workflowId)
        $paymentDeliveries = $timeline.Where({ $_.eventType -eq 'payment.authorized' }).Count
        $duplicateDeliveries = $timeline.Where({ $_.duplicateDelivery }).Count
        $duplicateDecisions = $timeline.Where({ $_.state -eq 'DUPLICATE_IGNORED' }).Count
        $completions = $timeline.Where({ $_.state -eq 'COMPLETED' }).Count
    } while (($paymentDeliveries -lt 2 -or $completions -lt 1) -and (Get-Date) -lt $deadline)

    if ($paymentDeliveries -ne 2 -or $duplicateDeliveries -ne 1 `
            -or $duplicateDecisions -ne 1 -or $completions -ne 1) {
        throw "Invariant failed: payment deliveries=$paymentDeliveries, duplicate deliveries=$duplicateDeliveries, duplicate decisions=$duplicateDecisions, completions=$completions"
    }

    $paymentEventIds = @($timeline.Where({ $_.eventType -eq 'payment.authorized' }) `
            | Select-Object -ExpandProperty eventId -Unique)
    if ($paymentEventIds.Count -ne 1) {
        throw "The retry did not preserve one logical payment event ID"
    }

    [pscustomobject]@{
        workflowId = $run.workflowId
        logicalPaymentEventId = $paymentEventIds[0]
        brokerDeliveries = $paymentDeliveries
        duplicateDeliveries = $duplicateDeliveries
        duplicateDecisions = $duplicateDecisions
        workflowCompletions = $completions
    } | ConvertTo-Json
} finally {
    Stop-PaymentService -Process $testPaymentProcess
    if ($originalPaymentStopped) {
        $normalPaymentProcess = Start-PaymentService
        Wait-ForHealth -Url "http://localhost:$paymentPort/actuator/health"
    }
}
