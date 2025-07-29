#!/bin/sh
echo "========== INICIANDO DEBUG DE AMBIENTE =========="
echo "SPRING_PROFILES_ACTIVE=$SPRING_PROFILES_ACTIVE"
echo "SPRING_DATA_MONGODB_URI=$SPRING_DATA_MONGODB_URI"
echo "REDIS_HOST=$REDIS_HOST"
echo "REDIS_PORT=$REDIS_PORT"
echo "PROCESSOR_DEFAULT_URL=$PROCESSOR_DEFAULT_URL"
echo "PROCESSOR_FALLBACK_URL=$PROCESSOR_FALLBACK_URL"
echo "========== TENTANDO INICIAR APLICACAO =========="
# Inicia a aplicação principal, passando quaisquer argumentos
exec /app/rinha-backend-app "$@"