FROM nginxinc/nginx-unprivileged:1.31.6-alpine-slim@sha256:ac4d9c7e457c6510b95ac72dd84b27f7cc68ecb9a83fbd44f05a6460cf08aba4
COPY infrastructure/nginx/telemetry-gateway.conf /etc/nginx/conf.d/default.conf
USER 101
