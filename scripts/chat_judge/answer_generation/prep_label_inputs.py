"""라벨링 입력 만들기. 작성 에이전트에게는 질문·대화·조건만 주고 정답 FAQ·검색 근거는 숨긴다."""
import json, sys
from pathlib import Path
# 사용: prep_label_inputs.py <작업 폴더> <inputs-a.json> <inputs-b182.json> <capture-b182.jsonl>
S = Path(sys.argv[1]); A = Path(sys.argv[2])
a = json.loads(A.read_text(encoding="utf-8"))
b = json.loads(Path(sys.argv[3]).read_text(encoding="utf-8"))
rows = {(r["scenarioId"], r["turn"]): r for r in map(json.loads, open(sys.argv[4], encoding="utf-8"))}
items, ref = [], {}
for x in a:
    items.append({"id": x["id"], "question": x["userQuery"], "conversation": []})
    ref[x["id"]] = {"set": x["meta"]["set"], "datasetMeta": x["meta"]}
for x in b:
    m = x["meta"]
    conv = [{"customer": rows[(m["scenarioId"], t)]["question"], "app": rows[(m["scenarioId"], t)]["outputContent"]}
            for t in range(m["turn"])]
    item = {"id": x["id"], "question": x["userQuery"], "conversation": conv, "currentCustomerTurn": m["customerTurn"]}
    if x["resolvedQuery"] != x["userQuery"]:
        item["resolvedQuestionByApp"] = x["resolvedQuery"]
    known = {x["conditionLabels"].get(k, k): v for k, v in x["conditions"].items()}
    unknown = [lab for k, lab in x["conditionLabels"].items() if k not in x["conditions"]]
    if known: item["customerConditions"] = known
    if unknown: item["askedButUnanswered"] = unknown
    items.append(item)
    ref[x["id"]] = {"set": "B", "datasetMeta": m}
chunks = {"A1": items[0:55], "A2": items[55:110], "A3": items[110:165], "A4": items[165:217],
          "B1": items[217:261], "B2": items[261:305], "B3": items[305:348]}
for k, v in chunks.items():
    (S / f"label-in/{k}.json").write_text(json.dumps(v, ensure_ascii=False, indent=1), encoding="utf-8")
    print(k, len(v), v[0]["id"], v[-1]["id"])
(S / "label-ref.json").write_text(json.dumps(ref, ensure_ascii=False, indent=1), encoding="utf-8")
