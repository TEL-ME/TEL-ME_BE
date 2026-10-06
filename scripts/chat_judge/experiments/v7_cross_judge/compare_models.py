"""Compare the frozen Qwen and GPT-OSS V7 judgements without invoking Bedrock."""

import argparse
from collections import Counter
from datetime import datetime
import gzip
import json
from pathlib import Path
import statistics

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v6_live_chat_pipeline import compare_human_review as human_review
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as v6
from scripts.chat_judge.experiments.v7_cross_judge.build_v7_capture import V7_DIR


V6_DIR = V7_DIR.parent / "V6-live-chat-pipeline"
DEFAULT_QWEN = V7_DIR / "v7-qwen-final-raw.json.gz"
DEFAULT_GPT_OSS = V7_DIR / "v7-gpt-oss-120b-raw.json.gz"
DEFAULT_REPORT = V7_DIR / "v7-gpt-oss-120b-comparison.md"
DEFAULT_DISAGREEMENTS = V7_DIR / "v7-gpt-oss-120b-disagreements.md"
DEFAULT_HUMAN_REVIEW = V7_DIR / "v7-gpt-oss-120b-human-review.md"
HUMAN_FILE = V6_DIR / "20261005-heldout40-human-review-lyj.md"
HUMAN_KEY = V6_DIR / "20261005-heldout40-human-review-key.json"


def load(path):
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        return json.load(stream)


def turn_key(turn):
    return turn["caseId"], turn["turnIndex"]


def model_map(path, alias):
    data = load(path)
    return data, {turn_key(row): row["models"][alias]
                  for row in data["turns"] if alias in row.get("models", {})}


def axis_value(item, axis):
    if not item or item.get("axisStatus", {}).get(axis) != "SCORED":
        return None
    value = item.get(axis, {}).get("result", {})
    if axis == "grounding":
        return value.get("overall")
    if axis == "quality":
        return tuple(group.get("outcome") for group in value.get("groups", []))
    if axis == "abstention":
        return ((item.get("abstentionDecision") or {}).get("label"),
                value.get("answerIsRefusal"), value.get("evidenceAnswerability"),
                tuple(part.get("evidenceAnswerability")
                      for part in item.get("abstention", {}).get("questionParts", [])))
    raise ValueError(axis)


def claim_verdicts(item):
    if not item or item.get("axisStatus", {}).get("grounding") != "SCORED":
        return None
    return tuple(claim.get("verdict") for claim in
                 item.get("grounding", {}).get("result", {}).get("claims", []))


def stage_stats(rows):
    requests, failures, tokens_in, tokens_out, durations = 0, 0, 0, 0, []

    def visit(value):
        nonlocal requests, failures, tokens_in, tokens_out
        if isinstance(value, dict):
            if "request" in value and "durationMs" in value:
                requests += 1
                durations.append(value["durationMs"])
                if "error" in value:
                    failures += 1
                response = value.get("rawResponse", {})
                usage = response.get("usage", {}) if isinstance(response, dict) else {}
                tokens_in += usage.get("inputTokens", 0) or 0
                tokens_out += usage.get("outputTokens", 0) or 0
            for nested in value.values():
                visit(nested)
        elif isinstance(value, list):
            for nested in value:
                visit(nested)

    for item in rows.values():
        visit(item)
    ordered = sorted(durations)
    p95 = ordered[min(len(ordered) - 1, int(len(ordered) * 0.95))] if ordered else 0
    return {"requests": requests, "stageErrors": failures, "inputTokens": tokens_in,
            "outputTokens": tokens_out, "durationSumMs": sum(durations),
            "durationMeanMs": statistics.mean(durations) if durations else 0,
            "durationP95Ms": p95}


def human_match_counts(alias, rows, human):
    evaluation = {"turns": []}
    for (case_id, turn_index), item in rows.items():
        evaluation["turns"].append({"caseId": case_id, "turnIndex": turn_index, **item})
    counts, _ = human_review.compare(human, evaluation)
    return counts


