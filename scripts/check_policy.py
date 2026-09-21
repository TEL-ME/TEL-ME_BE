#!/usr/bin/env python3

"""정책 수치 대조"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from pathlib import Path

from telme_docs import Policy, Taxonomy, extract_numbers, load_policy, load_taxonomy

REQUIRED_FIELDS = ("category", "question", "answer", "policy_ref")

# COMPARE 답변은 정책 항목을 둘 이상 인용.
# 대표 항목은 policy_ref에 적고 나머지는 이 필드에(FAQ_TAXONOMY.md 2절)
# 생성, 검증용 메타데이터라 적재 시 제외
EXTRA_REFS_FIELD = "extra_policy_refs"

@dataclass
class Finding:
    index: int
    slot: str
    kind: str
    detail: str


def check(items: list[dict], tax: Taxonomy, pol: Policy) -> list[Finding]:
    found: list[Finding] = []

    def add(i: int, item: dict, kind: str, detail: str) -> None:
        found.append(Finding(i, item.get("slot_id") or item.get("policy_ref", "?"),
                             kind, detail))

    for i, item in enumerate(items):
        missing = [f for f in REQUIRED_FIELDS if not item.get(f)]
        if missing:
            add(i, item, "필수 필드 누락", ", ".join(missing))
            continue

        category, ref = item["category"], item["policy_ref"]

        if category not in tax.categories:
            add(i, item, "알 수 없는 카테고리", category)
            continue
        if ref not in pol.items:
            add(i, item, "알 수 없는 policy_ref", ref)
            continue
        if pol.items[ref].category != category:
            add(i, item, "카테고리-정책 불일치",
                f"{ref}은 {pol.items[ref].category} 항목인데 {category}로 분류됨")

        for field, valid in (("question_type", tax.question_types),
                             ("persona", tax.personas)):
            value = item.get(field)
            if value and value not in valid:
                add(i, item, f"알 수 없는 {field}", value)

        extras: list[str] = []
        for extra in item.get(EXTRA_REFS_FIELD) or []:
            if extra == ref:
                add(i, item, "불필요한 extra_policy_refs", f"{extra}는 이미 policy_ref임")
            elif extra not in pol.items:
                add(i, item, "알 수 없는 extra_policy_refs", extra)
            else:
                extras.append(extra)

        refs = [ref, *extras]
        allowed = pol.allowed(*refs)
        numbers = extract_numbers(item["answer"])
        sources = ", ".join(pol.items[r].sources for r in refs)

        for bad in sorted(numbers - allowed):
            add(i, item, "정책에 없는 수치",
                f"답변의 '{bad}'이 {sources}의 허용값에 없음 "
                f"(허용: {', '.join(sorted(allowed)) or '없음'})")

        # 수치가 없는 항목(구비 서류 등)은 대조할 것이 없으므로 제외
        base = pol.allowed(ref)
        for extra in extras:
            contributed = pol.items[extra].numbers - base
            if contributed and not (contributed & numbers):
                add(i, item, "인용하지 않은 extra_policy_refs",
                    f"{extra}의 수치가 답변에 없음 "
                    f"(추가된 허용값: {', '.join(sorted(contributed))})")

    return found


# 자기 검증: 일부러 틀린 건으로 검출 여부 확인

SELF_TEST_CASES: list[tuple[dict, str]] = [
    (
        {
            "category": "USIM", "policy_ref": "USIM-01",
            "question": "유심 재발급 얼마예요?",
            "answer": "유심 재발급 비용은 8,800원입니다.",  # 실제 7,700원
        },
        "정책에 없는 수치",
    ),
    (
        {
            "category": "USIM", "policy_ref": "ROAMING-01",
            "question": "유심 재발급 얼마예요?",
            "answer": "유심 재발급 비용은 7,700원입니다.",
        },
        "카테고리-정책 불일치",
    ),
    (
        {
            "category": "BILLING", "policy_ref": "BILLING-03",
            "question": "미납하면 언제 정지되나요?",
            # BILLING-02의 값(항목 단위 비교여야만 검출)
            "answer": "납부 기한 후 25일이면 발신이 정지됩니다.",
        },
        "정책에 없는 수치",
    ),
    (
        {
            "category": "PLAN", "policy_ref": "PLAN-05",
            "question": "5G 스탠다드 월정액이요",
            "answer": "5G 스탠다드는 월 65,000원에 데이터 50GB를 제공합니다.",
        },
        "",  # 블록 안 표의 값(통과 기대)
    ),
    (
        {
            "category": "USIM", "policy_ref": "USIM-01",
            "question": "유심 재발급 택배로 받으면 며칠 걸려요?",
            "answer": "택배로 받으시면 2~3 영업일이 걸립니다.",
        },
        "",  # 범위 전개(통과 기대)
    ),
    # COMPARE가 항목을 넘나드는 경우
    (
        {
            "category": "BILLING", "policy_ref": "BILLING-01",
            "question": "요금제 바꾸면 청구가 어떻게 되나요? 납부일도 같이 알려주세요",
            "answer": "요금제는 월 1회 변경할 수 있고 신청일 다음 날 00:00부터 적용됩니다. "
                      "청구서는 매월 10일 발송되고 납부 기한은 매월 25일입니다.",
            "question_type": "COMPARE",
            "extra_policy_refs": ["BILLING-02"],
        },
        "",  # 인용 항목을 밝히면 통과
    ),
    (
        {
            "category": "BILLING", "policy_ref": "BILLING-01",
            "question": "요금제 바꾸면 청구가 어떻게 되나요? 납부일도 같이 알려주세요",
            "answer": "요금제는 월 1회 변경할 수 있고 신청일 다음 날 00:00부터 적용됩니다. "
                      "청구서는 매월 10일 발송되고 납부 기한은 매월 25일입니다.",
            "question_type": "COMPARE",
        },
        "정책에 없는 수치",  # 밝히지 않으면 그대로 걸린다
    ),
    (
        {
            "category": "USIM", "policy_ref": "USIM-01",
            "question": "유심 재발급 얼마예요?",
            "answer": "유심 재발급 비용은 7,700원입니다.",
            "extra_policy_refs": ["BILLING-02"],
        },
        "인용하지 않은 extra_policy_refs",  # 선언만 하고 안 쓰면 검사만 헐거워진다
    ),
    (
        {
            "category": "USIM", "policy_ref": "USIM-01",
            "question": "유심 재발급 얼마예요?",
            "answer": "유심 재발급 비용은 7,700원입니다.",
            "extra_policy_refs": ["USIM-99"],
        },
        "알 수 없는 extra_policy_refs",
    ),
]


def self_test(tax: Taxonomy, pol: Policy) -> int:
    failures = 0
    for n, (case, expected) in enumerate(SELF_TEST_CASES, 1):
        kinds = {f.kind for f in check([case], tax, pol)}
        ok = (expected in kinds) if expected else not kinds
        label = expected or "통과해야 함"
        actual = ", ".join(sorted(kinds)) or "지적 없음"
        tail = "" if ok else f" → 실제: {actual}"
        print(f"  {'OK  ' if ok else 'FAIL'} {n}. {label}{tail}")
        failures += 0 if ok else 1
    print(f"\n자기 검증 {len(SELF_TEST_CASES)}건 중 {failures}건 실패")
    return 1 if failures else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("path", nargs="?", type=Path, help="검사할 FAQ JSON")
    ap.add_argument("--self-test", action="store_true",
                    help="일부러 틀린 건으로 검사기 동작 확인")
    args = ap.parse_args()

    tax, pol = load_taxonomy(), load_policy()

    if args.self_test:
        return self_test(tax, pol)
    if not args.path:
        ap.error("검사할 JSON 경로 필요 (또는 --self-test)")

    items = json.loads(args.path.read_text(encoding="utf-8"))
    findings = check(items, tax, pol)

    print(f"{args.path} — {len(items)}건 검사")
    if not findings:
        print("정책 대조 통과. 답변 수치가 모두 참조 항목의 허용값 안")
        return 0

    for f in findings:
        print(f"\n  [{f.index}] {f.slot}  {f.kind}\n      {f.detail}")
    print(f"\n{len(findings)}건 불일치")
    return 1


if __name__ == "__main__":
    sys.exit(main())
