#!/usr/bin/env python3
"""Slow tool-calling mock: initial non-stream tool call, then a 10s SSE answer."""
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DSML = """< | | DSML | | calls>
< | | DSML | | invoke name="web_search">
< | | DSML | | parameter name="query" string="true">ReinChat 流式测试</ | | DSML | | parameter>
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
            payload = {"choices": [{"message": {"role": "assistant", "content": DSML}}]}
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
        for index in range(12):
            data = json.dumps({"choices": [{"delta": {"content": "流式输出第%d段。" % (index + 1)}}]}, ensure_ascii=False)
            self.wfile.write(("data: " + data + "\n\n").encode())
            self.wfile.flush()
            time.sleep(0.55)
        self.wfile.write(b"data: [DONE]\n\n")
        self.wfile.flush()


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 8790), Handler).serve_forever()
