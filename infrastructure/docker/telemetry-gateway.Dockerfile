FROM nginxinc/nginx-unprivileged:1.31.3-alpine-slim@sha256:d61d7ef52430df468e74ed6ee6e914429b80e20ba988e3176278a73165f876cf
COPY infrastructure/nginx/telemetry-gateway.conf /etc/nginx/conf.d/default.conf
USER 101
