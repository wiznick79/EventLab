FROM nginx:1.29-alpine
COPY infrastructure/nginx/telemetry-gateway.conf /etc/nginx/conf.d/default.conf
