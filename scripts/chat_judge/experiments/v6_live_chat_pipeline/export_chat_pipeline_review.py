"""Render actual answers and evidence for scoring failures without rerunning models."""

import argparse
from pathlib import Path

from scripts.chat_judge.experiments.v6_live_chat_pipeline.evaluate_chat_pipeline import load_json


def review_report(result, capture):
    original = {(case["caseId"], index): turn for case in capture["cases"]
                for index, turn in enumerate(case["turns"])}
    lines = ["# 실제 채팅 평가의 오류와 검토 자료", "",
             "실행 실패, Judge 출력 검증 실패, 모호한 판정과 근거 없는 주장을 구분한다.",
             "아래 근거성 판정은 자동 Judge의 결과이며 사람 확정 라벨이 아니다.", ""]
    unscored = [row for row in result["turns"]
                if row["executionStatus"] == "COMPLETED" and row.get("judgeStatus") != "SCORED"]
    review = [row for row in result["turns"] if row.get("requiresReview")]
    lines.extend([f"- 실행 완료 후 Judge 검증 실패: {len(unscored)}건",
                  f"- 검토 필요 판정: {len(review)}건",
                  "- 실행 실패는 위 Judge 검증 실패 수에 포함하지 않는다.", "",
                  "먼저 확인할 사례: " + ", ".join(row["caseId"] for row in unscored + review), ""])
    for row in result["turns"]:
        flags = set(row.get("findings", []))
        if row.get("judgeStatus") == "SCORED" and not flags & {"UNSUPPORTED_CLAIM", "HUMAN_REVIEW", "PROCESSING_FAILURE"}:
            continue
        turn = original[row["caseId"], row["turnIndex"]]
        lines.extend([f"## {row['caseId']} / 질문 {row['turnIndex'] + 1}", "",
                      f"- 실행: {row['executionStatus']}",
                      f"- 실행 오류 코드: {turn.get('errorCode') or '없음'}",
                      f"- 판정: {row.get('judgeStatus')} / {', '.join(row.get('findings', []))}",
                      f"- 질문: {row['question']}", "", "**저장된 답변**", "", row.get("answer") or "답변 없음", ""])
        for record in turn.get("generatorCallRecords", []):
            if record.get("status") != "SUCCESS":
                lines.extend([f"- 생성 기록: {record.get('status')} / {record.get('error_message')}", ""])
        for axis in ("grounding", "quality", "abstention"):
            data = row.get(axis, {})
            if data.get("error"):
                lines.extend([f"**{axis} 검증 오류**: {data['error']}", ""])
            if axis == "grounding":
                for claim in data.get("result", {}).get("claims", []):
                    if claim["verdict"] != "SUPPORTED":
                        lines.extend([f"- {claim['verdict']}: {claim['claim']}", f"  - 판단 이유: {claim['reason']}", ""])
            elif axis == "quality":
                for group in data.get("result", {}).get("groups", []):
                    lines.extend([f"- 하위 질문 {group['groupIndex'] + 1}: {group['outcome']}",
                                  f"  - 판단 이유: {group['reason']}", ""])
        if row.get("sources"):
            lines.extend(["**RAG에 전달된 실제 근거**", ""])
            for source in row["sources"]:
                lines.extend([f"- {source['sourceId']}: {source.get('question', '')}", "", source["answer"], ""])
        if turn["fixture"].get("qualityReferenceGroups"):
            lines.extend(["**질문 충족도 비교용 정답 FAQ (생성 모델에 제공하지 않음)**", ""])
            for reference in turn["fixture"]["qualityReferenceGroups"]:
                lines.extend([f"- {reference['sourceId']}: {reference['question']}", "", reference["answer"], ""])
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("result", type=Path)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if args.out.exists():
        parser.error("Existing review document is preserved; choose a new output")
    args.out.write_text(review_report(load_json(args.result), load_json(args.capture)), encoding="utf-8")


if __name__ == "__main__":
    main()
