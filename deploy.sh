#!/usr/bin/env bash
#
# ReinChat server deploy
#   1. cross-compile the Go server locally
#   2. upload the binary to root@rainMark with a live progress bar
#   3. install/restart a systemd service bound to 127.0.0.1:10089
#
# Usage:
#   bash deploy.sh
#   REMOTE=root@rainMark bash deploy.sh
#
# The server data and master key are preserved between deploys:
#   /var/lib/reinchat
#   /etc/reinchat/reinchat.env   (contains REINCHAT_MASTER_KEY, mode 0600)
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$ROOT/server"
BUILD_DIR="$ROOT/build/deploy"
BIN="$BUILD_DIR/reinchat-server-linux-amd64"

# Linux OpenSSH host matching is case-sensitive. The local ~/.ssh/config has
# `Host rainMark`; keep that spelling unless REMOTE is overridden.
REMOTE="${REMOTE:-root@rainMark}"
REMOTE_DIR="${REMOTE_DIR:-/opt/1panel/www/sites/reinchat.markjy.com}"
SERVICE="${SERVICE:-reinchat.service}"
PORT="${PORT:-10089}"
DATA_DIR="/var/lib/reinchat"
ENV_DIR="/etc/reinchat"
ENV_FILE="$ENV_DIR/reinchat.env"

SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=20 -o ServerAliveInterval=15 -o ServerAliveCountMax=3)
SCP_OPTS=(-q -o BatchMode=yes -o ConnectTimeout=20)

log()  { printf '\033[1;36m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

human_size() {
    awk -v b="${1:-0}" 'BEGIN {
        split("B KiB MiB GiB", u, " ");
        i = 1;
        while (b >= 1024 && i < 4) { b /= 1024; i++ }
        if (i == 1) printf "%d %s", b, u[i];
        else printf "%.1f %s", b, u[i];
    }'
}

draw_progress() {
    local current="${1:-0}" total="${2:-1}" pct filled bar="" i
    if (( total <= 0 )); then total=1; fi
    pct=$(( current * 100 / total ))
    (( pct > 100 )) && pct=100
    filled=$(( pct * 24 / 100 ))
    for ((i = 0; i < filled; i++)); do bar+="#"; done
    for ((i = filled; i < 24; i++)); do bar+="."; done
    printf '\r\033[K  [%s] %3d%%  %10s / %s' \
        "$bar" "$pct" "$(human_size "$current")" "$(human_size "$total")"
}

# ---------- local build ----------
command -v go >/dev/null 2>&1 || die "找不到 go，请先安装 Go 1.25+"
command -v ssh >/dev/null 2>&1 || die "找不到 ssh"
command -v scp >/dev/null 2>&1 || die "找不到 scp"

log "检查服务端测试"
(cd "$SERVER_DIR" && go test ./... && go vet ./...)

log "交叉编译 Linux amd64"
mkdir -p "$BUILD_DIR"
(cd "$SERVER_DIR" && \
    CGO_ENABLED=0 GOOS=linux GOARCH=amd64 \
    go build -trimpath -ldflags="-s -w" -o "$BIN" .)
[[ -s "$BIN" ]] || die "编译产物为空：$BIN"
TOTAL="$(wc -c < "$BIN" | tr -d ' ')"
log "构建完成：$BIN ($(human_size "$TOTAL"))"

# ---------- remote preflight ----------
log "检查 SSH：$REMOTE"
ssh "${SSH_OPTS[@]}" "$REMOTE" true \
    || die "无法免密登录 $REMOTE。请检查 ~/.ssh/config 与密钥；当前 SSH 配置中的 Host 是 rainMark（区分大小写）。"
REMOTE_ARCH="$(ssh "${SSH_OPTS[@]}" "$REMOTE" 'uname -m')"
[[ "$REMOTE_ARCH" == "x86_64" ]] \
    || die "服务器架构是 $REMOTE_ARCH，但脚本只构建 linux/amd64"
log "SSH 可用，服务器架构：$REMOTE_ARCH"

# ---------- upload with a live progress bar ----------
REMOTE_TMP="/tmp/reinchat-server.$(date +%s).$$"
SCP_LOG="$(mktemp "${TMPDIR:-/tmp}/reinchat-scp.XXXXXX")"
SCP_PID=""
DEPLOY_OK=0

