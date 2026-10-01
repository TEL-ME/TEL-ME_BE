#!/usr/bin/env python3

"""새 평가셋이 기존 평가셋과 겹치는지 검사: 질문 텍스트 유사도 + 정답 slot_id 겹침

임베딩을 쓰지 않아 검색 점수를 보지 않고 계산된다 (docs/DUAL_VECTOR_VS_RERANKER.md 13.1절)
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

# 조사, 어미 차이를 흡수하려고 어절 앞 2~3글자만 본다
PREFIX_LENGTHS = (2, 3)
MIN_WORD_LENGTH = 2
# 13.1절 제외 규칙: 같은 뜻의 질문(1), 같은 FAQ 그룹을 정답으로 갖는 질문(2)
DEFAULT_TEXT_THRESHOLD = 0.35
DEFAULT_GOLD_THRESHOLD = 0.50
NON_WORD = re.compile(r"[^가-힣a-zA-Z0-9]")


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise SystemExit(f"{path}: 파일을 읽을 수 없습니다: {exc}") from None
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path}: JSON 형식이 아닙니다: {exc}") from None


def load_items(paths: list[Path]) -> list[dict]:
    items: list[dict] = []
    for path in paths:
        data = load_json(path)
        if not isinstance(data, list) or not data:
            raise SystemExit(f"{path}: 비어 있지 않은 평가 질문 배열이 필요합니다")
        for index, item in enumerate(data):
            if not isinstance(item, dict) or not item.get("question"):
                raise SystemExit(f"{path}: {index}번 질문 형식 오류")
            if not (item.get("eval_id") or item.get("id")):
                raise SystemExit(f"{path}: {index}번 eval_id 없음 (필드 이름이 id면 eval_id로 바꾸세요)")
        items.extend(data)
    return items


def eval_id(item: dict) -> str:
    return item.get("eval_id") or item["id"]


def tokens(text: str) -> set[str]:
    """어절 앞 n글자 집합. '요금제를'과 '요금제는'이 같은 토큰을 공유한다."""
    out: set[str] = set()
    for word in NON_WORD.sub(" ", text).split():
        if len(word) < MIN_WORD_LENGTH:
            continue
        for length in PREFIX_LENGTHS:
            out.add(word[:length])
    return out


def similarity(left: str, right: str) -> float:
    """토큰 집합의 Jaccard."""
    a, b = tokens(left), tokens(right)
    union = a | b
    return len(a & b) / len(union) if union else 0.0


def gold_slots(item: dict, expanded: dict[str, set[str]] | None = None) -> set[str]:
    """정답 slot_id 집합. 다중 정답 라벨을 주면 합쳐서 본다."""
    slots = item.get("expected_slot_id")
    if slots is None:
        out: set[str] = set()
    elif isinstance(slots, str):
        out = {slots}
    else:
        out = set(slots)
    if expanded:
        out |= expanded.get(eval_id(item), set())
    return out


def gold_overlap(new_slots: set[str], old_slots: set[str]) -> float:
    """정답 집합의 Jaccard. 무관 질문(정답 없음)은 비교하지 않는다."""
    if not new_slots or not old_slots:
        return 0.0
    return len(new_slots & old_slots) / len(new_slots | old_slots)


def worst_matches(new_items: list[dict], old_items: list[dict],
                  expanded: dict[str, set[str]] | None = None) -> list[dict]:
    """새 문항마다 가장 비슷한 기존 문항을 텍스트·정답 기준으로 각각 찾는다."""
    rows = []
    for new in new_items:
        text_score, text_id = 0.0, None
        gold_score, gold_id = 0.0, None
        new_slots = gold_slots(new, expanded)
        for old in old_items:
            score = similarity(new["question"], old["question"])
            if score > text_score:
                text_score, text_id = score, eval_id(old)
            overlap = gold_overlap(new_slots, gold_slots(old, expanded))
            if overlap > gold_score:
                gold_score, gold_id = overlap, eval_id(old)
        rows.append({"eval_id": eval_id(new), "type": new.get("type"),
                     "text": text_score, "text_id": text_id,
                     "gold": gold_score, "gold_id": gold_id})
    return rows


def internal_duplicates(items: list[dict], threshold: float) -> list[tuple[str, str, float]]:
    out = []
    for index, left in enumerate(items):
        for right in items[index + 1:]:
            score = similarity(left["question"], right["question"])
            if score >= threshold:
                out.append((eval_id(left), eval_id(right), score))
    return sorted(out, key=lambda x: -x[2])


def self_test() -> int:
    checks = [
        ("요금" in tokens("요금제를 바꿔요"), True),
        (tokens("아"), set()),  # 한 글자 어절은 버린다
        # 유사도
        (similarity("같은 문장입니다", "같은 문장입니다"), 1.0),
        (similarity("청구서를 영문으로 받을 수 있나요", "김치찌개 끓이는 방법") < 0.1, True),
        (similarity("", ""), 0.0),
        # 실제 제외했던 쌍이 규칙에 걸리는지
        (similarity("할부가 남은 폰을 분실하면 남은 할부금은 면제되나요?",
                    "할부가 남은 폰을 분실하면 남은 할부금은 면제되나요?") >= DEFAULT_TEXT_THRESHOLD, True),
        (similarity("유심 재발급 비용은 현금으로만 내야 하나요?",
                    "유심 재발급 비용은 현금영수증 발급이 되나요?") >= DEFAULT_TEXT_THRESHOLD, True),
        # 정답 겹침
        (gold_overlap({"A", "B"}, {"A", "B"}), 1.0),
        (gold_overlap({"A", "B"}, {"B", "C"}), 1 / 3),
        (gold_overlap(set(), {"A"}), 0.0),  # 무관 질문
        (gold_overlap({"A"}, set()), 0.0),
        # 다중 정답 라벨 합산
        (gold_slots({"eval_id": "E1", "expected_slot_id": ["A"]}, {"E1": {"B"}}), {"A", "B"}),
        (gold_slots({"eval_id": "E1", "expected_slot_id": "A"}), {"A"}),  # 문자열 정답
        (gold_slots({"eval_id": "E1", "expected_slot_id": None}), set()),
        # 최근접 탐색
        (worst_matches([{"eval_id": "N1", "question": "요금제를 바꿀 수 있나요", "expected_slot_id": ["A"]}],
                       [{"eval_id": "O1", "question": "요금제를 바꿀 수 있나요", "expected_slot_id": ["Z"]},
                        {"eval_id": "O2", "question": "전혀 다른 질문", "expected_slot_id": ["A"]}])[0],
         {"eval_id": "N1", "type": None, "text": 1.0, "text_id": "O1", "gold": 1.0, "gold_id": "O2"}),
        # 내부 중복
        (internal_duplicates([{"eval_id": "A", "question": "같은 문장입니다"},
                              {"eval_id": "B", "question": "같은 문장입니다"},
                              {"eval_id": "C", "question": "완전히 다른 내용"}], 0.45),
         [("A", "B", 1.0)]),
    ]
    failed = [(i, got, want) for i, (got, want) in enumerate(checks) if got != want]
    for i, got, want in failed:
        print(f"  검사 {i} 실패: {got!r} != {want!r}")
    print(f"자기 검증 {len(checks) - len(failed)}/{len(checks)} 통과")
    return 1 if failed else 0


def main() -> int:
    ap = argparse.ArgumentParser(description="새 평가셋이 기존 평가셋과 겹치는지 검사")
    ap.add_argument("path", nargs="?", type=Path, help="검사할 새 평가셋 JSON")
    ap.add_argument("--against", nargs="+", type=Path, default=[],
                    help="기존 평가셋 JSON (여러 개)")
    ap.add_argument("--multigold", type=Path,
                    help="다중 정답 라벨 JSON. 주면 정답을 합쳐 넓게 비교한다")
    ap.add_argument("--text-threshold", type=float, default=DEFAULT_TEXT_THRESHOLD,
                    help=f"질문 텍스트 유사도 제외선 (기본 {DEFAULT_TEXT_THRESHOLD})")
    ap.add_argument("--gold-threshold", type=float, default=DEFAULT_GOLD_THRESHOLD,
                    help=f"정답 slot 겹침 제외선 (기본 {DEFAULT_GOLD_THRESHOLD})")
    ap.add_argument("--internal-threshold", type=float, default=0.45,
                    help="같은 파일 안에서 중복으로 볼 선 (기본 0.45)")
    ap.add_argument("--top", type=int, default=10, help="유사도 상위 몇 건을 출력할지 (기본 10)")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        return self_test()
    if not args.path or not args.against:
        raise SystemExit("사용법: check_eval_overlap.py <새 평가셋> --against <기존 평가셋...>")

    new_items = load_items([args.path])
    old_items = load_items(args.against)
    expanded = None
    if args.multigold:
        expanded = {eval_id(q): gold_slots(q) for q in load_items([args.multigold])}

    rows = sorted(worst_matches(new_items, old_items, expanded), key=lambda r: -r["text"])
    print(f"{args.path} {len(new_items)}건 vs 기존 {len(old_items)}건"
          + (f" (다중 정답 라벨 {args.multigold.name} 합산)" if expanded else ""))

    print(f"\n  텍스트 유사도 상위 {min(args.top, len(rows))}건")
    for row in rows[:args.top]:
        print(f"    {row['text']:.3f}  {row['eval_id']:<8} <-> {row['text_id']}")

    gold_rows = [r for r in rows if r["gold"] > 0]
    if gold_rows:
        print(f"\n  정답 slot 겹침 상위 {min(args.top, len(gold_rows))}건")
        for row in sorted(gold_rows, key=lambda r: -r["gold"])[:args.top]:
            print(f"    {row['gold']:.3f}  {row['eval_id']:<8} <-> {row['gold_id']}")

    dups = internal_duplicates(new_items, args.internal_threshold)
    print(f"\n  파일 내부 중복({args.internal_threshold} 이상): {len(dups)}건")
    for left, right, score in dups[:args.top]:
        print(f"    {score:.3f}  {left} / {right}")

    violations = [r for r in rows
                  if r["text"] >= args.text_threshold or r["gold"] >= args.gold_threshold]
    print(f"\n  최대 텍스트 유사도 {rows[0]['text']:.3f} ({rows[0]['eval_id']})")
    if not violations and not dups:
        print(f"겹침 검사 통과 (텍스트 {args.text_threshold} / 정답 {args.gold_threshold} 미만).")
        return 0
    for row in violations:
        reason = (f"텍스트 {row['text']:.2f} <-> {row['text_id']}" if row["text"] >= args.text_threshold
                  else f"정답겹침 {row['gold']:.2f} <-> {row['gold_id']}")
        print(f"  제외 대상: {row['eval_id']} ({reason})")
    print(f"\n{len(violations)}건 제외 대상, 내부 중복 {len(dups)}건")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
