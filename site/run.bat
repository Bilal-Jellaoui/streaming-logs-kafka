@echo off
title SoukLine E-Commerce - Membre 1
chcp 65001 > nul
echo ==============================================================================
echo   Lancement de SoukLine E-Commerce (Spring Boot + Tomcat Embedded)
echo ==============================================================================

if "%JAVA_HOME%"=="" (
    if exist "C:\Users\med\.jdks\openjdk-24.0.1" (
        set "JAVA_HOME=C:\Users\med\.jdks\openjdk-24.0.1"
    )
)

echo Java Home: %JAVA_HOME%
echo Serveur web : http://localhost:8080/
echo Mode Panne  : http://localhost:8080/admin/panne?etat=on^|off
echo Logs acces  : logs/access.log
echo Logs erreur : logs/error.log
echo.

call .\mvnw.cmd spring-boot:run
pause
