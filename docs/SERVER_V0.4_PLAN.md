# ReinChat 服务端 v0.4 规划

目标：把“客户端直连服务商”升级为“ReinChat 官方中转 + 账号等级 + Web 控制台”，并让 AI 生成在客户端进程被杀后仍能在服务端继续完成。

## 阶段 0：兼容与部署

- Nginx 同时兼容现有 `/api/v1/*` 与新的 `/api/*`（新路径内部 rewrite 到 v1，避免一次性打断已安装 App）。
- `reinchat.markjy.com` 配置 HTTPS；Vue2 Web 发布到根路径，管理台发布到 `/admin`。
- 服务端仍为 Go 标准库 + SQLite；新增表通过幂等迁移执行。

## 阶段 1：账号等级与升级工单

数据库：

```sql
ALTER TABLE users ADD COLUMN level INTEGER NOT NULL DEFAULT 1; -- 1=LV1, 2=LV2

CREATE TABLE upgrade_requests (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  from_level INTEGER NOT NULL,
  to_level INTEGER NOT NULL,
  reason TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'pending', -- pending/approved/rejected
  reject_reason TEXT,
  admin_note TEXT,
  reviewed_by TEXT,
  created_at INTEGER NOT NULL,
  reviewed_at INTEGER
);
```

接口：

- `GET /api/account`：返回 level、是否可申请 LV2、最近工单。
- `POST /api/account/upgrade-requests`：创建 LV2 工单（限频、同一时间只允许一个 pending）。
- `GET /api/account/upgrade-requests`：用户查看自己的工单。
- 管理端：
  - `GET /api/admin/upgrade-requests?status=pending`
  - `POST /api/admin/upgrade-requests/{id}/approve`
  - `POST /api/admin/upgrade-requests/{id}/reject`（必填拒绝理由，可附备注）
- 所有管理员接口单独鉴权；普通用户 token 不得访问。
- 审批通过后写 `users.level=2`，保留工单审计记录。

## 阶段 2：AI 中转任务（LV2）

核心不是“代理一次请求”，而是“服务端持有任务并在客户端断开后继续写”。

```sql
CREATE TABLE relay_jobs (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  conversation_id TEXT,
  provider_id TEXT NOT NULL,
  status TEXT NOT NULL, -- queued/running/completed/failed/cancelled
  request_json TEXT NOT NULL,
  assistant_text TEXT NOT NULL DEFAULT '',
  reasoning_text TEXT NOT NULL DEFAULT '',
  error TEXT,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

流程：

1. 客户端 `POST /api/relay/jobs`，携带消息、provider 配置引用、模型、思考强度。
2. 服务端校验 `users.level >= 2`，解密该用户保存的服务商密钥，向上游发起流式请求。
3. 服务端把 token 持续写入 `relay_jobs`，同时通过 SSE 推给在线客户端。
4. 客户端 `GET /api/relay/jobs/{id}/events` 重连，先补发已生成内容，再继续实时事件。
5. 客户端进程被杀不影响服务端 loop；重开 App 后按 job 状态恢复。
6. 支持 `POST /api/relay/jobs/{id}/cancel`、超时、并发上限、每日额度。
7. 客户端只保存 jobId；消息以服务端最终状态为准，避免双写冲突。

安全：

- 服务商密钥只在服务端解密，日志不落明文。
- 任务按 user_id 做行级权限校验。
- 请求体大小、模型白名单、max_tokens、并发数均有限制。
- LV1 调用中转返回明确的 `level_required`，客户端引导申请升级。

## 阶段 3：Vue2 Web 客户端

- 技术栈：Vue2 + Vue Router + Vuex + Axios；SSE 用 `EventSource` 或 fetch stream。
- 路径：`https://reinchat.markjy.com/`，API 基址 `https://reinchat.markjy.com/api`。
- 功能对齐 App：登录/注册、会话列表、流式聊天、思考块、模型/思考强度、图片、重试/回退、账号等级与工单。
- 选中 LV2 时走 relay；LV1 可只读历史或走自己的本地配置（按产品策略决定）。
- 管理台 `/admin`：独立登录、用户列表、等级调整、工单审批、会话/服务商审计、任务队列、用量统计。

## 阶段 4：验收标准

- 手机关屏/杀进程后，重开 App 能看到中转任务继续生成或已完成。
- LV1 无法创建 relay job；LV2 可以；审批通过即时生效。
- 管理员可通过/拒绝工单并填写理由/备注，用户端可见。
- Web 与 App 看到同一云端会话和同一条 AI 回复。
- 现有 `/api/v1` 客户端在迁移期不失效。

## 建议顺序

先做阶段 1 + 阶段 2（等级、工单、relay、SSE 重连），这是后台续跑和 LV2 限制的地基；再做阶段 3 的 Vue2 Web/Admin。这样每一步都可独立部署、独立回归。
