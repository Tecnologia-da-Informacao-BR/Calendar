# ==========================================================
# Backend (Spring Boot 4 / Java 21) - imagem de PRODUÇÃO
# ==========================================================

# ---- Etapa 1: build ----
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

# Copia primeiro só o necessário para resolver dependências (cache de camada)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B dependency:go-offline

# Agora copia o código-fonte e builda
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ---- Etapa 2: runtime ----
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# curl só para o healthcheck (ver observação abaixo)
RUN apk add --no-cache curl

# Usuário não-root
RUN addgroup -S spring && adduser -S spring -G spring

COPY --from=build /app/target/*.jar app.jar
RUN chown spring:spring app.jar
USER spring

EXPOSE 8080

ENV JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=prod

# Healthcheck: como a aplicação ainda não tem nenhum endpoint mapeado
# (nem Spring Actuator), um 404 em "/" já significa que o Spring está de pé
# e respondendo. Por isso aceitamos QUALQUER status HTTP (2xx a 5xx) como saudável,
# só validando que o servidor está aceitando conexões na porta 8080.
#
# Quando adicionar Spring Boot Actuator, troque por:
#   CMD curl -sf http://localhost:8080/actuator/health || exit 1
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD sh -c 'curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ | grep -qE "^[0-9]{3}$"' || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
