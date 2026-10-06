"""Compare the preserved V6 human review with one Judge result without changing either."""

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re

from scripts.chat_judge.experiments.v6_live_chat_pipeline.evaluate_chat_pipeline import load_json


def load_human_review(path, review_key):
    source = path.read_text(encoding="utf-8")
    sections = re.split(r"(?m)^## ((?:V6H|H)-\d{3})\s*$", source)
    if len(sections) < 3 or len(sections[1::2]) != len(review_key["cases"]):
        raise ValueError("사람 판정 건수가 검토 키와 다릅니다.")

    def extract(pattern, body, review_id):
        match = re.search(pattern, body, re.MULTILINE)
        if not match:
            raise ValueError(f"{review_id}에서 판정값을 찾지 못했습니다: {pattern}")
        return match.group(1)

    mapping = {(case["reviewId"]): (case["caseId"], case["turnIndex"])
               for case in review_key["cases"]}
    human = {}
    for review_id, body in zip(sections[1::2], sections[2::2]):
        if review_id not in mapping or review_id in human:
            raise ValueError(f"검토 ID가 중복되었거나 키에 없습니다: {review_id}")
        coverage = re.findall(r"(?m)^  - 기준 \d+: `(COMPLETE|PARTIAL|MISSED|NOT_APPLICABLE|NEEDS_HUMAN_REVIEW)`\s*$", body)
        if not coverage:
            raise ValueError(f"{review_id}의 하위 질문 판정이 없습니다.")
        failure = re.search(
            r"^- 별도 파이프라인 실패 유형: `(NONE|MISROUTED_CLARIFICATION|OTHER|NEEDS_HUMAN_REVIEW)`\s*$",
            body, re.MULTILINE)
        human[review_id] = {
            "caseId": mapping[review_id][0], "turnIndex": mapping[review_id][1],
            "pipelineFailure": failure.group(1) if failure else None,
            "grounding": extract(r"^- 근거성 전체 판정: `(SUPPORTED|UNSUPPORTED|NOT_APPLICABLE|NEEDS_HUMAN_REVIEW)`", body, review_id),
            "quality": coverage,
            "abstention": extract(r"^- 답변 불가 판정: `(APPROPRIATE|SHOULD_ABSTAIN|OVER_REFUSAL|NOT_APPLICABLE|NEEDS_HUMAN_REVIEW)`", body, review_id),
            "answerability": extract(r"^- 실제 전달 근거 충분성: `(ENOUGH|INSUFFICIENT|NEEDS_HUMAN_REVIEW)`", body, review_id),
        }
    if set(human) != set(mapping):
        raise ValueError("검토 키와 사람 판정 ID가 일치하지 않습니다.")
    if any(item["pipelineFailure"] is not None for item in human.values()) and any(
            item["pipelineFailure"] is None for item in human.values()):
        raise ValueError("별도 파이프라인 실패 유형이 일부 사례에서 누락됐습니다.")
    return human, hashlib.sha256(path.read_bytes()).hexdigest()


def compare(human, evaluation):
    candidate = {(turn["caseId"], turn["turnIndex"]): turn for turn in evaluation["turns"]}
    counts = {axis: Counter() for axis in ("grounding", "quality", "abstention", "answerability")}
    differences = {axis: [] for axis in counts}
    for review_id, gold in human.items():
        key = (gold["caseId"], gold["turnIndex"])
        if key not in candidate:
            raise ValueError(f"Judge 결과에 {review_id}가 없습니다: {key}")
        turn = candidate[key]
        observed = {
            "grounding": (turn.get("grounding", {}).get("result") or {}).get("overall"),
            "quality": [(group.get("outcome")) for group in
                        (turn.get("quality", {}).get("result") or {}).get("groups", [])],
            "abstention": (turn.get("abstentionDecision") or {}).get("label"),
            "answerability": (turn.get("abstention", {}).get("result") or {}).get("evidenceAnswerability"),
        }
        for axis in counts:
            expected, actual = gold[axis], observed[axis]
            if expected == "NEEDS_HUMAN_REVIEW" or (isinstance(expected, list) and "NEEDS_HUMAN_REVIEW" in expected):
                counts[axis]["human_deferred"] += 1
                differences[axis].append((review_id, expected, actual, "HUMAN_DEFERRED"))
            elif actual is None or actual == [] or actual == "REVIEW" or "REVIEW" in actual:
                counts[axis]["unresolved"] += 1
                differences[axis].append((review_id, expected, actual, "UNRESOLVED"))
            elif actual == expected:
                counts[axis]["match"] += 1
            else:
                counts[axis]["mismatch"] += 1
                differences[axis].append((review_id, expected, actual, "MISMATCH"))
        if observed["quality"] and "REVIEW" not in observed["quality"]:
            if len(observed["quality"]) != len(gold["quality"]):
                raise ValueError(f"{review_id}의 하위 질문 수가 다릅니다.")
            for expected, actual in zip(gold["quality"], observed["quality"]):
                if expected == "NEEDS_HUMAN_REVIEW":
                    counts["quality"]["group_human_deferred"] += 1
                else:
                    counts["quality"]["group_match" if expected == actual else "group_mismatch"] += 1
    return counts, differences


