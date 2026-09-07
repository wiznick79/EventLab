# syntax=docker/dockerfile:1.18
FROM maven:3-eclipse-temurin-26-alpine@sha256:9cbcc5b82c2deb26c1b608bdcdfebd9bc71e5edada8c07a430c36826c41f7b2b AS build
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

FROM eclipse-temurin:25-jre-alpine@sha256:3137541deb3cac6626b5d9a4a2187bc0d6a34312f858bd2c67dd01e732e6b682
RUN apk upgrade --no-cache
RUN addgroup -S eventlab && adduser -S eventlab -G eventlab
WORKDIR /app
ARG SERVICE
COPY --from=build "/workspace/services/${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar" app.jar
USER eventlab
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
