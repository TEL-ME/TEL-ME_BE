#!/usr/bin/env python3

"""검색 품질 측정 — Recall@1/3/5, MRR 산출"""

from __future__ import annotations

import argparse
import json
import math
import re
import socket
import statistics
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

from check_eval_questions import content_hash as _content_hash

DEFAULT_API_URL = "http://localhost:8080/api/v1/faq/search"
DEFAULT_TOP_K = 3
# 서버의 embedding.search-read-timeout(15s)보다 길어야, 서버가 정상 처리 중인 요청을
# 스크립트가 먼저 실패로 판단하는 일이 없다
DEFAULT_TIMEOUT_SEC = 20


@dataclass
class SearchOutcome:
    """긍정 질문의 정답 순위 또는 무관 질문의 결과 없음 여부."""
    question_type: str
    returned_rank: int | None
    has_results: bool
    eval_id: str | None = None
    # 긍정 질문은 매칭된 결과의 score, UNRELATED는 top-1 score(임계값 진단용)
    score: float | None = None


def compute_metrics(
    outcomes: list[SearchOutcome],
    k_values: tuple[int, ...] = (1, 3, 5),
) -> dict[str, float]:
    positives = [o for o in outcomes if o.question_type != "UNRELATED"]
    negatives = [o for o in outcomes if o.question_type == "UNRELATED"]
    total = len(positives)
    if total == 0:
        # 긍정 질문이 0건이면 recall/mrr은 "측정 안 됨"이지 0이 아니다 — 키 자체를 안 넣어서
        # format_experiment_row의 기존 가드가 가짜 0.000 행 대신 명확한 에러를 내게 한다
        metrics: dict[str, float] = {}
        if negatives:
            metrics["unrelated_rejection_rate"] = sum(not o.has_results for o in negatives) / len(negatives)
        return metrics

    metrics: dict[str, float] = {}
    for k in k_values:
        hits = sum(1 for o in positives if o.returned_rank is not None and o.returned_rank <= k)
        metrics[f"recall@{k}"] = hits / total

    reciprocal_ranks = [1 / o.returned_rank if o.returned_rank else 0.0 for o in positives]
    metrics["mrr"] = sum(reciprocal_ranks) / total
    if negatives:
        metrics["unrelated_rejection_rate"] = sum(not o.has_results for o in negatives) / len(negatives)

    return metrics


# 자기 검증: 손으로 계산해둔 입력·기대값으로 compute_metrics() 자체를 검증

SELF_TEST_CASES: list[tuple[str, list[SearchOutcome], dict[str, float]]] = [
    (
        "전부 1등",
        [SearchOutcome("SIMILAR", 1, True), SearchOutcome("VARIANT", 1, True), SearchOutcome("SIMILAR", 1, True)],
        {"recall@1": 1.0, "recall@3": 1.0, "recall@5": 1.0, "mrr": 1.0},
    ),
    (
        "1등 2개, 3등 1개",
        [SearchOutcome("SIMILAR", 1, True), SearchOutcome("VARIANT", 1, True), SearchOutcome("SIMILAR", 3, True)],
        {"recall@1": 2 / 3, "recall@3": 1.0, "recall@5": 1.0, "mrr": (1 + 1 + 1 / 3) / 3},
    ),
    (
        "정답 못 찾음 포함",
        [SearchOutcome("SIMILAR", 1, True), SearchOutcome("VARIANT", None, True), SearchOutcome("SIMILAR", 5, True)],
        {"recall@1": 1 / 3, "recall@3": 1 / 3, "recall@5": 2 / 3, "mrr": (1 + 0 + 1 / 5) / 3},
    ),
    (
        "빈 리스트",
        [],
        {},
    ),
    (
        # 정확히 경계(3등)는 recall@3엔 포함, recall@1엔 미포함 — off-by-one 검출용
        "경계값 3등",
        [SearchOutcome("VARIANT", 3, True)],
        {"recall@1": 0.0, "recall@3": 1.0, "recall@5": 1.0, "mrr": 1 / 3},
    ),
    (
        "무관 질문은 Recall 분모에서 제외",
        [SearchOutcome("SIMILAR", 1, True), SearchOutcome("UNRELATED", None, False),
         SearchOutcome("UNRELATED", None, True)],
        {"recall@1": 1.0, "recall@3": 1.0, "recall@5": 1.0,
         "mrr": 1.0, "unrelated_rejection_rate": 0.5},
    ),
    (
        # recall/mrr 키 자체가 없어야 한다(측정 안 됨과 0을 구분) — self_test()가 키 집합까지 정확히 비교
        "전부 UNRELATED (긍정 질문 없음)",
        [SearchOutcome("UNRELATED", None, False), SearchOutcome("UNRELATED", None, True)],
        {"unrelated_rejection_rate": 0.5},
    ),
]