cleanup() {
    local rc=$?
    [[ -n "${SCP_PID:-}" ]] && kill "$SCP_PID" 2>/dev/null || true
    [[ -n "${SCP_PID:-}" ]] && wait "$SCP_PID" 2>/dev/null || true
    rm -f "$SCP_LOG" 2>/dev/null || true
    if [[ "$DEPLOY_OK" -ne 1 ]]; then
        ssh "${SSH_OPTS[@]}" "$REMOTE" "rm -f -- '$REMOTE_TMP'" >/dev/null 2>&1 || true
    fi
    exit "$rc"
}
trap cleanup EXIT

log "上传二进制（实时进度）"
scp "${SCP_OPTS[@]}" "$BIN" "$REMOTE:$REMOTE_TMP" >"$SCP_LOG" 2>&1 &
SCP_PID=$!

LAST_SIZE=-1
while kill -0 "$SCP_PID" 2>/dev/null; do
    SIZE="$(ssh "${SSH_OPTS[@]}" "$REMOTE" \
        "stat -c %s -- '$REMOTE_TMP' 2>/dev/null || true" 2>/dev/null \
        | tr -cd '0-9' || true)"
    SIZE="${SIZE:-0}"
    if [[ "$SIZE" != "$LAST_SIZE" ]]; then
        draw_progress "$SIZE" "$TOTAL"
        LAST_SIZE="$SIZE"
    fi
    sleep 0.25
done

set +e
wait "$SCP_PID"
SCP_RC=$?
set -e
SCP_PID=""
if [[ "$SCP_RC" -ne 0 ]]; then
    echo
    cat "$SCP_LOG" >&2 || true
    die "上传失败（scp 退出码 $SCP_RC）"
fi
draw_progress "$TOTAL" "$TOTAL"
echo
log "上传完成，开始安装服务"

# ---------- remote install ----------
ssh "${SSH_OPTS[@]}" "$REMOTE" \
    env REMOTE_DIR="$REMOTE_DIR" REMOTE_TMP="$REMOTE_TMP" \
        SERVICE="$SERVICE" EXPECTED_SIZE="$TOTAL" PORT="$PORT" \
        DATA_DIR="$DATA_DIR" ENV_DIR="$ENV_DIR" ENV_FILE="$ENV_FILE" \
    bash -s <<'REMOTE_SCRIPT'
set -euo pipefail

REMOTE_BIN="$REMOTE_DIR/reinchat-server"
ENV_DIR="${ENV_DIR:-/etc/reinchat}"
ENV_FILE="${ENV_FILE:-$ENV_DIR/reinchat.env}"
DATA_DIR="${DATA_DIR:-/var/lib/reinchat}"
PORT="${PORT:-10089}"

if [[ ! -f "$REMOTE_TMP" ]]; then
    echo "上传的临时文件不存在：$REMOTE_TMP" >&2
    exit 1
fi
ACTUAL_SIZE="$(stat -c %s -- "$REMOTE_TMP")"
if [[ "$ACTUAL_SIZE" != "$EXPECTED_SIZE" ]]; then
    echo "文件大小校验失败：本地 $EXPECTED_SIZE 字节，服务器 $ACTUAL_SIZE 字节" >&2
    rm -f -- "$REMOTE_TMP"
    exit 1
fi

# Dedicated, unprivileged service account.
if ! getent group reinchat >/dev/null 2>&1; then
    groupadd --system reinchat
fi
if ! id -u reinchat >/dev/null 2>&1; then
    useradd --system --gid reinchat --home-dir "$DATA_DIR" \
        --no-create-home --shell /usr/sbin/nologin reinchat
fi

install -d -o reinchat -g reinchat -m 700 "$DATA_DIR"
install -d -o root -g root -m 750 "$ENV_DIR"

# Preserve the existing master key. Generating a new one would make every
# already-synced provider API key undecryptable.
ADMIN_TOKEN=""
if [[ -f "$ENV_FILE" ]]; then
    ADMIN_TOKEN="$(sed -n 's/^REINCHAT_ADMIN_TOKEN=//p' "$ENV_FILE" | tail -n 1 | tr -d '\r')"
fi
if [[ -z "$ADMIN_TOKEN" ]]; then
    ADMIN_TOKEN="$(openssl rand -hex 24)"
fi

