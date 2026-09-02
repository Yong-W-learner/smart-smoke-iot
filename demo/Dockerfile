FROM m.daocloud.io/docker.io/library/maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /workspace
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package

FROM m.daocloud.io/docker.io/library/eclipse-temurin:8-jre-jammy
WORKDIR /app
COPY --from=build /workspace/target/demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
