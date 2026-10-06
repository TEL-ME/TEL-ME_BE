"""Compare Sonnet 4.6 with the frozen Qwen and GPT-OSS V7 judgments."""

import argparse
from collections import Counter
from datetime import datetime
import gzip
import json
from pathlib import Path

from scripts.chat_judge.experiments.v6_live_chat_pipeline import compare_human_review
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as v6
from scripts.chat_judge.experiments.v7_cross_judge import compare_models
from scripts.chat_judge.experiments.v7_cross_judge.build_v7_capture import V7_DIR


V6_DIR = V7_DIR.parent / "V6-live-chat-pipeline"
DEFAULT_SONNET = V7_DIR / "v7-sonnet-4-6-raw.json.gz"
DEFAULT_QWEN = V7_DIR / "v7-qwen-final-raw.json.gz"
DEFAULT_GPT_OSS = V7_DIR / "v7-gpt-oss-120b-raw.json.gz"
DEFAULT_REPORT = V7_DIR / "v7-sonnet-4-6-comparison.md"
DEFAULT_REVIEW = V7_DIR / "v7-sonnet-4-6-human-review.md"
HUMAN_FILE = V6_DIR / "20261005-heldout40-human-review-lyj.md"
HUMAN_KEY = V6_DIR / "20261005-heldout40-human-review-key.json"


def load(path):
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        return json.load(stream)


def key(row):
    return row["caseId"], row["turnIndex"]


def rows_by_model(doc, alias):
    return {key(row): row["models"][alias] for row in doc["turns"]
            if alias in row.get("models", {})}


def usage(rows):
    totals = Counter()
    durations = []

    def visit(value):
        if isinstance(value, dict):
            if "request" in value and "durationMs" in value:
                totals["requests"] += 1
                durations.append(value["durationMs"])
                if value.get("error"):
                    totals["errors"] += 1
                response_usage = (value.get("rawResponse") or {}).get("usage") or {}
                totals["inputTokens"] += response_usage.get("inputTokens", 0) or 0
                totals["outputTokens"] += response_usage.get("outputTokens", 0) or 0
            for nested in value.values():
                visit(nested)
        elif isinstance(value, list):
            for nested in value:
                visit(nested)

    for row in rows.values():
        visit(row)
    totals["durationSumMs"] = sum(durations)
    totals["durationMeanMs"] = round(sum(durations) / len(durations)) if durations else 0
    totals["durationP95Ms"] = sorted(durations)[int((len(durations) - 1) * .95)] if durations else 0
    return totals


def axis_counts(rows):
    return {axis: dict(Counter(item.get("axisStatus", {}).get(axis, "MISSING")
                               for item in rows.values()))
            for axis in ("grounding", "quality", "abstention")}


def abstention_parts(item):
    return {
        "policyLabel": (item.get("abstentionDecision") or {}).get("label"),
        "answerIsRefusal": (item.get("abstention", {}).get("result") or {}).get("answerIsRefusal"),
        "evidenceAnswerability": (item.get("abstention", {}).get("result") or {}).get("evidenceAnswerability"),
        "questionPartAnswerability": tuple(part.get("evidenceAnswerability") for part in
            item.get("abstention", {}).get("questionParts", [])),
    }