def self_test() -> int:
    failures = 0
    for n, (label, outcomes, expected) in enumerate(SELF_TEST_CASES, 1):
        actual = compute_metrics(outcomes)
        ok = (set(actual) == set(expected)
              and all(math.isclose(actual[key], value, abs_tol=1e-9) for key, value in expected.items()))
        tail = "" if ok else f" → 실제: {actual}, 기대: {expected}"
        print(f"  {'OK  ' if ok else 'FAIL'} {n}. {label}{tail}")
        failures += 0 if ok else 1

    print(f"\n자기 검증 {len(SELF_TEST_CASES)}건 중 {failures}건 실패")
    return 1 if failures else 0


def load_eval_set(path: Path) -> list[dict]:
    try:
        raw = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise SystemExit(f"{path}: 파일을 읽을 수 없습니다: {exc}") from None
    try:
        items = json.loads(raw)
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path}: JSON 형식이 아닙니다: {exc}") from None
    if not isinstance(items, list) or not items:
        raise SystemExit(f"{path}: 비어 있지 않은 평가 질문 배열이 필요합니다")
    for index, item in enumerate(items, 1):
        if not isinstance(item, dict) or not isinstance(item.get("question"), str) or not item["question"].strip():
            raise SystemExit(f"{path}: {index}번 질문 형식 오류")
        kind = item.get("type")
        expected = item.get("expected_content_hash")
        if kind not in ("SIMILAR", "VARIANT", "UNRELATED"):
            raise SystemExit(f"{path}: {index}번 type 오류: {kind}")
        if kind == "UNRELATED":
            if expected is not None:
                raise SystemExit(f"{path}: {index}번 무관 질문의 정답 해시는 null이어야 합니다")
        elif not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
            raise SystemExit(f"{path}: {index}번 정답 해시 형식 오류")
    return items


def content_hash(result: dict) -> str:
    """check_eval_questions.py(TELME-35)의 SHA-256(question + answer) 규칙을 그대로 재사용한다."""
    try:
        return _content_hash(result["question"], result["answer"])
    except (KeyError, TypeError) as exc:
        raise SystemExit(f"검색 API 응답에 FAQ 질문·답변이 없습니다: {result}") from exc


def search(question: str, top_k: int, api_url: str, timeout: int) -> list[dict]:
    query = urllib.parse.urlencode({"query": question, "topK": top_k})
    req = urllib.request.Request(f"{api_url}?{query}")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = json.loads(resp.read())
    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8", errors="replace")
        raise SystemExit(f"검색 API가 오류 응답을 반환함 (HTTP {e.code}): {error_body}") from None
    except urllib.error.URLError as e:
        raise SystemExit(f"검색 API 호출 실패: {e}\n  {api_url} 기동 여부 확인") from None
    except socket.timeout:
        raise SystemExit(
            f"검색 API 응답 대기 시간 초과(timeout={timeout}s) — "
            f"Ollama 콜드 스타트 등으로 서버가 느릴 수 있습니다: {api_url}"
        ) from None

    # 서버 에러는 "정답 못 찾음"이 아니라 인프라 문제라 조용히 넘기지 않고 바로 중단한다
    if not body.get("isSuccess", False):
        raise SystemExit(f"검색 API가 실패 응답을 반환함: {body}")

    results = body.get("result")
    if not isinstance(results, list):
        raise SystemExit(f"검색 API 결과 형식 오류: {body}")
    return results


def evaluate(eval_items: list[dict], top_k: int, api_url: str, timeout: int) -> list[SearchOutcome]:
    outcomes = []
    for item in eval_items:
        results = search(item["question"], top_k, api_url, timeout)
        eval_id = item.get("eval_id")
        if item["type"] == "UNRELATED":
            scores = [r["score"] for r in results if isinstance(r.get("score"), (int, float))]
            top_score = max(scores) if scores else None
            outcomes.append(SearchOutcome("UNRELATED", None, bool(results), eval_id, top_score))
            continue
        expected_hash = item["expected_content_hash"]
        rank = None
        score = None
        for result in results:
            if content_hash(result) != expected_hash:
                continue
            rank = result.get("searchRank")
            # searchRank는 API 계약상 항상 1..top_k 범위로 와야 한다 — 없거나 범위를 벗어나면
            # "정답 못 찾음"으로 조용히 넘기지 않고 바로 중단한다
            if not isinstance(rank, int) or not 1 <= rank <= top_k:
                raise SystemExit(f"검색 API 응답의 searchRank가 잘못됨(topK={top_k}): {result}")
            score = result.get("score")
            break
        outcomes.append(SearchOutcome(item["type"], rank, bool(results), eval_id, score))
    return outcomes


