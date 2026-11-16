@echo off
REM Sobe a API GlowUp localmente carregando o api\.env.
REM Uso (Prompt de Comando ou PowerShell, dentro da pasta api\):  rodar-local.cmd
setlocal enabledelayedexpansion
cd /d "%~dp0"

if not exist .env (
  echo Arquivo .env nao encontrado. Copie .env.example para .env e preencha.
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
  set "chave=%%A"
  if not "!chave:~0,1!"=="#" if not "!chave!"=="" set "%%A=%%B"
)

mvnw.cmd spring-boot:run
