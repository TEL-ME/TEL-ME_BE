"""Summarize a stored chat run without calling the model or database."""

import argparse
import gzip
import json
from collections import Counter
from pathlib import Path


def summarize(result):
    rows = result["cases"]
    basis = Counter((row.get("trace") or {}).get("answerBasis") for row in rows)
    flagged = []
    for row in rows:
        trace = row.get("trace") or {}
        steps = trace.get("steps") or {}
        reasons = []
        if row.get("issues"):
            reasons.append("실행 검사 실패")
        if any(item.get("status") == "MODEL_ERROR" for item in trace.get("modelAttempts", [])):
            reasons.append("모델 처리 오류")
        if any(item.get("reason") == "GUARD_EXCEPTION" for item in steps.get("guardResults", [])):
            reasons.append("답변 가드 예외")
        if row.get("routingIndex", -1) >= 60:
            sources = {
                source.get("slotId")
                for entry in steps.get("generationInputs", [])
                for source in entry.get("sources", [])
            }
            if row.get("id") in sources and trace.get("answerBasis") == "NO_EVIDENCE":
                reasons.append("정답 FAQ 전달 후 답변 불가")
        if reasons:
            flagged.append((row["sequence"], row["id"], ", ".join(reasons)))

    lines = [
        "# TELME-132 채팅 실행 요약",
        "",
        f"- 실행 시각: {result['recordedAt']}",
        f"- 코드 SHA-256: `{result['sourceTreeSha256']}`",
        f"- 모델: `{result['model']}`",
        f"- 실행 건수: {len(rows)}",
        f"- 실행 검사 실패: {sum(bool(row.get('issues')) for row in rows)}",
        f"- 답변 근거 유형: {', '.join(f'{key} {count}건' for key, count in basis.items())}",
        "",
        "## 확인할 사례",
        "",
        "| 순번 | ID | 사유 |",
        "| ---: | --- | --- |",
    ]
    lines.extend(f"| {sequence} | `{case_id}` | {reason} |" for sequence, case_id, reason in flagged)
    if not flagged:
        lines.append("| - | - | 자동 검사에서 발견된 사례 없음 |")
    lines.extend([
        "",
        "질문별 검색 근거와 최종 답변은 입력 원시 자료의 `cases`에서 확인한다.",
        "자동 검사 통과는 답변의 정확성을 보증하지 않는다.",
        "",
    ])
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with gzip.open(args.input, "rt", encoding="utf-8") as raw:
        result = json.load(raw)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(summarize(result), encoding="utf-8")
    print(json.dumps({"cases": len(result["cases"]), "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
