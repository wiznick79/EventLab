# syntax=docker/dockerfile:1.18
FROM maven:3.9-eclipse-temurin-26-alpine@sha256:4c85d516dd8c7e50d49a403d604e565e0dbb09a790186fe3245725d313ae7cb9 AS build
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

FROM eclipse-temurin:25-jre-alpine@sha256:3c0a9084927a221ccd1d007fcaf614465672c0af37aaa834c5184483afe56d61
# Include a per-workflow cache key so CI refreshes Alpine packages instead of reusing a stale upgrade layer.
ARG APK_UPGRADE_CACHE_BUST=local
RUN printf '%s' "$APK_UPGRADE_CACHE_BUST" >/dev/null && apk upgrade --no-cache
RUN addgroup -S eventlab && adduser -S eventlab -G eventlab
WORKDIR /app
ARG SERVICE
COPY --from=build "/workspace/services/${SERVICE}/target/${SERVICE}-0.1.0-SNAPSHOT.jar" app.jar
USER eventlab
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
