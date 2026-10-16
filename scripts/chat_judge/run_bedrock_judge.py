#!/usr/bin/env python3
"""저장한 채팅 캡처를 Bedrock 모델 하나로 판정한다. 답변은 다시 만들지 않는다.

판정 단계(근거성, 충족도, 보류)와 코드 규칙은 vLLM 실행기(evaluate_chat_pipeline)와 같다. 모델마다 따로 돌려 원본을 남기고,
여러 모델을 합치는 것은 combine_judges.py가 판정 뒤에 한다. 유료 호출이라 --allow-bedrock-call이 필요하다.
--prior에 같은 모델의 이전 결과를 주면 판정 입력(답변, 근거, 평가 라벨, 이전 턴)이 그대로인 턴은 그 판정을 재사용하고
(코드 규칙은 지금 기준으로 다시 적용), 바뀐 턴만 모델을 부른다. 근거 정의나 라벨이 바뀐 턴만 다시 판정하는 용도다.
판정 입력 해시가 없는 예전 결과는 그 결과를 만든 캡처를 --prior-capture로 함께 준다.
사용: python -X utf8 -m scripts.chat_judge.run_bedrock_judge <capture.json(.gz)> --model deepseek-v3.2 --allow-bedrock-call --out <결과.json.gz>
"""
import argparse
import copy
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import time
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as pipeline

AXES = ("grounding", "quality", "abstention")


# 판정 모델 요청에 들어가는 라벨 필드. 출처 표시(answerSources)나 검수 기록처럼 요청에 안 들어가는 필드는 뺀다
JUDGED_FIXTURE_FIELDS = ("question", "expectedBehavior", "requiredFacts", "qualityReferenceGroups", "missingFact")


def input_key(turn, previous, item):
    """모델 판정에 들어가는 입력의 해시. 같으면 이전 판정을 그대로 쓸 수 있다."""
    fixture = {key: turn["fixture"].get(key) for key in JUDGED_FIXTURE_FIELDS}
    return judge.sha256({"answer": item["answer"], "sources": item["sources"], "fixture": fixture,
                         "previous": previous})


def prior_input_keys(prior, prior_capture, catalog):
    from scripts.chat_judge.experiments.v7_cross_judge import run_cross_judge
    keys = {(row["caseId"], row["turnIndex"]): row["judgeInputSha256"]
            for row in prior.get("turns", []) if "judgeInputSha256" in row}
    if prior_capture is not None:
        for turn, previous, item in run_cross_judge.build_jobs(prior_capture, catalog):
            keys.setdefault((item["caseId"], item["turnIndex"]), input_key(turn, previous, item))
    missing = [row for row in prior.get("turns", []) if (row["caseId"], row["turnIndex"]) not in keys]
    if missing:
        raise ValueError("이전 결과에 판정 입력 해시가 없는 턴이 있다. 그 결과를 만든 캡처를 --prior-capture로 준다")
    return keys


