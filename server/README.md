# ReinChat Server v0.2

这是 ReinChat 的轻量自托管后端，供 Android 客户端通过 HTTPS 调用。服务提供账号、对话云同步、选中的 Provider 配置云同步和图片存储，面向单实例部署：Go 标准库 HTTP + SQLite + AES-256-GCM + bcrypt。

- 目标环境：Debian 12 VPS、Docker Compose、Caddy 自动 HTTPS
- 正式域名：**https://reinchat.markjy.com**
- API 基础路径：`/api/v1`
- 监听地址：默认 `:8080`（仅容器内暴露，不对公网发布）
- SQLite 驱动：`modernc.org/sqlite`（纯 Go，无需 CGO）
- 默认 token 有效期：30 天

## 功能

- 注册、登录、自动登录、当前用户、登出
- Provider 配置按用户隔离、upsert、删除；`apiKey` 使用 AES-256-GCM 加密后落库
- 对话全量创建、摘要列表、读取、乐观锁更新、删除
- 图片上传（JPEG/PNG/WebP，最大 10 MiB）与鉴权下载；文件仅所有者可访问
- 对话中图片 ID 的归属校验
- 健康检查：`GET /healthz`
- 请求日志（方法、路径、状态码、耗时，不含 body 与 token）
- 启动时幂等建表、SQLite WAL/busy timeout、优雅退出、请求超时、限制请求体大小

## 目录

```text
server/
├── *.go                  # 服务实现与 httptest 集成测试
├── schema.sql            # 幂等 SQLite schema
├── Dockerfile            # 多阶段构建，非 root 运行
├── docker-compose.yml    # app + Caddy
├── Caddyfile             # reinchat.markjy.com 反向代理
├── .env.example          # 环境变量模板（不含真实密钥）
├── build-linux.sh        # Linux amd64 交叉编译脚本
├── build-linux.ps1       # Windows PowerShell 交叉编译脚本
├── bin/                  # 预编译 Linux amd64 可执行文件
└── README.md
```

## 环境变量

| 变量 | 必填 | 默认值 | 说明 |
|---|---:|---|---|
| `REINCHAT_MASTER_KEY` | 是 | — | Base64 编码的 32 字节主密钥；用于 AES-256-GCM 加密 Provider API Key |
| `REINCHAT_ADDR` | 否 | `:8080` | HTTP 监听地址 |
| `REINCHAT_DATA_DIR` | 否 | `/data` | SQLite 与图片文件根目录 |
| `REINCHAT_TOKEN_TTL_HOURS` | 否 | `720` | Bearer token 有效小时数 |

生成主密钥：

```bash
openssl rand -base64 32
```

**主密钥必须长期保存并单独备份。** 修改主密钥后，数据库中已有的 Provider `apiKey` 将无法解密；当前版本不提供在线轮换工具。不要把真实密钥写进 README、Git 或聊天记录。

## 本地运行

需要 Go 1.25+。在 `server/` 目录执行：

```bash
cd server
export REINCHAT_MASTER_KEY="$(openssl rand -base64 32)"
export REINCHAT_DATA_DIR="$PWD/data"
go run .
```

Windows PowerShell：

```powershell
cd server
$env:REINCHAT_MASTER_KEY = (openssl rand -base64 32)
$env:REINCHAT_DATA_DIR = "$PWD\data"
go run .
```

不要把每次启动时重新生成的值用于生产：同一个数据目录必须继续使用同一个主密钥。验证：

```bash
curl http://127.0.0.1:8080/healthz
# {"status":"ok"}
```

数据目录中会生成：

```text
data/reinchat.db
data/reinchat.db-wal
data/reinchat.db-shm
data/files/<uuid>
```

## 测试与构建

```bash
cd server
go test ./...
go vet ./...
```

测试使用 `httptest` 与临时数据目录，覆盖：

- 注册/登录/重复用户名/当前用户/登出/过期 token/未授权访问
- Provider upsert、列表、删除、密钥往返与“不是明文落库”
- 对话创建、列表、读取、更新、版本冲突、删除与校验边界
- 图片上传/下载、跨用户拒绝、对话图片归属校验
- 限流器

交叉编译 Linux amd64（本机无需 Docker）：

```bash
sh build-linux.sh
# 或 Windows PowerShell:
.\build-linux.ps1
```

产物：`bin/reinchat-server-linux-amd64`。构建使用 `CGO_ENABLED=0 GOOS=linux GOARCH=amd64`。

## Debian 12 + Docker Compose + Caddy 部署

### 1. DNS

在 DNS 服务商为以下记录设置 VPS 公网 IP：

