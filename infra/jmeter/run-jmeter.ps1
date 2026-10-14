
# ============================================================
# SoukLine - Execution automatique des tests JMeter
# Responsable : Infrastructure et integration
#
# Phase 1 : Trafic normal
# Phase 2 : Pic de trafic
# Phase 3 : Simulation de panne HTTP 503
# ============================================================

$ErrorActionPreference = "Stop"

# Dossier contenant ce script et les fichiers JMX
$jmeterDir = $PSScriptRoot

# Creer un dossier de resultats unique pour chaque execution
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$resultsDir = Join-Path $jmeterDir "results\$timestamp"

New-Item -ItemType Directory -Path $resultsDir -Force | Out-Null

# Verifier que JMeter est installe
if (-not (Get-Command jmeter -ErrorAction SilentlyContinue)) {
    throw "JMeter est introuvable. Verifiez votre variable PATH."
}

# Verifier que SoukLine est accessible
try {
    $response = Invoke-WebRequest `
        -Uri "http://localhost:8080/" `
        -UseBasicParsing `
        -TimeoutSec 10

    Write-Host "SoukLine est accessible : HTTP $($response.StatusCode)" -ForegroundColor Green
}
catch {
    throw "SoukLine est inaccessible sur localhost:8080. Demarrez Spring Boot."
}

# Les phases doivent s'executer dans cet ordre
$phases = @(
    @{ Name = "Phase 1 - Trafic normal"; File = "phase1-normal.jmx"; Result = "phase1.jtl" },
    @{ Name = "Phase 2 - Pic de trafic"; File = "phase2-pic.jmx"; Result = "phase2.jtl" },
    @{ Name = "Phase 3 - Simulation de panne"; File = "phase3-panne.jmx"; Result = "phase3.jtl" }
)

try {
    foreach ($phase in $phases) {
        Write-Host ""
        Write-Host "========================================" -ForegroundColor Cyan
        Write-Host $phase.Name -ForegroundColor Cyan
        Write-Host "========================================" -ForegroundColor Cyan

        $testPlan = Join-Path $jmeterDir $phase.File
        $resultFile = Join-Path $resultsDir $phase.Result

        if (-not (Test-Path $testPlan)) {
            throw "Fichier JMX introuvable : $testPlan"
        }

        # Lancer JMeter sans interface graphique
        & jmeter -n -t $testPlan -l $resultFile

        if ($LASTEXITCODE -ne 0) {
            throw "Echec de JMeter pendant : $($phase.Name)"
        }

        Write-Host "$($phase.Name) terminee." -ForegroundColor Green
    }

    Write-Host ""
    Write-Host "Les trois phases sont terminees !" -ForegroundColor Green
    Write-Host "Resultats : $resultsDir"
}
finally {
    # Securite : retablir le paiement meme si le script echoue
    try {
        Invoke-WebRequest `
            -Uri "http://localhost:8080/admin/panne?etat=off" `
            -UseBasicParsing `
            -TimeoutSec 10 | Out-Null

        Write-Host "Mode panne desactive." -ForegroundColor Green
    }
    catch {
        Write-Warning "Impossible de desactiver la panne automatiquement."
    }
}
