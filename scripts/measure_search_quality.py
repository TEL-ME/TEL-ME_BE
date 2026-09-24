#!/usr/bin/env python3

"""검색 품질 측정: Recall@1/3/5, MRR 산출"""

from __future__ import annotations

import argparse
import json
import math
import re
import socket
import statistics
import sys
import time
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
    unrelated_kind: str | None = None


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
        # 정확히 경계(3등)는 recall@3엔 포함, recall@1엔 미포함(off-by-one 검출용)
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
        # recall/mrr 키 자체가 없어야 한다(측정 안 됨과 0을 구분) - self_test()가 키 집합까지 정확히 비교
        "전부 UNRELATED (긍정 질문 없음)",
        [SearchOutcome("UNRELATED", None, False), SearchOutcome("UNRELATED", None, True)],
        {"unrelated_rejection_rate": 0.5},
    ),
]


def latency_stats(latencies: list[float]) -> dict[str, float]:
    """초 단위 응답 시간 목록 -> 평균/p95(ms). 빈 목록이면 빈 dict(측정 안 됨과 0 구분)."""
    if not latencies:
        return {}
    sorted_latencies = sorted(latencies)
    p95_index = max(0, math.ceil(0.95 * len(sorted_latencies)) - 1)
    return {
        "mean_ms": statistics.mean(latencies) * 1000,
        "p95_ms": sorted_latencies[p95_index] * 1000,
    }


LATENCY_SELF_TEST_CASES: list[tuple[str, list[float], dict[str, float]]] = [
    ("빈 목록", [], {}),
    ("단건", [0.1], {"mean_ms": 100.0, "p95_ms": 100.0}),
    # 5건 중 p95는 ceil(0.95*5)=5번째(인덱스 4, 1초=1000ms) -> 오름차순 정렬 후 마지막 값
    ("5건", [0.1, 0.2, 0.3, 0.4, 1.0], {"mean_ms": 400.0, "p95_ms": 1000.0}),
]


# 카테고리 분해 자기 검증: (라벨, 평가 항목, 결과, 기대 recall@1, 기대 건수, 기대 제외 건수)
def _item(kind: str, slot_id: str | None) -> dict:
    item = {"type": kind}
    if slot_id is not None:
        item["expected_slot_id"] = slot_id
    return item


