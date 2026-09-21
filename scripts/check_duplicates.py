#!/usr/bin/env python3

"""임베딩 중복 탐지"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
import urllib.error
import urllib.request
from pathlib import Path

OLLAMA_URL = "http://localhost:11434/api/embed"
MODEL = "bge-m3"
DIMENSION = 1024
DEFAULT_THRESHOLD = 0.95
# Ollama는 요청을 순차 처리(대량 배치는 검색 임베딩을 큐에서 대기시킴)
DEFAULT_BATCH = 50
TIMEOUT_SEC = 120

try:
    import numpy as _np
except ImportError:  # 없으면 순수 파이썬 경로
    _np = None


def _post(texts: list[str]) -> list[list[float]]:
    payload = json.dumps({"model": MODEL, "input": texts}).encode("utf-8")
    req = urllib.request.Request(
        OLLAMA_URL, data=payload, headers={"Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SEC) as resp:
            body = json.loads(resp.read())
    except urllib.error.URLError as e:
        raise SystemExit(
            f"Ollama 호출 실패: {e}\n"
            f"  {OLLAMA_URL} 기동 여부, `ollama pull {MODEL}` 완료 여부 확인"
        ) from None

    vectors = body.get("embeddings")
    if not vectors or len(vectors) != len(texts):
        raise SystemExit(
            f"임베딩 개수 불일치: 요청 {len(texts)} / 응답 "
            f"{len(vectors) if vectors else 0}"
        )
    for v in vectors:
        if len(v) != DIMENSION:
            raise SystemExit(
                f"차원 {len(v)} ({DIMENSION} 기대). 모델이 {MODEL}인지, "
                f"자바 EmbeddingClient와 같은 설정인지 확인"
            )
    return vectors


def embed_all(texts: list[str], batch: int, cache_path: Path | None) -> list[list[float]]:
    cache: dict[str, list[float]] = {}
    if cache_path and cache_path.exists():
        cache = json.loads(cache_path.read_text(encoding="utf-8"))

    def key(t: str) -> str:
        return hashlib.sha256(f"{MODEL}\n{t}".encode()).hexdigest()

    todo = [t for t in dict.fromkeys(texts) if key(t) not in cache]
    for start in range(0, len(todo), batch):
        chunk = todo[start : start + batch]
        print(f"  임베딩 {start + len(chunk)}/{len(todo)} ...", file=sys.stderr)
        for text, vec in zip(chunk, _post(chunk)):
            cache[key(text)] = vec

    if cache_path and todo:
        cache_path.parent.mkdir(parents=True, exist_ok=True)
        cache_path.write_text(json.dumps(cache), encoding="utf-8")

    return [cache[key(t)] for t in texts]

# 단위 벡터화
def _normalize(vectors: list[list[float]]):
    if _np is not None:
        arr = _np.asarray(vectors, dtype=_np.float32)
        norms = _np.linalg.norm(arr, axis=1, keepdims=True)
        norms[norms == 0] = 1.0
        return arr / norms
    out = []
    for v in vectors:
        n = math.sqrt(sum(x * x for x in v)) or 1.0
        out.append([x / n for x in v])
    return out


def cosine(v1: list[float], v2: list[float]) -> float:
    unit = _normalize([v1, v2])
    return float(sum(x * y for x, y in zip(unit[0], unit[1])))

# 임계값 이상 쌍 목록과 전체 최고 유사도
def similar_pairs(
    vectors: list[list[float]], threshold: float
) -> tuple[list[tuple[int, int, float]], float]:

    unit = _normalize(vectors)
    pairs: list[tuple[int, int, float]] = []
    peak = 0.0
    if _np is not None:
        sim = unit @ unit.T
        rows, cols = _np.triu_indices(len(vectors), k=1)
        upper = sim[rows, cols]
        peak = float(upper.max()) if upper.size else 0.0
        pairs = [(int(a), int(b), float(sim[a, b]))
                 for a, b in zip(rows[upper >= threshold], cols[upper >= threshold])]
    else:
        for a in range(len(unit)):
            for b in range(a + 1, len(unit)):
                score = sum(x * y for x, y in zip(unit[a], unit[b]))
                peak = max(peak, score)
                if score >= threshold:
                    pairs.append((a, b, score))
    return sorted(pairs, key=lambda p: -p[2]), peak

# 유사 쌍 검출 및 무관 쌍 오탐 여부 확인
def self_test(threshold: float, batch: int, cache: Path | None) -> int:
    texts = [
        "유심 재발급 비용이 얼마인가요?",
        "유심 재발급 수수료는 얼마예요?",       # 0과 중복 기대
        "해외 로밍 요금제는 어떻게 신청하나요?",  # 오탐 금지
    ]
    vectors = embed_all(texts, batch, cache)
    found = {(a, b) for a, b, _ in similar_pairs(vectors, threshold)[0]}

    ok_dup = (0, 1) in found
    ok_clean = (0, 2) not in found and (1, 2) not in found
    print(f"  유사 쌍 (0,1) 유사도 {cosine(vectors[0], vectors[1]):.4f} → "
          f"{'OK  검출됨' if ok_dup else 'FAIL 임계값 미만'}")
    print(f"  무관 쌍 (0,2) 유사도 {cosine(vectors[0], vectors[2]):.4f} → "
          f"{'OK  통과' if ok_clean else 'FAIL 오탐'}")
    if not ok_dup:
        print(f"\n  임계값 {threshold}가 이 모델에 비해 높음. --threshold로 조정")
    return 0 if (ok_dup and ok_clean) else 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("path", nargs="?", type=Path, help="검사할 FAQ JSON")
    ap.add_argument("--threshold", type=float, default=DEFAULT_THRESHOLD)
    ap.add_argument("--batch", type=int, default=DEFAULT_BATCH,
                    help="1회 전송 건수")
    ap.add_argument("--field", choices=("question", "both"), default="question",
                    help="question만 볼지, question+answer를 이어 볼지")
    ap.add_argument("--cache",
                    default=str(Path(__file__).parent / "data" / ".embed_cache.json"),
                    help="임베딩 캐시 경로 (--cache '' 로 비활성)")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    cache = Path(args.cache) if args.cache else None

    if args.self_test:
        return self_test(args.threshold, args.batch, cache)
    if not args.path:
        ap.error("검사할 JSON 경로 필요 (또는 --self-test)")

    items = json.loads(args.path.read_text(encoding="utf-8"))
    texts = [
        i["question"] if args.field == "question"
        else f"{i['question']}\n{i['answer']}"
        for i in items
    ]

    print(f"{args.path} — {len(items)}건, 필드 {args.field}, 임계값 {args.threshold}")
    pairs, peak = similar_pairs(embed_all(texts, args.batch, cache), args.threshold)

    if not pairs:
        print(f"중복 없음. 최고 유사도 {peak:.4f} (임계값 {args.threshold})")
        return 0

    for a, b, score in pairs:
        print(f"\n  {score:.4f}  [{a}] {items[a]['category']} {items[a]['question']}"
              f"\n          [{b}] {items[b]['category']} {items[b]['question']}")
    print(f"\n{len(pairs)}쌍이 임계값 이상 (최고 {peak:.4f})")
    return 1


if __name__ == "__main__":
    sys.exit(main())
