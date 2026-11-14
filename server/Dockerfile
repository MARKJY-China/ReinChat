# syntax=docker/dockerfile:1

FROM golang:1.25.5-bookworm AS build
WORKDIR /src
ENV CGO_ENABLED=0 GOOS=linux GOARCH=amd64 GOTOOLCHAIN=local

COPY go.mod go.sum ./
RUN go mod download

COPY *.go schema.sql ./
RUN go build -trimpath -buildvcs=false -ldflags="-s -w" -o /out/reinchat-server .

FROM debian:12-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /out/reinchat-server /app/reinchat-server
RUN mkdir -p /data \
    && chown -R 10001:10001 /app /data

USER 10001:10001
ENV REINCHAT_ADDR=:8080 \
    REINCHAT_DATA_DIR=/data

EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["/app/reinchat-server"]
