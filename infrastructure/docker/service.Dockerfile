# syntax=docker/dockerfile:1.18
FROM maven:3.9.16-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
ARG SERVICE

COPY pom.xml ./
COPY contracts/pom.xml contracts/pom.xml
COPY messaging-support/pom.xml messaging-support/pom.xml
COPY services/workflow-service/pom.xml services/workflow-service/pom.xml
COPY services/payment-service/pom.xml services/payment-service/pom.xml
COPY services/fulfilment-service/pom.xml services/fulfilment-service/pom.xml
COPY services/lab-console/pom.xml services/lab-console/pom.xml
RUN mvn -B -ntp -pl "services/${SERVICE}" -am dependency:go-offline

COPY contracts/src contracts/src
COPY messaging-support/src messaging-support/src
COPY services/${SERVICE}/src services/${SERVICE}/src
RUN mvn -B -ntp -pl "services/${SERVICE}" -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S eventlab && adduser -S eventlab -G eventlab
WORKDIR /app
ARG SERVICE
COPY --from=build "/workspace/services/${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar" app.jar
USER eventlab
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