def compare_pipeline_failures(human, evaluation):
    """Keep human failure subtypes separate from the existing routing finding."""
    labeled = {review_id: item for review_id, item in human.items()
               if item.get("pipelineFailure") is not None}
    if not labeled:
        return None
    candidate = {(turn["caseId"], turn["turnIndex"]): turn for turn in evaluation["turns"]}
    rows = []
    for review_id, item in labeled.items():
        key = (item["caseId"], item["turnIndex"])
        if key not in candidate:
            raise ValueError(f"Judge 결과에 {review_id}가 없습니다: {key}")
        findings = candidate[key].get("findings") or []
        rows.append({"reviewId": review_id, "humanLabel": item["pipelineFailure"],
                     "routingMismatch": "ROUTING_MISMATCH" in findings})
    return {"total": len(rows),
            "humanCounts": Counter(row["humanLabel"] for row in rows),
            "routingMismatchCount": sum(row["routingMismatch"] for row in rows),
            "rows": rows}


def report(review_path, human_hash, evaluation_path, counts, differences, pipeline_failures=None):
    lines = ["# V6 사람 판정과 Judge 대조", "",
        f"- 사람 판정: `{review_path.name}` (SHA-256 `{human_hash}`)",
        f"- Judge 결과: `{evaluation_path.name}`", "",
        "선정된 검토 표본의 일치 수치다. 전체 서비스 품질이나 Judge의 모집단 정확도가 아니다.",
        "불일치는 사람 라벨과 자동 판정의 차이이며 어느 쪽 오류인지 원문 대조 전에는 확정하지 않는다.",
        "미채점과 REVIEW는 불일치율의 분모에서 제외하고 별도로 표시한다.", "",
        "| 항목 | 일치 | 불일치 | Judge 미해결 | 사람 보류 |", "| --- | ---: | ---: | ---: | ---: |"]
    for axis, label in (("grounding", "근거성"), ("quality", "질문별 충족도: 모든 하위 질문 일치"),
                        ("answerability", "실제 RAG 근거 충분성"), ("abstention", "최종 답변 불가 판정")):
        row = counts[axis]
        lines.append(f"| {label} | {row['match']} | {row['mismatch']} | {row['unresolved']} | {row['human_deferred']} |")
    q = counts["quality"]
    lines += ["", f"충족도 하위 질문별 일치: {q['group_match']}/{q['group_match'] + q['group_mismatch']}.",
              "최종 답변 불가 판정은 근거성 결과와 거절 신호를 결합한 값이며, 모델 단독 출력이 아니다.", ""]
    for axis, label in (("grounding", "근거성"), ("quality", "질문 충족도"),
                        ("answerability", "근거 충분성"), ("abstention", "답변 불가")):
        lines += [f"## {label}", "", "| ID | 사람 | Judge | 상태 |", "| --- | --- | --- | --- |"]
        for review_id, expected, actual, status in differences[axis]:
            lines.append(f"| {review_id} | `{expected}` | `{actual}` | {status} |")
        lines.append("")
    if pipeline_failures is not None:
        lines.extend(["## 별도 파이프라인 실패 유형", "",
            "이 항목은 사람의 원인 분류다. 자동 `ROUTING_MISMATCH`는 기대 의도와 실제 라우팅 의도가 다른지 코드로 비교한 신호이며, 엉뚱한 되묻기까지 판정한 결과는 아니다.", "",
            f"- 사람 판정: {', '.join(f'`{label}` {count}건' for label, count in sorted(pipeline_failures['humanCounts'].items()))}",
            f"- 자동 `ROUTING_MISMATCH`: {pipeline_failures['routingMismatchCount']}/{pipeline_failures['total']}건", "",
            "| ID | 사람 분류 | 자동 의도 불일치 신호 |", "| --- | --- | --- |"])
        for row in pipeline_failures["rows"]:
            if row["humanLabel"] != "NONE" or row["routingMismatch"]:
                lines.append(f"| {row['reviewId']} | `{row['humanLabel']}` | {'있음' if row['routingMismatch'] else '없음'} |")
        lines.extend(["", "사람 분류 `MISROUTED_CLARIFICATION`을 자동으로 판정한 건수는 아직 없다. 위 표의 자동 신호는 더 넓은 의도 불일치를 뜻한다.", ""])
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--human", type=Path, required=True)
    parser.add_argument("--key", type=Path, required=True)
    parser.add_argument("--judge", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    human, human_hash = load_human_review(args.human, load_json(args.key))
    evaluation = load_json(args.judge)
    counts, differences = compare(human, evaluation)
    pipeline_failures = compare_pipeline_failures(human, evaluation)
    args.out.write_text(report(args.human, human_hash, args.judge, counts, differences,
                               pipeline_failures), encoding="utf-8")
    print(json.dumps({axis: dict(value) for axis, value in counts.items()}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
