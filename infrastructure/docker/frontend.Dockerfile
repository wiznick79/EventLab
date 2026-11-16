# syntax=docker/dockerfile:1.18
FROM node:24-alpine@sha256:116b800c3754b440ee0aa3ed2f20f5bf3d99a554bfd9adeea09ea8017f6e85f5 AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31.6-alpine-slim@sha256:ac4d9c7e457c6510b95ac72dd84b27f7cc68ecb9a83fbd44f05a6460cf08aba4
USER root
# Refresh security packages on every CI run, even when the pinned base is cached.
ARG APK_UPGRADE_CACHE_BUST=local
RUN printf '%s' "$APK_UPGRADE_CACHE_BUST" >/dev/null && apk upgrade --no-cache
COPY infrastructure/nginx/eventlab.conf /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist /usr/share/nginx/html
USER 101
EXPOSE 8080
