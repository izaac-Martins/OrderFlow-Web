@echo off
chcp 65001 >nul
title Ecossistema OrderFlow & Cheiro de Cafe — Orquestrador Local

echo ============================================================================
echo   ORDERFLOW ^& CHEIRO DE CAFE — INICIALIZACAO DO ECOSSISTEMA COMPLETO
echo ============================================================================
echo   [1/3] Backend Spring Boot (api-restaurante + MySQL cheirocafe_db) -^> :8080
echo   [2/3] Frontend Master (Retaguarda, Catalogo e Monitor de Pedidos) -^> :5173
echo   [3/3] Frontend KDS (Kitchen Display System em Tempo Real)         -^> :5174
echo ============================================================================
echo.

set "ROOT_DIR=%~dp0"

echo Iniciando [1/3] Backend Spring Boot (api-restaurante) na porta 8080...
start "OrderFlow - Backend api-restaurante (:8080)" cmd /k "cd /d "%ROOT_DIR%api-restaurante" && call mvnw.cmd spring-boot:run"

echo Aguardando 5 segundos para disparar os Frontends...
timeout /t 5 /nobreak >nul

echo Iniciando [2/3] Frontend Master na porta 5173...
start "OrderFlow - Frontend Master (:5173)" cmd /k "cd /d "%ROOT_DIR%frontend-master" && call npm.cmd run dev -- --host 0.0.0.0"

echo Iniciando [3/3] Frontend KDS na porta 5174...
start "OrderFlow - Frontend KDS (:5174)" cmd /k "cd /d "%ROOT_DIR%frontend-kds" && call npm.cmd run dev -- --host 0.0.0.0"

echo.
echo ============================================================================
echo   Todos os servicos foram disparados em janelas dedicadas!
echo   - API Produtos (Spring) : http://localhost:8080/api/v1/produtos
echo   - API Pedidos  (Spring) : http://localhost:8080/api/v1/pedidos
echo   - Frontend Master (Web) : http://localhost:5173
echo   - Frontend KDS (Cozinha): http://localhost:5174
echo ============================================================================
pause