def find_missed(eval_items: list[dict], outcomes: list[SearchOutcome]) -> tuple[list, list]:
    """(개별 miss한 eval_id 목록, 같은 정답 해시를 공유하는 질문이 전부 못 찾아 적재 누락이 의심되는 eval_id 목록)."""
    missed = [o.eval_id for o in outcomes if o.question_type != "UNRELATED" and o.returned_rank is None]

    hash_to_eval_ids: dict[str, list] = {}
    hash_found: dict[str, bool] = {}
    for item, outcome in zip(eval_items, outcomes):
        if item["type"] == "UNRELATED":
            continue
        h = item["expected_content_hash"]
        hash_to_eval_ids.setdefault(h, []).append(item.get("eval_id"))
        hash_found[h] = hash_found.get(h, False) or outcome.returned_rank is not None

    never_found = [
        eval_id
        for h, eval_ids in hash_to_eval_ids.items() if not hash_found[h]
        for eval_id in eval_ids
    ]
    return missed, never_found


# 튜닝 실험 규칙 문서의 표(실험 | 변경 내용 | Recall@1 | Recall@3 | MRR | 담당)에 그대로 붙여넣을 수 있는 한 줄
def format_experiment_row(experiment: str, change: str, metrics: dict[str, float], owner: str) -> str:
    if "recall@1" not in metrics or "recall@3" not in metrics:
        raise SystemExit(
            "실험 기록표는 recall@1·recall@3가 필요합니다 — "
            "--top-k가 3 미만이거나, 평가셋에 SIMILAR/VARIANT(긍정 질문)가 하나도 없으면 측정되지 않습니다"
        )
    return (
        f"| {experiment} | {change} | {metrics['recall@1']:.3f} | "
        f"{metrics['recall@3']:.3f} | {metrics['mrr']:.3f} | {owner} |"
    )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("path", nargs="?", type=Path, help="평가 질문 JSON")
    ap.add_argument("--top-k", type=int, default=DEFAULT_TOP_K)
    ap.add_argument("--api-url", default=DEFAULT_API_URL)
    ap.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT_SEC,
                    help=f"API 호출 제한 시간(초), 기본 {DEFAULT_TIMEOUT_SEC}")
    ap.add_argument("--self-test", action="store_true", help="계산 로직 자체 검증")
    ap.add_argument("--experiment", help="실험 기록표용 실험 이름(예: 기준선, E3). 주면 표 형식 한 줄도 같이 출력")
    ap.add_argument("--change", default="", help="실험 기록표용 변경 내용 설명")
    ap.add_argument("--owner", default="A", help="실험 기록표용 담당 (기본 A)")
    args = ap.parse_args()

    if args.self_test:
        return self_test()
    if not args.path:
        ap.error("평가 질문 JSON 경로 필요 (또는 --self-test)")
    if not 1 <= args.top_k <= 10:
        ap.error("--top-k는 1~10이어야 합니다")
    if args.timeout <= 0:
        ap.error("--timeout은 0보다 커야 합니다")

    eval_items = load_eval_set(args.path)

    outcomes = evaluate(eval_items, args.top_k, args.api_url, args.timeout)
    # 요청한 topK를 넘는 순위는 애초에 API가 안 돌려주므로, 그 이상의 recall@k는 측정한 게 아니라 표시하지 않는다
    k_values = tuple(k for k in (1, 3, 5) if k <= args.top_k)
    metrics = compute_metrics(outcomes, k_values=k_values)

    print(f"{args.path} — {len(eval_items)}건 평가 (topK={args.top_k})")
    for key, value in metrics.items():
        print(f"  {key}: {value:.3f}")

    # 임계값 캘리브레이션 진단 — SEARCH_SIMILARITY_THRESHOLD=0으로 돌렸을 때만 의미 있다
    hit_scores = [o.score for o in outcomes
                  if o.question_type != "UNRELATED" and o.returned_rank is not None and o.score is not None]
    unrelated_top_scores = [o.score for o in outcomes if o.question_type == "UNRELATED" and o.score is not None]
    if hit_scores:
        print(f"\n  정답 hit score — 최소 {min(hit_scores):.4f}, 중앙값 {statistics.median(hit_scores):.4f}")
    if unrelated_top_scores:
        print(f"  UNRELATED top-1 score — 최댓값 {max(unrelated_top_scores):.4f}")

    missed, never_found = find_missed(eval_items, outcomes)
    if missed:
        print(f"\n  정답 못 찾은 질문(eval_id): {missed}")
    if never_found:
        print(f"  경고: 다음 정답 FAQ는 어느 질문에서도 한 번도 안 나왔습니다(적재 누락 의심) — "
              f"eval_id: {never_found}")

    if args.experiment:
        print()
        print(format_experiment_row(args.experiment, args.change, metrics, args.owner))

    return 0


if __name__ == "__main__":
    sys.exit(main())
