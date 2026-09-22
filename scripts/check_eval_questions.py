#!/usr/bin/env python3

"""검색 품질 평가셋(eval_questions_30.json) 검증"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from check_duplicates import DEFAULT_BATCH, cosine, embed_all

VALID_TYPES = ("SIMILAR", "VARIANT", "UNRELATED")
EXPECTED_COUNT = 30
# SIMILAR/VARIANT 각 10건(카테고리 10종에 1건씩 대칭 커버) + UNRELATED 10건
EXPECTED_PER_TYPE = 10
EXPECTED_PER_CATEGORY = 1  # 카테고리마다 SIMILAR 1건, VARIANT 1건

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
    type_count: Counter[str] = Counter()
    # (category, type) -> 건수. 해시가 유효한 SIMILAR/VARIANT만 센다
    coverage: Counter[tuple[str, str]] = Counter()

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
        type_count[item_type] += 1

        expected_hash = item.get("expected_content_hash")
        expected_slot = item.get("expected_slot_id")

        if item_type == "UNRELATED":
            if expected_hash is not None:
                add(i, item, "UNRELATED인데 expected_content_hash가 있음", str(expected_hash))
            if expected_slot is not None:
                add(i, item, "UNRELATED인데 expected_slot_id가 있음", str(expected_slot))
            continue

        # SIMILAR / VARIANT
        if not expected_hash:
            add(i, item, "expected_content_hash 없음", f"type={item_type}")
            continue
        faq = faq_by_hash.get(expected_hash)
        if faq is None:
            add(i, item, "faq_sample_30.json에 없는 해시",
                f"{expected_hash[:12]}... (질문, 답변 수정으로 해시가 바뀌었을 수 있음)")
            continue

        # expected_slot_id는 사람 확인용 메타데이터지만, 해시가 가리키는 FAQ와 어긋나면
        # 리뷰어가 엉뚱한 FAQ를 보고 판단하게 되므로 대조한다
        if expected_slot != faq.get("slot_id"):
            add(i, item, "expected_slot_id 불일치",
                f"명시 {expected_slot}, 해시가 가리키는 FAQ는 {faq.get('slot_id')}")
        coverage[(faq["category"], item_type)] += 1

    for t in VALID_TYPES:
        if type_count[t] != EXPECTED_PER_TYPE:
            found.append(Finding(-1, "-", "유형별 건수 불일치",
                                 f"{t} {type_count[t]}건 (기대 {EXPECTED_PER_TYPE}건)"))

    categories = sorted({f["category"] for f in faq_by_hash.values()})
    for cat in categories:
        for t in ("SIMILAR", "VARIANT"):
            n = coverage[(cat, t)]
            if n != EXPECTED_PER_CATEGORY:
                found.append(Finding(-1, "-", "카테고리 커버 불일치",
                                     f"{cat} {t} {n}건 (기대 {EXPECTED_PER_CATEGORY}건)"))

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


# check_static()이 낼 수 있는 지적 종류 전부
# self_test 픽스처는 이 각각을 최소 1건씩 유발해야 한다
STATIC_KINDS = (
    "건수 불일치",
    "eval_id 없음",
    "eval_id 중복",
    "question 없음",
    "알 수 없는 type",
    "UNRELATED인데 expected_content_hash가 있음",
    "UNRELATED인데 expected_slot_id가 있음",
    "expected_content_hash 없음",
    "faq_sample_30.json에 없는 해시",
    "expected_slot_id 불일치",
    "유형별 건수 불일치",
    "카테고리 커버 불일치",
)


# 일부러 틀린 예시를 넣어 검사기가 실제로 잡아내는지 확인
def self_test() -> int:
    usim = {"slot_id": "USIM-S01", "category": "USIM"}
    plan = {"slot_id": "PLAN-S01", "category": "PLAN"}
    usim_hash = content_hash("유심 재발급 얼마예요?", "7,700원입니다.")
    plan_hash = content_hash("요금제 종류가 뭐예요?", "5G 4종, LTE 3종, 알뜰 2종입니다.")
    faq_by_hash = {usim_hash: usim, plan_hash: plan}

    items = [
        {"eval_id": "T01", "type": "SIMILAR", "question": "유심 재발급 비용이 얼마인가요?",
         "expected_content_hash": usim_hash, "expected_slot_id": "USIM-S01"},   # 정상
        {"eval_id": "T02", "type": "UNRELATED", "question": "날씨 어때요?",
         "expected_content_hash": None, "expected_slot_id": None},              # 정상
        {"eval_id": "T03", "type": "VARIANT", "question": "유심 값이 얼마죠?",
         "expected_content_hash": "0" * 64, "expected_slot_id": "USIM-S01"},    # 존재 안 하는 해시
        {"eval_id": "T04", "type": "UNRELATED", "question": "영화 추천해줘",
         "expected_content_hash": usim_hash, "expected_slot_id": "USIM-S01"},   # UNRELATED인데 해시·slot_id 있음
        {"eval_id": "T01", "type": "SIMILAR", "question": "중복 id",
         "expected_content_hash": usim_hash, "expected_slot_id": "USIM-S01"},   # eval_id 중복 (+ USIM SIMILAR 2건째)
        {"type": "SIMILAR", "question": "id가 없어요",
         "expected_content_hash": usim_hash, "expected_slot_id": "USIM-S01"},   # eval_id 없음
        {"eval_id": "T06", "type": "SIMILAR", "question": "",
         "expected_content_hash": usim_hash, "expected_slot_id": "USIM-S01"},   # question 없음
        {"eval_id": "T07", "type": "SIMILA", "question": "오타 type",
         "expected_content_hash": usim_hash, "expected_slot_id": "USIM-S01"},   # 알 수 없는 type
        {"eval_id": "T08", "type": "VARIANT", "question": "해시를 빠뜨림",
         "expected_content_hash": None, "expected_slot_id": "USIM-S01"},        # SIMILAR/VARIANT인데 해시 없음
        {"eval_id": "T09", "type": "VARIANT", "question": "slot_id를 잘못 적음",
         "expected_content_hash": plan_hash, "expected_slot_id": "USIM-S01"},   # expected_slot_id 불일치
    ]
    # 10건 픽스처라 "건수 불일치"와 "유형별 건수 불일치"는 자동적으로 걸림
    # "카테고리 커버 불일치"는 USIM SIMILAR가 여러 건이고 PLAN SIMILAR가 0건이라 걸림
    findings = check_static(items, faq_by_hash)
    kinds = {f.kind for f in findings}

    ok = True
    for name in STATIC_KINDS:
        passed = name in kinds
        ok = ok and passed
        print(f"  {'OK  검출됨' if passed else 'FAIL 못 잡음'}  {name}")
    unexpected = kinds - set(STATIC_KINDS)
    if unexpected:
        ok = False
        print(f"  FAIL STATIC_KINDS에 없는 지적 종류: {sorted(unexpected)}")
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

    print(f"{args.path} - {len(items)}건, 대조 대상 {args.faq} {len(faqs)}건")
    findings = check_static(items, faq_by_hash)
    static_result = report(findings)

    if not args.live:
        return static_result

    if static_result != 0:
        print("\n정적 검사 실패 - --live 생략 (해시부터 고친 뒤 재실행)")
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
