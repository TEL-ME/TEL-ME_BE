"""B-1 캡처 결과(AnswerInputCaptureProbe jsonl)를 AnswerGenerationProbe가 읽는 입력(B)으로 바꾼다. 모델을 부르지 않는다.

앞단을 실제로 통과한 뒤 답변 생성 직전에 저장한 입력이라, 검색 근거가 비어 모델을 부르지 않은 턴은 입력에서 뺀다.
캡처 때 앱이 만든 답변은 입력에 넣지 않는다(두 모델이 새로 답한다). 대화 이력에는 앞 턴의 답변이 들어 있다.

사용: python3 -I scripts/answer-generation-evaluation/build_inputs_b.py <캡처.jsonl> [<캡처2.jsonl> ...] [-o 출력 경로]
"""

import argparse
import gzip
import json
from collections import Counter
from pathlib import Path

DEFAULT_OUT = Path(__file__).resolve().parent / "inputs-b.json"


def load(path):
    opener = gzip.open if path.endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as stream:
        return [json.loads(line) for line in stream if line.strip()]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("captures", nargs="+")
    parser.add_argument("-o", "--out", default=str(DEFAULT_OUT))
    args = parser.parse_args()
    rows = [row for path in args.captures for row in load(path)]
    inputs, dropped = [], Counter()
    for row in rows:
        if row.get("error"):
            dropped["캡처 오류"] += 1
            continue
        for index, request in enumerate(row["answerRequests"]):
            if not request["searchResults"]:
                dropped["근거 없음(모델 호출 없음)"] += 1
                continue
            item = {"id": f"{row['scenarioId']}-t{row['turn']}-r{index}", "userQuery": request["userQuery"],
                    "resolvedQuery": request["resolvedQuery"], "conditions": request["conditions"],
                    "conditionLabels": request["conditionLabels"], "searchResults": request["searchResults"],
                    "meta": {"set": "B", "group": row["group"], "source": row["source"],
                             "scenarioId": row["scenarioId"], "turn": row["turn"], "turns": row["turns"],
                             "customerTurn": row["question"], "appOutputType": row.get("outputType"),
                             "appAnswerBasis": row.get("answerBasis")}}
            if request.get("history") or request.get("summary"):
                item["history"] = request.get("history", [])
                item["summary"] = request.get("summary")
            inputs.append(item)
    Path(args.out).write_text(json.dumps(inputs, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"캡처 {len(rows)}턴 → 입력 {len(inputs)}개 → {args.out}")
    print("묶음별:", dict(Counter(item["meta"]["group"] for item in inputs)))
    print("이력이 있는 입력:", sum(1 for item in inputs if item.get("history")),
          "| 조건이 있는 입력:", sum(1 for item in inputs if item["conditions"]),
          "| 해석된 질문이 원문과 다른 입력:", sum(1 for item in inputs if item["resolvedQuery"] != item["userQuery"]))
    print("뺀 것:", dict(dropped))


if __name__ == "__main__":
    main()
