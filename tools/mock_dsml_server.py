#!/usr/bin/env python3
"""Mock DeepSeek-style DSML tool-call output, then a normal final SSE answer."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DSML = """< | | DSML | | calls>
< | | DSML | | invoke name="web_search">
< | | DSML | | parameter name="query" string="true">玄铁 语言 编译器 达摩院</ | | DSML | | parameter>
</ | | DSML | | invoke>
</ | | DSML | | calls>"""


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        if not body.get("stream"):
            payload = {"choices": [{"message": {"role": "assistant", "content": DSML, "reasoning_content": DSML}}]}
            raw = json.dumps(payload, ensure_ascii=False).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(raw)))
            self.end_headers()
            self.wfile.write(raw)
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        for chunk in ("已执行联网检索，", "这是最终回答。"):
            data = json.dumps({"choices": [{"delta": {"content": chunk}}]}, ensure_ascii=False)
            self.wfile.write(("data: " + data + "\n\n").encode())
            self.wfile.flush()
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8789), Handler).serve_forever()
