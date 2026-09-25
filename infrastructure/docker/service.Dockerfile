# syntax=docker/dockerfile:1.18
FROM maven:3.9.16-eclipse-temurin-21-alpine@sha256:529278c758cac25258da21a71de9eecc553e2fc66304676bc8004260e8fc15bf AS build
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

FROM eclipse-temurin:21-jre-alpine@sha256:974b08960c5d96694c780e65b2d5705268ab1e1ca1a0dd0caf4ba6c3fe34d699
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
