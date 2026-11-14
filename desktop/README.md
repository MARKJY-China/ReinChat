# ReinChat Desktop

Windows 桌面版，技术栈：

- Tauri 2.12（Rust 后端、WebView2 渲染）
- 原生 TypeScript + Vite，零框架，手写 DOM
- 本地状态使用 Windows DPAPI 加密
- AI 流式请求通过 Rust/reqwest 代理，避免 WebView CORS/SSE 限制

## 状态：开发暂缓

调试版已经完成并验证：

- 流式对话、停止、编辑、回退、错误重试
- 服务商配置、多模型（英文逗号分隔）切换
- 图片附件
- 账号注册/登录、云端会话同步、服务商同步
- Tauri 架构、Windows 构建链、DPAPI 状态加密

本轮暂不发布 Windows 安装包。源码保留在 `desktop/`，后续恢复开发后继续完善安装包与发布文档。

## 开发/构建（本机）

当前机器没有 MSVC，Tauri 使用 GNU 工具链 + MSYS2 UCRT64：

```powershell
$env:PATH = "D:\MSYS2\ucrt64\bin;" + $env:PATH
cd desktop
pnpm install
pnpm tauri dev
# 或
pnpm tauri build
```

`src-tauri/.cargo/config.toml` 固定了 UCRT64 的 `x86_64-w64-mingw32-gcc` 链接器，并启用 `+crt-static`。换到有 MSVC 的机器时应改用 Rust 的 MSVC target，并移除该配置。

需要 WebView2 Runtime（Windows 11 / 新版 Windows 10 通常自带）。

## 本地数据

`%APPDATA%\com.reinchat.desktop\state.json`，由 Windows DPAPI 加密，包含会话、服务商配置、云端 token。旧版明文 state 会在下一次保存时自动加密迁移。
