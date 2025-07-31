# Stage 1: Build com GraalVM Native Image
FROM ghcr.io/graalvm/graalvm-community:21 AS builder

RUN microdnf install -y findutils
WORKDIR /app

# Cache layers
COPY gradle gradle
COPY gradlew .
COPY gradle.properties .
COPY settings.gradle.kts .
COPY build.gradle.kts .
RUN ./gradlew dependencies --no-daemon

COPY src src

# Build do executável nativo e otimização
RUN ./gradlew nativeCompile --no-daemon \
    -Dspring.aot.jvmArgs="-XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:+UseTransparentHugePages" \
    -Dspring.native.buildArgs="--optimize=2 -H:+UnlockExperimentalVMOptions -H:+UseContainerSupport" \
    && ls -la build/native/nativeCompile/rinha-backend-app \
    && strip build/native/nativeCompile/rinha-backend-app

# Stage 2: Preparar dependências de runtime (libz.so.1)
FROM ubuntu:22.04 AS runtime_deps_builder

# Primeiro, atualiza o apt-get
RUN apt-get update

# Depois, instala a biblioteca (usando --no-install-recommends que é mais robusto) e limpa
RUN apt-get install -y --no-install-recommends zlib1g && rm -rf /var/lib/apt/lists/*

# Stage 3: Runtime ultra-minimal com glibc e libz.so.1
FROM gcr.io/distroless/base:nonroot

# Copiar libz.so.1 da stage 'runtime_deps_builder' para o local esperado
COPY --from=runtime_deps_builder /lib/x86_64-linux-gnu/libz.so.1 /usr/lib/libz.so.1

# Copiar apenas o executável otimizado da stage 'builder'
COPY --from=builder /app/build/native/nativeCompile/rinha-backend-app /app

EXPOSE 8080

# >>>>> ENTRYPOINT FINAL E CORRETO <<<<<
# Executa o binário nativo (/app) e força todas as configurações via argumentos
ENTRYPOINT ["/app", \
            "--spring.profiles.active=prod", \
            "--spring.data.mongodb.uri=mongodb://root:password@mongo:27017/rinhaDB?replicaSet=rs0", \
            "--spring.redis.host=redis", \
            "--spring.redis.port=6379"]