# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- Run stage ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN mkdir -p /app/uploads
EXPOSE 8080
# Memory-friendly JVM settings for small (512MB) free instances:
# SerialGC uses far less overhead than the default G1GC, and the heap is capped
# so the container is not OOM-killed. Override via the JAVA_TOOL_OPTIONS env var.
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xmx200m -Xss512k"
ENTRYPOINT ["java", "-jar", "app.jar"]
