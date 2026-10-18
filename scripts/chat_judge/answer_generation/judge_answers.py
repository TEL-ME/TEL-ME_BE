"""run_bedrock_judge를 그대로 쓰되, case.previousTurns를 이전 대화의 시작값으로 넣는다(B의 앞 턴 대화).
판정 단계·코드 규칙·집계는 TELME-180 코드 그대로다. 인자는 run_bedrock_judge와 같다.

사용(저장소 루트에서): python -X utf8 scripts/chat_judge/answer_generation/judge_answers.py <capture.json.gz> --model qwen --allow-bedrock-call --out <결과.json.gz>
"""
import copy
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

from scripts.chat_judge import judge_chat_flow as judge  # noqa: E402
from scripts.chat_judge import evaluate_chat_pipeline as pipeline  # noqa: E402
from scripts.chat_judge import run_bedrock_judge as runner  # noqa: E402


def build_jobs(capture, catalog):
    judge.validate_capture(capture)
    enriched = pipeline.enrich_rag_sources(capture, catalog)
    jobs = []
    for case in enriched["cases"]:
        previous = copy.deepcopy(case.get("previousTurns", []))
        for index, turn in enumerate(case["turns"]):
            sources, provenance = pipeline.generation_sources(turn)
            item = {"caseId": case["caseId"], "turnIndex": index, "category": case.get("category"),
                    "suite": case.get("suite", "legacy_capture"), "question": turn["fixture"]["question"],
                    "expectedBehavior": turn["fixture"]["expectedBehavior"],
                    "executionStatus": turn["executionStatus"], "pipelineDurationMs": turn.get("durationMs"),
                    "answer": (turn.get("outputMessage") or {}).get("content"), "sources": sources,
                    "sourceProvenance": provenance, "savedSources": turn.get("savedSources", [])}
            jobs.append((turn, copy.deepcopy(previous), item))
            previous.extend(judge.completed_dialogue(turn, item["answer"]))
    return jobs


runner.build_jobs = build_jobs

if __name__ == "__main__":
    runner.main()
