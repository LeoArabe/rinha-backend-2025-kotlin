# Stage 1: Build com GraalVM Native Image
FROM ghcr.io/graalvm/graalvm-community:21 AS builder

# Instalar ferramentas necessárias
RUN microdnf install -y findutils

# Configurar diretório de trabalho
WORKDIR /app

# Copiar arquivos de configuração do Gradle primeiro (cache layer)
COPY gradle gradle
COPY gradlew .
COPY gradle.properties .
COPY settings.gradle.kts .
COPY build.gradle.kts .

# Fazer download das dependências (cache layer)
RUN ./gradlew dependencies --no-daemon

# Copiar código fonte
COPY src src

# Build do executável nativo
RUN ./gradlew nativeCompile --no-daemon \
    && ls -la build/native/nativeCompile/ \
    && file build/native/nativeCompile/rinha-backend-app

# Stage 2: Runtime com imagem mínima (inclui curl para healthcheck)
FROM gcr.io/distroless/cc-debian12:nonroot

# Configurar usuário não-root
USER nonroot:nonroot

# Definir diretório de trabalho
WORKDIR /app

# Copiar apenas o executável nativo
COPY --from=builder --chown=nonroot:nonroot /app/build/native/nativeCompile/rinha-backend-app ./rinha-backend-app

# Configurar variáveis de ambiente padrão
ENV APP_PORT=8080
ENV SPRING_PROFILES_ACTIVE=production
ENV MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info
ENV MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=when-authorized

# Expor porta da aplicação
EXPOSE 8080

# Configurar health check usando Spring Boot Actuator
HEALTHCHECK --interval=30s --timeout=3s --start-period=15s --retries=3 \
    CMD ["/usr/bin/curl", "-f", "http://localhost:8080/actuator/health"]

# Entrada da aplicação
ENTRYPOINT ["/app/rinha-backend-app"]