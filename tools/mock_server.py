#!/usr/bin/env python3
"""Local mock OpenAI-compatible server for ReinChat UI verification.

Run:
    python tools/mock_server.py
Then configure Base URL http://10.0.2.2:8787/v1 in the Android emulator.
"""
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = 8787
LOG = "I:/Project/ReinChat/tools/mock_requests.log"

RESPONSE = """## 液态玻璃是怎么形成的？

液态玻璃不是一种材质，而是一种**界面语言**：它把背景里的颜色、光和运动，折射进半透明的表面中。

### 三个关键层次

1. **透**：让底色仍然可感知，而不是盖上一层灰。
2. **折**：用高光、内阴影和细边框暗示表面的弧度。
3. **动**：所有出现与离开都沿着同一条缓动曲线，像液体一样连续。

> 好的界面不会抢走注意力，而是让内容浮起来。

```kotlin
val glass = Modifier
    .clip(RoundedCornerShape(28.dp))
    .background(Color.White.copy(alpha = 0.08f))
    .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
```

如果你愿意，我们可以继续把这套语言延伸到设置、会话列表和图片查看器里。
"""

REASONING = """用户想理解一个概念。我应该先确认概念范围，再用一个直观比喻说明，最后给出关键结论。

- 先解释“透、折、动”三个视觉层次
- 避免堆叠术语
- 用一句总结收束
"""

def tokenize(text, size=4):
    return [text[i:i + size] for i in range(0, len(text), size)]


def last_user_text(body):
    messages = body.get("messages") or []
    for message in reversed(messages):
        if message.get("role") == "user":
            content = message.get("content")
            if isinstance(content, str):
                return content
            if isinstance(content, list):
                return " ".join(str(part.get("text", "")) for part in content if isinstance(part, dict))
    for item in reversed(body.get("input") or []):
        content = item.get("content")
        if isinstance(content, str):
            return content
        if isinstance(content, list):
            return " ".join(str(part.get("text", "")) for part in content if isinstance(part, dict))
    return ""


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print("[mock]", fmt % args, flush=True)

    def _read_body(self):
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            return json.loads(raw.decode("utf-8"))
        except Exception:
            return {}

    def _json(self, code, payload):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _log(self, path, body):
        def strip_images(items):
            cleaned = []
            for item in items or []:
                content = item.get("content")
                if isinstance(content, list):
                    content = [
                        {**part, "image_url": "<data-url>" if part.get("image_url") else None}
                        if part.get("type") == "image_url"
                        else ({**part, "image_url": "<data-url>"} if part.get("type") == "input_image" else part)
                        for part in content
                    ]
                cleaned.append({**item, "content": content})
            return cleaned

        summary = {
            "path": path,
            "model": body.get("model"),
            "stream": body.get("stream"),
            "images": self._image_count(body),
            "messages": strip_images(body.get("messages")),
            "input": strip_images(body.get("input")),
        }
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps(summary, ensure_ascii=False, indent=2) + "\n---\n")

    def do_POST(self):
        body = self._read_body()
        self._log(self.path, body)
        if self.path.endswith("/chat/completions"):
            self._stream_chat(body)
        elif self.path.endswith("/responses"):
            self._stream_responses(body)
        else:
            self._json(404, {"error": {"message": "Unknown endpoint " + self.path}})

    def _image_count(self, body):
        count = 0
        for message in body.get("messages", []) or []:
            content = message.get("content")
            if isinstance(content, list):
                count += sum(1 for part in content if part.get("type") == "image_url")
        for item in body.get("input", []) or []:
            content = item.get("content")
            if isinstance(content, list):
                count += sum(1 for part in content if part.get("type") == "input_image")
        return count

    def _stream_chat(self, body):
        images = self._image_count(body)
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        intro = "我看到了你上传的图片。" if images else ""
        prompt = last_user_text(body).lower()
        if "think" in prompt or "思考" in prompt:
            for chunk in tokenize(REASONING):
                payload = {
                    "id": "chatcmpl-mock-reasoning",
                    "object": "chat.completion.chunk",
                    "choices": [{"index": 0, "delta": {"reasoning_content": chunk}, "finish_reason": None}],
                }
                self.wfile.write(("data: " + json.dumps(payload, ensure_ascii=False) + "\n\n").encode("utf-8"))
                self.wfile.flush()
                time.sleep(0.03)
        for chunk in tokenize(intro + RESPONSE):
            payload = {
                "id": "chatcmpl-mock",
                "object": "chat.completion.chunk",
                "choices": [{"index": 0, "delta": {"content": chunk}, "finish_reason": None}],
            }
            self.wfile.write(("data: " + json.dumps(payload, ensure_ascii=False) + "\n\n").encode("utf-8"))
            self.wfile.flush()
            time.sleep(0.025)
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()

    def _stream_responses(self, body):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        for chunk in tokenize(RESPONSE):
            payload = {
                "type": "response.output_text.delta",
                "delta": chunk,
                "output_index": 0,
                "content_index": 0,
            }
            self.wfile.write(("data: " + json.dumps(payload, ensure_ascii=False) + "\n\n").encode("utf-8"))
            self.wfile.flush()
            time.sleep(0.025)
        self.wfile.write(
            b"data: {\"type\":\"response.completed\",\"response\":{\"status\":\"completed\"}}\n\n"
        )
        self.wfile.flush()


if __name__ == "__main__":
    print(f"Mock API at http://127.0.0.1:{PORT}/v1", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
