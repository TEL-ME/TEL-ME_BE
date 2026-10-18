"""판정 검토 페이지(HTML)를 만든다: B의 2순위 판정 쟁점 전부 + A의 2순위 중 20개 표본(고정 시드).
페이지는 판정을 artifact 저장소(verdicts 컬렉션)에 남긴다. 저장된 판정은 human-verdicts.json으로 내보낸다.

사용(저장소 루트에서): python -X utf8 scripts/chat_judge/answer_generation/build_review_page.py <capture-*.json.gz와 runs/가 있는 폴더> <출력.html>
"""
import json, gzip, random, sys
from pathlib import Path
sys.path.insert(0, ".")
from scripts.chat_judge import combine_judges as cj
RES = Path(sys.argv[1]); OUT = Path(sys.argv[2])
M = [("qwen", "Qwen3 235B"), ("deepseek-v3.2-usw2", "DeepSeek V3.2"), ("mistral-large-3", "Mistral Large 3")]
review = json.load(open("scripts/chat_judge/answer_generation/labels/human-review.json", encoding="utf-8"))["priority2_verdicts"]
b = [r for r in review if r["set"] == "B"]
a = [r for r in review if r["set"] == "A"]
random.Random(20261018).shuffle(a)
a = sorted(a[:20], key=lambda r: r["inputId"])
items = []
for s, rows in (("B", b), ("A", a)):
    cap = {c["caseId"]: c for c in json.load(gzip.open(RES / f"capture-{s}.json.gz", "rt", encoding="utf-8"))["cases"]}
    R = {m: {t["caseId"]: t for t in json.load(gzip.open(RES / f"runs/{s}-{m}.json.gz", "rt", encoding="utf-8"))["turns"]} for m, _ in M}
    for row in rows:
        cases = [c for c in cap.values() if c["inputId"] == row["inputId"]]
        cases.sort(key=lambda c: (c["model"] != "exaone", min(c["runs"])))
        turn = cases[0]["turns"][0]
        fx = turn["fixture"]
        answers = []
        for c in cases:
            cid = c["caseId"]
            judges = []
            for m, name in M:
                t = R[m][cid]
                g = (t.get("grounding") or {}).get("result")
                q = (t.get("quality") or {}).get("result")
                ab = ((t.get("abstention") or {}).get("stages") or {})
                ev = (ab.get("evidence") or {}).get("result") or {}
                judges.append({
                    "name": name, "pass": t["answerQualityPass"], "findings": t["findings"],
                    "grounding": g["overall"] if g else None,
                    "claims": [{"claim": x["claim"], "verdict": x["verdict"], "reason": x.get("reason", "")}
                               for x in (g or {}).get("claims", []) if x["verdict"] in ("UNSUPPORTED", "REVIEW")],
                    "groundingError": None if g else (t.get("grounding") or {}).get("error"),
                    "quality": [{"outcome": x["outcome"], "reason": x["reason"]} for x in (q or {}).get("groups", [])],
                    "qualityRule": (t.get("quality") or {}).get("rule"),
                    "abstention": (t.get("abstentionDecision") or {}).get("label"),
                    "evidence": [x["evidenceAnswerability"] for x in ev.get("parts", [])],
                })
            votes = [j["pass"] for j in judges]
            combined = cj.combine_any_fail([cj.turn_labels(R[m][cid]) for m, _ in M])["pass"]
            answers.append({"key": f"{c['inputId']}__{c['model']}__{cid.rsplit('|', 1)[1]}", "model": c["model"],
                            "runs": c["runs"], "text": turn_text if (turn_text := c["turns"][0]["outputMessage"]["content"]) else "",
                            "guard": c.get("guardOutcome"), "combined": combined,
                            "contested": combined is None or (True in votes and False in votes) or None in votes,
                            "judges": judges})
        items.append({"id": row["inputId"], "set": s, "group": cases[0].get("category"),
                      "question": fx["question"], "previous": cases[0].get("previousTurns", []),
                      "conditions": turn.get("confirmedConditions", {}),
                      "expected": fx["expectedBehavior"], "facts": fx["requiredFacts"], "missing": fx.get("missingFact"),
                      "labelReviewed": row.get("labelHumanReviewed"),
                      "sources": [{"id": x.get("slotId"), "q": x["question"], "a": x["answer"]} for x in turn["ragInputs"][0]["searchResults"]],
                      "reference": [{"part": g["questionPart"], "alts": [x["sourceId"] for x in g["alternatives"]]} for g in fx["qualityReferenceGroups"]],
                      "answers": answers})
data = json.dumps(items, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
template = (Path(__file__).resolve().parent / "review_page_template.html").read_text(encoding="utf-8")
OUT.write_text(template.replace("__DATA__", data), encoding="utf-8")
print("입력", len(items), "| 답", sum(len(i["answers"]) for i in items),
      "| 판단 대상", sum(a["contested"] for i in items for a in i["answers"]), "->", OUT)