def summarize(model_rows):
    shared = set.intersection(*(set(rows) for rows in model_rows.values()))
    completed = {item for item in shared if all(
        rows[item].get("executionStatus") == "COMPLETED" for rows in model_rows.values())}
    result = {"pairedTurns": len(shared), "completedPairedTurns": len(completed), "axes": {}}
    for axis in ("grounding", "quality", "abstention"):
        values = {name: {item: compare_models.axis_value(rows[item], axis)
                         for item in completed} for name, rows in model_rows.items()}
        scored = {item for item in completed if all(values[name][item] is not None for name in values)}
        full_agreement = sum(len({values[name][item] for name in values}) == 1 for item in scored)
        pairs = {}
        names = tuple(values)
        for left_index, left in enumerate(names):
            for right in names[left_index + 1:]:
                pair_rows = [item for item in completed
                             if values[left][item] is not None and values[right][item] is not None]
                agrees = sum(values[left][item] == values[right][item] for item in pair_rows)
                pairs[f"{left} vs {right}"] = {"scored": len(pair_rows), "agree": agrees,
                                               "disagree": len(pair_rows) - agrees,
                                               "rate": agrees / len(pair_rows) if pair_rows else None}
        disagreements = [item for item in scored
                         if len({values[name][item] for name in values}) != 1]
        missing = [item for item in completed
                   if any(values[name][item] is None for name in values)]
        result["axes"][axis] = {"scoredAllThree": len(scored), "allThreeAgree": full_agreement,
                                "allThreeDisagree": len(disagreements), "pairs": pairs,
                                "disagreementTurns": sorted(disagreements),
                                "missingTurns": [{"turn": item, "models": [
                                    name for name in values if values[name][item] is None]}
                                    for item in sorted(missing)]}
    claim_values = {name: {item: compare_models.claim_verdicts(rows[item])
                           for item in completed} for name, rows in model_rows.items()}
    result["claimVerdictPairs"] = {}
    names = tuple(claim_values)
    for left_index, left in enumerate(names):
        for right in names[left_index + 1:]:
            pairs = [(claim_values[left][item], claim_values[right][item]) for item in completed
                     if claim_values[left][item] is not None and claim_values[right][item] is not None]
            agreements = sum(one == two for one, two in pairs)
            result["claimVerdictPairs"][f"{left} vs {right}"] = {
                "scored": len(pairs), "exactSequenceAgreements": agreements,
                "different": len(pairs) - agreements,
                "rate": agreements / len(pairs) if pairs else None,
            }
    result["abstentionComponents"] = {}
    components = ("policyLabel", "answerIsRefusal", "evidenceAnswerability",
                  "questionPartAnswerability")
    for component in components:
        component_values = {name: {item: abstention_parts(rows[item])[component]
                                   for item in completed
                                   if rows[item].get("axisStatus", {}).get("abstention") == "SCORED"}
                           for name, rows in model_rows.items()}
        component_result = {"allThreeScored": 0, "allThreeAgree": 0, "pairs": {}}
        component_keys = set.intersection(*(set(values) for values in component_values.values()))
        component_result["allThreeScored"] = len(component_keys)
        component_result["allThreeAgree"] = sum(
            len({component_values[name][item] for name in component_values}) == 1
            for item in component_keys)
        for left_index, left in enumerate(names):
            for right in names[left_index + 1:]:
                pairs = [(component_values[left][item], component_values[right][item])
                         for item in set(component_values[left]) & set(component_values[right])]
                agrees = sum(one == two for one, two in pairs)
                component_result["pairs"][f"{left} vs {right}"] = {
                    "scored": len(pairs), "agree": agrees, "disagree": len(pairs) - agrees,
                    "rate": agrees / len(pairs) if pairs else None,
                }
        result["abstentionComponents"][component] = component_result
    return result


def compare_human(alias_rows, human):
    evaluation = {"turns": [{"caseId": item[0], "turnIndex": item[1], **row}
                             for item, row in alias_rows.items()]}
    counts, differences = compare_human_review.compare(human, evaluation)
    return counts, differences