```text
A     reinchat.markjy.com    <VPS_IPv4>
AAAA  reinchat.markjy.com    <VPS_IPv6>   # 可选；没有 IPv6 就不要添加
```

先确认解析生效：

```bash
dig +short reinchat.markjy.com
```

Caddy 首次启动会自动申请 Let's Encrypt 证书。80/443 必须能从公网访问，且域名必须已经指向本机。

### 2. 防火墙

只开放 SSH 和 HTTP/HTTPS，**不要**开放 8080：

```bash
sudo ufw allow 22/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw enable
sudo ufw status
```

### 3. 安装 Docker Engine 与 Compose 插件（Debian 12）

```bash
sudo apt update
sudo apt install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/debian/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

### 4. 上传并配置

将整个 `server/` 目录上传到 VPS，例如 `/opt/reinchat`：

```bash
scp -r server user@<VPS_IP>:/opt/reinchat
ssh user@<VPS_IP>
cd /opt/reinchat
cp .env.example .env
KEY="$(openssl rand -base64 32)"
printf 'REINCHAT_MASTER_KEY=%s\nREINCHAT_TOKEN_TTL_HOURS=720\n' "$KEY" > .env
chmod 600 .env
```

将 `$KEY`（主密钥）立即保存到独立的密码管理器/离线介质。不要丢失。

### 5. 启动

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f app
docker compose logs -f caddy
```

Caddy 会监听 80/443、自动完成 HTTPS，并把 `https://reinchat.markjy.com` 反代到 `app:8080`。Compose 只使用 `expose`，宿主机不发布 8080。

验证：

```bash
curl https://reinchat.markjy.com/healthz
# {"status":"ok"}

curl -i https://reinchat.markjy.com/api/v1/providers
# 401，错误 JSON
```

如果证书申请失败，检查 DNS、80/443 防火墙、Caddy 日志，以及 80 端口是否被其他 Web 服务器占用。

### 6. 更新

```bash
cd /opt/reinchat
docker compose up -d --build
```

数据在命名卷中，重建容器不会丢失。

## 备份与恢复

### 需要备份的内容

- `reinchat_data` 卷：SQLite DB 与 `files/` 图片
- `.env` 中的 `REINCHAT_MASTER_KEY`（单独、加密保存）
- 可选：`reinchat_caddy_data`（证书与 ACME 状态）

SQLite 使用 WAL。最稳妥的备份方式是短暂停止 app 后复制整个 `/data`，确保 DB 与图片一致：

```bash
cd /opt/reinchat
mkdir -p backups
docker compose stop app
docker run --rm \
  -v reinchat_data:/data:ro \
  -v "$PWD/backups:/backup" \
  debian:12-slim \
  tar czf "/backup/reinchat-data-$(date +%F-%H%M%S).tar.gz" -C /data .
docker compose start app
```

加密备份文件后再转移到离线位置。备份中包含密码哈希、token 哈希、加密的 Provider 密钥和私有图片。

### 恢复

**先确认压缩包路径正确，并再次备份当前数据。**

```bash
cd /opt/reinchat
docker compose down
docker run --rm \
  -v reinchat_data:/data \
  -v "$PWD/backups:/backup" \
  debian:12-slim \
  sh -c 'rm -rf /data/* /data/.[!.]* /data/..?* 2>/dev/null; tar xzf /backup/reinchat-data-YYYY-MM-DD-HHMMSS.tar.gz -C /data'
docker compose up -d
```

恢复后若宿主机改过文件所有者，需保证 `/data` 可被容器内 UID 10001 写入：

```bash
docker run --rm -v reinchat_data:/data debian:12-slim chown -R 10001:10001 /data
```

把 `.env` 恢复为与备份时相同的主密钥。若主密钥丢失，账号和对话仍可恢复，但已保存的 Provider API Key 无法解密。

## API 摘要

所有非认证接口必须带：

```http
Authorization: Bearer <opaque-token>
```

时间均为 Unix epoch 毫秒。错误格式：

```json
{"error":{"code":"snake_case_code","message":"human readable"}}
```

### 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/register` | `{"username","password"}` → 201 自动登录 |
| POST | `/api/v1/auth/login` | 登录 → 200 |
| GET | `/api/v1/auth/me` | 当前用户 |
| POST | `/api/v1/auth/logout` | 204，注销当前 token |
| GET | `/healthz` | 无认证健康检查 |

认证成功响应字段：

```json
{"token":"...","expiresAt":1700000000000,"user":{"username":"alice"}}
```

用户名去除首尾空白，3–32 个 Unicode 字母/数字及 `_-.`，大小写不敏感唯一。密码 8–128 字符；超过 bcrypt 72 字节限制的密码会先做 SHA-256 预哈希再交给 bcrypt。

