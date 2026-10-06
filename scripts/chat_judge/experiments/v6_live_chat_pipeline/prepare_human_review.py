"""Build a blinded human-review packet and a separate Judge comparison key."""

from __future__ import annotations

import gzip
import hashlib
import json
from collections import Counter
from pathlib import Path
from typing import Any, Callable


ROOT = Path(__file__).resolve().parents[4]
EXPERIMENT = ROOT / "docs/chat-judge/experiments/V6-live-chat-pipeline"
RAW_PATH = EXPERIMENT / "20261002-service-pipeline-judged-raw.json.gz"
CAPTURE_PATH = EXPERIMENT / "20261002-service-pipeline-capture.json.gz"
BLIND_PATH = EXPERIMENT / "20261005-service-pipeline-human-review.md"
AI_REVIEW_PATH = EXPERIMENT / "20261005-service-pipeline-ai-review.md"
KEY_PATH = EXPERIMENT / "20261005-service-pipeline-human-review-key.json"
KEY_MD_PATH = EXPERIMENT / "20261005-service-pipeline-human-review-key.md"
SEED = "20261005"

SAMPLE_TYPE_LABELS = {
    "COMPLETED_BUT_UNSCORED": "실행은 완료됐지만 Judge 출력 검증 실패",
    "JUDGE_REVIEW_REQUIRED": "Judge가 사람 검토를 요청한 사례",
    "SUPPORTED_COMPLETE": "근거가 있고 완전 답변으로 채점된 사례",
    "UNSUPPORTED_CLAIM_WITH_COMPLETE_ANSWER": "완전 답변이지만 근거 밖 주장이 있는 사례",
    "SINGLE_QUESTION_PARTIAL": "단일 질문 일부 누락 사례",
    "COMPOUND_QUESTION_PARTIAL": "복합 질문 일부 누락 사례",
    "OVER_REFUSAL_WITH_SUFFICIENT_EVIDENCE": "근거가 충분한데 거절한 사례",
    "OUT_OF_SCOPE_REFUSAL": "범위 밖 질문을 거절한 사례",
    "RETRIEVAL_MISS_WITH_SAFE_REFUSAL": "검색 근거가 부족해 보류한 사례",
    "STORE_LOOKUP": "매장 조회 사례",
    "MULTI_TURN": "이전 대화가 포함된 후속 질문 사례",
}


def read_gzip_json(path: Path) -> dict[str, Any]:
    with gzip.open(path, "rt", encoding="utf-8") as source:
        return json.load(source)


def axis_result(turn: dict[str, Any], axis: str) -> dict[str, Any]:
    return (turn.get(axis) or {}).get("result") or {}


def quality_outcomes(turn: dict[str, Any]) -> list[str]:
    return [group.get("outcome", "MISSING") for group in axis_result(turn, "quality").get("groups", [])]


def grounding_outcome(turn: dict[str, Any]) -> str | None:
    return axis_result(turn, "grounding").get("overall")


def abstention_result(turn: dict[str, Any]) -> dict[str, Any]:
    return axis_result(turn, "abstention")


def is_completed_unscored(turn: dict[str, Any]) -> bool:
    return turn.get("executionStatus") == "COMPLETED" and turn.get("judgeStatus") == "UNSCORED"


def is_review_required(turn: dict[str, Any]) -> bool:
    return turn.get("executionStatus") == "COMPLETED" and turn.get("requiresReview") is True


def sample_order(label: str, turn: dict[str, Any]) -> str:
    value = f"{SEED}|{label}|{turn['caseId']}|{turn['turnIndex']}"
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def clean_text(value: Any) -> str:
    if value is None:
        return "(없음)"
    return str(value).replace("\r\n", "\n").replace("\r", "\n")


def quote(text: Any) -> str:
    return "\n".join(f"> {line}" if line else ">" for line in clean_text(text).split("\n"))


def json_block(value: Any) -> str:
    return "```json\n" + json.dumps(value, ensure_ascii=False, indent=2) + "\n```"


def expected_groups(fixture: dict[str, Any]) -> list[dict[str, Any]]:
    return fixture.get("qualityReferenceGroups") or []


