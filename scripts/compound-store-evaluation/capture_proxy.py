"""라우팅 실측용 모델 호출 기록 프록시. 앱의 OLLAMA_URL을 이 프록시로 두고 실행한다."""
import argparse
import json
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

PARSER = argparse.ArgumentParser(description='앱과 Ollama 사이에서 /api/chat 요청과 원문 응답을 JSONL로 남긴다.')
PARSER.add_argument('--log', type=Path, required=True)
PARSER.add_argument('--port', type=int, default=11436)
PARSER.add_argument('--target', default='http://localhost:11434')
ARGS = PARSER.parse_args()
LOG = ARGS.log
LOCK = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.forward(None)

    def do_POST(self):
        if self.headers.get('Transfer-Encoding', '').lower() == 'chunked':
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b';')[0], 16)
                if size == 0:
                    while self.rfile.readline().strip():
                        pass
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)
            body = b''.join(chunks)
        else:
            body = self.rfile.read(int(self.headers.get('Content-Length', 0)))
        self.forward(body)

    def forward(self, body):
        started = time.monotonic()
        record = {'startedAt': datetime.now(timezone.utc).isoformat(), 'path': self.path}
        if body:
            record['request'] = json.loads(body)
        response_bytes = []
        try:
            request = urllib.request.Request(ARGS.target + self.path, data=body,
                                             headers={'Content-Type': 'application/json'})
            try:
                response = urllib.request.urlopen(request, timeout=240)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                record['status'] = response.status
                self.send_response(response.status)
                self.send_header('Content-Type', response.headers.get('Content-Type', 'application/json'))
                self.end_headers()
                for chunk in response:
                    response_bytes.append(chunk)
                    self.wfile.write(chunk)
                    self.wfile.flush()
            record['rawResponse'] = b''.join(response_bytes).decode('utf-8')
        except Exception as error:
            record['error'] = f'{type(error).__name__}: {error}'
        finally:
            record['elapsedMs'] = round((time.monotonic() - started) * 1000)
            if self.path == '/api/chat':
                with LOCK, LOG.open('a', encoding='utf-8') as output:
                    output.write(json.dumps(record, ensure_ascii=False) + '\n')


LOG.parent.mkdir(parents=True, exist_ok=True)
ThreadingHTTPServer(('127.0.0.1', ARGS.port), Handler).serve_forever()
