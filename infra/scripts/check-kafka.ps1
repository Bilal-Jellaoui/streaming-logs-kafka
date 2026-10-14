
# SoukLine - Verification de l'infrastructure Kafka

$ErrorActionPreference = "Stop"

$brokers = @(
    "soukline-kafka-1",
    "soukline-kafka-2",
    "soukline-kafka-3"
)

$topics = @("web-logs", "stats-a", "stats-b")

Write-Host "=== Verification des brokers Docker ==="

foreach ($broker in $brokers) {
    $status = docker inspect --format '{{.State.Running}}' $broker 2>$null

    if ($LASTEXITCODE -ne 0 -or $status -ne "true") {
        throw "ERREUR : $broker n'est pas demarre."
    }

    Write-Host "OK : $broker"
}

Write-Host ""
Write-Host "=== Verification du quorum KRaft ==="

docker exec soukline-kafka-1 `
    /opt/kafka/bin/kafka-metadata-quorum.sh `
    --bootstrap-server kafka-1:19092 `
    describe --status

if ($LASTEXITCODE -ne 0) {
    throw "ERREUR : le quorum KRaft ne repond pas."
}

Write-Host ""
Write-Host "=== Verification des topics ==="

foreach ($topic in $topics) {
    docker exec soukline-kafka-1 `
        /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server kafka-1:19092 `
        --describe --topic $topic

    if ($LASTEXITCODE -ne 0) {
        throw "ERREUR : topic $topic indisponible."
    }

    Write-Host "OK : $topic"
}

Write-Host ""
Write-Host "Verification terminee."
    