### Provider

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/providers` | `{"providers":[...]}` |
| PUT | `/api/v1/providers/{id}` | 只 upsert 当前用户的行，返回 200 DTO |
| DELETE | `/api/v1/providers/{id}` | 204 |

Provider DTO 字段与接口约定完全一致；`temperature` 和 `maxTokens` 可为 `null`。`apiKey` 在 DB 中以 `AES-256-GCM(masterKey)` 保存，返回给已认证所有者时解密。Provider 的 `id` 最长 100 字符，请求体中的 `id` 必须与 URL 一致。

### 对话

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/conversations` | 客户端生成 UUID；同用户重复 → 409 `conversation_exists` |
| GET | `/api/v1/conversations` | 最多 500 条 SummaryDTO，`updatedAt` 降序 |
| GET | `/api/v1/conversations/{id}` | 完整 ConversationDTO |
| PUT | `/api/v1/conversations/{id}` | 乐观锁更新；冲突 → 409 + 当前完整 conversation |
| DELETE | `/api/v1/conversations/{id}` | 204（幂等） |

创建时忽略客户端 `version`，新版本为 1。PUT 使用 `baseVersion`，成功后版本加 1，并由服务端写入 `updatedAt = now`。POST 保留客户端提交的 `createdAt`/`updatedAt`。

版本冲突响应：

```json
{
  "error": {"code":"version_conflict","message":"..."},
  "conversation": {"id":"...","title":"...","createdAt":1,"updatedAt":2,"version":2,"messages":[]}
}
```

对话消息中的图片 `remoteId` 可以为 `null`；非空时必须是当前用户名下的已上传文件 UUID。`images` 为空时返回 `[]`，不是 `null`。

### 文件

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/files` | `multipart/form-data`，字段名 `file`，最大 10 MiB |
| GET | `/api/v1/files/{id}` | 仅所有者；非所有者统一 404 |

上传成功：`201 {"id":"...","mimeType":"image/png","size":123}`。服务端根据文件魔数验证 JPEG/PNG/WebP，不信任客户端 MIME。文件保存为 `REINCHAT_DATA_DIR/files/<uuid>`，元数据存 SQLite。

## 安全说明

- 传输：只通过 Caddy 的 HTTPS 对外提供服务，app 的 8080 端口不映射到宿主机。应用在 HTTPS 请求下发送 HSTS、`X-Content-Type-Options: nosniff`、CSP、`X-Frame-Options: DENY` 等安全头。
- Provider 密钥：服务端必须能在配置同步时解密 `apiKey`，因此这**不是端到端加密**。VPS 或主密钥泄露意味着 Provider 密钥泄露。只使用 HTTPS，限制 VPS SSH 访问，`/opt/reinchat/.env` 设为 `600`。
- 主密钥：`REINCHAT_MASTER_KEY` 必须是 Base64 32 字节。请单独备份；丢失后已有 Provider 密钥不可恢复。当前不支持不中断地更换主密钥。
- 密码：bcrypt cost 12；长密码先 SHA-256 预哈希，避免超过 bcrypt 的 72 字节输入上限。数据库不保存明文密码。
- Token：32 字节随机 URL-safe 值，只保存 SHA-256 哈希，默认 30 天过期；登出立即删除当前 token。
- 限流：注册/登录按客户端 IP 每分钟 20 次，内存实现，重启清零；单实例有效。Caddy 覆盖 `X-Real-IP`，8080 不公网暴露，应用只信任这个头。
- 日志：只记录方法、路径、状态、耗时；不记录请求体、Authorization、token、密码或 API Key。
- 数据：SQLite 开启 WAL、busy timeout、外键。数据目录 0700，容器以 UID 10001 非 root 运行，根文件系统只读（`/data` 卷与 `/tmp` 除外）。
- 图片：仅允许 JPEG/PNG/WebP，下载使用 `nosniff` 和 `Content-Disposition: inline`，私有缓存；跨用户访问返回 404。
- 备份：备份包含敏感数据；请先加密再放到离线位置。

## 已知限制（v0.2）

- 单实例、SQLite、进程内限流；不适合多副本共享。
- 不提供密码重置、邮箱验证、管理员后台、文件删除接口。
- 删除对话不会立即删除已上传图片，可能留下孤儿文件；后续版本可增加清理任务。
- 对话列表最多 500 条，无分页。
- 单条消息正文最大 1 MiB、对话请求体最大 16 MiB、最多 1000 条消息、每消息最多 20 张图片、整对话最多 1000 张图片引用。
- Provider 配置仅在服务端加密静态存储；服务端可见明文 API Key。
- 不包含 Android 客户端代码，也不修改 `app/`。
