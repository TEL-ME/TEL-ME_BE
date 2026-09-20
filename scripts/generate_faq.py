#!/usr/bin/env python3

"""FAQ 조합표 생성"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from telme_docs import DocumentError, Policy, Taxonomy, load_policy, load_taxonomy

# 목표 건수를 15개 조합(질문유형 5 × 페르소나 3)에 균등 배분
def _slots_for_category(
    code: str, tax: Taxonomy, pol: Policy, quota: int
) -> list[dict[str, str]]:

    types = list(tax.question_types)
    personas = list(tax.personas)
    triggers = tax.triggers[code]
    refs = [i.ref for i in pol.by_category(code)]
    if not refs:
        raise DocumentError(f"{code} 카테고리에 정책 항목 없음")

    combos = [(t, p) for t in types for p in personas]
    base, rem = divmod(quota, len(combos))

    slots: list[dict[str, str]] = []
    seq = 0
    for idx, (qtype, persona) in enumerate(combos):
        # 나머지는 앞쪽 조합부터 1건씩
        count = base + (1 if idx < rem else 0)
        for _ in range(count):
            item = pol.items[refs[seq % len(refs)]]
            slots.append(
                {
                    "slot_id": f"{code}-{seq + 1:04d}",
                    "category": code,
                    "question_type": qtype,
                    "persona": persona,
                    "trigger": triggers[seq % len(triggers)],
                    "policy_ref": item.ref,
                    "policy_title": item.title,
                    "policy_key_values": item.key_values,
                }
            )
            seq += 1
    return slots


def build_full(tax: Taxonomy, pol: Policy) -> list[dict[str, str]]:
    slots: list[dict[str, str]] = []
    for code in tax.categories:
        slots += _slots_for_category(code, tax, pol, tax.quotas[code])
    return slots

# 카테고리 균등 소규모 표
def build_sample(tax: Taxonomy, pol: Policy, size: int) -> list[dict[str, str]]:
    n_cat = len(tax.categories)
    if size % n_cat:
        raise SystemExit(f"--sample 값은 카테고리 수({n_cat})의 배수여야 함")
    per_cat = size // n_cat

    types = list(tax.question_types)
    personas = list(tax.personas)
    slots: list[dict[str, str]] = []

    for c, code in enumerate(tax.categories):
        refs = [i.ref for i in pol.by_category(code)]
        triggers = tax.triggers[code]
        for j in range(per_cat):
            i = c * per_cat + j
            item = pol.items[refs[j % len(refs)]]
            slots.append(
                {
                    "slot_id": f"{code}-S{j + 1:02d}",
                    "category": code,
                    "question_type": types[i % len(types)],
                    "persona": personas[i % len(personas)],
                    "trigger": triggers[j % len(triggers)],
                    "policy_ref": item.ref,
                    "policy_title": item.title,
                    "policy_key_values": item.key_values,
                }
            )
    return slots


def print_summary(slots: list[dict[str, str]], tax: Taxonomy, pol: Policy) -> None:
    def tally(key: str) -> dict[str, int]:
        out: dict[str, int] = {}
        for s in slots:
            out[s[key]] = out.get(s[key], 0) + 1
        return out

    by_cat = tally("category")
    print(f"총 {len(slots)}건\n")
    print(f"{'카테고리':<12} {'건수':>5} {'목표':>5} {'정책':>4}")
    print("-" * 30)
    for code in tax.categories:
        print(f"{code:<12} {by_cat.get(code, 0):>5} {tax.quotas[code]:>5} "
              f"{len({s['policy_ref'] for s in slots if s['category'] == code}):>4}")
    print("-" * 30)
    print(f"{'합계':<12} {len(slots):>5} {tax.total_quota:>5}\n")

    for label, key in (("질문유형", "question_type"), ("페르소나", "persona")):
        line = "  ".join(f"{k} {v}" for k, v in tally(key).items())
        print(f"{label}: {line}")

    uncovered = set(pol.items) - {s["policy_ref"] for s in slots}
    if uncovered:
        print(f"\n미인용 정책 항목 {len(uncovered)}개: {sorted(uncovered)}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--sample", type=int, metavar="N",
                    help="N건짜리 균등 표 (카테고리 수의 배수)")
    ap.add_argument("--out", type=Path, help="JSON 출력 경로")
    ap.add_argument("--summary", action="store_true", help="배분 결과 표 출력")
    args = ap.parse_args()

    if not args.out and not args.summary:
        ap.error("--out 또는 --summary 필요")

    tax, pol = load_taxonomy(), load_policy()
    slots = build_sample(tax, pol, args.sample) if args.sample else build_full(tax, pol)

    if args.summary:
        print_summary(slots, tax, pol)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(
            json.dumps(slots, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        print(f"{args.out} — {len(slots)}건")
    return 0


if __name__ == "__main__":
    sys.exit(main())