def validation_error_summary(turn: dict[str, Any]) -> dict[str, Any]:
    errors: dict[str, Any] = {}
    for axis in ("grounding", "quality", "abstention"):
        if turn.get("axisStatus", {}).get(axis) == "SCORED":
            continue
        result = turn.get(axis) or {}
        errors[axis] = {
            "error": result.get("error"),
            "attemptErrors": [
                attempt.get("error")
                for attempt in result.get("attempts", [])
                if attempt.get("error")
            ],
        }
    return errors


def format_ai_decision(turn: dict[str, Any]) -> list[str]:
    lines = [
        "### AI 판정",
        f"- Judge 상태: `{turn.get('judgeStatus')}`",
        f"- 사람 검토 필요 표시: `{turn.get('requiresReview', False)}`",
        f"- 판정 검증 이슈: `{json.dumps(turn.get('findings', []), ensure_ascii=False)}`",
        "",
        "#### 근거성",
    ]
    grounding = axis_result(turn, "grounding")
    if turn.get("axisStatus", {}).get("grounding") == "SCORED":
        lines.append(f"- 전체 판정: `{grounding.get('overall', 'MISSING')}`")
        for claim in grounding.get("claims", []):
            lines.extend([
                f"- 주장: {quote(claim.get('claim', ''))}",
                f"  - 판정: `{claim.get('verdict', 'MISSING')}`",
                f"  - 근거 ID: `{json.dumps(claim.get('sourceIds', []), ensure_ascii=False)}`",
                f"  - 이유: {claim.get('reason', '')}",
            ])
    else:
        errors = validation_error_summary(turn).get("grounding", {})
        lines.append(f"- 상태: `UNSCORED` / 검증 오류: `{errors.get('error') or '결과 없음'}`")

    lines.extend(["", "#### 질문 충족도"])
    quality = axis_result(turn, "quality")
    if turn.get("axisStatus", {}).get("quality") == "SCORED":
        for group in quality.get("groups", []):
            lines.extend([
                f"- 하위 질문 기준 {group.get('groupIndex', '?')}: `{group.get('outcome', 'MISSING')}`",
                f"  - 답변 인용: `{json.dumps(group.get('answerQuotes', []), ensure_ascii=False)}`",
                f"  - 이유: {group.get('reason', '')}",
            ])
    else:
        errors = validation_error_summary(turn).get("quality", {})
        lines.append(f"- 상태: `UNSCORED` / 검증 오류: `{errors.get('error') or '결과 없음'}`")

    lines.extend(["", "#### 답변 불가 판단"])
    abstention = axis_result(turn, "abstention")
    if turn.get("axisStatus", {}).get("abstention") == "SCORED":
        lines.extend([
            f"- 답변을 거절로 판단했나: `{abstention.get('answerIsRefusal')}`",
            f"- 실제 RAG 근거 충분성 판정: `{abstention.get('evidenceAnswerability')}`",
            f"- 근거 인용: `{json.dumps(abstention.get('evidenceQuotes', []), ensure_ascii=False)}`",
            f"- 이유: {abstention.get('reason', '')}",
        ])
    else:
        errors = validation_error_summary(turn).get("abstention", {})
        lines.append(f"- 상태: `UNSCORED` / 검증 오류: `{errors.get('error') or '결과 없음'}`")
    decision = turn.get("abstentionDecision") or {}
    lines.append(
        f"- 최종 답변 불가 라벨: `{decision.get('label', 'MISSING')}` "
        f"(규칙: `{decision.get('rule', 'MISSING')}`; Judge와 불일치: `{decision.get('disagreesWithJudge', False)}`)"
    )
    lines.append("")
    return lines


