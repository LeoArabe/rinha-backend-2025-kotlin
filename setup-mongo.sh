#!/bin/bash
set -euo pipefail

MONGO_HOST=${MONGO_HOST:-mongo}
MONGO_PORT=${MONGO_PORT:-27017}
DB_NAME=${DB_NAME:-rinhaDB}
SKIP_DROP=${SKIP_DROP:-false}
MAX_RETRIES=${MAX_RETRIES:-60}
RETRY_INTERVAL=${RETRY_INTERVAL:-2}

log() { echo "[$(date '+%H:%M:%S')] $*"; }
log "🔧 Iniciando setup-mongo (host=${MONGO_HOST}:${MONGO_PORT} db=${DB_NAME})"

# Espera TCP
for i in $(seq 1 $MAX_RETRIES); do
  if timeout 3 bash -c "</dev/tcp/${MONGO_HOST}/${MONGO_PORT}" &>/dev/null; then
    log "✅ TCP OK"
    break
  fi
  log "⏳ Esperando TCP (${i}/${MAX_RETRIES})..."
  sleep $RETRY_INTERVAL
  if [ "$i" -eq "$MAX_RETRIES" ]; then
    echo "❌ Timeout conectando ao Mongo"; exit 1
  fi
done

# Init RS (se necessário) e aguarda PRIMARY
mongosh "mongodb://${MONGO_HOST}:${MONGO_PORT}" --quiet --eval "
try {
  rs.status();
  print('RS_EXISTS');
} catch(e) {
  try {
    print('RS_INIT');
    rs.initiate({ _id: 'rs0', members: [{ _id: 0, host: '${MONGO_HOST}:${MONGO_PORT}' }] });
  } catch(err) {
    print('RS_INIT_ERR:' + err.message);
  }
}
" | tail -n 1

# Espera PRIMARY
for i in $(seq 1 $MAX_RETRIES); do
  state=$(mongosh "mongodb://${MONGO_HOST}:${MONGO_PORT}" --quiet --eval "try { const s=rs.status(); const me=s.members.find(m=>m.self); print(me?me.stateStr:'NO'); } catch(e){print('ERR');}" | tail -n1 | tr -d '\r\n')
  log "Estado RS: $state ($i/$MAX_RETRIES)"
  if [ "$state" = "PRIMARY" ]; then break; fi
  sleep $RETRY_INTERVAL
  if [ "$i" -eq "$MAX_RETRIES" ]; then
    echo "❌ Nó não entrou em PRIMARY"; exit 1
  fi
done

log "✅ Replica set PRIMARY pronto"

# Executa criação de índices / limpeza
mongosh "mongodb://${MONGO_HOST}:${MONGO_PORT}/${DB_NAME}" --quiet --eval "
try {
  if ('${SKIP_DROP}' !== 'true') {
    ['payments','payment_outbox','leader_locks'].forEach(c=>{ try{ db[c].drop(); } catch(e){} });
    print('DROPPED');
  } else print('SKIP_DROP');

  try { db.payments.createIndex({ status:1,lastUpdatedAt:1,processorUsed:1 }, { name:'summary_idx' }); } catch(e){}
  try { db.payments.createIndex({ correlationId:1 }, { name:'correlation_idx', unique:true }); } catch(e){}
  try { db.payment_outbox.createIndex({ status:1, createdAt:1 }, { name:'status_created_at_idx' }); } catch(e){}
  try { db.leader_locks.createIndex({ expireAt:1 }, { expireAfterSeconds:0, name:'ttl_idx' }); } catch(e){}

  print('SETUP_OK');
} catch(e) {
  print('SETUP_FAIL:' + e.message);
}
" | tail -n 1

log "✅ setup-mongo finalizado"
