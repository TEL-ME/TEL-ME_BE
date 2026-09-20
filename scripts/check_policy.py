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

        allowed = pol.allowed(ref)
        for bad in sorted(extract_numbers(item["answer"]) - allowed):
            add(i, item, "정책에 없는 수치",
                f"답변의 '{bad}'이 {pol.items[ref].sources}의 허용값에 없음 "
                f"(허용: {', '.join(sorted(allowed)) or '없음'})")

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