CATEGORY_SELF_TEST_CASES: list[tuple[str, list[dict], list[SearchOutcome], dict, dict, int]] = [
    (
        # 무관 질문은 카테고리가 없으므로 집계에서 아예 빠져야 한다
        "카테고리 2종 + UNRELATED",
        [_item("SIMILAR", "BILLING-S01"), _item("VARIANT", "BILLING-S02"),
         _item("SIMILAR", "USIM-S01"), _item("UNRELATED", None)],
        [SearchOutcome("SIMILAR", 1, True), SearchOutcome("VARIANT", None, True),
         SearchOutcome("SIMILAR", 1, True), SearchOutcome("UNRELATED", None, False)],
        {"BILLING": 0.5, "USIM": 1.0},
        {"BILLING": 2, "USIM": 1},
        0,
    ),
    (
        # slot_id가 없는 긍정 질문은 조용히 섞이지 않고 제외 건수로 보고돼야 한다
        "slot_id 없는 긍정 질문",
        [_item("SIMILAR", "PLAN-S01"), _item("SIMILAR", None)],
        [SearchOutcome("SIMILAR", 1, True), SearchOutcome("SIMILAR", 1, True)],
        {"PLAN": 1.0},
        {"PLAN": 1},
        1,
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
    print(f"자기 검증(Recall/MRR) {len(SELF_TEST_CASES)}건 중 {failures}건 실패")

    offset = len(SELF_TEST_CASES)
    for n, (label, items, outcomes, expected_recall, expected_counts, expected_skipped) in enumerate(
            CATEGORY_SELF_TEST_CASES, offset + 1):
        per_category, counts, skipped = metrics_by_category(items, outcomes)
        actual_recall = {name: m["recall@1"] for name, m in per_category.items()}
        ok = (set(actual_recall) == set(expected_recall)
              and all(math.isclose(actual_recall[k], v, abs_tol=1e-9) for k, v in expected_recall.items())
              and counts == expected_counts
              and skipped == expected_skipped)
        tail = "" if ok else f" → 실제: {actual_recall}, {counts}, 제외 {skipped}"
        print(f"  {'OK  ' if ok else 'FAIL'} {n}. {label}{tail}")
        failures += 0 if ok else 1

    latency_failures = 0
    latency_offset = len(SELF_TEST_CASES) + len(CATEGORY_SELF_TEST_CASES)
    for n, (label, latencies, expected) in enumerate(LATENCY_SELF_TEST_CASES, latency_offset + 1):
        actual = latency_stats(latencies)
        ok = (set(actual) == set(expected)
              and all(math.isclose(actual[key], value, abs_tol=1e-9) for key, value in expected.items()))
        tail = "" if ok else f" → 실제: {actual}, 기대: {expected}"
        print(f"  {'OK  ' if ok else 'FAIL'} {n}. {label}{tail}")
        latency_failures += 0 if ok else 1
    print(f"\n자기 검증(지연시간) {len(LATENCY_SELF_TEST_CASES)}건 중 {latency_failures}건 실패")

    total_failures = failures + latency_failures
    total = len(SELF_TEST_CASES) + len(CATEGORY_SELF_TEST_CASES) + len(LATENCY_SELF_TEST_CASES)
    print(f"\n전체 자기 검증 {total}건 중 {total_failures}건 실패")
    return 1 if total_failures else 0


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
            if item.get("unrelated_kind") is not None and not isinstance(item["unrelated_kind"], str):
                raise SystemExit(f"{path}: {index}번 unrelated_kind 형식 오류")
        else:
            # 답변이 사실상 같은 FAQ가 여럿이면 어느 쪽이 나와도 정답이라 배열로 적는다
            hashes = expected if isinstance(expected, list) else [expected]
            if not hashes or not all(isinstance(h, str) and re.fullmatch(r"[0-9a-f]{64}", h) for h in hashes):
                raise SystemExit(f"{path}: {index}번 정답 해시 형식 오류")
    return items


def content_hash(result: dict) -> str:
    """check_eval_questions.py(TELME-35)의 SHA-256(question + answer) 규칙을 그대로 재사용한다."""
    try:
        return _content_hash(result["question"], result["answer"])
    except (KeyError, TypeError) as exc:
        raise SystemExit(f"검색 API 응답에 FAQ 질문·답변이 없습니다: {result}") from exc


def search(question: str, top_k: int, api_url: str, timeout: int) -> tuple[list[dict], float]:
    """(결과, 응답 시간(초))를 반환. 응답 시간은 요청 전송~응답 수신까지만 잰다(JSON 파싱 등은 제외)."""
    query = urllib.parse.urlencode({"query": question, "topK": top_k})
    req = urllib.request.Request(f"{api_url}?{query}")
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read()
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
    elapsed = time.perf_counter() - start
    body = json.loads(raw)

    # 서버 에러는 "정답 못 찾음"이 아니라 인프라 문제라 조용히 넘기지 않고 바로 중단한다
    if not body.get("isSuccess", False):
        raise SystemExit(f"검색 API가 실패 응답을 반환함: {body}")

    results = body.get("result")
    if not isinstance(results, list):
        raise SystemExit(f"검색 API 결과 형식 오류: {body}")
    return results, elapsed


def evaluate(
    eval_items: list[dict],
    top_k: int,
    api_url: str,
    timeout: int,
    raw_sink: list | None = None,
) -> tuple[list[SearchOutcome], list[float]]:
    outcomes = []
    latencies = []
    for item in eval_items:
        results, elapsed = search(item["question"], top_k, api_url, timeout)
        latencies.append(elapsed)
        eval_id = item.get("eval_id")
        if raw_sink is not None:
            raw_sink.append({
                "eval_id": eval_id,
                "type": item["type"],
                "unrelated_kind": item.get("unrelated_kind"),
                "expected_slot_id": item.get("expected_slot_id"),
                "expected_content_hash": item.get("expected_content_hash"),
                "results": [{"rank": r.get("searchRank"), "score": r.get("score"),
                             "content_hash": content_hash(r)} for r in results],
            })
        if item["type"] == "UNRELATED":
            scores = [r["score"] for r in results if isinstance(r.get("score"), (int, float))]
            top_score = max(scores) if scores else None
            outcomes.append(SearchOutcome("UNRELATED", None, bool(results), eval_id, top_score,
                                          item.get("unrelated_kind")))
            continue
        expected = item["expected_content_hash"]
        expected_hashes = set(expected if isinstance(expected, list) else [expected])
        rank = None
        score = None
        for result in results:
            if content_hash(result) not in expected_hashes:
                continue
            rank = result.get("searchRank")
            # searchRank는 API 계약상 항상 1..top_k 범위로 와야 한다 — 없거나 범위를 벗어나면
            # "정답 못 찾음"으로 조용히 넘기지 않고 바로 중단한다
            if not isinstance(rank, int) or not 1 <= rank <= top_k:
                raise SystemExit(f"검색 API 응답의 searchRank가 잘못됨(topK={top_k}): {result}")
            score = result.get("score")
            break
        outcomes.append(SearchOutcome(item["type"], rank, bool(results), eval_id, score))
    return outcomes, latencies


def find_missed(eval_items: list[dict], outcomes: list[SearchOutcome]) -> tuple[list, list]:
    """(개별 miss한 eval_id 목록, 같은 정답 해시를 공유하는 질문이 전부 못 찾은 eval_id 목록).
    후자는 적재 누락인지 검색 실패인지 이 함수만으로는 가릴 수 없다 — content_hash로 faqs를
    직접 조회해야 한다. 후보가 많아질수록 "적재는 됐지만 둘 다 top-k 밖으로 밀려난" 경우가
    드물지 않아, 이 목록을 곧장 "적재 누락"으로 해석하면 오탐이 된다."""
    missed = [o.eval_id for o in outcomes if o.question_type != "UNRELATED" and o.returned_rank is None]

    hash_to_eval_ids: dict[str, list] = {}
    hash_found: dict[str, bool] = {}
    for item, outcome in zip(eval_items, outcomes):
        if item["type"] == "UNRELATED":
            continue
        expected = item["expected_content_hash"]
        # 정답이 배열이면 조합 전체를 하나의 키로 본다(같은 정답군을 노린 질문끼리 묶기 위해)
        key = tuple(sorted(expected)) if isinstance(expected, list) else (expected,)
        hash_to_eval_ids.setdefault(key, []).append(item.get("eval_id"))
        hash_found[key] = hash_found.get(key, False) or outcome.returned_rank is not None

    never_found = [
        eval_id
        for h, eval_ids in hash_to_eval_ids.items() if not hash_found[h]
        for eval_id in eval_ids
    ]
    return missed, never_found


def category_of(item: dict) -> str | None:
    """expected_slot_id의 접두사가 카테고리다 (BILLING-S01 → BILLING), 없으면 None."""
    slot_id = item.get("expected_slot_id")
    if not isinstance(slot_id, str) or "-" not in slot_id:
        return None
    return slot_id.rsplit("-", 1)[0]


def metrics_by_category(
    eval_items: list[dict],
    outcomes: list[SearchOutcome],
    k_values: tuple[int, ...] = (1, 3, 5),
) -> tuple[dict[str, dict[str, float]], dict[str, int], int]:
    """(카테고리별 지표, 카테고리별 건수, slot_id가 없어 집계에서 빠진 긍정 질문 수)."""
    grouped: dict[str, list[SearchOutcome]] = {}
    skipped = 0
    for item, outcome in zip(eval_items, outcomes):
        if item["type"] == "UNRELATED":
            continue
        category = category_of(item)
        if category is None:
            skipped += 1
            continue
        grouped.setdefault(category, []).append(outcome)

    per_category = {name: compute_metrics(group, k_values=k_values) for name, group in sorted(grouped.items())}
    counts = {name: len(group) for name, group in sorted(grouped.items())}
    return per_category, counts, skipped


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
    ap.add_argument("--dump-json", type=Path,
                    help="질문별 top-k 원시 결과(rank·score·content_hash)를 JSON으로 저장. "
                         "임계값·top-k 스윕을 다시 호출하지 않고 오프라인에서 계산할 때 쓴다")
    ap.add_argument("--by-category", action="store_true",
                    help="expected_slot_id 접두사로 묶어 카테고리별 Recall/MRR도 출력")
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

    raw_sink: list | None = [] if args.dump_json else None
    outcomes, latencies = evaluate(eval_items, args.top_k, args.api_url, args.timeout, raw_sink)
    if raw_sink is not None:
        args.dump_json.parent.mkdir(parents=True, exist_ok=True)
        args.dump_json.write_text(json.dumps(
            {"api_url": args.api_url, "top_k": args.top_k, "path": str(args.path), "items": raw_sink},
            ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        print(f"  원시 결과 저장: {args.dump_json} ({len(raw_sink)}건)")
    # 요청한 topK를 넘는 순위는 애초에 API가 안 돌려주므로, 그 이상의 recall@k는 측정한 게 아니라 표시하지 않는다
    k_values = tuple(k for k in (1, 3, 5) if k <= args.top_k)
    metrics = compute_metrics(outcomes, k_values=k_values)

    print(f"{args.path} — {len(eval_items)}건 평가 (topK={args.top_k})")
    for key, value in metrics.items():
        print(f"  {key}: {value:.3f}")

    lat = latency_stats(latencies)
    if lat:
        print(f"\n  응답 지연시간 — 평균 {lat['mean_ms']:.1f}ms, p95 {lat['p95_ms']:.1f}ms (n={len(latencies)})")

    # 임계값 캘리브레이션 진단 — SEARCH_SIMILARITY_THRESHOLD=0으로 돌렸을 때만 의미 있다
    hit_scores = [o.score for o in outcomes
                  if o.question_type != "UNRELATED" and o.returned_rank is not None and o.score is not None]
    unrelated_top_scores = [o.score for o in outcomes if o.question_type == "UNRELATED" and o.score is not None]
    if hit_scores:
        print(f"\n  정답 hit score - 최소 {min(hit_scores):.4f}, 중앙값 {statistics.median(hit_scores):.4f}")
    if unrelated_top_scores:
        print(f"  UNRELATED top-1 score - 최댓값 {max(unrelated_top_scores):.4f}")

    # 무관 질문은 "완전 무관"과 "도메인 인접"의 거부 실패 의미가 달라 종류별로 따로 본다
    kinds = sorted({o.unrelated_kind for o in outcomes if o.question_type == "UNRELATED" and o.unrelated_kind})
    for kind in kinds:
        group = [o for o in outcomes if o.question_type == "UNRELATED" and o.unrelated_kind == kind]
        rejected = sum(1 for o in group if not o.has_results)
        tops = [o.score for o in group if o.score is not None]
        top_text = f", top-1 최댓값 {max(tops):.4f}" if tops else ""
        print(f"  거부율({kind}) {rejected / len(group):.3f} - {rejected}/{len(group)}건{top_text}")

    missed, never_found = find_missed(eval_items, outcomes)
    if missed:
        print(f"\n  정답 못 찾은 질문(eval_id): {missed}")
    if never_found:
        print(f"  참고: 다음 정답 FAQ는 같은 정답을 공유하는 질문(SIMILAR/VARIANT) 모두에서 한 번도 안 나왔습니다 — "
              f"eval_id: {never_found}")
        print("  적재 자체가 안 됐는지, 적재는 됐는데 top-k 밖으로 밀려난 것인지는 이 목록만으로 "
              "가릴 수 없습니다 - content_hash로 faqs를 직접 조회해서 확인하세요.")

    if args.by_category:
        per_category, counts, skipped = metrics_by_category(eval_items, outcomes, k_values=k_values)
        print("\n  카테고리별 (긍정 질문만)")
        header = "  | 카테고리 | 건수 | " + " | ".join(f"recall@{k}" for k in k_values) + " | MRR |"
        print(header)
        print("  |" + "---|" * (len(k_values) + 3))
        for name, category_metrics in per_category.items():
            cells = " | ".join(f"{category_metrics[f'recall@{k}']:.3f}" for k in k_values)
            print(f"  | {name} | {counts[name]} | {cells} | {category_metrics['mrr']:.3f} |")
        if skipped:
            print(f"  (expected_slot_id가 없어 카테고리 집계에서 빠진 긍정 질문 {skipped}건)")

    if args.experiment:
        print()
        print(format_experiment_row(args.experiment, args.change, metrics, args.owner))

    return 0


if __name__ == "__main__":
    sys.exit(main())
