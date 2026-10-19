# ============================================================
# Exporte le contenu d'un topic de statistiques (stats-a ou stats-b) dans un fichier texte,
# un message JSON par ligne. Utile pour garder une copie des resultats d'un essai.
# (Le verificateur sait aussi lire le topic directement : voir run-verification.ps1)
# Utilise le conteneur Kafka du dossier infra/, comme les scripts d'Ali.
#
# Exemple : .\export-stats.ps1 -Topic stats-a -Out results\stats-a.txt
# ============================================================
param(
    [string]$Topic = "stats-a",
    [string]$Out = "stats-a.txt",
    [int]$TimeoutMs = 10000
)

$container = "soukline-kafka-1"
$bootstrap = "kafka-1:19092"
$kafkaPath = "/opt/kafka/bin"

$dir = Split-Path -Parent $Out
if ($dir -and -not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

Write-Host "Lecture du topic $Topic (arret apres $TimeoutMs ms sans nouveau message)..."

# 2>$null : on cache le message "Processed a total of N messages" que Kafka ecrit sur la sortie d'erreur
docker exec $container "$kafkaPath/kafka-console-consumer.sh" `
    --bootstrap-server $bootstrap `
    --topic $Topic `
    --from-beginning `
    --timeout-ms $TimeoutMs 2>$null | Out-File -FilePath $Out -Encoding utf8

$count = (Get-Content $Out | Measure-Object -Line).Lines
Write-Host "$count messages ecrits dans $Out" -ForegroundColor Green