def evaluate(capture, catalog, alias, profile="telme-bedrock-eval", workers=6, limit=0, client=None, prior=None,
             prior_capture=None):
    """완료된 턴마다 세 축을 판정하고 실행기와 같은 규칙으로 집계한다. 실행 실패 턴도 분모에 남긴다."""
    from scripts.chat_judge.experiments.v7_cross_judge import bedrock, run_cross_judge
    judge_client = bedrock.BedrockJudge(alias, profile, client=client)
    jobs = run_cross_judge.build_jobs(capture, catalog)
    if limit:
        jobs = jobs[:limit]
    if prior is not None and prior.get("alias") != alias:
        raise ValueError(f"--prior는 같은 모델 결과여야 한다: {prior.get('alias')} != {alias}")
    previous_items = {(row["caseId"], row["turnIndex"]): row for row in (prior or {}).get("turns", [])}
    previous_keys = prior_input_keys(prior, prior_capture, catalog) if prior is not None else {}
    reused = set()

    def run(job):
        turn, previous, item = job
        item = copy.deepcopy(item)
        key = (item["caseId"], item["turnIndex"])
        item["judgeInputSha256"] = input_key(turn, previous, item)
        old = previous_items.get(key)
        if old is not None and previous_keys.get(key) == item["judgeInputSha256"]:
            for axis in (*AXES, "modelQuality"):
                if axis in old:
                    item[axis] = copy.deepcopy(old[axis])
            reused.add((item["caseId"], item["turnIndex"]))
        elif turn["executionStatus"] == "COMPLETED" and turn.get("outputMessage"):
            grounding_data = judge.prompt_data(turn, previous, "grounding")
            grounding_data["sources"] = item["sources"]
            item["grounding"] = judge_client.grounding(grounding_data)
            item["quality"] = judge_client.quality(turn, previous)
            abstention_data = judge.prompt_data(turn, previous, "adequacy")
            abstention_data["sources"] = item["sources"]
            abstention_data["questionParts"] = [group["questionPart"]
                                                for group in pipeline.reference_groups(turn["fixture"])]
            item["abstention"] = judge_client.abstention(abstention_data)
        return pipeline.finalize_turn(turn, item)

    started = time.monotonic()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        turns = list(pool.map(run, jobs))
    usage = token_usage([item for item in turns if (item["caseId"], item["turnIndex"]) not in reused])
    price = bedrock.PRICES.get(alias)
    planned = capture.get("plannedTurnCount", len(turns)) if not limit else len(turns)
    return {"schemaVersion": 1, "kind": "BEDROCK_JUDGE", "createdAt": datetime.now(timezone.utc).isoformat(),
            "alias": alias, "model": bedrock.MODELS[alias],
            "identity": {"captureSha256": judge.sha256(capture), "catalogSha256": judge.sha256(catalog),
                         "judgeCodeSha256": judge.sha256(Path(judge.__file__).read_bytes()),
                         "evaluatorSha256": judge.sha256(Path(pipeline.__file__).read_bytes()),
                         "bedrockCodeSha256": judge.sha256(Path(bedrock.__file__).read_bytes())},
            "turns": turns, "summary": pipeline.summarize(turns, planned),
            "bySuite": {suite: pipeline.summarize([item for item in turns if item["suite"] == suite])
                        for suite in sorted({item["suite"] for item in turns})},
            "wallSeconds": round(time.monotonic() - started, 1), "usage": usage,
            "reusedTurns": len(reused), "reusedFrom": prior.get("identity") if prior else None,
            "estimatedCostUsd": round(usage["promptTokens"] / 1e6 * price[0]
                                      + usage["outputTokens"] / 1e6 * price[1], 4) if price else None}


def token_usage(turns):
    """판정 기록 안의 모든 모델 호출 토큰과 호출 수를 합친다(재시도와 실패 포함). 코드 규칙 판정은 호출이 아니다."""
    totals = {"calls": 0, "promptTokens": 0, "outputTokens": 0, "failedCalls": 0, "durationsMs": []}

    def walk(value):
        if isinstance(value, dict):
            if "inputSha256" in value and ("rawResponse" in value or "error" in value):
                totals["calls"] += 1
                totals["promptTokens"] += value.get("promptTokens") or 0
                totals["outputTokens"] += value.get("outputTokens") or 0
                totals["failedCalls"] += "error" in value
                if value.get("durationMs") is not None:
                    totals["durationsMs"].append(value["durationMs"])
            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)
    for turn in turns:
        walk({axis: turn.get(axis) for axis in AXES})
    durations = sorted(totals.pop("durationsMs"))
    totals["callP50Ms"] = durations[len(durations) // 2] if durations else None
    return totals


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--model", required=True, help="bedrock.py MODELS의 별칭")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--profile", default="telme-bedrock-eval")
    parser.add_argument("--workers", type=int, default=6)
    parser.add_argument("--limit", type=int, default=0, help="앞에서부터 N턴만(연결 확인용)")
    parser.add_argument("--prior", type=Path, help="같은 모델의 이전 결과. 판정 입력이 같은 턴은 판정을 재사용한다")
    parser.add_argument("--prior-capture", type=Path, help="--prior를 만든 캡처(판정 입력 해시가 없는 예전 결과용)")
    parser.add_argument("--allow-bedrock-call", action="store_true", help="유료 Bedrock 호출 허가")
    args = parser.parse_args()
    if not args.allow_bedrock_call:
        parser.error("Bedrock 호출은 유료라 --allow-bedrock-call이 필요하다")
    if args.out.exists():
        parser.error("기존 결과를 덮어쓰지 않는다. 새 경로를 고른다")
    result = evaluate(pipeline.load_json(args.capture), pipeline.load_json(judge.DEFAULT_CATALOG), args.model,
                      args.profile, args.workers, args.limit,
                      prior=pipeline.load_json(args.prior) if args.prior else None,
                      prior_capture=pipeline.load_json(args.prior_capture) if args.prior_capture else None)
    pipeline.checkpoint(args.out, result)
    print(json.dumps({key: result[key] for key in ("alias", "usage", "estimatedCostUsd", "wallSeconds", "reusedTurns")}
                     | {"qualitySuccess": result["summary"]["qualitySuccess"]}, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