def summarize(qwen, gpt):
    keys = set(qwen) & set(gpt)
    completed = {key for key in keys if qwen[key].get("executionStatus") == "COMPLETED"
                 and gpt[key].get("executionStatus") == "COMPLETED"}
    result = {"modelTurns": (len(qwen), len(gpt)), "pairedTurns": len(keys),
              "completedPairedTurns": len(completed), "axes": {}, "anyDisagreement": set(),
              "rows": []}
    for axis in ("grounding", "quality", "abstention"):
        pairs = [(key, axis_value(qwen[key], axis), axis_value(gpt[key], axis))
                 for key in completed]
        scored = [(key, left, right) for key, left, right in pairs
                  if left is not None and right is not None]
        disagreements = [(key, left, right) for key, left, right in scored if left != right]
        missing_qwen = sum(left is None for _, left, _ in pairs)
        missing_gpt = sum(right is None for _, _, right in pairs)
        result["axes"][axis] = {"scoredPairs": len(scored), "agreements": len(scored) - len(disagreements),
                                  "disagreements": len(disagreements), "missingQwen": missing_qwen,
                                  "missingGptOss": missing_gpt,
                                  "agreementRate": ((len(scored) - len(disagreements)) / len(scored)
                                                    if scored else None)}
        for key, left, right in disagreements:
            result["anyDisagreement"].add(key)
            result["rows"].append({"key": key, "axis": axis, "qwen": left, "gpt": right})
    # Claim segmentation can vary even when the overall grounding verdict agrees.
    verdict_pairs = [(key, claim_verdicts(qwen[key]), claim_verdicts(gpt[key])) for key in completed]
    verdict_scored = [(key, left, right) for key, left, right in verdict_pairs
                      if left is not None and right is not None]
    result["claimVerdictAgreement"] = {
        "scoredPairs": len(verdict_scored),
        "agreements": sum(left == right for _, left, right in verdict_scored),
        "different": sum(left != right for _, left, right in verdict_scored),
    }
    result["abstentionComponents"] = {}
    component_getters = {
        "policyLabel": lambda item: (item.get("abstentionDecision") or {}).get("label"),
        "answerIsRefusal": lambda item: (item.get("abstention", {}).get("result") or {}).get("answerIsRefusal"),
        "evidenceAnswerability": lambda item: (item.get("abstention", {}).get("result") or {}).get("evidenceAnswerability"),
        "questionPartAnswerability": lambda item: tuple(part.get("evidenceAnswerability") for part in
            item.get("abstention", {}).get("questionParts", [])),
    }
    for name, getter in component_getters.items():
        pairs = [(getter(qwen[key]), getter(gpt[key])) for key in completed
                 if qwen[key].get("axisStatus", {}).get("abstention") == "SCORED"
                 and gpt[key].get("axisStatus", {}).get("abstention") == "SCORED"]
        result["abstentionComponents"][name] = {
            "scoredPairs": len(pairs), "agreements": sum(left == right for left, right in pairs),
            "disagreements": sum(left != right for left, right in pairs),
        }
    result["turnsWithAnyDisagreement"] = len(result["anyDisagreement"])
    return result


def fmt(value):
    if value is None:
        return "미채점"
    return "`" + json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "`"


