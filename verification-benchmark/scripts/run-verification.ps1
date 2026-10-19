# ============================================================
# Lance le verificateur : recompte access.log et le compare aux resultats des moteurs.
#
# Prerequis (une seule fois, et apres toute modification du module common) :
#     mvn -q -DskipTests install        (depuis la racine du depot)
#
# Exemples :
#   Moteur A seul, lu directement dans Kafka :
#     .\run-verification.ps1 -AccessLog ..\..\site\logs\access.log
#   Moteurs A et B, mode strict (fenetres) :
#     .\run-verification.ps1 -AccessLog ..\..\site\logs\access.log -StatsB kafka:stats-b -Mode windows
#   Depuis des fichiers exportes :
#     .\run-verification.ps1 -AccessLog ..\..\site\logs\access.log -StatsA results\stats-a.txt
#
# Limite : les chemins ne doivent pas contenir d'espace.
# ============================================================
param(
    [Parameter(Mandatory = $true)] [string]$AccessLog,
    [string]$StatsA = "kafka:stats-a",
    [string]$StatsB = "-",
    [int]$Window = 5,
    [ValidateSet("totals", "windows")] [string]$Mode = "totals",
    [string]$Csv = "verification-benchmark/results/verification.csv"
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$log = (Resolve-Path $AccessLog).Path

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) { throw "Maven (mvn) est introuvable dans le PATH." }

Push-Location $repo
try {
    $execArgs = "$log $StatsA $StatsB --window $Window --mode $Mode --csv $Csv"
    & mvn -q -pl verification-benchmark "exec:java@verify" "-Dexec.args=$execArgs"
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}

if ($code -eq 0) { Write-Host "Verification : CONFORME" -ForegroundColor Green }
else { Write-Host "Verification : ECART DETECTE (code $code)" -ForegroundColor Red }
exit $code
