"""평가 전용 Ollama 호환 라우터. 앱의 LLM 호출을 받아 근거 판정 요청과 나머지 생성 요청을 서로 다른 모델로 보낸다.

- 근거 판정(EvidenceRelevanceChecker): --judge bedrock이면 Bedrock, chat이면 생성 모델과 같은 곳
- 나머지 생성(라우팅, 문맥 판정, 답변 등): --chat ollama | vllm | bedrock
- 임베딩: 항상 로컬 Ollama

앱은 OLLAMA_URL을 이 라우터 주소로 바꿔 붙인다. 앱 코드는 바꾸지 않는다.
사용: python judge_router.py --chat vllm --judge bedrock --vllm-url http://<EC2>:8000 --log router.jsonl
"""
from __future__ import annotations

import argparse
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

import boto3
from botocore.config import Config

# EvidenceRelevancePromptTemplates.SYSTEM_PROMPT의 첫 문장. 판정 요청만 이 문장으로 시작한다.
JUDGE_MARKER = "당신은 통신사 FAQ 검색 결과를 검수합니다."


def is_judge(source: dict) -> bool:
    return any(m.get("role") == "system" and m.get("content", "").lstrip().startswith(JUDGE_MARKER)
               for m in source.get("messages") or [])


def vllm_payload(source: dict, model: str) -> dict:
    """Ollama /api/chat 요청을 vLLM(OpenAI 호환) 요청으로 바꾼다."""
    options = source.get("options") or {}
    payload = {
        "model": model,
        "messages": source["messages"],
        "stream": bool(source.get("stream")),
        "temperature": options.get("temperature", 0),
        "max_tokens": options.get("num_predict", 512),
        "seed": 0,
    }
    fmt = source.get("format")
    if fmt == "json":
        payload["response_format"] = {"type": "json_object"}
    elif isinstance(fmt, dict):
        payload["response_format"] = {"type": "json_schema",
                                      "json_schema": {"name": "telme_response", "schema": fmt}}
    return payload


def bedrock_request(source: dict, model_id: str) -> dict:
    """Ollama /api/chat 요청을 Bedrock Converse 요청으로 바꾼다."""
    options = source.get("options") or {}
    messages = source["messages"]
    fmt = source.get("format")
    user_text = "\n".join(m["content"] for m in messages if m["role"] == "user")
    if isinstance(fmt, dict):
        # Bedrock outputConfig(json_schema)를 gpt-oss에 쓰면 "{\n{" 같은 깨진 JSON이 나온다(5회 중 5회).
        # 스키마 강제 대신 스키마를 지시문으로 넣고 JSON만 출력하게 한다. 검증은 앱이 그대로 한다
        user_text += "\n\n다음 JSON Schema를 따르는 JSON 객체 하나만 출력하세요.\n" + json.dumps(fmt, ensure_ascii=False)
    elif fmt == "json":
        user_text += "\n\n응답은 유효한 JSON 객체 하나만 출력하세요."
    max_tokens = int(options["num_predict"]) if options.get("num_predict") is not None else 512
    if model_id.startswith("openai.gpt-oss-"):
        # gpt-oss는 내부 추론도 출력 한도를 쓴다. 한도가 작으면 세션 제목처럼 짧은 요청의 출력이 비어 버린다
        max_tokens = max(max_tokens, 512)
    request = {
        "modelId": model_id,
        "messages": [{"role": "user", "content": [{"text": user_text}]}],
        "inferenceConfig": {"temperature": float(options.get("temperature") or 0), "maxTokens": max_tokens},
    }
    if model_id.startswith("openai.gpt-oss-"):
        request["additionalModelRequestFields"] = {"reasoning_effort": "low"}
    system = [m["content"] for m in messages if m["role"] == "system"]
    if system:
        request["system"] = [{"text": "\n".join(system)}]
    return request


