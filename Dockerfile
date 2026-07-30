FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B clean package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/target/taktak-backend-1.0.0.jar app.jar

EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
