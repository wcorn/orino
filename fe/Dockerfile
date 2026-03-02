# Stage 1: Build
FROM node:24-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
ARG VITE_API_URL
ENV VITE_API_URL=$VITE_API_URL
RUN npm run build

# Stage 2: Upload to MinIO
FROM minio/mc AS deployer
ARG MINIO_ALIAS
ARG MINIO_BUCKET
COPY --from=builder /app/dist /dist
RUN mc mirror --overwrite /dist ${MINIO_ALIAS}/${MINIO_BUCKET}
