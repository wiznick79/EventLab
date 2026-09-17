FROM nginxinc/nginx-unprivileged:1.31.5-alpine-slim@sha256:736aa11ab9f9c320825722e411661c64559881e15e77f37137eef168ebe9515c
COPY infrastructure/nginx/telemetry-gateway.conf /etc/nginx/conf.d/default.conf
USER 101
