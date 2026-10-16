#!/usr/bin/env python3
"""실행 기록과 요금제 표가 없는 예전 캡처를 평가 DB에서 채운다. 답변은 바꾸지 않는다.

지금 프로브는 턴마다 pipelineTrace, 실행 전체에 serviceData(요금제 표, 가입 연령)를 남긴다. 그 전에 만든 캡처는
평가 DB(chat_executions.pipeline_trace, plans)가 남아 있으면 이 스크립트로 같은 형식을 채운다.
실행 ID마다 DB의 사용자 질문이 캡처 질문과 같은지 확인해 다른 실행으로 덮인 DB를 쓰지 않는다.
사용: python -X utf8 -m scripts.chat_judge.backfill_capture_service_data <capture.json(.gz)> --out <새 캡처.json.gz>
"""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import subprocess

from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as pipeline

ROOT = Path(__file__).resolve().parents[2]
ELIGIBILITY = ROOT / "src/main/java/com/telme/subscription/service/SubscriptionEligibilityChecker.java"


def query_json(container, database, sql):
    output = subprocess.run(["docker", "exec", container, "psql", "-U", "telme", "-d", database, "-At", "-c", sql],
                            capture_output=True, text=True, encoding="utf-8", check=True).stdout.strip()
    return json.loads(output) if output else None


def plan_eligibility(source=ELIGIBILITY):
    text = source.read_text(encoding="utf-8")
    value = {name: int(re.search(rf"{constant}\s*=\s*(\d+)", text).group(1)) for name, constant in (
        ("youthMinAge", "YOUTH_MIN_AGE"), ("youthMaxAge", "YOUTH_MAX_AGE"), ("seniorMinAge", "SENIOR_MIN_AGE"))}
    return value


def backfill(capture, rows, plans, eligibility):
    by_id = {row["executionId"]: row for row in rows}
    for case in capture["cases"]:
        for turn in case["turns"]:
            row = by_id.get(turn["executionId"])
            if row is None:
                raise ValueError(f"평가 DB에 실행 기록이 없다: {case['caseId']} executionId={turn['executionId']}")
            if row["question"] != turn["fixture"]["question"]:
                raise ValueError(f"실행 ID의 질문이 캡처와 다르다(DB가 다시 쓰였다): {case['caseId']} "
                                 f"executionId={turn['executionId']}")
            turn["pipelineTrace"] = row["trace"]
    capture["serviceData"] = {"plans": plans, "planEligibility": eligibility}
    return capture


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--postgres-container", default="telme-flow-postgres")
    args = parser.parse_args()
    if args.out.exists():
        parser.error("기존 파일을 덮어쓰지 않는다")
    capture = pipeline.load_json(args.capture)
    database = capture["database"]
    ids = ",".join(str(int(turn["executionId"])) for case in capture["cases"] for turn in case["turns"])
    rows = query_json(args.postgres_container, database,
                      "SELECT json_agg(json_build_object('executionId', e.execution_id, 'trace', e.pipeline_trace, "
                      "'question', m.content)) FROM chat_executions e JOIN chat_messages m "
                      f"ON m.message_id = e.input_message_id WHERE e.execution_id IN ({ids})") or []
    plans = query_json(args.postgres_container, database,
                       "SELECT json_agg(t) FROM (SELECT code, name, network_type, target_group, monthly_fee, data_gb, "
                       "is_unlimited, voice_minutes, is_voice_unlimited, sms_count, is_sms_unlimited, status, sort_order "
                       "FROM plans ORDER BY sort_order, plan_id) t") or []
    result = backfill(capture, rows, plans, plan_eligibility())
    result["serviceDataBackfill"] = {"at": datetime.now(timezone.utc).isoformat(),
                                     "postgresContainer": args.postgres_container, "database": database,
                                     "source": str(args.capture)}
    pipeline.checkpoint(args.out, result)
    traced = sum(bool(turn["pipelineTrace"]) for case in result["cases"] for turn in case["turns"])
    plan_turns = sum(bool((turn["pipelineTrace"] or {}).get("planAnswer"))
                     for case in result["cases"] for turn in case["turns"])
    print(json.dumps({"turns": len(rows), "traced": traced, "planAnswerTurns": plan_turns, "plans": len(plans),
                      "out": str(args.out)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
