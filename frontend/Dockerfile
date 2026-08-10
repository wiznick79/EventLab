# syntax=docker/dockerfile:1.18
FROM node:24.16.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.29.1-alpine
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
