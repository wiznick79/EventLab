FROM nginxinc/nginx-unprivileged:1.31.3-alpine-slim@sha256:ff4671e70f4f903721c5eacce1373d3e5d21b3d5f6fb03982154aabd084ed32e
COPY infrastructure/nginx/telemetry-gateway.conf /etc/nginx/conf.d/default.conf
USER 101
