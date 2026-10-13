
# ============================================================
# SoukLine - Test automatique de messagerie Kafka
# Producer Kafka 1 -> Topic web-logs -> Consumer Kafka 2
# ============================================================

$ErrorActionPreference = "Stop"

# Configuration
$topic = "web-logs"
$bootstrapProducer = "kafka-1:19092"
$bootstrapConsumer = "kafka-2:19092"

$containerProducer = "soukline-kafka-1"
$containerConsumer = "soukline-kafka-2"

$kafkaPath = "/opt/kafka/bin"

# Nombre de messages a tester
$messageCount = 100

# Identifiant unique pour chaque execution
$testId = [guid]::NewGuid().ToString("N")

Write-Host "=== TEST MESSAGERIE KAFKA ==="
Write-Host "Topic : $topic"
Write-Host "Messages attendus : $messageCount"
Write-Host "Test ID : $testId"
Write-Host ""

# ============================================================
# ETAPE 1 - Generer les messages
# ============================================================

$messages = @(
    for ($i = 1; $i -le $messageCount; $i++) {
        "SOUKLINE-TEST-$testId-$i"
    }
)

# ============================================================
# ETAPE 2 - Envoyer les messages vers Kafka
# ============================================================

Write-Host "Envoi des messages..."

$messages | docker exec -i $containerProducer `
    "$kafkaPath/kafka-console-producer.sh" `
    --bootstrap-server $bootstrapProducer `
    --topic $topic `
    --producer-property acks=all

if ($LASTEXITCODE -ne 0) {
    throw "FAIL : echec de l'envoi des messages."
}

Write-Host "Envoi termine."
Write-Host ""

# ============================================================
# ETAPE 3 - Lire les messages depuis Kafka 2
# ============================================================

Write-Host "Lecture des messages..."

# Fichiers temporaires pour separer les messages des erreurs
$stdoutFile = [System.IO.Path]::GetTempFileName()
$stderrFile = [System.IO.Path]::GetTempFileName()

try {

    # Lancer le consumer sans interrompre PowerShell
    # lorsque Kafka signale un timeout sur stderr.

    $process = Start-Process `
        -FilePath "docker.exe" `
        -ArgumentList @(
            "exec",
            $containerConsumer,
            "$kafkaPath/kafka-console-consumer.sh",
            "--bootstrap-server", $bootstrapConsumer,
            "--topic", $topic,
            "--from-beginning",
            "--timeout-ms", "15000"
        ) `
        -Wait `
        -PassThru `
        -NoNewWindow `
        -RedirectStandardOutput $stdoutFile `
        -RedirectStandardError $stderrFile

    # Recuperer les messages lus
    $received = @(Get-Content $stdoutFile)

    # ========================================================
    # ETAPE 4 - Verifier les messages
    # ========================================================

    # Ensemble des messages attendus
    $expected = [System.Collections.Generic.HashSet[string]]::new()

    foreach ($msg in $messages) {
        [void]$expected.Add($msg)
    }

    # Ensemble des messages retrouves
    $found = [System.Collections.Generic.HashSet[string]]::new()

    foreach ($line in $received) {
        $cleanLine = $line.Trim()

        if ($expected.Contains($cleanLine)) {
            [void]$found.Add($cleanLine)
        }
    }

    # Calculer les resultats
    $receivedCount = $found.Count
    $missingCount = $messageCount - $receivedCount

    # ========================================================
    # ETAPE 5 - Afficher les resultats
    # ========================================================

    Write-Host ""
    Write-Host "=== RESULTATS ==="
    Write-Host "Messages envoyes : $messageCount"
    Write-Host "Messages retrouves : $receivedCount"
    Write-Host "Messages manquants : $missingCount"
    Write-Host ""

    if ($receivedCount -eq $messageCount) {

        Write-Host "PASS : tous les messages ont ete retrouves." `
            -ForegroundColor Green

    }
    else {

        Write-Host "FAIL : certains messages sont introuvables." `
            -ForegroundColor Red

        Write-Host "Code de sortie consumer : $($process.ExitCode)"

        Write-Host ""
        Write-Host "Messages manquants :"

        foreach ($msg in $messages) {
            if (-not $found.Contains($msg)) {
                Write-Host "Manquant : $msg"
            }
        }

        Write-Host ""
        Write-Host "Dernieres erreurs Kafka :"

        Get-Content $stderrFile -Tail 10

        throw "Test de messagerie echoue."
    }

}
finally {

    # Supprimer les fichiers temporaires
    Remove-Item $stdoutFile, $stderrFile `
        -ErrorAction SilentlyContinue
}
