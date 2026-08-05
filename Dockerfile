FROM maven:3.9.9-eclipse-temurin-17-alpine AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /workspace/target/taktak-backend-1.0.0.jar app.jar

CMD ["/opt/java/openjdk/bin/java", "-XX:MaxRAMPercentage=75.0", "-XX:TieredStopAtLevel=1", "-noverify", "-jar", "/app/app.jar"]