def key_case_summary(case: dict[str, Any]) -> list[str]:
    judge = case["judge"]
    grounding = judge.get("grounding", {})
    quality = judge.get("quality", {})
    abstention = judge.get("abstention", {})
    lines = [
        f"### {case['reviewId']} / `{case['caseId']}`",
        f"- 실행 기대: `{case.get('expectedBehavior')}`",
        f"- Judge 상태: `{case.get('judgeStatus')}`; 검토 요청: `{case.get('requiresReview')}`",
        f"- 축 상태: `{json.dumps(case.get('axisStatus', {}), ensure_ascii=False)}`",
        f"- 근거성 전체: `{grounding.get('overall', '미채점')}`",
        f"- 주장 판정: `{json.dumps([{ 'claim': item.get('claim'), 'verdict': item.get('verdict'), 'sourceIds': item.get('sourceIds', []) } for item in grounding.get('claims', [])], ensure_ascii=False)}`",
        f"- 질문 충족도: `{json.dumps([group.get('outcome') for group in quality.get('groups', [])], ensure_ascii=False)}`",
        f"- 답변 불가 판단: `{json.dumps({'answerIsRefusal': abstention.get('answerIsRefusal'), 'evidenceAnswerability': abstention.get('evidenceAnswerability'), 'decision': case.get('abstentionDecision')}, ensure_ascii=False)}`",
    ]
    if case.get("validationErrors"):
        errors = {
            axis: {
                "error": detail.get("error"),
                "attemptErrors": detail.get("attemptErrors", []),
            }
            for axis, detail in case["validationErrors"].items()
        }
        lines.append(f"- 출력 검증 실패: `{json.dumps(errors, ensure_ascii=False)}`")
    lines.append("")
    return lines


def choose_normal_samples(turns: list[dict[str, Any]], capture_by_key: dict[tuple[str, int], dict[str, Any]]) -> list[tuple[str, dict[str, Any]]]:
    eligible = [
        turn
        for turn in turns
        if turn.get("executionStatus") == "COMPLETED"
        and turn.get("judgeStatus") == "SCORED"
        and not turn.get("requiresReview")
        and all(status == "SCORED" for status in turn.get("axisStatus", {}).values())
    ]
    selected: list[tuple[str, dict[str, Any]]] = []
    used: set[tuple[str, int]] = set()

    def add(label: str, predicate: Callable[[dict[str, Any]], bool], count: int) -> None:
        candidates = [
            turn for turn in eligible
            if (turn["caseId"], turn["turnIndex"]) not in used and predicate(turn)
        ]
        candidates.sort(key=lambda turn: sample_order(label, turn))
        if len(candidates) < count:
            raise ValueError(f"Not enough normal examples for {label}: need {count}, found {len(candidates)}")
        for turn in candidates[:count]:
            key = (turn["caseId"], turn["turnIndex"])
            selected.append((label, turn))
            used.add(key)

    add(
        "SUPPORTED_COMPLETE",
        lambda t: t.get("expectedBehavior") == "ANSWER"
        and grounding_outcome(t) == "SUPPORTED"
        and quality_outcomes(t) == ["COMPLETE"]
        and not abstention_result(t).get("answerIsRefusal"),
        2,
    )
    add(
        "UNSUPPORTED_CLAIM_WITH_COMPLETE_ANSWER",
        lambda t: t.get("expectedBehavior") == "ANSWER"
        and grounding_outcome(t) == "UNSUPPORTED"
        and quality_outcomes(t) == ["COMPLETE"],
        2,
    )
    add(
        "SINGLE_QUESTION_PARTIAL",
        lambda t: t.get("expectedBehavior") in ("ANSWER", "PARTIAL_ANSWER")
        and len(quality_outcomes(t)) == 1
        and "PARTIAL" in quality_outcomes(t),
        2,
    )
    add(
        "COMPOUND_QUESTION_PARTIAL",
        lambda t: t.get("expectedBehavior") in ("ANSWER", "PARTIAL_ANSWER")
        and len(quality_outcomes(t)) > 1
        and any(outcome in ("PARTIAL", "MISSED") for outcome in quality_outcomes(t)),
        2,
    )
    add(
        "OVER_REFUSAL_WITH_SUFFICIENT_EVIDENCE",
        lambda t: t.get("expectedBehavior") == "ANSWER"
        and abstention_result(t).get("answerIsRefusal") is True
        and abstention_result(t).get("evidenceAnswerability") == "ENOUGH",
        2,
    )
    add(
        "OUT_OF_SCOPE_REFUSAL",
        lambda t: t.get("expectedBehavior") == "OUT_OF_SCOPE"
        and abstention_result(t).get("answerIsRefusal") is True,
        2,
    )
    add(
        "RETRIEVAL_MISS_WITH_SAFE_REFUSAL",
        lambda t: t.get("expectedBehavior") == "ANSWER"
        and abstention_result(t).get("answerIsRefusal") is True
        and abstention_result(t).get("evidenceAnswerability") == "INSUFFICIENT",
        2,
    )
    add("STORE_LOOKUP", lambda t: t.get("expectedBehavior") == "STORE_LOOKUP", 1)
    add(
        "MULTI_TURN",
        lambda t: t.get("caseId") == "REG-MULTI_TURN" and t.get("turnIndex") == 1,
        1,
    )
    return selected


