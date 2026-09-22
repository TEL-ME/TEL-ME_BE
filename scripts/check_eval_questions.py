#!/usr/bin/env python3

"""검색 품질 평가셋(eval_questions_30.json) 검증"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from dataclasses import dataclass
from pathlib import Path

from check_duplicates import DEFAULT_BATCH, cosine, embed_all

VALID_TYPES = ("SIMILAR", "VARIANT", "UNRELATED")
EXPECTED_COUNT = 30

DEFAULT_FAQ_PATH = Path(__file__).parent / "data" / "faq_sample_30.json"


def content_hash(question: str, answer: str) -> str:
    # scripts/README.md: content_hash = SHA-256(question + answer), 구분자 없음
    return hashlib.sha256((question + answer).encode("utf-8")).hexdigest()


@dataclass
class Finding:
    index: int
    eval_id: str
    kind: str
    detail: str


def load_faq_hashes(faq_path: Path) -> dict[str, dict]:
    faqs = json.loads(faq_path.read_text(encoding="utf-8"))
    return {content_hash(f["question"], f["answer"]): f for f in faqs}


def check_static(items: list[dict], faq_by_hash: dict[str, dict]) -> list[Finding]:
    found: list[Finding] = []

    def add(i: int, item: dict, kind: str, detail: str) -> None:
        found.append(Finding(i, item.get("eval_id", "?"), kind, detail))

    if len(items) != EXPECTED_COUNT:
        found.append(Finding(-1, "-", "건수 불일치", f"{len(items)}건 (기대 {EXPECTED_COUNT}건)"))

    seen_ids: set[str] = set()
    for i, item in enumerate(items):
        eval_id = item.get("eval_id")
        if not eval_id:
            add(i, item, "eval_id 없음", "")
        elif eval_id in seen_ids:
            add(i, item, "eval_id 중복", eval_id)
        else:
            seen_ids.add(eval_id)

        question = item.get("question")
        if not question:
            add(i, item, "question 없음", "")

        item_type = item.get("type")
        if item_type not in VALID_TYPES:
            add(i, item, "알 수 없는 type", str(item_type))
            continue

        expected_hash = item.get("expected_content_hash")

        if item_type == "UNRELATED":
            if expected_hash is not None:
                add(i, item, "UNRELATED인데 expected_content_hash가 있음", str(expected_hash))
            continue

        # SIMILAR / VARIANT
        if not expected_hash:
            add(i, item, "expected_content_hash 없음", f"type={item_type}")
        elif expected_hash not in faq_by_hash:
            add(i, item, "faq_sample_30.json에 없는 해시",
                f"{expected_hash[:12]}... (질문, 답변 수정으로 해시가 바뀌었을 수 있음)")

    return found


def check_live(
    items: list[dict],
    faqs: list[dict],
    batch: int,
    cache: Path | None,
) -> list[Finding]:
    found: list[Finding] = []
    faq_texts = [f["question"] for f in faqs]
    faq_hashes = [content_hash(f["question"], f["answer"]) for f in faqs]

    similar_or_variant = [it for it in items if it.get("type") in ("SIMILAR", "VARIANT")]
    unrelated = [it for it in items if it.get("type") == "UNRELATED"]

    all_texts = faq_texts + [it["question"] for it in items]
    vectors = embed_all(all_texts, batch, cache)
    faq_vectors = vectors[: len(faq_texts)]
    eval_vectors = dict(zip((it["eval_id"] for it in items), vectors[len(faq_texts):]))

    print(f"\n[SIMILAR/VARIANT] {len(similar_or_variant)}건 - 기대 FAQ가 최고 유사도로 나오는지")
    for item in similar_or_variant:
        vec = eval_vectors[item["eval_id"]]
        scored = sorted(
            ((cosine(vec, fv), fh) for fv, fh in zip(faq_vectors, faq_hashes)),
            key=lambda x: -x[0],
        )
        top_score, top_hash = scored[0]
        expected = item.get("expected_content_hash")
        ok = top_hash == expected
        mark = "OK" if ok else "FAIL"
        print(f"  {mark}  [{item['eval_id']}] {item['type']:7s} 최고유사도 {top_score:.4f}"
              f"  {item['question']}")
        if not ok:
            expected_score = next((s for s, h in scored if h == expected), None)
            found.append(Finding(
                -1, item["eval_id"], "기대 FAQ가 최고 유사도가 아님",
                f"1위 해시 {top_hash[:12]}..., 기대 해시 {str(expected)[:12]}... "
                f"(기대 해시 유사도 {expected_score:.4f})" if expected_score is not None
                else f"1위 해시 {top_hash[:12]}..., 기대 해시가 faq 목록에 없음",
            ))

    print(f"\n[UNRELATED] {len(unrelated)}건 - 30건 대비 유사도 분포 (낮을수록 좋음)")
    unrelated_maxes = []
    for item in unrelated:
        vec = eval_vectors[item["eval_id"]]
        scores = [cosine(vec, fv) for fv in faq_vectors]
        peak = max(scores) if scores else 0.0
        unrelated_maxes.append(peak)
        print(f"        [{item['eval_id']}] 최고유사도 {peak:.4f}  {item['question']}")
    if unrelated_maxes:
        avg = sum(unrelated_maxes) / len(unrelated_maxes)
        print(f"\n  UNRELATED 최고유사도 평균 {avg:.4f}, 전체 최댓값 {max(unrelated_maxes):.4f}")

    return found


def report(findings: list[Finding]) -> int:
    if not findings:
        print("정적 검사 통과.")
        return 0
    for f in findings:
        loc = f"[{f.index}] {f.eval_id}" if f.index >= 0 else "-"
        print(f"  {loc}  {f.kind}: {f.detail}")
    print(f"\n{len(findings)}건 발견")
    return 1


# 일부러 틀린 예시를 넣어 검사기가 실제로 잡아내는지 확인
def self_test() -> int:
    faq_by_hash = {
        content_hash("유심 재발급 얼마예요?", "7,700원입니다."): {"slot_id": "USIM-S01"},
    }
    real_hash = next(iter(faq_by_hash))

    items = [
        {"eval_id": "T01", "type": "SIMILAR", "question": "유심 재발급 비용이 얼마인가요?",
         "expected_content_hash": real_hash},                          # 정상
        {"eval_id": "T02", "type": "UNRELATED", "question": "날씨 어때요?",
         "expected_content_hash": None},                               # 정상
        {"eval_id": "T03", "type": "VARIANT", "question": "유심 값이 얼마죠?",
         "expected_content_hash": "0" * 64},                           # FAIL: 존재 안 하는 해시
        {"eval_id": "T04", "type": "UNRELATED", "question": "영화 추천해줘",
         "expected_content_hash": real_hash},                          # FAIL: UNRELATED인데 해시 있음
        {"eval_id": "T01", "type": "SIMILAR", "question": "중복 id",
         "expected_content_hash": real_hash},                          # FAIL: eval_id 중복
    ]
    # 30건 카운트 체크는 이 5건 픽스처에서는 일부러 건너뛴다 (건수 불일치 자체도 findings에 잡히므로 함께 확인)
    findings = check_static(items, faq_by_hash)
    kinds = {f.kind for f in findings}

    expects = {
        "건수 불일치": "건수 불일치" in kinds,
        "faq_sample_30.json에 없는 해시": "faq_sample_30.json에 없는 해시" in kinds,
        "UNRELATED인데 expected_content_hash가 있음": "UNRELATED인데 expected_content_hash가 있음" in kinds,
        "eval_id 중복": "eval_id 중복" in kinds,
    }
    ok = all(expects.values())
    for name, passed in expects.items():
        print(f"  {'OK  검출됨' if passed else 'FAIL 못 잡음'}  {name}")
    return 0 if ok else 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("path", nargs="?", type=Path, help="검사할 eval_questions JSON")
    ap.add_argument("--faq", type=Path, default=DEFAULT_FAQ_PATH,
                    help="정답 매핑 대조 대상 FAQ JSON (기본: faq_sample_30.json)")
    ap.add_argument("--live", action="store_true",
                    help="Ollama로 실제 임베딩해 유사도까지 확인 (정적 검사 통과 후 실행)")
    ap.add_argument("--batch", type=int, default=DEFAULT_BATCH)
    ap.add_argument("--cache",
                    default=str(Path(__file__).parent / "data" / ".embed_cache.json"),
                    help="임베딩 캐시 경로 (--cache '' 로 비활성)")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        return self_test()
    if not args.path:
        ap.error("검사할 JSON 경로 필요 (또는 --self-test)")

    items = json.loads(args.path.read_text(encoding="utf-8"))
    faqs = json.loads(args.faq.read_text(encoding="utf-8"))
    faq_by_hash = load_faq_hashes(args.faq)

    print(f"{args.path} — {len(items)}건, 대조 대상 {args.faq} {len(faqs)}건")
    findings = check_static(items, faq_by_hash)
    static_result = report(findings)

    if not args.live:
        return static_result

    if static_result != 0:
        print("\n정적 검사 실패 — --live 생략 (해시부터 고친 뒤 재실행)")
        return static_result

    cache = Path(args.cache) if args.cache else None
    live_findings = check_live(items, faqs, args.batch, cache)
    if live_findings:
        print()
        return report(live_findings)
    print("\n실측 검사 통과.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
