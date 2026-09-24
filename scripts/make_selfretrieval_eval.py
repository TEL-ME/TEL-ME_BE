#!/usr/bin/env python3

"""자기검색 평가셋 생성: FAQ의 question을 그대로 쿼리로 써서 자기 자신을 찾는지 본다"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from check_eval_questions import content_hash

DEFAULT_SOURCE = Path(__file__).parent / "data" / "faq_full_1150.json"
DEFAULT_OUT = Path(__file__).parent / "data" / "eval_selfretrieval_1150.json"


def build(faqs: list[dict]) -> list[dict]:
    items = []
    seen_hashes = set()
    for index, faq in enumerate(faqs, 1):
        for key in ("question", "answer", "slot_id"):
            if not isinstance(faq.get(key), str) or not faq[key].strip():
                raise SystemExit(f"{index}번 FAQ에 {key}가 없습니다: {faq}")
        digest = content_hash(faq["question"], faq["answer"])
        # 해시가 겹치면 어느 쪽을 찾아도 정답이라 순위 판정이 무의미해진다
        if digest in seen_hashes:
            raise SystemExit(f"{index}번 FAQ의 content_hash가 중복입니다: {faq['slot_id']}")
        seen_hashes.add(digest)
        items.append({
            "eval_id": faq["slot_id"],
            "type": "SIMILAR",
            "question": faq["question"],
            "expected_content_hash": digest,
            "expected_slot_id": faq["slot_id"],
        })
    return items


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", type=Path, default=DEFAULT_SOURCE, help=f"FAQ JSON (기본 {DEFAULT_SOURCE.name})")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT, help=f"출력 경로 (기본 {DEFAULT_OUT.name})")
    args = ap.parse_args()

    try:
        faqs = json.loads(args.source.read_text(encoding="utf-8"))
    except OSError as exc:
        raise SystemExit(f"{args.source}: 파일을 읽을 수 없습니다: {exc}") from None
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{args.source}: JSON 형식이 아닙니다: {exc}") from None
    if not isinstance(faqs, list) or not faqs:
        raise SystemExit(f"{args.source}: 비어 있지 않은 FAQ 배열이 필요합니다")

    items = build(faqs)
    args.out.write_text(json.dumps(items, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    categories = sorted({item["expected_slot_id"].rsplit("-", 1)[0] for item in items})
    print(f"{args.out} — {len(items)}건, 카테고리 {len(categories)}종")
    print("  " + ", ".join(categories))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
