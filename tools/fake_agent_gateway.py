#!/usr/bin/env python3
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = json.loads(self.rfile.read(length) or b"{}")
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(("event: session\ndata: " + json.dumps({"sessionId": body.get("sessionId", "fake")}) + "\n\n").encode())
        self.wfile.write(("event: frame\ndata: " + json.dumps({"type": "text-delta", "text": "DSH "}) + "\n\n").encode())
        self.wfile.write(("event: frame\ndata: " + json.dumps({"type": "text-delta", "text": "proxy works"}) + "\n\n").encode())
        self.wfile.write(("event: done\ndata: {}\n\n").encode())
        self.wfile.flush()


ThreadingHTTPServer(("127.0.0.1", 18093), Handler).serve_forever()
