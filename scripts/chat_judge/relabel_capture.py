#!/usr/bin/env python3
"""저장한 캡처의 평가 라벨(fixture)을 새 평가셋 버전으로 바꾼다. 답변과 실행 기록은 바꾸지 않는다.

라벨 버전을 올리면 같은 답변을 새 라벨로 다시 채점한다(평가 기준 1절). 질문 문장이 다른 턴이 있으면 멈춘다.
사용: python -X utf8 -m scripts.chat_judge.relabel_capture <capture.json(.gz)> --fixture <평가셋.json> --out <새 캡처.json.gz>
"""
import argparse
import copy
import json
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as pipeline


def relabel(capture, fixtures):
    by_id = {case["caseId"]: case for case in fixtures}
    changed = 0
    for case in capture["cases"]:
        labeled = by_id.get(case["caseId"])
        if labeled is None or len(labeled["turns"]) != len(case["turns"]):
            raise ValueError(f"평가셋에 같은 대화가 없다: {case['caseId']}")
        for key in ("category", "suite", "questionType"):
            if key in labeled:
                case[key] = labeled[key]
        for turn, fixture in zip(case["turns"], labeled["turns"]):
            if fixture["question"] != turn["fixture"]["question"]:
                raise ValueError(f"질문이 다르다: {case['caseId']}")
            changed += fixture != turn["fixture"]
            turn["fixture"] = copy.deepcopy(fixture)
    return changed


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("capture", type=Path)
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if args.out.exists():
        parser.error("기존 파일을 덮어쓰지 않는다")
    capture = pipeline.load_json(args.capture)
    fixtures = json.loads(args.fixture.read_text(encoding="utf-8"))
    changed = relabel(capture, fixtures)
    capture["fixtureReplacedFrom"] = {"path": args.fixture.as_posix(), "sha256": judge.sha256(fixtures),
                                      "source": args.capture.as_posix()}
    pipeline.checkpoint(args.out, capture)
    print(json.dumps({"changedTurns": changed, "out": str(args.out)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
