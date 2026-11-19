# ReinChat Agent Gateway

This directory is the new DSH-backed agent layer for ReinChat.

## Why a sidecar

DeepSeek Harness is a Node/Cordis runtime, not a Go library. The Go server cannot
`import` it. This gateway spawns the installed `dsh --profile sdk` runtime and
speaks the newline-delimited JSON-RPC protocol from
`@deepseek-ai/dsh-sdk-protocol` over stdio. ReinChat's Go API remains responsible
for accounts, cloud conversation sync, levels and tickets.

## POC status

- Verified against installed DSH `0.2.2-rc.3`.
- Verified `initialize` handshake and `session/query` enqueue.
- Verified `session.event` / `session.status` notification stream.
- **Vendored the 1.29 MiB browser/Node DSH core** from the XuanTie IDE:
  `agent-gateway/vendor/dsh-core.mjs`, with its MIT license and third-party notices.
- `src/embedded-runtime.mjs` creates a Cordis context and registers the seven core
  services plus an OpenAI-compatible adapter; `src/embedded-server.mjs` exposes
  frame/event SSE without spawning Electron or the dsh CLI.
- Model authentication is supplied per request.
- The current POC does not yet include the Go proxy or the Vue2 SFC frontend.

## Security boundary

`dsh --profile sdk` ships coding tools. The gateway must always launch with the
restricted profile patch in `profiles/reinchat.patch.yml`, which:

- disables plugin management, workflow, subagents and Office tools;
- keeps web search/fetch tools;
- confines the sandbox to `REINCHAT_WORKSPACE_ROOT` and uses `workspace-write`.

Do not expose the gateway port publicly. It is intended to bind to loopback and
be called by the ReinChat Go server only.

## Run the embedded core gateway (recommended)

```bash
DEEPSEEK_API_KEY="..." node src/embedded-server.mjs
```

Then:

```bash
curl -N -X POST http://127.0.0.1:18092/v1/run \
  -H 'Content-Type: application/json' \
  -d '{"baseUrl":"https://api.deepseek.com/v1","model":"deepseek-chat","text":"Say hello"}'
```

The response is SSE: `session`, `frame` (DSH AssistantStreamFrame), `event`
(DSH SessionEvent), `done`/`error`.

## Run the CLI sidecar POC

```bash
DSH_NODE_EXECUTABLE="H:/DeepSeekHarness/DeepSeek Harness.exe" \
DSH_CLI_BIN="H:/DeepSeekHarness/resources/app.asar/dsh/node_modules/@deepseek-ai/dsh/lib/bin.js" \
DEEPSEEK_API_KEY="..." \
REINCHAT_WORKSPACE_ROOT="$PWD/workspaces/demo" \
node src/server.mjs
```

Then:

```bash
curl -N -X POST http://127.0.0.1:18091/v1/run \
  -H 'Content-Type: application/json' \
  -d '{"provider":"deepseek-official","model":"deepseek-chat","text":"Say hello"}'
```

The response is SSE. `session.event` frames are forwarded as-is under
`event: dsh`; a terminal `session.status idle` is sent as `event: done`.
