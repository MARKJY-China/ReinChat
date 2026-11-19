#!/usr/bin/env python3
"""Mock ReinChat cloud server for the Android OCR / tool-timeline probe.

It implements only the endpoints the debug OCR probe touches:

    POST /api/v1/pdf/ocr                 returns canned OCR text
    POST /api/v1/relay/jobs              returns a job id
    GET  /api/v1/relay/jobs/<id>/events  replays thinking + web_search/fetch_url + answer
    ... plus the auth / provider / conversation endpoints ChatViewModel syncs.

Every request is appended to ``tools/mock_cloud_ocr_requests.log`` so the relay
POST body can be inspected for ``pdfText``.

Run:

    python tools/mock_cloud_ocr_server.py --port 8788

Then inside the emulator use ``http://10.0.2.2:8788`` as the cloud server:

    adb shell am start -n com.reinchat.app.debug/com.reinchat.app.DebugSeedActivity \
      --ez demo_cloud true --es cloud_server http://10.0.2.2:8788
    adb shell am start -n com.reinchat.app.debug/com.reinchat.app.DebugOcrProbeActivity
    adb logcat -s OcrProbe
"""
import argparse
import json
import os
import sys
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "mock_cloud_ocr_requests.log")

OCR_TEXT = (
    "--- 第 1 页 ---\n"
    "液态玻璃的三个层次：透光、折射、留白。透光保留底色，折射用高光与内阴影"
    "暗示表面弧度，留白让内容浮起来。\n"
    "--- 第 2 页 ---\n"
    "设计评审结论：减少边框、放大留白、只保留一处强调色。"
)

REASONING_A = "先看附件 PDF 的 OCR 内容，确认三个层次，再决定要不要联网补充。"
SEARCH_DETAIL = "液态玻璃设计原则\n1. 透光：保留底层色彩\n2. 折射：高光与内阴影\n3. 留白：让内容浮起来"
REASONING_B = "搜索结果与 PDF 一致，再验证一个具体网页案例。"
BROWSE_DETAIL = "读取 https://example.com/glass 失败：连接超时（10s）"
REASONING_C = "网页打不开，按 PDF 和搜索资料，用「透、折、动」组织回答。"
ANSWER = (
    "根据附件 PDF 的 OCR 内容，液态玻璃可以拆成三个层次：\n\n"
    "1. **透光**：保留底层颜色，而不是盖上一层灰。\n"
    "2. **折射**：用高光、内阴影和细边框暗示表面弧度。\n"
    "3. **留白**：让内容浮起来，界面不抢注意力。\n\n"
    "搜索资料与 PDF 一致；网页案例读取失败，因此回答以 PDF 与搜索结果为准。"
)

PROVIDER = {
    "id": "probe-provider",
    "name": "Probe Provider",
    "protocol": "CHAT_COMPLETIONS",
    "baseUrl": "http://10.0.2.2:9999/v1",
    "apiKey": "sk-probe",
    "model": "probe-model",
    "models": [{"id": "probe-model", "name": "probe-model"}],
    "systemPrompt": "",
    "temperature": None,
    "maxTokens": None,
}

CONVERSATIONS = {}
LOCK = threading.Lock()


