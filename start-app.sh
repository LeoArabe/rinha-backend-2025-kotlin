#!/bin/bash
set -euo pipefail

BLUE='\033[0;34m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

MONGO_HOST=${MONGO_HOST:-mongo}
MONGO_PORT=${MONGO_PORT:-27017}
MONGODB_URI=${MONGODB_URI:-mongodb://mongo:27017/rinhaDB?replicaSet=rs0}
MAX_RETRIES=${MAX_RETRIES:-30}
RETRY_INTERVAL=${RETRY_INTERVAL:-2}

echo -e "${BLUE}🚀 Iniciando Rinha Backend 2025 (debug)${NC}"
echo -e "${BLUE}📋 Instância: ${INSTANCE_ID:-UNKNOWN}${NC}"

check_tcp() {
  local host="$1" port="$2" tries=0
  while [ "$tries" -lt "$MAX_RETRIES" ]; do
    if timeout 3 bash -c "</dev/tcp/${host}/${port}" &>/dev/null; then
      echo -e "${GREEN}✅ TCP reachable: ${host}:${port}${NC}"
      return 0
    fi
    tries=$((tries+1))
    echo -e "${YELLOW}⏳ Esperando ${host}:${port} (${tries}/${MAX_RETRIES})...${NC}"
    sleep "$RETRY_INTERVAL"
  done
  return 1
}

if ! check_tcp "$MONGO_HOST" "$MONGO_PORT"; then
  echo -e "${RED}❌ MongoDB não acessível.${NC}"
  exit 1
fi

# Optional: quick ping (only if mongosh is available inside container)
if command -v mongosh &>/dev/null; then
  echo -e "${BLUE}🔎 Tentando ping ao MongoDB via mongosh...${NC}"
  if mongosh "$MONGODB_URI" --quiet --eval "db.adminCommand('ping')" &>/dev/null; then
    echo -e "${GREEN}✅ Mongo responde ao ping${NC}"
  else
    echo -e "${YELLOW}⚠️ Mongosh ping falhou — continuando mesmo assim (setup-mongo deve ter rodado)${NC}"
  fi
fi

# Construir args da app a partir das envs
JAVA_ARGS=()
[ ! -z "${SERVER_PORT:-}" ] && JAVA_ARGS+=("--server.port=${SERVER_PORT}")
[ ! -z "${SPRING_PROFILES_ACTIVE:-}" ] && JAVA_ARGS+=("--spring.profiles.active=${SPRING_PROFILES_ACTIVE}")
[ ! -z "${INSTANCE_ID:-}" ] && JAVA_ARGS+=("--app.instance-id=${INSTANCE_ID}")

echo -e "${BLUE}⚙️ Lançando binário com args: ${JAVA_ARGS[*]}${NC}"
exec /app/rinha-backend-app "${JAVA_ARGS[@]}"
