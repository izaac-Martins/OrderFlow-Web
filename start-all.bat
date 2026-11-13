@echo off
chcp 65001 >nul
title Ecossistema OrderFlow — Orquestrador Local (Fase 5)

echo ============================================================================
echo   ORDERFLOW — INICIALIZACAO DO ECOSSISTEMA COMPLETO (LOCAL)
echo ============================================================================
echo   [1/3] Backend Spring Boot (API REST + STOMP WebSocket + DB) -> Porta 8080
echo   [2/3] Frontend Master (Retaguarda, Caixa Cego e PDV)        -> Porta 5173
echo   [3/3] Frontend KDS (Kitchen Display System em Tempo Real)   -> Porta 5174
echo ============================================================================
echo.

set "ROOT_DIR=%~dp0"

echo Iniciando [1/3] Backend Spring Boot na porta 8080...
start "OrderFlow - Backend (:8080)" cmd /k "cd /d "%ROOT_DIR%backend" && call mvnw.cmd spring-boot:run"

echo Aguardando 5 segundos para disparar os Frontends...
timeout /t 5 /nobreak >nul

echo Iniciando [2/3] Frontend Master na porta 5173...
start "OrderFlow - Frontend Master (:5173)" cmd /k "cd /d "%ROOT_DIR%frontend-master" && call npm.cmd run dev"

echo Iniciando [3/3] Frontend KDS na porta 5174...
start "OrderFlow - Frontend KDS (:5174)" cmd /k "cd /d "%ROOT_DIR%frontend-kds" && call npm.cmd run dev"

echo.
echo ============================================================================
echo   Todos os servicos foram disparados em janelas dedicadas!
echo   - Backend API / Swagger : http://localhost:8080/swagger-ui.html
echo   - Health Check          : http://localhost:8080/actuator/health
echo   - Frontend Master (PDV) : http://localhost:5173
echo   - Frontend KDS (Cozinha): http://localhost:5174
echo ============================================================================
pause