class Handler(BaseHTTPRequestHandler):
    def read_body(self) -> bytes:
        if self.headers.get("Transfer-Encoding", "").lower() == "chunked":
            parts = []
            while True:
                size = int(self.rfile.readline().split(b";", 1)[0].strip(), 16)
                if size == 0:
                    while self.rfile.readline().strip():
                        pass
                    break
                parts.append(self.rfile.read(size))
                self.rfile.read(2)
            return b"".join(parts)
        return self.rfile.read(int(self.headers["Content-Length"]))

    def do_GET(self) -> None:
        if self.path in ("/api/tags", "/api/version", "/health"):
            self.reply(200, {"models": [{"name": self.server.vllm_model}], "version": "judge-router"})
        else:
            self.reply(404, {"error": "unknown path"})

    def do_POST(self) -> None:
        if self.path == "/api/embed":
            self.forward_embed()
            return
        if self.path != "/api/chat":
            self.reply(404, {"error": "unknown path"})
            return
        raw = self.read_body()
        source = json.loads(raw)
        judge = is_judge(source)
        kind = "judge" if judge else "chat"
        started = time.monotonic()
        target = "bedrock" if judge and self.server.judge == "bedrock" else self.server.chat
        try:
            if target == "bedrock":
                self.to_bedrock(source, kind, started)
            elif target == "ollama":
                self.to_ollama(raw, kind, started)
            else:
                self.to_vllm(source, kind, started)
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception as error:  # 앱 판정기는 실패하면 통과(fail-open)시킨다. 실패 수를 따로 센다
            self.record(kind, target, started, 502, error=f"{type(error).__name__}: {str(error)[:300]}")
            try:
                self.reply(502, {"error": str(error)[:300]})
            except (BrokenPipeError, ConnectionResetError):
                pass

    def to_bedrock(self, source: dict, kind: str, started: float) -> None:
        request = bedrock_request(source, self.server.bedrock_model)
        if source.get("stream"):
            # Bedrock 스트림을 모두 받은 뒤 Ollama 형식으로 보낸다. 첫 토큰 시간 비교에는 쓰지 않는다
            response = self.server.bedrock.converse_stream(**request)
            chunks, usage = [], {}
            for event in response["stream"]:
                if "contentBlockDelta" in event:
                    text = event["contentBlockDelta"]["delta"].get("text", "")
                    if text:
                        chunks.append(text)
                elif "metadata" in event:
                    usage = event["metadata"].get("usage", {})
            if not chunks:
                raise ValueError("empty Bedrock stream")
            self.stream_ollama(chunks)
            self.record(kind, "bedrock", started, 200, usage=usage)
        else:
            response = self.server.bedrock.converse(**request)
            content = "".join(b.get("text", "") for b in response["output"]["message"]["content"])
            self.reply(200, self.ollama_message(content, True))
            self.record(kind, "bedrock", started, 200, content=content if kind == "judge" else None,
                        usage=response.get("usage", {}))

    def to_ollama(self, raw: bytes, kind: str, started: float) -> None:
        request = Request(self.server.ollama_url + "/api/chat", data=raw, headers={"Content-Type": "application/json"})
        body = []
        with urlopen(request, timeout=240) as upstream:
            self.send_response(upstream.status)
            self.send_header("Content-Type", upstream.headers.get("Content-Type", "application/json"))
            self.end_headers()
            for chunk in iter(lambda: upstream.read(8192), b""):
                body.append(chunk)
                self.wfile.write(chunk)
                self.wfile.flush()
        content = None
        if kind == "judge":
            try:
                content = json.loads(b"".join(body))["message"]["content"]
            except (ValueError, KeyError):
                content = None
        self.record(kind, "ollama", started, 200, content=content)

    def to_vllm(self, source: dict, kind: str, started: float) -> None:
        payload = vllm_payload(source, self.server.vllm_model)
        request = Request(self.server.vllm_url + "/v1/chat/completions", data=json.dumps(payload).encode(),
                          headers={"Content-Type": "application/json"})
        try:
            upstream = urlopen(request, timeout=240)
        except HTTPError as error:
            raise ValueError(f"vLLM HTTP {error.code}: {error.read().decode(errors='replace')[:200]}") from error
        with upstream:
            if payload["stream"]:
                chunks = []
                for line in upstream:
                    if not line.startswith(b"data: ") or line[6:].strip() == b"[DONE]":
                        continue
                    text = json.loads(line[6:])["choices"][0].get("delta", {}).get("content") or ""
                    if text:
                        chunks.append(text)
                self.stream_ollama(chunks)
                self.record(kind, "vllm", started, 200)
            else:
                content = json.load(upstream)["choices"][0]["message"].get("content") or ""
                self.reply(200, self.ollama_message(content, True))
                self.record(kind, "vllm", started, 200, content=content if kind == "judge" else None)

    def forward_embed(self) -> None:
        try:
            request = Request(self.server.ollama_url + "/api/embed", data=self.read_body(),
                              headers={"Content-Type": "application/json"})
            with urlopen(request, timeout=120) as upstream:
                self.reply(upstream.status, json.load(upstream))
        except (HTTPError, URLError) as error:
            self.reply(502, {"error": str(error)})

    def ollama_message(self, content: str, done: bool) -> dict:
        return {"model": self.server.vllm_model, "message": {"role": "assistant", "content": content}, "done": done}

    def stream_ollama(self, chunks: list[str]) -> None:
        self.send_response(200)
        self.send_header("Content-Type", "application/x-ndjson")
        self.end_headers()
        for chunk in chunks:
            self.wfile.write(json.dumps(self.ollama_message(chunk, False), ensure_ascii=False).encode() + b"\n")
        self.wfile.write(json.dumps(self.ollama_message("", True)).encode() + b"\n")
        self.wfile.flush()

    def record(self, kind: str, target: str, started: float, status: int, **extra) -> None:
        row = {"kind": kind, "target": target, "status": status,
               "elapsedMs": round((time.monotonic() - started) * 1000),
               **{k: v for k, v in extra.items() if v is not None}}
        if kind == "judge" and extra.get("content"):
            try:
                row["answerable"] = json.loads(extra["content"]).get("answerable")
            except ValueError:
                row["answerable"] = None
        with self.server.log_lock, self.server.log_path.open("a", encoding="utf-8") as log:
            log.write(json.dumps(row, ensure_ascii=False) + "\n")

    def reply(self, status: int, data: dict) -> None:
        encoded = json.dumps(data, ensure_ascii=False).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, *args) -> None:
        pass


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=11435)
    parser.add_argument("--chat", choices=["ollama", "vllm", "bedrock"], required=True)
    parser.add_argument("--judge", choices=["bedrock", "chat"], required=True)
    parser.add_argument("--vllm-url", default="")
    parser.add_argument("--vllm-model", default="ab-chat", help="vLLM --served-model-name")
    parser.add_argument("--ollama-url", default="http://127.0.0.1:11434")
    parser.add_argument("--profile", default="telme-bedrock-eval")
    parser.add_argument("--region", default="ap-northeast-1", help="서울 리전에는 gpt-oss-120b가 없다")
    parser.add_argument("--bedrock-model", default="openai.gpt-oss-120b-1:0")
    parser.add_argument("--log", type=Path, required=True)
    args = parser.parse_args()
    if args.chat == "vllm" and not args.vllm_url:
        parser.error("--chat vllm에는 --vllm-url이 필요합니다")
    args.log.parent.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.chat, server.judge = args.chat, args.judge
    server.vllm_url, server.vllm_model = args.vllm_url.rstrip("/"), args.vllm_model
    server.ollama_url = args.ollama_url.rstrip("/")
    server.bedrock_model = args.bedrock_model
    server.log_path, server.log_lock = args.log, threading.Lock()
    server.bedrock = boto3.Session(profile_name=args.profile).client(
        "bedrock-runtime", region_name=args.region,
        config=Config(read_timeout=120, connect_timeout=10, retries={"max_attempts": 2}))
    print(f"router ready: chat={args.chat} judge={args.judge} ({args.bedrock_model}, {args.region})", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
