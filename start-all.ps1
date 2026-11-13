# ═══════════════════════════════════════════════════════════════════════════
# OrderFlow — Script PowerShell de Orquestração Completa (Fase 5)
# Inicia Backend (:8080), Frontend Master (:5173) e Frontend KDS (:5174)
# ═══════════════════════════════════════════════════════════════════════════

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "============================================================================" -ForegroundColor Amber
Write-Host "  ORDERFLOW — ORQUESTRAÇÃO INTEGRADA (BACKEND + MASTER + KDS)" -ForegroundColor Cyan
Write-Host "============================================================================"

Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$RootDir\backend'; .\mvnw.cmd spring-boot:run"
Start-Sleep -Seconds 3
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$RootDir\frontend-master'; npm.cmd run dev"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$RootDir\frontend-kds'; npm.cmd run dev"

Write-Host "Serviços inicializados:" -ForegroundColor Green
Write-Host "  -> Backend Spring Boot : http://localhost:8080/actuator/health"
Write-Host "  -> Frontend Master     : http://localhost:5173"
Write-Host "  -> Frontend KDS        : http://localhost:5174"
