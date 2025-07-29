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

# Stage 2: Imagem final baseada em Debian Slim
# CORREÇÃO: Usamos debian:12-slim que é minimalista mas contém /bin/sh
FROM debian:12-slim

# Cria um usuário não-root para segurança
RUN groupadd --gid 1001 nonroot && \
    useradd --uid 1001 --gid 1001 -m nonroot
USER nonroot:nonroot
WORKDIR /home/nonroot

# Copiar apenas o executável da stage de build
COPY --from=builder --chown=nonroot:nonroot /app/build/native/nativeCompile/rinha-backend-app ./rinha-backend-app

EXPOSE 8080

# O Entrypoint é o próprio executável. A configuração será passada pelo docker-compose.
ENTRYPOINT ["./rinha-backend-app"]