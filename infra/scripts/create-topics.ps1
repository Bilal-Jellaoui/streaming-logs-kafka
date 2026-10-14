
# Projet SoukLine - Initialisation des topics Kafka

$ErrorActionPreference = "Stop"

$container = "soukline-kafka-1"
$bootstrap = "kafka-1:19092"
$kafkaTopics = "/opt/kafka/bin/kafka-topics.sh"

$topics = @("web-logs", "stats-a", "stats-b")

foreach ($topic in $topics) {
    Write-Host "Verification du topic : $topic"

    & docker exec $container $kafkaTopics `
        --bootstrap-server $bootstrap `
        --create `
        --if-not-exists `
        --topic $topic `
        --partitions 8 `
        --replication-factor 3

    if ($LASTEXITCODE -ne 0) {
        throw "Erreur lors de la creation du topic $topic"
    }
}

Write-Host ""
Write-Host "Liste des topics Kafka :"

& docker exec $container $kafkaTopics `
    --bootstrap-server $bootstrap `
    --list

if ($LASTEXITCODE -ne 0) {
    throw "Impossible de recuperer la liste des topics"
}

Write-Host ""
Write-Host "Initialisation terminee."