def fmt(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def write(args):
    sonnet_doc = load(args.sonnet)
    qwen_doc = load(args.qwen)
    gpt_doc = load(args.gpt_oss)
    data = {"sonnet-4-6": rows_by_model(sonnet_doc, "sonnet-4-6"),
            "qwen": rows_by_model(qwen_doc, "qwen"),
            "gpt-oss-120b": rows_by_model(gpt_doc, "gpt-oss-120b")}
    comparison = summarize(data)
    human, human_hash = compare_human_review.load_human_review(
        HUMAN_FILE, v6.load_json(HUMAN_KEY))
    human_results = {alias: compare_human(rows, human) for alias, rows in data.items()}
    human_counts, human_diffs = human_results["sonnet-4-6"]
    sonnet_usage = usage(data["sonnet-4-6"])
    score_counts = axis_counts(data["sonnet-4-6"])
    preflight_usage = (sonnet_doc.get("preflight", {}).get("sonnet-4-6", {})
                       .get("rawResponse", {}).get("usage", {}))
    input_tokens = sonnet_usage["inputTokens"] + (preflight_usage.get("inputTokens", 0) or 0)
    output_tokens = sonnet_usage["outputTokens"] + (preflight_usage.get("outputTokens", 0) or 0)
    estimated_usd = input_tokens * 3 / 1_000_000 + output_tokens * 15 / 1_000_000
    elapsed = (datetime.fromisoformat(sonnet_doc["completedAt"]) -
               datetime.fromisoformat(sonnet_doc["createdAt"])).total_seconds()
    usage_audit = sonnet_doc.get("usageAudit", {})
    first_pass = usage_audit.get("firstPassBeforeRetry", {})

    report = [
        "# V7 Claude Sonnet 4.6 교차 검증", "",
        "## 실행 조건", "",
        f"- Sonnet 원시 결과: `{args.sonnet.name}` (SHA-256 `{__import__('hashlib').sha256(args.sonnet.read_bytes()).hexdigest()}`)",
        f"- 동일한 고정 답변을 채점한 Qwen 결과: `{args.qwen.name}`",
        f"- 동일한 고정 답변을 채점한 GPT-OSS 결과: `{args.gpt_oss.name}`",
        "- 채팅 파이프라인은 재실행하지 않았고, 기존 V7 캡처의 509개 턴 중 생성된 답변을 재채점했습니다.",
        "- Bedrock Converse, `jp.anthropic.claude-sonnet-4-6`, Tokyo 리전, 동시 요청 3개.",
        "- Claude 사고 모드는 모든 저장된 Sonnet 요청에서 `thinking.type=disabled`로 비활성화된 것을 확인했습니다. Judge 기준과 스키마는 V7 공통 설정을 사용했습니다.",
        "- API 응답의 토큰 usage를 합산했습니다. 가격 추정은 입력 $3/백만 토큰, 출력 $15/백만 토큰 기준이며, 실제 청구액은 AWS Cost Explorer에서 확인해야 합니다.",
        "", "## 처리량 및 사용량", "",
        f"- V7 캡처 턴: {sonnet_doc.get('summary', {}).get('turns', 0)}; 파이프라인 실패: {sonnet_doc.get('summary', {}).get('pipelineFailed', 0)}.",
        f"- Sonnet 채점 성공: {sonnet_doc.get('summary', {}).get('byModelScored', {}).get('sonnet-4-6', 0)}턴.",
        f"- 최종 파일에 남은 채점 단계 요청: {sonnet_usage['requests']} (오류 {sonnet_usage['errors']}); 입력 {input_tokens:,} tokens; 출력 {output_tokens:,} tokens. 연결 사전 확인 1회는 토큰 합계에 포함했습니다.",
        f"- API 처리시간 합계 {sonnet_usage['durationSumMs']/1000:,.1f}초; 평균 {sonnet_usage['durationMeanMs']:,}ms; P95 {sonnet_usage['durationP95Ms']:,}ms.",
        f"- 전체 실행 경과시간 {elapsed/60:,.1f}분 (재시도 포함). 최종 파일 usage로 계산한 참고 금액은 ${estimated_usd:.4f} USD입니다.",
        "- Sonnet과 Qwen/GPT-OSS의 차이는 모델 차이와 설정 차이를 포함하며, 사고 모드·양자화/모델 계열 차이를 분리한 인과 실험은 아닙니다.",
        "- 재시도 중 이전 미완료 턴의 요청 기록이 교체되어 최종 파일 usage는 실제 누적 호출량이나 최종 청구액이 아닙니다.",
        "", "### Sonnet 축별 채점 상태", "",
        "| 축 | 채점 완료 | 미채점 | 결과 누락 |", "| --- | ---: | ---: | ---: |",
    ]
    for axis, label in (("grounding", "근거성"), ("quality", "답변 충실도"),
                        ("abstention", "답변 보류")):
        stat = score_counts[axis]
        report.append(f"| {label} | {stat.get('SCORED', 0)} | {stat.get('UNSCORED', 0)} | {stat.get('MISSING', 0)} |")
    unresolved_grounding = usage_audit.get("serializedRetry", {}).get(
        "remainingGroundingClaimTextMismatchCases", [])
    report += [
        f"", f"근거성 미채점 {len(unresolved_grounding)}건은 추출 인용이 답변의 정확한 연속 구간이 아니어서 검증기가 거부했습니다: {', '.join(f'`{case_id}`' for case_id in unresolved_grounding)}.",
        "", "## 과금 해석 주의", "",
        f"- 직렬 재시도 전 첫 배치 usage: 입력 {first_pass.get('inputTokensIncludingPreflight', 0):,} tokens, 출력 {first_pass.get('outputTokensIncludingPreflight', 0):,} tokens; 공개 단가 참고 계산 ${first_pass.get('referenceEstimateUsd', 0):.4f}.",
        "- 첫 배치의 호출 제한 실패를 직렬 재실행했습니다. 재시도 전 이미 성공한 같은 턴의 요청 기록이 결과 파일에서 바뀌어 추가 토큰을 독립 합산할 수 없습니다.",
        "- 실행 중 보조 배치가 실수로 중복 시작되어 약 1분 44초 후 중단했습니다. 보조 프로세스의 usage는 저장되지 않아 실제 비용은 여기 적힌 첫 배치 참고 금액보다 높을 수 있습니다.",
        "- $3/$15 per million input/output은 [Sonnet 4.6 공개 가격](https://platform.claude.com/docs/en/models/sonnet-4-6/overview)을 이용한 참고치입니다. Bedrock 모델 카드에 따르면 요금은 Marketplace 공급자 항목에 청구되므로 실제 금액은 AWS Billing/Cost Explorer에서 확인해야 합니다.",
        "", "## 모델 간 일치", "",
        f"비교 가능한 공통 완료 답변: {comparison['completedPairedTurns']}턴.", "",
        "| 항목 | 세 모델 모두 채점 | 세 모델 일치 | 세 모델 불일치 |", "| --- | ---: | ---: | ---: |",
    ]
    for axis, label in (("grounding", "근거성"), ("quality", "질문별 답변 충실도"),
                        ("abstention", "답변 보류 적절성")):
        stat = comparison["axes"][axis]
        report.append(f"| {label} | {stat['scoredAllThree']} | {stat['allThreeAgree']} | {stat['allThreeDisagree']} |")
    report += ["", "### 쌍별 일치율", "", "| 항목 | 비교 모델 | 채점 쌍 | 일치 | 불일치 | 일치율 |",
               "| --- | --- | ---: | ---: | ---: | ---: |"]
    for axis, label in (("grounding", "근거성"), ("quality", "답변 충실도"),
                        ("abstention", "보류 판정 전체 구성 요소")):
        for pair, stat in comparison["axes"][axis]["pairs"].items():
            report.append(f"| {label} | {pair} | {stat['scored']} | {stat['agree']} | {stat['disagree']} | {stat['rate']:.1%} |")
    report += ["", "Claim 분할 순서까지 같아야 일치로 보는 보조 수치입니다. Claim을 다르게 나누면 의미 판정이 비슷해도 불일치로 계산될 수 있습니다.",
               "", "| 비교 모델 | 채점 쌍 | Claim 판정 배열 완전 일치 | 불일치 | 일치율 |", "| --- | ---: | ---: | ---: | ---: |"]
    for pair, stat in comparison["claimVerdictPairs"].items():
        report.append(f"| {pair} | {stat['scored']} | {stat['exactSequenceAgreements']} | {stat['different']} | {stat['rate']:.1%} |")
    report += ["", "보류 판정을 이루는 항목별 일치율입니다. 전체 구성 요소가 동시에 일치하는 비율은 이 표보다 낮을 수 있습니다.",
               "", "| 세부 판정 | 세 모델 모두 채점 | 모두 일치 |", "| --- | ---: | ---: |"]
    for component, label in (("policyLabel", "최종 보류/답변 정책"),
                             ("answerIsRefusal", "답변 문장 자체가 거절인지"),
                             ("evidenceAnswerability", "검색 근거 충분성"),
                             ("questionPartAnswerability", "복합 질문별 근거 충분성")):
        stat = comparison["abstentionComponents"][component]
        report.append(f"| {label} | {stat['allThreeScored']} | {stat['allThreeAgree']} |")
    report += ["", "### 기존 사람 판정 40건과 모델 비교", "",
               f"사람 판정 문서 SHA-256: `{human_hash}`. 이 40건은 보정 회귀셋이며 무작위 표본이나 독립 검증셋이 아닙니다.",
               "", "| 모델 | 항목 | 일치 | 불일치 | 미해결 | 사람 보류 |", "| --- | --- | ---: | ---: | ---: | ---: |"]
    for alias, title in (("sonnet-4-6", "Sonnet 4.6"), ("qwen", "Qwen3 235B"),
                         ("gpt-oss-120b", "GPT-OSS-120B")):
        counts, _ = human_results[alias]
        for axis, label in (("grounding", "근거성"), ("quality", "질문 답변 충실도"),
                            ("answerability", "근거 충분성"), ("abstention", "답변 보류")):
            stat = counts[axis]
            report.append(f"| {title} | {label} | {stat['match']} | {stat['mismatch']} | {stat['unresolved']} | {stat['human_deferred']} |")
    report += ["", "세 모델의 일치는 정답의 증명이 아닙니다. 기존 40건 비교도 소규모 회귀 확인이며 전체 답변 품질을 대표하지 않습니다.",
               "Sonnet의 의견이 다른 항목과 사람 판정 불일치는 별도 검토표에 정리했습니다.", ""]
    args.report.write_text("\n".join(report), encoding="utf-8")

    # Any pairwise disagreement across the three models, plus human-reference differences.
    base_doc = load(V7_DIR / "v7-capture.json.gz")
    sonnet_outer = {key(row): row for row in sonnet_doc["turns"]}
    bases = {(case["caseId"], index): (case, turn)
             for case in base_doc["cases"] for index, turn in enumerate(case["turns"])}
    human_key_by_id = {review_id: (gold["caseId"], gold["turnIndex"])
                       for review_id, gold in human.items()}
    disagreement_set = {turn for stat in comparison["axes"].values()
                        for turn in stat["disagreementTurns"]}
    missing_set = {turn for stat in comparison["axes"].values()
                   for entry in stat["missingTurns"] for turn in [tuple(entry["turn"])]}
    human_mismatch_set = set()
    for entries in human_diffs.values():
        human_mismatch_set.update(human_key_by_id[entry[0]] for entry in entries
                                  if entry[3] in {"MISMATCH", "UNRESOLVED"})
    keys = sorted(disagreement_set | missing_set | human_mismatch_set)
    details = ["# V7 세 모델 교차 검증 사람 검토 목록", "",
               "Qwen3 235B, GPT-OSS-120B, Sonnet 4.6의 판정 불일치, 채점 누락, 기존 사람 판정 자료와의 불일치가 포함된 답변입니다. 모델 다수결을 정답으로 간주하지 말고, 질문·답변·FAQ 근거를 보고 판정해 주세요.", ""]
    for case_id, turn_index in keys:
        base_case, turn = bases.get((case_id, turn_index), ({}, {}))
        details += [f"## {case_id} (turn {turn_index})", "",
                    f"- 질문: {turn.get('fixture', {}).get('question', '')}",
                    f"- 기대 동작: {turn.get('fixture', {}).get('expectedBehavior', '')}",
                    f"- 실제 답변: {(turn.get('outputMessage') or {}).get('content') or '(답변 없음)'}", ""]
        for alias in ("qwen", "gpt-oss-120b", "sonnet-4-6"):
            item = data[alias].get((case_id, turn_index))
            if item:
                compact = {axis: compare_models.axis_value(item, axis)
                           for axis in ("grounding", "quality", "abstention")}
                details.append(f"- {alias}: `{fmt(compact)}`")
        human_item = next((value for value in human.values()
                           if (value["caseId"], value["turnIndex"]) == (case_id, turn_index)), None)
        if human_item:
            details.append(f"- 사람 판정: `{fmt(human_item)}`")
        details += ["- FAQ 근거:"]
        for source in sonnet_outer.get((case_id, turn_index), {}).get("sources", []):
            details.append(f"  - `{source.get('sourceId', source.get('faqId', '?'))}`: {source.get('answer', '').replace(chr(10), ' ')}")
        if not sonnet_outer.get((case_id, turn_index), {}).get("sources"):
            details.append("  - 저장된 FAQ 근거 없음")
        details.append("")
    args.review.write_text("\n".join(details), encoding="utf-8")
    print(json.dumps({"comparisonSummary": {"pairedTurns": comparison["pairedTurns"],
                                             "completedPairedTurns": comparison["completedPairedTurns"],
                                             "axes": {axis: {"scoredAllThree": value["scoredAllThree"],
                                                             "allThreeAgree": value["allThreeAgree"],
                                                             "allThreeDisagree": value["allThreeDisagree"]}
                                                      for axis, value in comparison["axes"].items()},
                                             "humanReviewRows": len(keys)},
                      "sonnetUsage": dict(sonnet_usage),
                      "inputTokensIncludingPreflight": input_tokens,
                      "outputTokensIncludingPreflight": output_tokens,
                      "estimatedUsdAtThreeAndFifteen": estimated_usd,
                      "humanCounts": {axis: dict(counts) for axis, counts in human_counts.items()},
                      "reviewRows": len(keys), "report": str(args.report), "review": str(args.review)},
                     ensure_ascii=False, indent=2, default=str))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sonnet", type=Path, default=DEFAULT_SONNET)
    parser.add_argument("--qwen", type=Path, default=DEFAULT_QWEN)
    parser.add_argument("--gpt-oss", type=Path, default=DEFAULT_GPT_OSS)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--review", type=Path, default=DEFAULT_REVIEW)
    args = parser.parse_args()
    write(args)


if __name__ == "__main__":
    main()
