#!/usr/bin/env python3
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = r"I:\Project\ReinChat\temp_fake_openai.log"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        with open(LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps({"model": body.get("model"), "messages": body.get("messages", [])}, ensure_ascii=False) + "\n")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        for text in ("收到。", "这是第", "二轮回答。" if "第二轮" in str(body) else "回答。"):
            chunk = {"choices": [{"delta": {"content": text}}]}
            self.wfile.write(("data: " + json.dumps(chunk, ensure_ascii=False) + "\n\n").encode())
            self.wfile.flush()
        finish = {"choices": [{"delta": {}, "finish_reason": "stop"}]}
        self.wfile.write(("data: " + json.dumps(finish, ensure_ascii=False) + "\n\n").encode())
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()


ThreadingHTTPServer(("127.0.0.1", 8796), Handler).serve_forever()
