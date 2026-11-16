#!/usr/bin/env bash
# Sobe a API GlowUp localmente carregando o api/.env.
# Uso (Git Bash, dentro da pasta api/):  ./rodar-local.sh
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "Arquivo .env não encontrado. Copie .env.example para .env e preencha."
  exit 1
fi

set -a
source .env
set +a

exec ./mvnw spring-boot:run
