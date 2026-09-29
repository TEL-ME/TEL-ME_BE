#!/usr/bin/env python3

"""검색 품질 평가셋(eval_questions_*.json) 검증"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from check_duplicates import DEFAULT_BATCH, cosine, embed_all

# ANSWER: FAQ 질문 변형이 아니라 답변에만 있는 값·용어로 묻는 질문 (TELME-69)
VALID_TYPES = ("SIMILAR", "VARIANT", "ANSWER", "UNRELATED")
VALID_UNRELATED_KINDS = ("OFF_DOMAIN", "ADJACENT", "ADJACENT_HARD")

DEFAULT_FAQ_PATH = Path(__file__).parent / "data" / "faq_sample_30.json"


def content_hash(question: str, answer: str) -> str:
    # scripts/README.md: content_hash = SHA-256(question + answer), 구분자 없음
    return hashlib.sha256((question + answer).encode("utf-8")).hexdigest()


# 정답은 slot_id로 적는다(TELME-73). content_hash는 FAQ 내용을 고치면 바뀌지만 slot_id는 유지된다
# 문자열 하나 또는 배열(답변이 사실상 같은 FAQ가 여럿일 때). 문자열에 set()을 쓰면 글자 집합이 되므로 여기서 맞춘다
def expected_slots(item: dict) -> set[str]:
    value = item.get("expected_slot_id")
    if value is None:
        return set()
    return set(value) if isinstance(value, list) else {value}


# measure_search_quality.py --dump-json 결과를 읽는 스크립트가 공통으로 쓴다
# TELME-73 이전 원시 결과는 결과마다 content_hash만 있고 slot_id가 없다
def require_slot_dump(dump: dict, path: Path) -> None:
    if any("slot_id" not in r for item in dump["items"] for r in item["results"]):
        raise SystemExit(f"{path}: 결과에 slot_id가 없는 예전 원시 결과입니다 - "
                         "measure_search_quality.py --dump-json으로 다시 수집하세요")
    require_filled_slots(dump["items"], [r["slot_id"] for item in dump["items"] for r in item["results"]], path)


# 결과의 slot_id가 전부 null이면 로더를 아직 안 돌린 DB다. 그대로 두면 모든 긍정 질문이 miss로 집계돼
# "설정이 덜 됐다"가 아니라 "검색 품질이 0이다"로 보인다
# 시드·관리자 생성 FAQ는 slot_id가 없는 게 정상이라 개별 null은 통과시키고,
# 정답 slot_id가 있는 문항이 있는데 결과가 전부 null일 때만 멈춘다(eval_smoke.json은 정답 slot_id가 없어 걸리지 않는다)
def require_filled_slots(items: list[dict], result_slots: list[str | None], source: object) -> None:
    if any(expected_slots(item) for item in items) and result_slots and all(s is None for s in result_slots):
        raise SystemExit(f"{source}: 검색 결과의 slot_id가 전부 비어 있습니다 - "
                         "FAQ 적재 로더를 한 번 실행해 slot_id를 채우세요 (scripts/README.md 5절)")


@dataclass
class Finding:
    index: int
    eval_id: str
    kind: str
    detail: str


def load_faq_slots(faq_path: Path) -> dict[str, dict]:
    faqs = json.loads(faq_path.read_text(encoding="utf-8"))
    return {f["slot_id"]: f for f in faqs}


def check_static(items: list[dict], faq_by_slot: dict[str, dict]) -> list[Finding]:
    found: list[Finding] = []

    def add(i: int, item: dict, kind: str, detail: str) -> None:
        found.append(Finding(i, item.get("eval_id", "?"), kind, detail))

    if not items:
        found.append(Finding(-1, "-", "빈 평가셋", "0건"))

    seen_ids: set[str] = set()
    type_count: Counter[str] = Counter()
    # (category, type) -> 건수. 정답 slot_id가 유효한 SIMILAR/VARIANT만 센다
    coverage: Counter[tuple[str, str]] = Counter()
    # ANSWER는 SIMILAR/VARIANT 짝이 없어 대칭 검사에 섞으면 안 되므로 따로 센다
    answer_coverage: Counter[str] = Counter()
    unrelated_kind_count: Counter[str] = Counter()

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
            kind = item.get("unrelated_kind")
            if kind is not None and kind not in VALID_UNRELATED_KINDS:
                add(i, item, "알 수 없는 unrelated_kind", str(kind))
            else:
                unrelated_kind_count[kind or "(없음)"] += 1
            continue

        # SIMILAR / VARIANT / ANSWER - slot_id는 문자열 하나 또는 배열(답변이 사실상 같은 FAQ가 여럿일 때)
        slots = expected_slot if isinstance(expected_slot, list) else [expected_slot]
        if not expected_slot or not all(isinstance(s, str) and s for s in slots):
            add(i, item, "expected_slot_id 없음", f"type={item_type}")
            continue
        if len(set(slots)) != len(slots):
            add(i, item, "expected_slot_id 중복", str(slots))
        missing = [s for s in slots if s not in faq_by_slot]
        if missing:
            add(i, item, "FAQ 파일에 없는 slot_id", f"{missing[0]} (다른 코퍼스 파일과 대조하고 있는지 확인)")
            continue
        matched = [faq_by_slot[s] for s in slots]

        # 배열 전체가 같은 카테고리여야 한다
        # 서로 다른 카테고리가 섞이면 카테고리별 집계가 첫 slot_id에 쏠린다
        matched_categories = sorted({faq["category"] for faq in matched})
        if len(matched_categories) > 1:
            add(i, item, "정답 배열의 카테고리 불일치", ", ".join(matched_categories))
            continue

        # expected_content_hash는 참고용이지만, 적혀 있으면 slot_id가 가리키는 FAQ 내용과 맞아야 한다
        # 어긋나면 FAQ 문장이 바뀌었거나 정답을 잘못 옮긴 것이라 리뷰어가 엉뚱한 FAQ를 보고 판단하게 된다
        if expected_hash is not None:
            hashes = expected_hash if isinstance(expected_hash, list) else [expected_hash]
            matched_hashes = {content_hash(faq["question"], faq["answer"]) for faq in matched}
            if set(hashes) != matched_hashes:
                add(i, item, "expected_content_hash 불일치",
                    f"slot_id {slots}의 FAQ 내용과 다름 (FAQ 문장이 바뀌었는지 확인)")
        if item_type == "ANSWER":
            answer_coverage[matched_categories[0]] += 1
        else:
            coverage[(matched_categories[0], item_type)] += 1

    # 건수를 고정하지 않고 대칭성만
    if type_count["SIMILAR"] != type_count["VARIANT"]:
        found.append(Finding(-1, "-", "유형 불균형",
                             f"SIMILAR {type_count['SIMILAR']}건, VARIANT {type_count['VARIANT']}건"))

    covered = sorted({cat for cat, _ in coverage})
    per_cat = {cat: coverage[(cat, "SIMILAR")] + coverage[(cat, "VARIANT")] for cat in covered}
    if per_cat and len(set(per_cat.values())) > 1:
        detail = ", ".join(f"{c} {n}건" for c, n in sorted(per_cat.items(), key=lambda x: x[1]))
        found.append(Finding(-1, "-", "카테고리 불균형", detail))
    for cat in covered:
        if coverage[(cat, "SIMILAR")] != coverage[(cat, "VARIANT")]:
            found.append(Finding(-1, "-", "카테고리 유형 불균형",
                                 f"{cat} SIMILAR {coverage[(cat, 'SIMILAR')]}건, "
                                 f"VARIANT {coverage[(cat, 'VARIANT')]}건"))

    if answer_coverage and len(set(answer_coverage.values())) > 1:
        detail = ", ".join(f"{c} {n}건" for c, n in sorted(answer_coverage.items(), key=lambda x: x[1]))
        found.append(Finding(-1, "-", "ANSWER 카테고리 불균형", detail))

    kinds = ", ".join(f"{k} {n}건" for k, n in sorted(unrelated_kind_count.items()))
    print(f"  구성: SIMILAR {type_count['SIMILAR']} / VARIANT {type_count['VARIANT']} / "
          f"ANSWER {type_count['ANSWER']} / UNRELATED {type_count['UNRELATED']} ({kinds}), "
          f"카테고리 {len(set(covered) | set(answer_coverage))}종")
    return found


def check_live(
    items: list[dict],
    faqs: list[dict],
    batch: int,
    cache: Path | None,
) -> list[Finding]:
    found: list[Finding] = []
    faq_texts = [f["question"] for f in faqs]
    faq_slots = [f["slot_id"] for f in faqs]

    similar_or_variant = [it for it in items if it.get("type") in ("SIMILAR", "VARIANT")]
    answer_items = [it for it in items if it.get("type") == "ANSWER"]
    unrelated = [it for it in items if it.get("type") == "UNRELATED"]

    all_texts = faq_texts + [it["question"] for it in items]
    vectors = embed_all(all_texts, batch, cache)
    faq_vectors = vectors[: len(faq_texts)]
    eval_vectors = dict(zip((it["eval_id"] for it in items), vectors[len(faq_texts):]))

    print(f"\n[SIMILAR/VARIANT] {len(similar_or_variant)}건 - 기대 FAQ가 최고 유사도로 나오는지")
    for item in similar_or_variant:
        vec = eval_vectors[item["eval_id"]]
        scored = sorted(
            ((cosine(vec, fv), fs) for fv, fs in zip(faq_vectors, faq_slots)),
            key=lambda x: -x[0],
        )
        top_score, top_slot = scored[0]
        # 정답이 배열이면 그중 하나가 1위면 된다
        expected_set = expected_slots(item)
        ok = top_slot in expected_set
        mark = "OK" if ok else "FAIL"
        print(f"  {mark}  [{item['eval_id']}] {item['type']:7s} 최고유사도 {top_score:.4f}"
              f"  {item['question']}")
        if not ok:
            expected_score = next((s for s, slot in scored if slot in expected_set), None)
            found.append(Finding(
                -1, item["eval_id"], "기대 FAQ가 최고 유사도가 아님",
                f"1위 {top_slot}, 기대 {sorted(expected_set)[0]} "
                f"(기대 FAQ 유사도 {expected_score:.4f})" if expected_score is not None
                else f"1위 {top_slot}, 기대 slot_id가 faq 목록에 없음",
            ))

    # ANSWER는 실패로 걸러내지 않고 참고로만 나눈다. 정답 FAQ 질문 유사도는 질문만 임베딩한
    # 검색 점수와 같은 계산이라, 이 값으로 문항을 고르면 평가셋이 그 구성에 불리하게 기운다
    # 1위면 "FAQ 질문으로도 커버됨", 아니면 "답변에만 있음"(질문 벡터 구성의 손실 위험 구간)
    print(f"\n[ANSWER] {len(answer_items)}건 - 참고: 정답 FAQ 질문 유사도 순위 (문항 선별에 쓰지 말 것)")
    covered_ids: list[str] = []
    for item in answer_items:
        vec = eval_vectors[item["eval_id"]]
        scored = sorted(
            ((cosine(vec, fv), fs) for fv, fs in zip(faq_vectors, faq_slots)),
            key=lambda x: -x[0],
        )
        expected_set = expected_slots(item)
        rank, score = next(
            ((r, s) for r, (s, slot) in enumerate(scored, 1) if slot in expected_set), (None, None))
        covered = rank == 1
        if covered:
            covered_ids.append(item["eval_id"])
        rank_text = f"{rank}위 ({score:.4f})" if rank is not None else "목록에 없음"
        group = "FAQ 질문으로도 커버됨" if covered else "답변에만 있음"
        print(f"  [{item['eval_id']}] 정답 FAQ 질문 유사도 {rank_text} - {group}  {item['question']}")
    if answer_items:
        print(f"\n  FAQ 질문으로도 커버됨 {len(covered_ids)}건 / 답변에만 있음 "
              f"{len(answer_items) - len(covered_ids)}건")
        print(f"  커버됨: {covered_ids}")

    print(f"\n[UNRELATED] {len(unrelated)}건 - FAQ {len(faqs)}건 대비 유사도 분포 (낮을수록 좋음)")
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
    "빈 평가셋",
    "eval_id 없음",
    "eval_id 중복",
    "question 없음",
    "알 수 없는 type",
    "UNRELATED인데 expected_content_hash가 있음",
    "UNRELATED인데 expected_slot_id가 있음",
    "expected_slot_id 없음",
    "expected_slot_id 중복",
    "정답 배열의 카테고리 불일치",
    "FAQ 파일에 없는 slot_id",
    "expected_content_hash 불일치",
    "알 수 없는 unrelated_kind",
    "유형 불균형",
    "카테고리 불균형",
    "카테고리 유형 불균형",
    "ANSWER 카테고리 불균형",
)


# 일부러 틀린 예시를 넣어 검사기가 실제로 잡아내는지 확인
def self_test() -> int:
    usim = {"slot_id": "USIM-0001", "category": "USIM", "question": "유심 재발급 얼마예요?", "answer": "7,700원입니다."}
    usim2 = {"slot_id": "USIM-0002", "category": "USIM", "question": "유심 값이 얼마인가요?", "answer": "7,700원입니다."}
    plan = {"slot_id": "PLAN-0001", "category": "PLAN",
            "question": "요금제 종류가 뭐예요?", "answer": "5G 4종, LTE 3종, 알뜰 2종입니다."}
    faq_by_slot = {f["slot_id"]: f for f in (usim, usim2, plan)}
    usim_hash = content_hash(usim["question"], usim["answer"])
    plan_hash = content_hash(plan["question"], plan["answer"])

    items = [
        {"eval_id": "T01", "type": "SIMILAR", "question": "유심 재발급 비용이 얼마인가요?",
         "expected_content_hash": usim_hash, "expected_slot_id": "USIM-0001"},     # 정상
        {"eval_id": "T02", "type": "UNRELATED", "question": "날씨 어때요?",
         "expected_content_hash": None, "expected_slot_id": None},                # 정상
        {"eval_id": "T03", "type": "VARIANT", "question": "유심 값이 얼마죠?",
         "expected_slot_id": "USIM-9999"},                                        # 존재 안 하는 slot_id
        {"eval_id": "T04", "type": "UNRELATED", "question": "영화 추천해줘",
         "expected_content_hash": usim_hash, "expected_slot_id": "USIM-0001"},     # UNRELATED인데 해시·slot_id 있음
        {"eval_id": "T01", "type": "SIMILAR", "question": "중복 id",
         "expected_slot_id": "USIM-0001"},                                        # eval_id 중복 (+ USIM SIMILAR 2건째)
        {"type": "SIMILAR", "question": "id가 없어요", "expected_slot_id": "USIM-0001"},   # eval_id 없음
        {"eval_id": "T06", "type": "SIMILAR", "question": "", "expected_slot_id": "USIM-0001"},  # question 없음
        {"eval_id": "T07", "type": "SIMILA", "question": "오타 type", "expected_slot_id": "USIM-0001"},  # 알 수 없는 type
        {"eval_id": "T08", "type": "VARIANT", "question": "slot_id를 빠뜨림",
         "expected_content_hash": usim_hash, "expected_slot_id": None},           # SIMILAR/VARIANT인데 slot_id 없음
        {"eval_id": "T09", "type": "VARIANT", "question": "해시를 다른 FAQ 것으로 적음",
         "expected_content_hash": usim_hash, "expected_slot_id": "PLAN-0001"},     # expected_content_hash 불일치
        {"eval_id": "T10", "type": "UNRELATED", "question": "종류 오타",
         "unrelated_kind": "ADJACENT_HAD",                                        # 알 수 없는 unrelated_kind
         "expected_content_hash": None, "expected_slot_id": None},
        {"eval_id": "T11", "type": "SIMILAR", "question": "같은 slot_id를 두 번 적음",
         "expected_slot_id": ["USIM-0001", "USIM-0001"]},                        # 배열 안 중복
        {"eval_id": "T12", "type": "VARIANT", "question": "배열에 다른 카테고리가 섞임",
         "expected_slot_id": ["USIM-0001", "PLAN-0001"]},                        # 카테고리 불일치
        {"eval_id": "T13", "type": "SIMILAR", "question": "같은 카테고리 복수 정답",
         "expected_slot_id": ["USIM-0001", "USIM-0002"]},                        # 정상 (해시 없이 slot_id만)
        {"eval_id": "T14", "type": "ANSWER", "question": "7,700원이 유심값인가요?",
         "expected_slot_id": ["USIM-0001", "USIM-0002"]},
        {"eval_id": "T15", "type": "ANSWER", "question": "7,700원 내라는데 재발급 비용이에요?",
         "expected_content_hash": [usim_hash], "expected_slot_id": ["USIM-0001"]},  # 정상 (배열 해시도 대조)
        {"eval_id": "T16", "type": "ANSWER", "question": "알뜰 2종은 뭐예요?",
         "expected_slot_id": "PLAN-0001"},
    ]
    # 픽스처가 SIMILAR 6 / VARIANT 4라 "유형 불균형"이 걸리고,
    # USIM만 여러 건이고 PLAN은 1건이라 "카테고리 불균형"과 "카테고리 유형 불균형"도 걸린다
    # ANSWER는 USIM 2건 / PLAN 1건이라 "ANSWER 카테고리 불균형"이 걸린다
    findings = check_static(items, faq_by_slot)
    findings += check_static([], faq_by_slot)   # 빈 평가셋
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
    # 정상으로 적은 문항(T01, T13, T15)은 지적이 없어야 한다
    false_alarms = sorted({f.eval_id for f in findings if f.eval_id in ("T13", "T15")}
                          | {f.eval_id for f in findings if f.eval_id == "T01" and f.kind != "eval_id 중복"})
    if false_alarms:
        ok = False
        print(f"  FAIL 정상 문항 오탐: {false_alarms}")

    # 보강 평가셋처럼 ANSWER + UNRELATED만 있는 균형 잡힌 파일은 아무 지적도 없어야 한다
    # (SIMILAR/VARIANT 0건을 불균형으로 오탐하지 않는지)
    clean = [
        {"eval_id": "C01", "type": "ANSWER", "question": "7,700원이 유심값인가요?",
         "expected_content_hash": usim_hash, "expected_slot_id": "USIM-0001"},
        {"eval_id": "C02", "type": "ANSWER", "question": "알뜰 2종은 뭐예요?",
         "expected_content_hash": plan_hash, "expected_slot_id": "PLAN-0001"},
        {"eval_id": "C03", "type": "UNRELATED", "unrelated_kind": "ADJACENT_HARD",
         "question": "유심 재발급 비용은 카드로 결제할 수 있나요?",
         "expected_content_hash": None, "expected_slot_id": None},
    ]
    clean_findings = check_static(clean, faq_by_slot)
    clean_ok = not clean_findings
    ok = ok and clean_ok
    tail = "" if clean_ok else f" → {[f.kind for f in clean_findings]}"
    print(f"  {'OK  지적 없음' if clean_ok else 'FAIL 오탐'}  ANSWER + UNRELATED 정상 평가셋{tail}")

    checks = [
        (expected_slots({"expected_slot_id": "USIM-0001"}), {"USIM-0001"}),  # 문자열이 글자 집합이 되지 않는다
        (expected_slots({"expected_slot_id": ["USIM-0001", "USIM-0002"]}), {"USIM-0001", "USIM-0002"}),
        (expected_slots({"expected_slot_id": None}), set()),
    ]
    for got, want in checks:
        passed = got == want
        ok = ok and passed
        print(f"  {'OK  ' if passed else 'FAIL'} expected_slots → {got}")

    # (라벨, 평가 항목, 결과 slot_id, 멈춰야 하는지)
    scored = [{"type": "SIMILAR", "expected_slot_id": "USIM-0001"}, {"type": "UNRELATED"}]
    smoke = [{"type": "SIMILAR", "expected_content_hash": usim_hash}]
    fill_cases = [
        ("정답 slot_id가 있는데 결과가 전부 null → 로더 미실행", scored, [None, None, None], True),
        ("정답 slot_id 없는 smoke + 시드 DB(전부 null) → 정상", smoke, [None, None], False),
        ("일부만 null(시드·관리자 생성 FAQ 섞임) → 정상", scored, ["USIM-0001", None], False),
        ("결과가 하나도 없음 → 정상(거부율 측정 등)", scored, [], False),
    ]
    for label, fill_items, slots, should_stop in fill_cases:
        try:
            require_filled_slots(fill_items, slots, "self-test")
            stopped = False
        except SystemExit:
            stopped = True
        passed = stopped == should_stop
        ok = ok and passed
        print(f"  {'OK  ' if passed else 'FAIL'} require_filled_slots {label}")
    return 0 if ok else 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("path", nargs="?", type=Path, help="검사할 eval_questions JSON")
    ap.add_argument("--faq", type=Path, default=DEFAULT_FAQ_PATH,
                    help="정답 slot_id 대조 대상 FAQ JSON (기본: faq_sample_30.json)")
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
    faq_by_slot = load_faq_slots(args.faq)

    print(f"{args.path} - {len(items)}건, 대조 대상 {args.faq} {len(faqs)}건")
    findings = check_static(items, faq_by_slot)
    static_result = report(findings)

    if not args.live:
        return static_result

    if static_result != 0:
        print("\n정적 검사 실패 - --live 생략 (정답 slot_id부터 고친 뒤 재실행)")
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
