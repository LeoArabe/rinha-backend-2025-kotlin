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
    && strip build/native/nativeCompile/rinha-backend-app

# ---

# Stage 2: Runtime com uma base slim, segura e funcional
FROM debian:stable-slim

# Instala apenas as dependências de runtime estritamente necessárias
# ✅ GARANTE que 'curl' existe para o healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Cria um usuário não-root para máxima segurança
RUN useradd --user-group --create-home --shell /bin/false nonroot
USER nonroot:nonroot

WORKDIR /app

# Copia apenas o executável otimizado da stage 'builder'
COPY --from=builder /app/build/native/nativeCompile/rinha-backend-app .

EXPOSE 8080

# O ENTRYPOINT apenas executa a aplicação.
# As configurações e o healthcheck são geridos pelo docker-compose.
ENTRYPOINT ["/app/rinha-backend-app"]