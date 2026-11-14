use base64::{Engine as _, engine::general_purpose::STANDARD};
use futures_util::StreamExt;
use reqwest::header::{ACCEPT, AUTHORIZATION, CONTENT_TYPE, USER_AGENT};
use reqwest::multipart;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;
use tauri::{AppHandle, Emitter, Manager, State};
use tokio::sync::Mutex;
use tokio_util::sync::CancellationToken;

const USER_AGENT_VALUE: &str = "ReinChat/0.3 (Windows)";

struct RuntimeState {
    client: reqwest::Client,
    jobs: Arc<Mutex<HashMap<String, CancellationToken>>>,
}

impl RuntimeState {
    fn new() -> Self {
        let client = reqwest::Client::builder()
            .connect_timeout(Duration::from_secs(30))
            .build()
            .expect("build reqwest client");
        Self {
            client,
            jobs: Arc::new(Mutex::new(HashMap::new())),
        }
    }
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct ChatRequest {
    request_id: String,
    endpoint: String,
    protocol: String,
    api_key: String,
    body: Value,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct DeltaEvent {
    request_id: String,
    text: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct ErrorEvent {
    request_id: String,
    message: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct DoneEvent {
    request_id: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct HttpRequest {
    method: String,
    url: String,
    token: Option<String>,
    body: Option<Value>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct HttpResponse {
    status: u16,
    body: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct UploadImageRequest {
    url: String,
    token: String,
    data_url: String,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DownloadImageRequest {
    url: String,
    token: String,
}

enum SseOutcome {
    Ignore,
    Delta(String),
    Done,
    Error(String),
}

fn normalize_api_key(raw: &str) -> String {
    let mapped: String = raw
        .chars()
        .filter_map(|ch| match ch {
            '\u{00a0}' | '\u{2007}' | '\u{202f}' => Some(' '),
            '\u{2010}' | '\u{2011}' | '\u{2012}' | '\u{2013}' | '\u{2014}' | '\u{2015}'
            | '\u{2212}' | '\u{ff0d}' => Some('-'),
            '\u{ff3f}' => Some('_'),
            '\u{200b}' | '\u{200c}' | '\u{200d}' | '\u{2060}' | '\u{feff}' | '\u{00ad}'
            | '\u{2028}' | '\u{2029}' | '\u{180e}' => None,
            other => Some(other),
        })
        .filter(|ch| !ch.is_whitespace())
        .collect();
    mapped
}

fn api_key_problem(key: &str) -> Option<String> {
    let (index, ch) = key
        .char_indices()
        .find(|(_, ch)| !(33..=126).contains(&(*ch as u32)))?;
    Some(format!(
        "API Key 第 {} 个字符不可用于 HTTP 请求头（U+{:04X}）。请重新复制纯文本 Key。",
        key[..index].chars().count() + 1,
        ch as u32
    ))
}

fn emit_delta(app: &AppHandle, request_id: &str, text: String) {
    let _ = app.emit(
        "ai-delta",
        DeltaEvent {
            request_id: request_id.to_string(),
            text,
        },
    );
}

fn emit_done(app: &AppHandle, request_id: &str) {
    let _ = app.emit(
        "ai-done",
        DoneEvent {
            request_id: request_id.to_string(),
        },
    );
}

fn emit_error(app: &AppHandle, request_id: &str, message: impl Into<String>) {
    let _ = app.emit(
        "ai-error",
        ErrorEvent {
            request_id: request_id.to_string(),
            message: message.into(),
        },
    );
}

fn text_from_content(value: &Value) -> String {
    match value {
        Value::String(text) => text.clone(),
        Value::Array(parts) => parts
            .iter()
            .filter_map(|part| {
                let kind = part.get("type").and_then(Value::as_str).unwrap_or("");
                if matches!(kind, "text" | "output_text" | "input_text") {
                    part.get("text").and_then(Value::as_str).map(str::to_string)
                } else {
                    None
                }
            })
            .collect::<Vec<_>>()
            .join(""),
        _ => String::new(),
    }
}

fn error_value_message(value: &Value) -> String {
    match value {
        Value::String(text) => text.clone(),
        Value::Object(map) => {
            for key in ["message", "detail"] {
                if let Some(message) = map.get(key).and_then(Value::as_str) {
                    if !message.trim().is_empty() {
                        return message.to_string();
                    }
                }
            }
            if let Some(error) = map.get("error") {
                let nested = error_value_message(error);
                if !nested.trim().is_empty() {
                    return nested;
                }
            }
            value.to_string()
        }
        Value::Array(items) => items
            .iter()
            .map(error_value_message)
            .filter(|s| !s.trim().is_empty())
            .collect::<Vec<_>>()
            .join(" "),
        _ => value.to_string(),
    }
}

fn parse_sse(protocol: &str, raw: &str) -> SseOutcome {
    let data = raw.trim_start_matches("data:").trim();
    if data.is_empty() {
        return SseOutcome::Ignore;
    }
    if data == "[DONE]" {
        return SseOutcome::Done;
    }
    let root: Value = match serde_json::from_str(data) {
        Ok(value) => value,
        Err(_) => return SseOutcome::Ignore,
    };
    if let Some(error) = root.get("error") {
        return SseOutcome::Error(error_value_message(error));
    }

    if protocol == "RESPONSES" {
        let kind = root.get("type").and_then(Value::as_str).unwrap_or("");
        match kind {
            "response.output_text.delta" | "response.refusal.delta" => {
                let delta = root.get("delta").and_then(Value::as_str).unwrap_or("");
                if delta.is_empty() {
                    SseOutcome::Ignore
                } else {
                    SseOutcome::Delta(delta.to_string())
                }
            }
            "response.completed" | "response.incomplete" => SseOutcome::Done,
            "response.failed" | "response.cancelled" => {
                let message = root
                    .get("response")
                    .and_then(|response| response.get("error"))
                    .map(error_value_message)
                    .unwrap_or_else(|| "生成失败".to_string());
                SseOutcome::Error(message)
            }
            "error" => SseOutcome::Error(error_value_message(&root)),
            _ => SseOutcome::Ignore,
        }
    } else {
        let choice = root
            .get("choices")
            .and_then(Value::as_array)
            .and_then(|items| items.first());
        let Some(choice) = choice else {
            return SseOutcome::Ignore;
        };
        let delta = choice.get("delta");
        let content = delta
            .and_then(|d| d.get("content"))
            .or_else(|| delta.and_then(|d| d.get("text")))
            .or_else(|| choice.get("text"));
        let text = content.map(text_from_content).unwrap_or_default();
        if !text.is_empty() {
            return SseOutcome::Delta(text);
        }
        if choice.get("finish_reason").is_some_and(|v| !v.is_null()) {
            return SseOutcome::Done;
        }
        SseOutcome::Ignore
    }
}

fn full_response_text(protocol: &str, root: &Value) -> String {
    if protocol == "RESPONSES" {
        return root
            .get("output")
            .and_then(Value::as_array)
            .map(|output| {
                output
                    .iter()
                    .filter_map(|item| {
                        item.get("content").and_then(Value::as_array).map(|parts| {
                            parts
                                .iter()
                                .filter_map(|part| {
                                    let kind = part.get("type").and_then(Value::as_str).unwrap_or("");
                                    if matches!(kind, "output_text" | "text") {
                                        part.get("text").and_then(Value::as_str).map(str::to_string)
                                    } else {
                                        None
                                    }
                                })
                                .collect::<Vec<_>>()
                                .join("")
                        })
                    })
                    .collect::<Vec<_>>()
                    .join("")
            })
            .unwrap_or_default();
    }
    let choice = root
        .get("choices")
        .and_then(Value::as_array)
        .and_then(|items| items.first());
    choice
        .and_then(|c| c.get("message"))
        .and_then(|m| m.get("content"))
        .map(text_from_content)
        .or_else(|| {
            choice
                .and_then(|c| c.get("text"))
                .and_then(Value::as_str)
                .map(str::to_string)
        })
        .unwrap_or_default()
}

async fn run_chat(
    app: AppHandle,
    client: reqwest::Client,
    token: CancellationToken,
    request: ChatRequest,
) {
    let id = request.request_id.clone();
    if !(request.endpoint.starts_with("http://") || request.endpoint.starts_with("https://")) {
        emit_error(&app, &id, "Base URL 必须以 http:// 或 https:// 开头");
        return;
    }
    let key = normalize_api_key(&request.api_key);
    if let Some(problem) = api_key_problem(&key) {
        emit_error(&app, &id, problem);
        return;
    }

    let mut builder = client
        .post(&request.endpoint)
        .header(ACCEPT, "text/event-stream")
        .header(USER_AGENT, USER_AGENT_VALUE)
        .json(&request.body);
    if !key.is_empty() {
        let header_value = match reqwest::header::HeaderValue::from_str(&format!("Bearer {key}")) {
            Ok(value) => value,
            Err(_) => {
                emit_error(&app, &id, "API Key 无法写入 HTTP 请求头，请检查服务商配置");
                return;
            }
        };
        builder = builder.header(AUTHORIZATION, header_value);
    }

    let response = match builder.send().await {
        Ok(response) => response,
        Err(error) => {
            let message = if error.is_timeout() {
                "连接超时，请检查网络或服务商地址".to_string()
            } else if error.is_connect() {
                "无法连接服务商，请检查 Base URL、端口和网络".to_string()
            } else {
                format!("网络请求失败：{error}")
            };
            emit_error(&app, &id, message);
            return;
        }
    };

    if !response.status().is_success() {
        let status = response.status();
        let raw = response.text().await.unwrap_or_default();
        let detail = serde_json::from_str::<Value>(&raw)
            .ok()
            .map(|root| {
                root.get("error")
                    .map(error_value_message)
                    .or_else(|| root.get("message").map(error_value_message))
                    .unwrap_or_default()
            })
            .filter(|message| !message.trim().is_empty())
            .unwrap_or_else(|| {
                raw.replace('<', " <")
                    .split_whitespace()
                    .collect::<Vec<_>>()
                    .join(" ")
                    .chars()
                    .take(220)
                    .collect()
            });
        let prefix = match status.as_u16() {
            401 | 403 => "API Key 无效或没有权限",
            404 => "接口不存在，请检查 Base URL",
            429 => "请求过于频繁或额度不足",
            500..=599 => "服务商暂时不可用",
            _ => "请求失败",
        };
        emit_error(
            &app,
            &id,
            format!("{prefix}（HTTP {}）\n{detail}", status.as_u16()),
        );
        return;
    }

    let content_type = response
        .headers()
        .get(CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .unwrap_or("")
        .to_lowercase();
    if content_type.contains("text/html") {
        emit_error(&app, &id, "服务商返回了网页而不是 API 数据，请检查 Base URL");
        return;
    }

    if content_type.contains("application/json") && !content_type.contains("event-stream") {
        let raw = response.text().await.unwrap_or_default();
        let root: Value = match serde_json::from_str(&raw) {
            Ok(value) => value,
            Err(_) => {
                emit_error(&app, &id, "服务商返回了无法解析的数据");
                return;
            }
        };
        if let Some(error) = root.get("error") {
            emit_error(&app, &id, error_value_message(error));
            return;
        }
        let text = full_response_text(&request.protocol, &root);
        if !text.is_empty() {
            emit_delta(&app, &id, text);
        }
        emit_done(&app, &id);
        return;
    }

    let mut stream = response.bytes_stream();
    let mut buffer: Vec<u8> = Vec::new();
    let mut done = false;
    while !done {
        tokio::select! {
            _ = token.cancelled() => return,
            chunk = stream.next() => {
                let Some(chunk) = chunk else { break };
                match chunk {
                    Ok(bytes) => buffer.extend_from_slice(&bytes),
                    Err(error) => {
                        emit_error(&app, &id, format!("读取流式响应失败：{error}"));
                        return;
                    }
                }
                while let Some(pos) = buffer.iter().position(|byte| *byte == b'\n') {
                    let line_bytes: Vec<u8> = buffer.drain(..=pos).collect();
                    let line = String::from_utf8_lossy(&line_bytes);
                    let line = line.trim_end_matches(['\r', '\n']);
                    let trimmed = line.trim_start();
                    if !trimmed.starts_with("data:") {
                        continue;
                    }
                    match parse_sse(&request.protocol, trimmed) {
                        SseOutcome::Ignore => {}
                        SseOutcome::Delta(text) => emit_delta(&app, &id, text),
                        SseOutcome::Done => {
                            done = true;
                            break;
                        }
                        SseOutcome::Error(message) => {
                            emit_error(&app, &id, message);
                            return;
                        }
                    }
                }
            }
        }
    }
    emit_done(&app, &id);
}

#[tauri::command]
async fn start_chat(
    app: AppHandle,
    state: State<'_, RuntimeState>,
    request: ChatRequest,
) -> Result<(), String> {
    let endpoint = request.endpoint.trim().to_string();
    if !(endpoint.starts_with("http://") || endpoint.starts_with("https://")) {
        return Err("Base URL 必须以 http:// 或 https:// 开头".to_string());
    }
    if request.request_id.trim().is_empty() {
        return Err("requestId is required".to_string());
    }
    let request = ChatRequest {
        endpoint,
        ..request
    };
    let id = request.request_id.clone();
    let token = CancellationToken::new();
    {
        let mut jobs = state.jobs.lock().await;
        if let Some(previous) = jobs.insert(id.clone(), token.clone()) {
            previous.cancel();
        }
    }
    let client = state.client.clone();
    let jobs = state.jobs.clone();
    tauri::async_runtime::spawn(async move {
        run_chat(app, client, token.clone(), request).await;
        jobs.lock().await.remove(&id);
    });
    Ok(())
}

#[tauri::command]
async fn stop_chat(
    state: State<'_, RuntimeState>,
    request_id: String,
) -> Result<(), String> {
    if let Some(token) = state.jobs.lock().await.remove(&request_id) {
        token.cancel();
    }
    Ok(())
}

const DPAPI_MAGIC: &[u8] = b"RCDP1";

fn protect_with_dpapi(plain: &[u8]) -> Result<Vec<u8>, String> {
    use windows_sys::Win32::Foundation::LocalFree;
    use windows_sys::Win32::Security::Cryptography::{
        CRYPTPROTECT_UI_FORBIDDEN, CRYPT_INTEGER_BLOB, CryptProtectData,
    };
    let input = CRYPT_INTEGER_BLOB {
        cbData: plain.len() as u32,
        pbData: plain.as_ptr() as *mut u8,
    };
    let mut output = CRYPT_INTEGER_BLOB {
        cbData: 0,
        pbData: std::ptr::null_mut(),
    };
    let ok = unsafe {
        CryptProtectData(
            &input,
            std::ptr::null(),
            std::ptr::null(),
            std::ptr::null(),
            std::ptr::null(),
            CRYPTPROTECT_UI_FORBIDDEN,
            &mut output,
        )
    };
    if ok == 0 {
        return Err(format!(
            "Windows DPAPI 加密失败：{}",
            std::io::Error::last_os_error()
        ));
    }
    let encrypted = unsafe {
        std::slice::from_raw_parts(output.pbData, output.cbData as usize).to_vec()
    };
    unsafe {
        LocalFree(output.pbData as _);
    }
    Ok(encrypted)
}

fn unprotect_with_dpapi(cipher: &[u8]) -> Result<Vec<u8>, String> {
    use windows_sys::Win32::Foundation::LocalFree;
    use windows_sys::Win32::Security::Cryptography::{
        CRYPTPROTECT_UI_FORBIDDEN, CRYPT_INTEGER_BLOB, CryptUnprotectData,
    };
    let input = CRYPT_INTEGER_BLOB {
        cbData: cipher.len() as u32,
        pbData: cipher.as_ptr() as *mut u8,
    };
    let mut output = CRYPT_INTEGER_BLOB {
        cbData: 0,
        pbData: std::ptr::null_mut(),
    };
    let ok = unsafe {
        CryptUnprotectData(
            &input,
            std::ptr::null_mut(),
            std::ptr::null(),
            std::ptr::null(),
            std::ptr::null(),
            CRYPTPROTECT_UI_FORBIDDEN,
            &mut output,
        )
    };
    if ok == 0 {
        return Err(format!(
            "Windows DPAPI 解密失败：{}",
            std::io::Error::last_os_error()
        ));
    }
    let plain = unsafe {
        std::slice::from_raw_parts(output.pbData, output.cbData as usize).to_vec()
    };
    unsafe {
        LocalFree(output.pbData as _);
    }
    Ok(plain)
}

fn state_path(app: &AppHandle) -> Result<PathBuf, String> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|error| format!("无法定位应用数据目录：{error}"))?;
    fs::create_dir_all(&dir).map_err(|error| format!("无法创建应用数据目录：{error}"))?;
    Ok(dir.join("state.json"))
}

#[tauri::command]
fn load_state(app: AppHandle) -> Result<Option<String>, String> {
    let path = state_path(&app)?;
    if !path.exists() {
        return Ok(None);
    }
    let bytes = fs::read(&path).map_err(|error| format!("读取本地数据失败：{error}"))?;
    let plain = if bytes.starts_with(DPAPI_MAGIC) {
        unprotect_with_dpapi(&bytes[DPAPI_MAGIC.len()..])?
    } else {
        // Migrate v0.3.0 plaintext files. The next save encrypts them.
        bytes
    };
    String::from_utf8(plain)
        .map(Some)
        .map_err(|error| format!("本地数据不是有效 UTF-8：{error}"))
}

#[tauri::command]
fn save_state(app: AppHandle, contents: String) -> Result<(), String> {
    let path = state_path(&app)?;
    let temp = path.with_extension("json.tmp");
    let encrypted = protect_with_dpapi(contents.as_bytes())?;
    let mut payload = Vec::with_capacity(DPAPI_MAGIC.len() + encrypted.len());
    payload.extend_from_slice(DPAPI_MAGIC);
    payload.extend_from_slice(&encrypted);
    fs::write(&temp, &payload).map_err(|error| format!("写入本地数据失败：{error}"))?;
    if path.exists() {
        let _ = fs::remove_file(&path);
    }
    fs::rename(&temp, &path).map_err(|error| {
        let _ = fs::remove_file(&temp);
        format!("保存本地数据失败：{error}")
    })
}

#[tauri::command]
async fn cloud_request(
    state: State<'_, RuntimeState>,
    request: HttpRequest,
) -> Result<HttpResponse, String> {
    if !(request.url.starts_with("http://") || request.url.starts_with("https://")) {
        return Err("服务器地址必须使用 http:// 或 https://".to_string());
    }
    let method = reqwest::Method::from_bytes(request.method.as_bytes())
        .map_err(|_| "不支持的 HTTP 方法".to_string())?;
    let mut builder = state.client.request(method, &request.url);
    if let Some(token) = request.token.filter(|token| !token.is_empty()) {
        builder = builder.bearer_auth(token);
    }
    if let Some(body) = request.body {
        builder = builder.json(&body);
    }
    let response = builder
        .timeout(Duration::from_secs(60))
        .send()
        .await
        .map_err(|error| format!("请求云端失败：{error}"))?;
    let status = response.status().as_u16();
    let body = response.text().await.unwrap_or_default();
    Ok(HttpResponse { status, body })
}

#[tauri::command]
async fn cloud_upload_image(
    state: State<'_, RuntimeState>,
    request: UploadImageRequest,
) -> Result<HttpResponse, String> {
    if !(request.url.starts_with("http://") || request.url.starts_with("https://")) {
        return Err("服务器地址必须使用 http:// 或 https://".to_string());
    }
    let (meta, data) = request
        .data_url
        .split_once(',')
        .ok_or_else(|| "图片数据格式不正确".to_string())?;
    if !meta.contains(";base64") {
        return Err("图片必须使用 base64 data URL".to_string());
    }
    let mime = meta
        .strip_prefix("data:")
        .and_then(|value| value.split(';').next())
        .filter(|value| value.starts_with("image/"))
        .unwrap_or("image/jpeg");
    let bytes = STANDARD
        .decode(data)
        .map_err(|_| "图片 base64 解码失败".to_string())?;
    let extension = match mime {
        "image/png" => "png",
        "image/webp" => "webp",
        "image/gif" => "gif",
        _ => "jpg",
    };
    let part = multipart::Part::bytes(bytes)
        .file_name(format!("image.{extension}"))
        .mime_str(mime)
        .map_err(|error| format!("图片 MIME 无效：{error}"))?;
    let form = multipart::Form::new().part("file", part);
    let response = state
        .client
        .post(&request.url)
        .bearer_auth(request.token)
        .multipart(form)
        .send()
        .await
        .map_err(|error| format!("上传图片失败：{error}"))?;
    let status = response.status().as_u16();
    let body = response.text().await.unwrap_or_default();
    Ok(HttpResponse { status, body })
}

#[tauri::command]
async fn cloud_download_image(
    state: State<'_, RuntimeState>,
    request: DownloadImageRequest,
) -> Result<String, String> {
    if !(request.url.starts_with("http://") || request.url.starts_with("https://")) {
        return Err("服务器地址必须使用 http:// 或 https://".to_string());
    }
    let response = state
        .client
        .get(&request.url)
        .bearer_auth(request.token)
        .send()
        .await
        .map_err(|error| format!("下载图片失败：{error}"))?;
    if !response.status().is_success() {
        return Err(format!("下载图片失败（HTTP {}）", response.status().as_u16()));
    }
    let mime = response
        .headers()
        .get(CONTENT_TYPE)
        .and_then(|value| value.to_str().ok())
        .filter(|value| value.starts_with("image/"))
        .unwrap_or("image/jpeg")
        .to_string();
    let bytes = response
        .bytes()
        .await
        .map_err(|error| format!("读取图片失败：{error}"))?;
    Ok(format!("data:{mime};base64,{}", STANDARD.encode(bytes)))
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(RuntimeState::new())
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![
            load_state,
            save_state,
            start_chat,
            stop_chat,
            cloud_request,
            cloud_upload_image,
            cloud_download_image
        ])
        .run(tauri::generate_context!())
        .expect("error while running ReinChat");
}
