
# ============================================================
# SoukLine - Test de tolerance aux pannes Kafka
# ============================================================

$ErrorActionPreference = "Stop"

$failedBroker = "soukline-kafka-2"
$activeBroker = "soukline-kafka-1"
$bootstrap = "kafka-1:19092"
$topic = "web-logs"

$testId = [guid]::NewGuid().ToString("N")
$messageCount = 20

$messages = @(
    for ($i = 1; $i -le $messageCount; $i++) {
        "SOUKLINE-FAILOVER-$testId-$i"
    }
)

$stdoutFile = [System.IO.Path]::GetTempFileName()
$stderrFile = [System.IO.Path]::GetTempFileName()

$brokerStopped = $false
$testPassed = $false

Write-Host "=== TEST FAILOVER KAFKA ==="
Write-Host "Broker a arreter : $failedBroker"
Write-Host "Messages de test : $messageCount"
Write-Host ""

try {

    # ETAPE 1 - Arreter Kafka 2
    Write-Host "1. Arret de Kafka 2..."

    docker stop $failedBroker

    if ($LASTEXITCODE -ne 0) {
        throw "Impossible d'arreter Kafka 2."
    }

    $brokerStopped = $true

    Write-Host "Kafka 2 arrete."
    Write-Host ""

    # Laisser le cluster detecter la panne
    Start-Sleep -Seconds 15

    # ETAPE 2 - Verifier les brokers restants
    Write-Host "2. Verification des brokers restants..."

    foreach ($i in @(1, 3)) {
        $container = "soukline-kafka-$i"

        $running = docker inspect `
            --format '{{.State.Running}}' $container

        if ($LASTEXITCODE -ne 0 -or $running -ne "true") {
            throw "$container est indisponible."
        }

        Write-Host "OK : $container est actif."
    }

    Write-Host ""

    # ETAPE 3 - Envoyer des messages pendant la panne
    Write-Host "3. Envoi de messages pendant la panne..."

    $messages | docker exec -i $activeBroker `
        /opt/kafka/bin/kafka-console-producer.sh `
        --bootstrap-server $bootstrap `
        --topic $topic `
        --producer-property acks=all

    if ($LASTEXITCODE -ne 0) {
        throw "Echec de l'envoi pendant la panne."
    }

    Write-Host "$messageCount messages envoyes."
    Write-Host ""

    # ETAPE 4 - Consommer depuis Kafka 1
    Write-Host "4. Verification des messages..."

    $process = Start-Process `
        -FilePath "docker.exe" `
        -ArgumentList @(
            "exec",
            $activeBroker,
            "/opt/kafka/bin/kafka-console-consumer.sh",
            "--bootstrap-server", $bootstrap,
            "--topic", $topic,
            "--from-beginning",
            "--timeout-ms", "15000"
        ) `
        -Wait `
        -PassThru `
        -NoNewWindow `
        -RedirectStandardOutput $stdoutFile `
        -RedirectStandardError $stderrFile

    $received = @(Get-Content $stdoutFile)

    $expected = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($msg in $messages) {
        [void]$expected.Add($msg)
    }

    $found = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($line in $received) {
        $value = $line.Trim()

        if ($expected.Contains($value)) {
            [void]$found.Add($value)
        }
    }

    Write-Host "Messages envoyes : $messageCount"
    Write-Host "Messages retrouves : $($found.Count)"

    if ($found.Count -ne $messageCount) {
        throw "Certains messages sont introuvables pendant la panne."
    }

    Write-Host "PASS : Kafka fonctionne avec 2 brokers." `
        -ForegroundColor Green

    $testPassed = $true
}
finally {

    # ETAPE 5 - Redemarrer Kafka 2, meme si le test echoue
    if ($brokerStopped) {
        Write-Host ""
        Write-Host "5. Redemarrage de Kafka 2..."

        docker start $failedBroker

        if ($LASTEXITCODE -ne 0) {
            Write-Host "ATTENTION : redemarrage de Kafka 2 echoue !" `
                -ForegroundColor Red
        }
        else {
            Write-Host "Kafka 2 redemarre."
        }
    }

    Remove-Item $stdoutFile, $stderrFile `
        -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host "=== RESULTATS FAILOVER ==="

if ($testPassed) {
    Write-Host "PASS : tolerance a la panne validee." `
        -ForegroundColor Green
}
else {
    throw "FAIL : test failover echoue."
}
