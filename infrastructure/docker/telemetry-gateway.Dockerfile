FROM nginxinc/nginx-unprivileged:1.31.6-alpine-slim@sha256:ac4d9c7e457c6510b95ac72dd84b27f7cc68ecb9a83fbd44f05a6460cf08aba4
USER root
# Refresh security packages on every CI run, even when the pinned base is cached.
ARG APK_UPGRADE_CACHE_BUST=local
RUN printf '%s' "$APK_UPGRADE_CACHE_BUST" >/dev/null && apk upgrade --no-cache
COPY infrastructure/nginx/telemetry-gateway.conf /etc/nginx/conf.d/default.conf
USER 101
