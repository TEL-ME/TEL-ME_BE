"""13번 답변 생성 결과를 TELME-180 Judge 캡처 형식으로 바꾼다. 모델을 부르지 않는다.

- 같은 입력에 같은 답이 여러 번 나오면 한 번만 판정한다(caseId = 입력|모델|u번호, runs에 몇 회차 답인지 남긴다)
- 근거는 생성에 실제로 넘긴 searchResults(ragInputs)다
- B는 앞 턴 대화(고객 발화, 캡처 때 앱 응답)를 case.previousTurns에 넣는다. judge_answers.py가 이걸 이전 대화로 쓴다
- 라벨의 sourceIds는 카탈로그 answer 본문으로 채운다

사용: python -I -X utf8 scripts/chat_judge/answer_generation/to_capture.py <A|B> <inputs.json> <labels.jsonl> <out.json.gz> <answers.jsonl(.gz)>... [--capture capture.jsonl]
"""
import argparse
import gzip
import json
from collections import defaultdict
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[2] / "data/faq_full_1150.json"


def load_lines(path):
    opener = gzip.open if str(path).endswith(".gz") else open
    with opener(path, "rt", encoding="utf-8") as stream:
        return [json.loads(line) for line in stream if line.strip()]


def fixture(label, question, catalog):
    groups = []
    for part in label["parts"]:
        alternatives = [{"sourceId": sid, "answer": catalog[sid]} for sid in part["sourceIds"]]
        groups.append({"questionPart": part["questionPart"], "alternatives": alternatives})
    value = {"question": question, "expectedIntent": "FAQ", "expectedBehavior": label["expectedBehavior"],
             "goldSourceGroups": [part["sourceIds"] for part in label["parts"]],
             "requiredFacts": label["requiredFacts"], "qualityReferenceGroups": groups,
             "answerSources": label.get("answerSources", []), "labelProvenance": label.get("provenance", "agent")}
    if label.get("missingFact"):
        value["missingFact"] = label["missingFact"]
    return value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("set", choices=["A", "B"])
    parser.add_argument("inputs")
    parser.add_argument("labels")
    parser.add_argument("out")
    parser.add_argument("answers", nargs="+")
    parser.add_argument("--capture")
    args = parser.parse_args()
    catalog = {row["slot_id"]: row["answer"] for row in json.loads(CATALOG.read_text(encoding="utf-8"))}
    inputs = {item["id"]: item for item in json.loads(Path(args.inputs).read_text(encoding="utf-8"))}
    labels = {row["id"]: row for row in load_lines(args.labels)}
    missing = set(inputs) - set(labels)
    if missing:
        raise SystemExit(f"라벨 없는 입력 {len(missing)}개: {sorted(missing)[:5]}")
    unknown = {sid for row in labels.values() for part in row["parts"] for sid in part["sourceIds"]} - set(catalog)
    if unknown:
        raise SystemExit(f"카탈로그에 없는 sourceId: {sorted(unknown)}")
    rows = {}
    if args.capture:
        rows = {(r["scenarioId"], r["turn"]): r for r in load_lines(args.capture)}
    answers = defaultdict(lambda: defaultdict(list))  # (id, model) -> answer -> [record]
    for path in args.answers:
        for record in load_lines(path):
            answers[(record["id"], record["model"])][record["answer"]].append(record)
    cases = []
    for (item_id, model), unique in sorted(answers.items()):
        item = inputs[item_id]
        meta = item["meta"]
        previous = []
        if args.set == "B":
            previous = [{"question": rows[(meta["scenarioId"], turn)]["question"],
                         "answer": rows[(meta["scenarioId"], turn)]["outputContent"] or ""}
                        for turn in range(meta["turn"])]
        conditions = {item.get("conditionLabels", {}).get(key, key): value
                      for key, value in (item.get("conditions") or {}).items()}
        # 되묻기에 답한 턴은 생성 입력이 원래 질문이고 이번 고객 발화는 조건 답이다. Judge에는 TELME-180의
        # clarification_dialogue처럼 원래 질문·되묻기를 이전 대화로, 이번 고객 발화를 판정 질문으로 넘긴다
        question = item["userQuery"]
        if args.set == "B" and meta["turn"] > 0 and meta["customerTurn"] != item["userQuery"]:
            question = meta["customerTurn"]
        for index, (answer, records) in enumerate(sorted(unique.items(), key=lambda pair: min(r["run"] for r in pair[1]))):
            first = records[0]
            turn = {"fixture": fixture(labels[item_id], question, catalog),
                    "executionStatus": "COMPLETED",
                    "outputMessage": {"content": answer, "messageType": "ANSWER", "answerBasis": first.get("answerBasis")},
                    "ragInputs": [{"searchResults": item["searchResults"]}],
                    # 검색 단계 기록은 따로 없어 생성에 넘긴 근거를 그대로 둔다(검색 적중 집계는 이 비교에서 쓰지 않는다)
                    "searches": [{"query": item.get("resolvedQuery") or item["userQuery"], "results": item["searchResults"]}],
                    "confirmedConditions": conditions, "route": {"intent": "FAQ"}}
            cases.append({"caseId": f"{item_id}|{model}|u{index}", "suite": f"{args.set}:{model}",
                          "category": meta.get("group") or meta.get("set"), "previousTurns": previous,
                          "inputId": item_id, "model": model, "runs": sorted(r["run"] for r in records),
                          "guardOutcome": first.get("guardOutcome"), "turns": [turn]})
    capture = {"schemaVersion": 1, "kind": "ANSWER_GENERATION_JUDGE_INPUT", "set": args.set,
               "plannedTurnCount": len(cases), "cases": cases}
    with gzip.open(args.out, "wt", encoding="utf-8") as stream:
        json.dump(capture, stream, ensure_ascii=False)
    by_model = defaultdict(int)
    for case in cases:
        by_model[case["model"]] += 1
    print(f"{args.set}: 입력 {len(inputs)}개, 판정 턴 {len(cases)}개 {dict(by_model)} -> {args.out}")


if __name__ == "__main__":
    main()
