#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p bin
export CGO_ENABLED=0
export GOOS=linux
export GOARCH=amd64
go build -trimpath -buildvcs=false -ldflags="-s -w" -o bin/reinchat-server-linux-amd64 .
printf 'built %s\n' "$(cd bin && pwd)/reinchat-server-linux-amd64"