def log_request(method, path, extra=""):
    line = f"{time.strftime('%H:%M:%S')} {method} {path}"
    if extra:
        line += f" {extra}"
    print("[mock-cloud]", line, flush=True)
    with open(LOG_PATH, "a", encoding="utf-8") as handle:
        handle.write(line + "\n")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        # Keep stderr quiet; log_request already records what matters.
        pass

    # ---------- helpers ----------
    def _read_raw(self):
        length = int(self.headers.get("Content-Length", "0"))
        return self.rfile.read(length) if length else b""

    def _read_json(self):
        raw = self._read_raw()
        try:
            return json.loads(raw.decode("utf-8")), raw
        except Exception:
            return {}, raw

    def _json(self, code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        self.wfile.flush()

    def _empty(self, code=204):
        self.send_response(code)
        self.send_header("Content-Length", "0")
        self.end_headers()
        self.wfile.flush()

    def _sse_start(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache, no-transform")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.flush()

    def _sse(self, payload, event="relay"):
        data = json.dumps(payload, ensure_ascii=False)
        self.wfile.write(f"event: {event}\ndata: {data}\n\n".encode("utf-8"))
        self.wfile.flush()

    def _not_found(self):
        self._json(404, {"error": {"code": "not_found", "message": "not found"}})

    # ---------- routing ----------
    def do_GET(self):
        path = self.path.split("?", 1)[0]
        log_request("GET", path)
        if path == "/api/v1/auth/me":
            self._json(200, {"user": {"username": "probe", "level": 2}})
            return
        if path == "/api/v1/events":
            self._cloud_events()
            return
        if path == "/api/v1/providers":
            self._json(200, {"providers": [PROVIDER]})
            return
        if path == "/api/v1/relay/providers":
            self._json(200, {"providers": [PROVIDER]})
            return
        if path == "/api/v1/conversations":
            with LOCK:
                summaries = [
                    {
                        "id": item["id"],
                        "title": item.get("title", ""),
                        "createdAt": item.get("createdAt", 0),
                        "updatedAt": item.get("updatedAt", 0),
                        "version": item.get("version", 1),
                        "messageCount": len(item.get("messages", [])),
                        "preview": "",
                    }
                    for item in CONVERSATIONS.values()
                ]
            self._json(200, {"conversations": summaries})
            return
        if path.startswith("/api/v1/conversations/"):
            conversation_id = path.rsplit("/", 1)[-1]
            with LOCK:
                item = CONVERSATIONS.get(conversation_id)
            if item is None:
                self._not_found()
            else:
                self._json(200, item)
            return
        if path == "/api/v1/relay/jobs/probe-job/events":
            self._relay_events()
            return
        self._not_found()

    def do_POST(self):
        path = self.path.split("?", 1)[0]
        if path == "/api/v1/auth/login":
            body, _ = self._read_json()
            log_request("POST", path, f"user={body.get('username', '')}")
            self._json(
                200,
                {
                    "token": "probe-token",
                    "expiresAt": int(time.time() * 1000) + 7 * 24 * 60 * 60 * 1000,
                    "user": {"username": body.get("username") or "probe", "level": 2},
                },
            )
            return
        if path == "/api/v1/auth/logout":
            log_request("POST", path)
            self._empty()
            return
        if path == "/api/v1/pdf/ocr":
            raw = self._read_raw()
            log_request("POST", path, f"multipart_bytes={len(raw)}")
            self._json(200, {"text": OCR_TEXT, "pages": 2})
            return
        if path == "/api/v1/relay/jobs":
            body, raw = self._read_json()
            with open(LOG_PATH.replace(".log", "-relay-body.json"), "w", encoding="utf-8") as handle:
                json.dump(body, handle, ensure_ascii=False, indent=2)
            pdf_texts = [
                len(str(message.get("pdfText") or ""))
                for message in body.get("messages", [])
                if message.get("pdfText")
            ]
            log_request(
                "POST",
                path,
                f"messages={len(body.get('messages', []))} pdfTextChars={pdf_texts}",
            )
            self._json(200, {"id": "probe-job", "status": "running"})
            return
        if path == "/api/v1/relay/jobs/probe-job/cancel":
            log_request("POST", path)
            self._empty()
            return
        if path == "/api/v1/conversations":
            body, _ = self._read_json()
            conversation_id = body.get("id") or str(uuid.uuid4())
            stored = dict(body)
            stored["id"] = conversation_id
            stored["version"] = 1
            with LOCK:
                CONVERSATIONS[conversation_id] = stored
            log_request("POST", path, f"id={conversation_id} messages={len(stored.get('messages', []))}")
            self._json(201, stored)
            return
        if path.startswith("/api/v1/conversations/") and path.endswith("/title"):
            self._read_raw()
            log_request("POST", path)
            self._json(200, {"title": "PDF OCR 与工具时间线"})
            return
        self._not_found()

    def do_PUT(self):
        path = self.path.split("?", 1)[0]
        if path.startswith("/api/v1/providers/"):
            body, _ = self._read_json()
            log_request("PUT", path)
            self._json(200, body)
            return
        if path.startswith("/api/v1/conversations/"):
            conversation_id = path.rsplit("/", 1)[-1]
            body, _ = self._read_json()
            with LOCK:
                current = CONVERSATIONS.get(conversation_id, {})
                stored = dict(current)
                stored.update(body)
                stored["id"] = conversation_id
                stored["version"] = int(body.get("baseVersion", 1)) + 1
                CONVERSATIONS[conversation_id] = stored
            log_request(
                "PUT",
                path,
                f"messages={len(stored.get('messages', []))} segments="
                f"{sum(len(m.get('segments') or []) for m in stored.get('messages', []))}",
            )
            self._json(200, stored)
            return
        self._not_found()

    def do_DELETE(self):
        path = self.path.split("?", 1)[0]
        log_request("DELETE", path)
        self._empty()

    # ---------- streaming ----------
    def _cloud_events(self):
        self._sse_start()
        try:
            while True:
                self.wfile.write(b": ping\n\n")
                self.wfile.flush()
                time.sleep(15)
        except (BrokenPipeError, ConnectionResetError):
            return

    def _relay_events(self):
        self._sse_start()
        steps = [
            (0.0, {"type": "snapshot", "status": "running", "tools": []}),
            (0.8, {"type": "reasoning", "text": REASONING_A}),
            (1.0, {"type": "tool", "tool": "web_search", "status": "running", "detail": "液态玻璃 设计原则"}),
            (1.3, {"type": "tool", "tool": "web_search", "status": "done", "detail": SEARCH_DETAIL}),
            (1.0, {"type": "reasoning", "text": REASONING_B}),
            (1.0, {"type": "tool", "tool": "fetch_url", "status": "running", "detail": "https://example.com/glass"}),
            (1.3, {"type": "tool", "tool": "fetch_url", "status": "failed", "detail": BROWSE_DETAIL}),
            (0.9, {"type": "reasoning", "text": REASONING_C}),
            (0.6, {"type": "delta", "text": ANSWER[: len(ANSWER) // 3]}),
            (0.4, {"type": "delta", "text": ANSWER[len(ANSWER) // 3: 2 * len(ANSWER) // 3]}),
            (0.4, {"type": "delta", "text": ANSWER[2 * len(ANSWER) // 3:]}),
            (0.3, {"type": "status", "status": "completed"}),
        ]
        try:
            for delay, payload in steps:
                time.sleep(delay)
                self._sse(payload)
        except (BrokenPipeError, ConnectionResetError):
            return


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8788)
    args = parser.parse_args()
    open(LOG_PATH, "w", encoding="utf-8").close()
    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print(f"[mock-cloud] listening on http://0.0.0.0:{args.port} (log: {LOG_PATH})", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    sys.exit(main())
