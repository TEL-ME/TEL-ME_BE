"""사람 검토 목록. 1순위: 라벨 쟁점(검증 판단 불가 + 앱 규칙과 충돌하는 라벨). 2순위: 두 모델의 승패가 갈린 입력 중
그 판정이 Judge끼리 갈렸거나 미확정인 답. 작성자 확신 낮음만 있는 항목은 결과에 영향이 있는 것만 2순위로 들어간다."""
import json, gzip, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from scripts.chat_judge import combine_judges as cj
J = Path(__file__).resolve().parent / "labels"
RESULTS = Path(sys.argv[1])  # capture-*.json.gz와 runs/가 있는 폴더(raw.tar.xz를 푼 곳)
labels = {r["id"]: r for r in json.loads((J / "review.json").read_text(encoding="utf-8"))}
PROMPT_CONFLICT = {"HALLU-001", "HALLU-002"}
out = {"priority1_labels": [], "priority2_verdicts": []}
for rid, row in labels.items():
    if rid in PROMPT_CONFLICT or any(r.startswith("검증 판단 불가") for r in row["reasons"]):
        reasons = list(row["reasons"])
        if rid in PROMPT_CONFLICT:
            reasons.append("답변 프롬프트 4번 규칙(근거 숫자를 더하거나 곱하지 말 것)과 충돌")
        out["priority1_labels"].append({"id": rid, "set": row["set"], "question": row["question"],
                                        "conversation": row["conversation"], "label": row["verified"]["label"],
                                        "reasons": reasons})
for s in "AB":
    cap = {c["caseId"]: c for c in json.load(gzip.open(RESULTS / f"capture-{s}.json.gz", "rt", encoding="utf-8"))["cases"]}
    R = {m: {t["caseId"]: t for t in json.load(gzip.open(RESULTS / f"runs/{s}-{m}.json.gz", "rt", encoding="utf-8"))["turns"]}
         for m in ("qwen", "deepseek-v3.2-usw2", "mistral-large-3")}
    by = {(c["inputId"], c["model"]): [] for c in cap.values()}
    for cid, c in cap.items():
        by[(c["inputId"], c["model"])].append(cid)
    for (iid, model), cids in sorted(by.items()):
        if model != "exaone":
            continue
        groups = {"exaone": cids, "gptoss": by[(iid, "gptoss")]}
        verdict = {m: {cid: cj.combine_any_fail([cj.turn_labels(R[j][cid]) for j in R])["pass"] for cid in g}
                   for m, g in groups.items()}
        e = list(verdict["exaone"].values())[0]
        gs = set(verdict["gptoss"].values())
        decisive = e is None or None in gs or (e is True and False in gs) or (e is False and True in gs)
        if not decisive:
            continue
        contested = [cid for g in groups.values() for cid in g
                     if verdict["exaone" if cid in groups["exaone"] else "gptoss"][cid] is None
                     or len({R[j][cid]["answerQualityPass"] for j in R} - {None}) > 1]
        if not contested:
            continue
        fx = cap[cids[0]]["turns"][0]["fixture"]
        out["priority2_verdicts"].append({
            "inputId": iid, "set": s, "question": fx["question"], "previousTurns": cap[cids[0]]["previousTurns"],
            "expectedBehavior": fx["expectedBehavior"], "requiredFacts": fx["requiredFacts"],
            "labelReview": iid in labels,
            "answers": [{"caseId": cid, "model": cap[cid]["model"], "runs": cap[cid]["runs"],
                         "answer": cap[cid]["turns"][0]["outputMessage"]["content"],
                         "combined": verdict[cap[cid]["model"]][cid],
                         "byJudge": {j[:8]: {"pass": R[j][cid]["answerQualityPass"], "findings": R[j][cid]["findings"]}
                                     for j in R}} for g in groups.values() for cid in g]})
(J / "human-review.json").write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
print("1순위 라벨", len(out["priority1_labels"]), "| 2순위 판정(입력)", len(out["priority2_verdicts"]),
      {s: sum(r["set"] == s for r in out["priority2_verdicts"]) for s in "AB"})
