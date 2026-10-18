"""작성 라벨과 검증 결과를 합쳐 최종 라벨(labels-A.jsonl, labels-B.jsonl)과 사람 검토 목록(review.json)을 만든다.

최종 라벨은 검증 에이전트의 label이다(AGREE면 작성 라벨과 같다). 검토 목록에는 DISPUTE, 기대 동작을 바꾼 FIXED,
작성자가 confidence low로 남긴 항목을 넣는다.
"""
import json
from pathlib import Path

import sys
J = Path(sys.argv[1])  # label-in, label-out, verify-out이 있는 작업 폴더. 결과도 여기에 쓴다
CHUNKS = {"A": ["A1", "A2", "A3", "A4"], "B": ["B1", "B2", "B3"]}
BEHAVIORS = {"ANSWER", "PARTIAL_ANSWER", "ABSTAIN", "OUT_OF_SCOPE", "ANSWER_OR_CLARIFY"}
catalog = {row["slot_id"] for row in json.loads(
    Path(__file__).resolve().parents[2] / "data/faq_full_1150.json".read_text(encoding="utf-8"))}


def lines(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


review, totals = [], {}
for name, chunks in CHUNKS.items():
    final = []
    for chunk in chunks:
        inputs = json.loads((J / "label-in" / f"{chunk}.json").read_text(encoding="utf-8"))
        written = {row["id"]: row for row in lines(J / "label-out" / f"{chunk}.jsonl")}
        checked = {row["id"]: row for row in lines(J / "verify-out" / f"{chunk}.jsonl")}
        for item in inputs:
            w, v = written[item["id"]], checked[item["id"]]
            label = dict(v["label"], id=item["id"])
            assert label["expectedBehavior"] in BEHAVIORS, (item["id"], label["expectedBehavior"])
            for part in label["parts"]:
                assert part["sourceIds"] and set(part["sourceIds"]) <= catalog, (item["id"], part)
            if label["expectedBehavior"] in {"ABSTAIN", "OUT_OF_SCOPE"}:
                assert not label["parts"], item["id"]
            label["provenance"] = f"agent_written+agent_verified:{v['verdict']}"
            final.append(label)
            totals[v["verdict"]] = totals.get(v["verdict"], 0) + 1
            reasons = []
            if v["verdict"] == "DISPUTE":
                reasons.append("검증 판단 불가: " + v.get("reviewReason", ""))
            if v["verdict"] == "FIXED" and w["expectedBehavior"] != label["expectedBehavior"]:
                reasons.append(f"기대 동작 변경 {w['expectedBehavior']} → {label['expectedBehavior']}: " + v.get("changes", ""))
            if w.get("confidence") == "low":
                reasons.append("작성자 확신 낮음: " + w.get("note", ""))
            if reasons:
                review.append({"id": item["id"], "set": name, "question": item["question"],
                               "conversation": item.get("conversation", []), "written": w, "verified": v,
                               "reasons": reasons})
    (J / f"labels-{name}.jsonl").write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in final),
                                           encoding="utf-8")
    counts = {}
    for row in final:
        counts[row["expectedBehavior"]] = counts.get(row["expectedBehavior"], 0) + 1
    print(name, len(final), counts)
(J / "review.json").write_text(json.dumps(review, ensure_ascii=False, indent=1), encoding="utf-8")
print("검증:", totals, "| 사람 검토:", len(review))
