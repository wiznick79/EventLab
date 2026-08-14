# syntax=docker/dockerfile:1.18
FROM node:24-alpine@sha256:d32cdf619f63fe0471182d08996dd516c6275bb5fd31ae06e55a570bd9e1ad43 AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31.3-alpine-slim@sha256:ff4671e70f4f903721c5eacce1373d3e5d21b3d5f6fb03982154aabd084ed32e
COPY infrastructure/nginx/eventlab.conf /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist /usr/share/nginx/html
USER 101
EXPOSE 8080
