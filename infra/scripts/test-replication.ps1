
# ============================================================
# SoukLine - Test automatique de replication Kafka
# ============================================================

$ErrorActionPreference = "Stop"

$container = "soukline-kafka-1"
$bootstrap = "kafka-1:19092"
$topics = @("web-logs", "stats-a", "stats-b")

$expectedPartitions = 8
$expectedReplicas = 3

$totalPartitions = 0
$totalReplicas = 0
$errors = 0

Write-Host "=== TEST REPLICATION KAFKA ==="
Write-Host ""

# Verifier que les 3 brokers sont demarres
foreach ($i in 1..3) {
    $name = "soukline-kafka-$i"

    $running = docker inspect `
        --format '{{.State.Running}}' $name 2>$null

    if ($LASTEXITCODE -ne 0 -or $running -ne "true") {
        throw "FAIL : $name n'est pas demarre."
    }

    Write-Host "OK : $name est actif."
}

Write-Host ""

foreach ($topic in $topics) {

    Write-Host "Verification du topic : $topic"

    $description = @(
        docker exec $container `
            /opt/kafka/bin/kafka-topics.sh `
            --bootstrap-server $bootstrap `
            --describe `
            --topic $topic
    )

    if ($LASTEXITCODE -ne 0) {
        throw "FAIL : impossible de lire le topic $topic."
    }

    $partitionLines = @(
        $description | Where-Object {
            $_ -match 'Partition:\s*\d+'
        }
    )

    if ($partitionLines.Count -ne $expectedPartitions) {
        Write-Host "FAIL : nombre de partitions incorrect." `
            -ForegroundColor Red
        $errors++
    }

    foreach ($line in $partitionLines) {

        if ($line -notmatch 'Partition:\s*(\d+)') {
            $errors++
            continue
        }

        $partitionId = $Matches[1]

        if ($line -notmatch 'Replicas:\s*([\d,]+)') {
            $errors++
            continue
        }

        $replicas = @($Matches[1] -split ',')

        if ($line -notmatch 'Isr:\s*([\d,]+)') {
            $errors++
            continue
        }

        $isr = @($Matches[1] -split ',')

        $totalPartitions++
        $totalReplicas += $isr.Count

        $replicaIds = @($replicas | Sort-Object -Unique)
        $isrIds = @($isr | Sort-Object -Unique)

        $valid = (
            $replicas.Count -eq $expectedReplicas -and
            $isr.Count -eq $expectedReplicas -and
            $replicaIds.Count -eq $expectedReplicas -and
            $isrIds.Count -eq $expectedReplicas -and
            @($replicas | Where-Object {
                $_ -notin $isr
            }).Count -eq 0
        )

        if ($valid) {
            Write-Host "  Partition $partitionId : OK (3/3 ISR)" `
                -ForegroundColor Green
        }
        else {
            Write-Host "  Partition $partitionId : FAIL" `
                -ForegroundColor Red
            Write-Host "    Replicas : $($replicas -join ',')"
            Write-Host "    ISR      : $($isr -join ',')"
            $errors++
        }
    }

    Write-Host ""
}

Write-Host "=== RESULTATS ==="
Write-Host "Partitions verifiees : $totalPartitions"
Write-Host "Repliques synchronisees : $totalReplicas"
Write-Host "Erreurs detectees : $errors"

if (
    $errors -eq 0 -and
    $totalPartitions -eq ($topics.Count * $expectedPartitions) -and
    $totalReplicas -eq ($topics.Count * $expectedPartitions * $expectedReplicas)
) {
    Write-Host "PASS : replication Kafka correcte." `
        -ForegroundColor Green
}
else {
    throw "FAIL : probleme de replication Kafka."
}