def write_outputs(args):
    qwen_doc, qwen = model_map(args.qwen, "qwen")
    gpt_doc, gpt = model_map(args.gpt_oss, "gpt-oss-120b")
    comparison = summarize(qwen, gpt)
    human, human_hash = human_review.load_human_review(HUMAN_FILE, v6.load_json(HUMAN_KEY))
    human_counts = {"Qwen": human_match_counts("qwen", qwen, human),
                    "GPT-OSS-120B": human_match_counts("gpt-oss-120b", gpt, human)}
    qwen_stats, gpt_stats = stage_stats(qwen), stage_stats(gpt)
    summary = gpt_doc.get("summary", {})
    score_count = summary.get("byModelScored", {}).get("gpt-oss-120b", 0)
    pipeline_failed = summary.get("pipelineFailed", 0)
    # `gpt` contains completed-answer turns only; failed pipeline turns are absent.
    unscored = len(gpt) - score_count
    def failed_reasons(rows, axis):
        errors = Counter()
        def visit(value):
            if isinstance(value, dict):
                if value.get("error"):
                    errors[value["error"]] += 1
                for nested in value.values():
                    visit(nested)
            elif isinstance(value, list):
                for nested in value:
                    visit(nested)
        for item in rows.values():
            if item.get("axisStatus", {}).get(axis) != "SCORED":
                visit(item.get(axis) or {})
        return errors
    duration_seconds = (datetime.fromisoformat(gpt_doc["completedAt"]) -
                        datetime.fromisoformat(gpt_doc["createdAt"])).total_seconds()

    lines = [
        "# V7 GPT-OSS-120B 교차 검증 결과", "",
        "## 실행 범위", "",
        f"- 고정 V6 답변: `{args.gpt_oss.name}` (SHA-256 `{judge.sha256(args.gpt_oss.read_bytes())}`)",
        f"- 비교 Qwen 결과: `{args.qwen.name}` (SHA-256 `{judge.sha256(args.qwen.read_bytes())}`)",
        "- 채팅 API와 답변 생성을 다시 실행하지 않고, 저장된 동일 답변·근거를 두 Judge가 재채점했다.",
        "- 실행 리전: `ap-northeast-1`; 모델 ID: `openai.gpt-oss-120b-1:0`; 동시 요청 작업자: 3.",
        "- GPT-OSS 구조화 출력 모드는 유효한 JSON을 보장하지 않아 사용하지 않았다. JSON 형식을 프롬프트로 지정하고 응답 뒤 로컬 스키마·근거 인용 검증을 적용했다.",
        "", "## 처리 결과", "",
        f"- 고정 답변 509건 중 파이프라인 실패 15건은 채점 대상이 아니다. 생성된 답변 494건 중 GPT-OSS가 모든 축을 채점한 건 {score_count}건, 일부 또는 전부 미채점 {unscored}건이다.",
        f"- Qwen은 생성 답변 494건 중 {qwen_doc.get('summary', {}).get('byModelScored', {}).get('qwen', 0)}건을 전체 채점했다.",
        "- 축별 GPT-OSS 출력 검증 실패는 claim extraction/grounding 계열 15건, quality 11건, abstention 6건이다. 동일 응답이 여러 축에서 실패할 수 있어 합계는 미채점 턴 수와 다르다.",
        "", "## Qwen과의 일치", "",
        "일치율은 두 모델 모두 해당 축을 채점한 생성 답변만 분모로 삼았다. Grounding은 전체 판정, 품질은 질문별 결과 배열, 답변 보류는 보류 라벨·거절 여부·근거 충분성·하위 질문 판정이 모두 같을 때 일치로 계산했다.",
        "", "| 축 | 둘 다 채점 | 일치 | 불일치 | 일치율 |", "| --- | ---: | ---: | ---: | ---: |"]
    for axis, label in (("grounding", "근거성 전체 판정"), ("quality", "질문 답변 충실도"),
                        ("abstention", "답변 보류·근거 충분성")):
        stat = comparison["axes"][axis]
        lines.append(f"| {label} | {stat['scoredPairs']} | {stat['agreements']} | {stat['disagreements']} | {stat['agreementRate']:.1%} |")
    lines += ["", "답변 보류 축은 판정 구성요소별로도 비교했다:", "",
              "| 구성요소 | 둘 다 채점 | 일치 | 불일치 |", "| --- | ---: | ---: | ---: |"]
    for component, label in (("policyLabel", "최종 보류 정책 판정"), ("answerIsRefusal", "답변이 거절인지"),
                             ("evidenceAnswerability", "근거 충분성"),
                             ("questionPartAnswerability", "하위 질문별 근거 충분성")):
        stat = comparison["abstentionComponents"][component]
        lines.append(f"| {label} | {stat['scoredPairs']} | {stat['agreements']} | {stat['disagreements']} |")
    lines += ["", f"- 축별 판정 중 하나라도 다른 답변: {comparison['turnsWithAnyDisagreement']}건.",
              f"- Grounding claim verdict 배열까지 완전히 같은 비율: {comparison['claimVerdictAgreement']['agreements']}/{comparison['claimVerdictAgreement']['scoredPairs']}. 문장 분할 차이도 불일치로 잡으므로 보조 지표로만 본다.",
              "- 불일치는 곧 어느 한 모델이 틀렸다는 뜻이 아니다. 아래 검토 목록은 사람 판정 대상으로 사용한다.",
              "", "## 기존 사람 판정 40건과 비교", "",
              f"사람 판정 원본 SHA-256: `{human_hash}`. 기존 회귀 검토 자료이며 새 블라인드 검증셋은 아니다.",
              "", "| 축 | 모델 | 사람 판정 일치 | 불일치 | 미판정 또는 미채점 |", "| --- | --- | ---: | ---: | ---: |"]
    for axis, label in (("grounding", "근거성"), ("quality", "질문 충실도"),
                        ("answerability", "근거 충분성"), ("abstention", "보류 판정")):
        for alias, title in (("Qwen", "Qwen"), ("GPT-OSS-120B", "GPT-OSS-120B")):
            stat = human_counts[alias][axis]
            lines.append(f"| {label} | {title} | {stat['match']} | {stat['mismatch']} | {stat['unresolved'] + stat['human_deferred']} |")
    lines += ["", "## 사용량·처리 시간", "",
              "응답에서 보고된 Bedrock 토큰 사용량과 각 단계 API 왕복시간을 합산했다. 파이프라인 대기시간이나 실제 청구서 금액과는 다르다.",
              "", "| 모델 | 단계 요청 | 입력 토큰 | 출력 토큰 | 단계 응답시간 합 | 평균 / P95 |", "| --- | ---: | ---: | ---: | ---: | ---: |"]
    for name, stat in (("GPT-OSS-120B", gpt_stats), ("Qwen3 235B", qwen_stats)):
        lines.append(f"| {name} | {stat['requests']} | {stat['inputTokens']:,} | {stat['outputTokens']:,} | {stat['durationSumMs']/1000:,.1f}초 | {stat['durationMeanMs']:,.0f} / {stat['durationP95Ms']:,} ms |")
    gpt_cost = gpt_stats["inputTokens"] * 0.1545 / 1_000_000 + gpt_stats["outputTokens"] * 0.618 / 1_000_000
    lines += ["", f"- AWS 공개 요금표에서 확인되는 GPT-OSS-120B 표준 단가는 Sydney 기준 입력 $0.1545/백만 토큰, 출력 $0.618/백만 토큰이다. 이 단가를 단순 적용한 참고액은 약 `${gpt_cost:.4f}`이며, 실제 실행은 Tokyo였고 요금표에 Tokyo 단가가 따로 표시되지 않아 실제 청구액으로 간주하면 안 된다.",
              "- 비용 확정은 AWS Cost and Usage Report 또는 Billing에서 확인해야 한다.",
              "", "## 다음 판단", "",
              "현재 결과는 Qwen과 GPT-OSS 두 모델의 비교다. 합의한 다중 모델 판정 기준에 필요한 Sonnet·Luna가 참여하지 않아 자동 확정이나 전체 답변 품질 점수로 해석할 수 없다. 특히 보류·근거 충분성 판정의 모델 간 불일치가 커서 해당 축을 사람 검토 우선순위로 둔다.",
              ""]
    lines += ["", "## GPT-OSS 미채점 오류 내역", ""]
    for axis in ("grounding", "quality", "abstention"):
        causes = failed_reasons(gpt, axis)
        lines.append(f"- `{axis}`: " + ("; ".join(f"{reason} ({count})" for reason, count in causes.items()) or "없음"))
    lines += ["", f"- 배치 전체 경과시간: {duration_seconds / 60:.1f}분(동시 요청 작업자 3개).",
              "- 사용량 표는 원시 배치 파일에 저장된 채점 요청만 합산했다. 사전 연결 확인과 별도 형식 점검 호출은 토큰 사용량이 결과 파일에 보존되지 않아 제외되어 있으며, 위 비용 참고액은 전체 AWS 청구액이 아니다.", ""]
    args.report.write_text("\n".join(lines), encoding="utf-8")

    disagreement_lines = ["# V7 GPT-OSS-120B와 Qwen 판정 불일치 목록", "",
        "두 모델 모두 해당 축을 채점했지만 결과가 달랐던 항목이다. 불일치는 오류 확정이 아니며 사람 판정 후보를 뜻한다.",
        "", "| caseId | turn | 축 | Qwen | GPT-OSS-120B |", "| --- | ---: | --- | --- | --- |"]
    for row in sorted(comparison["rows"], key=lambda item: (item["key"], item["axis"])):
        disagreement_lines.append(f"| `{row['key'][0]}` | {row['key'][1]} | {row['axis']} | {fmt(row['qwen'])} | {fmt(row['gpt'])} |")
    disagreement_lines += ["", f"총 {len(comparison['rows'])}건의 축별 불일치이며, 같은 답변이 여러 축에서 반복될 수 있다. 고유 답변 수는 {comparison['turnsWithAnyDisagreement']}건이다.", ""]
    args.disagreements.write_text("\n".join(disagreement_lines), encoding="utf-8")
    base_rows = {turn_key(row): row for row in gpt_doc["turns"]}
    by_key = {}
    for item in comparison["rows"]:
        by_key.setdefault(item["key"], []).append(item)
    review_lines = ["# V7 GPT-OSS-120B와 Qwen 불일치 사람 검토", "",
        "두 Judge의 판정이 달라 직접 대조할 답변이다. 모델 판정만으로 정답을 결정하지 않는다.",
        "원시 요청·응답 전체는 `v7-gpt-oss-120b-raw.json.gz`에서 같은 caseId와 turnIndex로 확인한다.", ""]
    for key in sorted(by_key):
        row = base_rows[key]
        review_lines += [f"## {key[0]} (turn {key[1]})", "",
            f"- 기대 동작: {row.get('expectedBehavior')}",
            f"- 질문: {row.get('question', '')}", f"- 실제 답변: {row.get('answer', '') or '(빈 답변)'}", "",
            "| 판정 축 | Qwen | GPT-OSS-120B |", "| --- | --- | --- |"]
        for item in sorted(by_key[key], key=lambda value: value["axis"]):
            review_lines.append(f"| {item['axis']} | {fmt(item['qwen'])} | {fmt(item['gpt'])} |")
        review_lines += ["", "근거 FAQ:", ""]
        if row.get("sources"):
            for source in row["sources"]:
                review_lines += [f"- `{source.get('sourceId', '?')}`: {source.get('answer', '').replace(chr(10), ' ')}"]
        else:
            review_lines.append("- 검색 근거 없음")
        review_lines.append("")
    args.human_review.write_text("\n".join(review_lines), encoding="utf-8")
    print(json.dumps({"summary": summary, "comparison": {**comparison, "anyDisagreement": len(comparison['anyDisagreement']), "rows": len(comparison['rows'])},
                      "human": {alias: {axis: dict(counts) for axis, counts in axes.items()}
                                for alias, axes in human_counts.items()},
                      "gptOssUsage": gpt_stats, "qwenUsage": qwen_stats,
                      "gptOssSydneyPriceProxyUsd": round(gpt_cost, 4),
                      "report": str(args.report), "disagreements": str(args.disagreements),
                      "humanReview": str(args.human_review)},
                     ensure_ascii=False, indent=2, default=str))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--qwen", type=Path, default=DEFAULT_QWEN)
    parser.add_argument("--gpt-oss", type=Path, default=DEFAULT_GPT_OSS)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--disagreements", type=Path, default=DEFAULT_DISAGREEMENTS)
    parser.add_argument("--human-review", type=Path, default=DEFAULT_HUMAN_REVIEW)
    args = parser.parse_args()
    write_outputs(args)


if __name__ == "__main__":
    main()
