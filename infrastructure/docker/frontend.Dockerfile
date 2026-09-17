# syntax=docker/dockerfile:1.18
FROM node:24-alpine@sha256:50c8e8ca1d27439048670df5883f32d57cf81cff6233222c893fd0d9884cbd81 AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31.5-alpine-slim@sha256:736aa11ab9f9c320825722e411661c64559881e15e77f37137eef168ebe9515c
COPY infrastructure/nginx/eventlab.conf /etc/nginx/templates/default.conf.template
COPY --from=build /workspace/dist /usr/share/nginx/html
USER 101
EXPOSE 8080
