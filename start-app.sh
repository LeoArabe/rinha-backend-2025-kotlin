#!/bin/bash
set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configurações padrão
MONGO_HOST=${MONGO_HOST:-mongo}
MONGO_PORT=${MONGO_PORT:-27017}
REDIS_HOST=${REDIS_HOST:-redis}
REDIS_PORT=${REDIS_PORT:-6379}
MAX_RETRIES=30
RETRY_INTERVAL=3

echo -e "${BLUE}🚀 Iniciando Rinha Backend 2025${NC}"
echo -e "${BLUE}📋 Instância: ${INSTANCE_ID:-UNKNOWN}${NC}"

# Função para verificar conectividade
check_service() {
    local service_name=$1
    local host=$2
    local port=$3
    local retries=0

    echo -e "${YELLOW}🔍 Verificando conectividade com ${service_name} (${host}:${port})...${NC}"

    while [ $retries -lt $MAX_RETRIES ]; do
        if timeout 5 bash -c "</dev/tcp/${host}/${port}"; then
            echo -e "${GREEN}✅ ${service_name} conectado com sucesso!${NC}"
            return 0
        fi

        retries=$((retries + 1))
        echo -e "${YELLOW}⏳ Tentativa ${retries}/${MAX_RETRIES} - aguardando ${service_name}...${NC}"
        sleep $RETRY_INTERVAL
    done

    echo -e "${RED}❌ Falha na conexão com ${service_name} após ${MAX_RETRIES} tentativas${NC}"
    return 1
}

# Função para verificar replica set do MongoDB
check_mongo_replica_set() {
    local retries=0
    local mongo_uri="mongodb://${MONGO_HOST}:${MONGO_PORT}/rinhaDB?replicaSet=rs0"

    echo -e "${YELLOW}🔍 Verificando replica set MongoDB...${NC}"

    while [ $retries -lt $MAX_RETRIES ]; do
        if command -v mongosh >/dev/null 2>&1; then
            if mongosh "$mongo_uri" --quiet --eval "db.runCommand({ping: 1})" >/dev/null 2>&1; then
                echo -e "${GREEN}✅ MongoDB replica set ativo!${NC}"
                return 0
            fi
        else
            # Fallback para verificação básica TCP se mongosh não estiver disponível
            if timeout 5 bash -c "</dev/tcp/${MONGO_HOST}/${MONGO_PORT}"; then
                echo -e "${GREEN}✅ MongoDB TCP conectado (mongosh não disponível para verificar replica set)${NC}"
                return 0
            fi
        fi

        retries=$((retries + 1))
        echo -e "${YELLOW}⏳ Tentativa ${retries}/${MAX_RETRIES} - aguardando replica set...${NC}"
        sleep $RETRY_INTERVAL
    done

    echo -e "${RED}❌ Falha na verificação do replica set após ${MAX_RETRIES} tentativas${NC}"
    return 1
}

# Verificar dependências
echo -e "${BLUE}🔧 Verificando dependências...${NC}"

if ! check_service "Redis" "$REDIS_HOST" "$REDIS_PORT"; then
    echo -e "${RED}💥 Falha crítica: Redis não disponível${NC}"
    exit 1
fi

if ! check_service "MongoDB" "$MONGO_HOST" "$MONGO_PORT"; then
    echo -e "${RED}💥 Falha crítica: MongoDB não disponível${NC}"
    exit 1
fi

if ! check_mongo_replica_set; then
    echo -e "${YELLOW}⚠️ Aviso: Replica set pode não estar totalmente configurado${NC}"
    echo -e "${YELLOW}🔄 Continuando inicialização...${NC}"
fi

# Construir argumentos da aplicação
JAVA_ARGS=""
JAVA_ARGS="$JAVA_ARGS --spring.data.mongodb.uri=mongodb://${MONGO_HOST}:${MONGO_PORT}/rinhaDB?replicaSet=rs0&retryWrites=true&w=majority"
JAVA_ARGS="$JAVA_ARGS --spring.data.redis.host=${REDIS_HOST}"
JAVA_ARGS="$JAVA_ARGS --spring.data.redis.port=${REDIS_PORT}"
JAVA_ARGS="$JAVA_ARGS --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"

# Adicionar outras configurações se definidas
[ ! -z "$APP_PORT" ] && JAVA_ARGS="$JAVA_ARGS --server.port=${APP_PORT}"
[ ! -z "$INSTANCE_ID" ] && JAVA_ARGS="$JAVA_ARGS --app.instance-id=${INSTANCE_ID}"
[ ! -z "$WORKER_CONCURRENCY" ] && JAVA_ARGS="$JAVA_ARGS --payment.workers.concurrency=${WORKER_CONCURRENCY}"

# Log das configurações
echo -e "${BLUE}⚙️ Configurações da aplicação:${NC}"
echo -e "${BLUE}   MongoDB: mongodb://${MONGO_HOST}:${MONGO_PORT}/rinhaDB?replicaSet=rs0${NC}"
echo -e "${BLUE}   Redis: ${REDIS_HOST}:${REDIS_PORT}${NC}"
echo -e "${BLUE}   Profile: ${SPRING_PROFILES_ACTIVE:-prod}${NC}"
echo -e "${BLUE}   Instância: ${INSTANCE_ID:-UNKNOWN}${NC}"

# Iniciar aplicação
echo -e "${GREEN}🚀 Iniciando aplicação Java...${NC}"
echo -e "${BLUE}💾 Argumentos: ${JAVA_ARGS}${NC}"

exec /app $JAVA_ARGS