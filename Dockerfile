FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --system taktak && useradd --system --gid taktak taktak
WORKDIR /app

COPY --from=build /workspace/target/taktak-backend-1.0.0.jar app.jar

USER taktak
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget -qO- http://localhost:${PORT:-8081}/api/health || exit 1

CMD ["/opt/java/openjdk/bin/java", "-XX:MaxRAMPercentage=75.0", "-XX:TieredStopAtLevel=1", "-noverify", "-jar", "/app/app.jar"]