MASTER_KEY=""
if [[ -f "$ENV_FILE" ]]; then
    MASTER_KEY="$(sed -n 's/^REINCHAT_MASTER_KEY=//p' "$ENV_FILE" | tail -n 1 | tr -d '\r')"
    if [[ -z "$MASTER_KEY" ]]; then
        echo "现有 $ENV_FILE 缺少 REINCHAT_MASTER_KEY，拒绝重新生成以免丢失已加密数据。" >&2
        echo "请先恢复原主密钥，或确认数据可丢弃后手动删除该文件再部署。" >&2
        rm -f -- "$REMOTE_TMP"
        exit 1
    fi
else
    MASTER_KEY="$(openssl rand -base64 32)"
fi

KEY_BYTES="$(printf '%s' "$MASTER_KEY" | base64 -d 2>/dev/null | wc -c | tr -d ' ' || true)"
if [[ "$KEY_BYTES" != "32" ]]; then
    echo "REINCHAT_MASTER_KEY 不是合法的 32 字节 base64，拒绝启动。" >&2
    rm -f -- "$REMOTE_TMP"
    exit 1
fi

umask 077
cat > "$ENV_FILE" <<EOF
REINCHAT_ADDR=127.0.0.1:$PORT
REINCHAT_DATA_DIR=$DATA_DIR
REINCHAT_MASTER_KEY=$MASTER_KEY
REINCHAT_TOKEN_TTL_HOURS=720
REINCHAT_ADMIN_TOKEN=$ADMIN_TOKEN
EOF
chown root:root "$ENV_FILE"
chmod 600 "$ENV_FILE"

install -d -o root -g root -m 755 "$REMOTE_DIR"
install -m 0755 -o root -g root "$REMOTE_TMP" "$REMOTE_BIN.next"
mv -f "$REMOTE_BIN.next" "$REMOTE_BIN"
rm -f -- "$REMOTE_TMP"

# The binary sits under an existing 1Panel site directory. Ensure the service
# account can traverse and execute it without changing other site files.
if ! runuser -u reinchat -- test -x "$REMOTE_BIN"; then
    echo "服务用户 reinchat 无法执行 $REMOTE_BIN，请检查上级目录权限。" >&2
    exit 1
fi

cat > "/etc/systemd/system/$SERVICE" <<EOF
[Unit]
Description=ReinChat Cloud API
Documentation=https://reinchat.markjy.com
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=reinchat
Group=reinchat
WorkingDirectory=$DATA_DIR
EnvironmentFile=$ENV_FILE
ExecStart=$REMOTE_BIN
Restart=always
RestartSec=3
TimeoutStopSec=20
KillSignal=SIGTERM
NoNewPrivileges=true
PrivateTmp=true
ProtectHome=true
ProtectSystem=strict
ReadWritePaths=$DATA_DIR
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6
CapabilityBoundingSet=
UMask=0077
LimitNOFILE=65535

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable "$SERVICE" >/dev/null
systemctl restart "$SERVICE"

HEALTH_OK=0
for _ in $(seq 1 30); do
    if curl -fsS "http://127.0.0.1:$PORT/healthz" >/dev/null 2>&1; then
        HEALTH_OK=1
        break
    fi
    sleep 1
done

if [[ "$HEALTH_OK" -ne 1 ]]; then
    echo "服务未通过健康检查，最近日志：" >&2
    systemctl --no-pager --full status "$SERVICE" >&2 || true
    journalctl -u "$SERVICE" -n 60 --no-pager >&2 || true
    exit 1
fi

echo "systemd: $(systemctl is-enabled "$SERVICE") / $(systemctl is-active "$SERVICE")"
echo -n "healthz: "
curl -fsS "http://127.0.0.1:$PORT/healthz"
echo
REMOTE_SCRIPT

DEPLOY_OK=1
trap - EXIT
rm -f "$SCP_LOG"

log "部署成功"
printf '  二进制：%s\n' "$REMOTE_DIR/reinchat-server"
printf '  监听：  127.0.0.1:%s（由 Nginx 反向代理）\n' "$PORT"
printf '  数据：  %s\n' "$DATA_DIR"
printf '  密钥：  %s（0600，保留原值）\n' "$ENV_FILE"
printf '  服务：  %s（已 enable，随服务器自启）\n' "$SERVICE"
printf '  检查：  curl http://reinchat.markjy.com/healthz\n'
printf '  注意：  App 默认走 HTTPS；请在 1Panel 为 reinchat.markjy.com 启用 SSL 证书\n'
