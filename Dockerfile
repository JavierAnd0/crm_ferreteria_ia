# syntax=docker/dockerfile:1
# Etapa 1: compila la interfaz React (Node lo instala Maven) y empaqueta (las pruebas corren en CI, no aquí).
FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
COPY frontend ./frontend
RUN mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/inventario-ferreteria.jar extract --layers --launcher --destination /capas

# Etapa 2: imagen de ejecución mínima, usuario sin privilegios.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /capas/dependencies/ ./
COPY --from=build /capas/spring-boot-loader/ ./
COPY --from=build /capas/snapshot-dependencies/ ./
COPY --from=build /capas/application/ ./
USER app
ENV TZ=America/Bogota JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