def format_case(
    review_id: str,
    turn: dict[str, Any],
    captured: dict[str, Any],
    include_ai_decision: bool = False,
) -> str:
    fixture = captured.get("fixture", {})
    groups = expected_groups(fixture)
    lines = [f"## {review_id}", "", "### 대화 맥락"]
    for prior in captured.get("previousTurns", []):
        lines.extend([f"- 이전 질문: {prior['question']}", f"- 이전 답변: {prior['answer']}"])
    if not captured.get("previousTurns"):
        lines.append("- 이전 대화 없음")

    lines.extend(["", "### 현재 질문", quote(turn.get("question")), "", "### 참고 FAQ 기준"])
    for index, group in enumerate(groups, start=1):
        lines.extend([
            f"- 하위 질문 기준 {index}: {group.get('question', '')}",
            f"  - FAQ 답변: {group.get('answer', '')}",
        ])
    if fixture.get("requiredFacts"):
        lines.append("- 필수 사실:")
        lines.extend(f"  - {fact}" for fact in fixture["requiredFacts"])
    if fixture.get("goldSourceGroups"):
        lines.append(f"- 동등 정답 FAQ 묶음: `{json.dumps(fixture['goldSourceGroups'], ensure_ascii=False)}`")

    lines.extend(["", "### 답변 생성에 전달된 확정 조건"])
    conditions = captured.get("confirmedConditions") or []
    lines.extend([f"- {condition}" for condition in conditions] if conditions else ["- 없음"])

    lines.extend(["", "### 실제 저장 답변", quote(turn.get("answer")), "", "### 답변 생성에 전달된 실제 근거"])
    sources = turn.get("sources") or []
    if not sources:
        lines.append("- 실제 근거 없음")
    for source in sources:
        lines.extend([
            f"- 근거 ID: `{source.get('sourceId', 'unknown')}`",
            f"  - FAQ 질문: {source.get('question', '')}",
            f"  - FAQ 답변: {source.get('answer', '')}",
        ])
    lines.append(f"- 근거 출처 기록: `{turn.get('sourceProvenance', 'unknown')}`")

    saved = turn.get("savedSources") or []
    lines.extend(["", "### DB에 저장된 근거 ID", "- " + (", ".join(f"`{item.get('sourceId', item.get('faqId', 'unknown'))}`" for item in saved) if saved else "없음")])
    if include_ai_decision:
        lines.extend(["", *format_ai_decision(turn)])
    lines.extend([
        "",
        "### 사람 판정 기록",
        "- 근거성 전체 판정 (`SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):",
        "- 주장별 판정과 근거 ID:",
        "- 질문 충족도: 하위 질문 기준마다 `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW` 기록",
    ])
    for index in range(max(1, len(groups))):
        lines.append(f"  - 기준 {index + 1}:")
    lines.extend([
        "- 답변 불가 판정 (`APPROPRIATE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `NOT_APPLICABLE` / `NEEDS_HUMAN_REVIEW`):",
        "- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):",
        "- 메모:",
        "",
        "---",
        "",
    ])
    return "\n".join(lines)


