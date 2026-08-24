# syntax=docker/dockerfile:1.18
FROM node:26-alpine@sha256:aadf416b2cdce311a8811ba3f0608a61b77dbf997500e2eafe781b51f6a0b019 AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31.3-alpine-slim@sha256:d61d7ef52430df468e74ed6ee6e914429b80e20ba988e3176278a73165f876cf
COPY infrastructure/nginx/eventlab.conf /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist /usr/share/nginx/html
USER 101
EXPOSE 8080
