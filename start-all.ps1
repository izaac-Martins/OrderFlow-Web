# ═══════════════════════════════════════════════════════════════════════════
# OrderFlow & Cheiro de Café — Script PowerShell de Orquestração Completa
# Inicia Backend api-restaurante (:8080), Frontend Master (:5173) e KDS (:5174)
# ═══════════════════════════════════════════════════════════════════════════

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path

Write-Host "============================================================================" -ForegroundColor Yellow
Write-Host "  ORDERFLOW & CHEIRO DE CAFÉ — ORQUESTRAÇÃO INTEGRADA (BACKEND + WEB + KDS)" -ForegroundColor Cyan
Write-Host "============================================================================"

Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$RootDir\api-restaurante'; .\mvnw.cmd spring-boot:run"
Start-Sleep -Seconds 4
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$RootDir\frontend-master'; npm.cmd run dev -- --host 0.0.0.0"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$RootDir\frontend-kds'; npm.cmd run dev -- --host 0.0.0.0"

Write-Host "Serviços inicializados:" -ForegroundColor Green
Write-Host "  -> Backend Spring Boot : http://localhost:8080/api/v1/produtos"
Write-Host "  -> Frontend Master     : http://localhost:5173"
Write-Host "  -> Frontend KDS        : http://localhost:5174"