def main() -> None:
    judged = read_gzip_json(RAW_PATH)
    capture = read_gzip_json(CAPTURE_PATH)
    raw_hash = hashlib.sha256(RAW_PATH.read_bytes()).hexdigest()
    capture_by_key: dict[tuple[str, int], dict[str, Any]] = {}
    for case in capture["cases"]:
        for index, turn in enumerate(case.get("turns", [])):
            item = dict(turn)
            item["previousTurns"] = [
                {
                    "question": prior.get("fixture", {}).get("question", ""),
                    "answer": (prior.get("outputMessage") or {}).get("content", ""),
                }
                for prior in case.get("turns", [])[:index]
            ]
            capture_by_key[(case["caseId"], index)] = item

    if len(judged["turns"]) != 509:
        raise ValueError(f"Expected 509 captured turns, found {len(judged['turns'])}")
    if len(capture_by_key) != 509:
        raise ValueError(f"Expected 509 capture turns, found {len(capture_by_key)}")

    completed_unscored = [turn for turn in judged["turns"] if is_completed_unscored(turn)]
    review_required = [turn for turn in judged["turns"] if is_review_required(turn)]
    if len(completed_unscored) != 16 or len(review_required) != 7:
        raise ValueError(f"Unexpected review counts: unscored={len(completed_unscored)}, review={len(review_required)}")
    if {(t["caseId"], t["turnIndex"]) for t in completed_unscored} & {
        (t["caseId"], t["turnIndex"]) for t in review_required
    }:
        raise ValueError("Unscored and review-required samples overlap")

    selected: list[tuple[str, dict[str, Any]]] = []
    selected.extend(("COMPLETED_BUT_UNSCORED", turn) for turn in completed_unscored)
    selected.extend(("JUDGE_REVIEW_REQUIRED", turn) for turn in review_required)
    selected.extend(choose_normal_samples(judged["turns"], capture_by_key))
    if len(selected) != 39:
        raise ValueError(f"Expected 39 unique examples, found {len(selected)}")

    selected.sort(key=lambda pair: sample_order("blind-order", pair[1]))
    blind_cases = []
    key_cases = []
    markdown_cases = []
    ai_markdown_cases = []
    for number, (sample_type, turn) in enumerate(selected, start=1):
        review_id = f"H-{number:03d}"
        key = (turn["caseId"], turn["turnIndex"])
        if key not in capture_by_key:
            raise ValueError(f"Missing captured turn for {key}")
        captured = capture_by_key[key]
        fixture = captured.get("fixture", {})
        blind_cases.append({
            "reviewId": review_id,
            "previousTurns": captured.get("previousTurns", []),
            "question": turn.get("question"),
            "expectedBehavior": turn.get("expectedBehavior"),
            "requiredFacts": fixture.get("requiredFacts", []),
            "goldSourceGroups": fixture.get("goldSourceGroups", []),
            "qualityReferenceGroups": expected_groups(fixture),
            "confirmedConditions": captured.get("confirmedConditions", []),
            "answer": turn.get("answer"),
            "actualRagSources": turn.get("sources", []),
            "savedSourceIds": [item.get("sourceId", item.get("faqId")) for item in turn.get("savedSources", [])],
            "executionStatus": turn.get("executionStatus"),
        })
        key_cases.append({
            "reviewId": review_id,
            "caseId": turn["caseId"],
            "turnIndex": turn["turnIndex"],
            "sampleType": sample_type,
            "expectedBehavior": turn.get("expectedBehavior"),
            "judgeStatus": turn.get("judgeStatus"),
            "requiresReview": turn.get("requiresReview", False),
            "findings": turn.get("findings", []),
            "axisStatus": turn.get("axisStatus", {}),
            "abstentionDecision": turn.get("abstentionDecision"),
            "validationErrors": validation_error_summary(turn),
            "judge": {
                "grounding": axis_result(turn, "grounding"),
                "quality": axis_result(turn, "quality"),
                "abstention": axis_result(turn, "abstention"),
            },
        })
        markdown_cases.append(format_case(review_id, turn, captured))
        ai_markdown_cases.append(format_case(review_id, turn, captured, include_ai_decision=True))

    blind_doc = f"""# V6 채팅 파이프라인 사람 검토표

## 검토 목적

실제 Spring 채팅 파이프라인이 생성한 답변과 실제 RAG 입력을 사람이 직접 판정해 Judge 결과를 대조합니다. 원본은 고정 평가 질문으로 실행한 결과이며 실제 사용자 트래픽 표본은 아닙니다.

## 검토 방법

- 39개 사례의 Judge 판정과 표본 유형은 이 문서에 표시하지 않았습니다.
- 먼저 각 사례의 질문, 기대 기준, 답변, 실제 RAG 근거를 보고 사람 판정을 기록하세요.
- 답변 전체의 근거성, 각 요구사항에 대한 충족도, 답변 불가 판단을 각각 평가하세요. 표현이 달라도 의미가 같으면 같은 사실로 보고 조건, 대상, 부정, 금액, 기간, 예외가 맞는지 확인하세요.
- 판정이 모두 기록된 뒤에만 별도 `20261005-service-pipeline-human-review-key.json`을 열어 대조하세요.
- 실행 실패 15건은 최종 답변이 없어 이번 답변 품질 비교 표본에서는 제외했습니다. 실행 실패 자체는 기존 V6 결과 문서에서 확인하세요.

## 표본 구성

39건에는 여러 판정 상태와 유형의 사례가 섞여 있지만, 개별 사례의 표본 유형은 표시하지 않았습니다. 선정 규칙과 유형별 목록, Judge 라벨은 별도 key 파일에 보관했습니다.

사람 판정은 아래 사례 본문에 직접 기록하거나 복사해 별도 파일로 보관하세요. Judge 결과를 보기 전에 기록을 잠그는 것이 중요합니다.

---

{''.join(markdown_cases)}
"""
    BLIND_PATH.write_text(blind_doc, encoding="utf-8", newline="\n")
    ai_doc = f"""# V6 채팅 파이프라인 AI 판정 대조표

## 안내

블라인드 검토표와 같은 39개 사례와 순서를 사용하며, 각 사례에 저장된 Qwen Judge 결과를 함께 표시합니다. 사람 판정을 먼저 기록한 경우 이 문서에서 축별 판정과 검증 오류를 대조하세요. AI 판정은 자동 결과이며 사람 확정 라벨을 대신하지 않습니다.

---

{''.join(ai_markdown_cases)}
"""
    AI_REVIEW_PATH.write_text(ai_doc, encoding="utf-8", newline="\n")

    key_doc = {
        "schemaVersion": 1,
        "source": RAW_PATH.name,
        "sourceSha256": raw_hash,
        "capture": CAPTURE_PATH.name,
        "samplingSeed": SEED,
        "counts": {
            "completedJudgeUnscored": len(completed_unscored),
            "requiresHumanReview": len(review_required),
            "normallyScoredByType": 16,
            "total": len(key_cases),
        },
        "warning": "사람 판정을 고정하기 전에는 검토자에게 이 파일을 공유하지 마세요. Judge 판정과 샘플 유형이 포함되어 있습니다.",
        "cases": key_cases,
    }
    KEY_PATH.write_text(json.dumps(key_doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    markdown_key = [
        "# V6 사람 검토 대조 키",
        "",
        "> 사람 판정을 먼저 기록하고 잠근 뒤에 확인하세요. 이 문서에는 원본 caseId, 표본 유형, Judge 결과와 검증 오류가 들어 있습니다.",
        "",
        f"- 원본: `{RAW_PATH.name}`",
        f"- 원본 SHA-256: `{raw_hash}`",
        f"- 표본: 총 {len(key_cases)}건, 실행 완료 후 출력 검증 실패 {len(completed_unscored)}건, Judge 사람 검토 요청 {len(review_required)}건, 정상 채점 표본 16건",
        "",
        "## 사람 판정을 잠근 뒤 대조할 사례",
        "",
    ]
    error_counts: Counter[tuple[str, str]] = Counter()
    for case in key_cases:
        if case["sampleType"] != "COMPLETED_BUT_UNSCORED":
            continue
        for axis, details in case["validationErrors"].items():
            messages = [details.get("error"), *details.get("attemptErrors", [])]
            signature = next((str(message).split(":", 1)[0] for message in messages if message), "UNKNOWN")
            error_counts[(axis, signature)] += 1
    markdown_key.extend(["### 출력 검증 실패 유형", ""])
    markdown_key.extend(
        f"- `{axis}` / `{signature}`: {count}건"
        for (axis, signature), count in sorted(error_counts.items())
    )
    markdown_key.extend(["", "---", ""])
    by_type: dict[str, list[dict[str, Any]]] = {}
    for case in key_cases:
        by_type.setdefault(case["sampleType"], []).append(case)
    for sample_type, cases in by_type.items():
        markdown_key.extend([f"## {SAMPLE_TYPE_LABELS[sample_type]} ({len(cases)}건)", ""])
        for case in cases:
            markdown_key.extend(key_case_summary(case))
    KEY_MD_PATH.write_text("\n".join(markdown_key) + "\n", encoding="utf-8", newline="\n")
    print(f"Wrote {BLIND_PATH}")
    print(f"Wrote {AI_REVIEW_PATH}")
    print(f"Wrote {KEY_PATH}")
    print(f"Wrote {KEY_MD_PATH}")
    print(f"cases={len(key_cases)} unscored={len(completed_unscored)} review={len(review_required)} normal=16")


if __name__ == "__main__":
    main()
