"""Ollama 앞에 두는 로깅 프록시. 요청 본문을 가공 없이 그대로 jsonl로 남기고 응답은 그대로 전달한다.

TELME-186. EXAONE 호출이 스트리밍 경로라 앱 호출 기록에 옵션이 남지 않아서, 실제 /api/chat 요청(model, options, format,
think, messages)과 최종 청크의 사용량·종료 사유를 기록한다. 모델을 부르지 않고 Ollama 서버를 대신하지도 않는다.

사용: python3 -I ollama_logging_proxy.py <수신 포트> <업스트림 포트> <로그 jsonl>
"""

import http.client
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LISTEN_PORT, UPSTREAM_PORT, LOG_PATH = int(sys.argv[1]), int(sys.argv[2]), sys.argv[3]
LOCK = threading.Lock()
SEQ = [0]


def append(record):
    with LOCK:
        with open(LOG_PATH, "a", encoding="utf-8") as log:
            log.write(json.dumps(record, ensure_ascii=False) + "\n")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, *args):
        pass

    def _read_body(self):
        # 클라이언트(HttpURLConnection)가 chunked로 보내면 Content-Length가 없다
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            parts = []
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if size == 0:
                    while self.rfile.readline().strip():
                        pass
                    return b"".join(parts)
                parts.append(self.rfile.read(size))
                self.rfile.readline()
        length = int(self.headers.get("Content-Length") or 0)
        return self.rfile.read(length) if length else b""

    def _forward(self):
        body = self._read_body()
        with LOCK:
            SEQ[0] += 1
            seq = SEQ[0]
        started = time.time()
        conn = http.client.HTTPConnection("127.0.0.1", UPSTREAM_PORT, timeout=600)
        headers = {k: v for k, v in self.headers.items() if k.lower() not in ("host", "connection", "transfer-encoding", "content-length", "expect")}
        headers["Content-Length"] = str(len(body))
        conn.request(self.command, self.path, body=body, headers=headers)
        resp = conn.getresponse()
        self.send_response(resp.status)
        for key, value in resp.getheaders():
            if key.lower() not in ("transfer-encoding", "connection", "content-length"):
                self.send_header(key, value)
        self.send_header("Transfer-Encoding", "chunked")
        self.end_headers()
        last_line = b""
        pending = b""
        while True:
            chunk = resp.read1(65536)
            if not chunk:
                break
            self.wfile.write(f"{len(chunk):X}\r\n".encode() + chunk + b"\r\n")
            self.wfile.flush()
            pending += chunk
            lines = pending.split(b"\n")
            pending = lines.pop()
            for line in lines:
                if line.strip():
                    last_line = line
        if pending.strip():
            last_line = pending
        self.wfile.write(b"0\r\n\r\n")
        self.wfile.flush()
        conn.close()
        try:
            request = json.loads(body) if body else None
        except ValueError:
            request = {"unparsed": body.decode("utf-8", "replace")}
        try:
            final = json.loads(last_line)
            final.pop("message", None)
            final.pop("response", None)
        except ValueError:
            final = None
        append({"seq": seq, "startedAt": started, "elapsedSec": round(time.time() - started, 3),
                "method": self.command, "path": self.path, "status": resp.status,
                "request": request, "final": final})

    do_GET = do_POST = do_DELETE = _forward


ThreadingHTTPServer(("127.0.0.1", LISTEN_PORT), Handler).serve_forever()
