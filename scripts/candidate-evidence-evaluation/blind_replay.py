"""TELME-184 후보 근거 검증 블라인드 확인.

extract: 블라인드 캡처에서 근거 검증 입력과 후보를 꺼내고, 하위 질문을 고객 문장의 어느 부분인지(글자 2-gram 겹침) 맞춘다.
score:   판정 방식·모델별 앱 채택 결과를 부분별 정답 FAQ(FAQ 원본 전체 기준 slot_id)로 센다.
         P5가 고객 원문으로 더 붙인 후보(addedCandidates)도 원본 FAQ의 문답 완전 일치로 slot_id를 찾아 대조한다.
fixture: 캡처에서 캡처에 넣은 대화(--fixture 파일)를 되살린다. 비교 캡처에도 쓴다.
.gz 파일은 압축을 풀지 않고 읽는다.
"""

import collections
import gzip
import io
import json
import statistics
import sys
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[2] / "scripts" / "data" / "faq_full_1150.json"


def open_text(path):
    return gzip.open(path, "rt", encoding="utf-8") if str(path).endswith(".gz") else io.open(path, encoding="utf-8")


def load(path):
    with open_text(path) as source:
        return json.load(source)


def slot_pairs():
    return {(faq["question"], faq["answer"]): faq["slot_id"] for faq in load(CATALOG)}


def bigrams(text):
    text = "".join(text.split())
    return {text[i:i + 2] for i in range(len(text) - 1)}


def match_part(question, parts):
    scores = []
    for index, part in enumerate(parts):
        a, b = bigrams(question), bigrams(part["part"])
        scores.append((len(a & b) / max(1, len(a)), index))
    scores.sort(reverse=True)
    best, index = scores[0]
    second = scores[1][0] if len(scores) > 1 else 0
    return index, round(best, 2), round(best - second, 2)


def fixture(capture_path, out_path):
    # 캡처에 넣은 대화(--fixture)를 캡처에서 그대로 되살린다. 캡처는 턴마다 받은 fixture를 남긴다
    keys = ["caseId", "category", "suite", "questionType", "actor"]
    cases = [{**{key: case[key] for key in keys}, "turns": [turn["fixture"] for turn in case["turns"]]}
             for case in load(capture_path)["cases"]]
    with io.open(out_path, "w", encoding="utf-8") as out:
        json.dump(cases, out, ensure_ascii=False, indent=1)
    print(json.dumps({"cases": len(cases)}))


def extract(capture_path, out_path, message="raw"):
    # message: 근거 검증에 줄 고객 원문. raw는 이번 턴에 보낸 문장, resolved는 앱이 넘긴 문장(실행 기록의 customerMessage,
    # 후속 질문이면 문맥 해석으로 복원된 문장)이다. 부분 정답(parts)이 없는 턴(후속 대화의 첫 턴)은 건너뛴다
    capture = load(capture_path)
    pairs = slot_pairs()
    rows = []
    for case in capture["cases"]:
        for index, turn in enumerate(case["turns"]):
            trace = turn.get("pipelineTrace") or {}
            candidates = {}
            for result in trace.get("searchResults") or []:
                if result.get("kind") == "FAQ_CANDIDATE":
                    candidates[result.get("consultRequestId")] = result.get("sources") or []
            parts = turn["fixture"].get("parts")
            if not parts:
                continue
            for order, item in enumerate(trace.get("faqCandidateEvidenceInputs") or []):
                sources = []
                for source in candidates.get(item.get("consultRequestId"), []):
                    source = dict(source)
                    if source.get("slotId") is None:
                        source["slotId"] = pairs.get((source["question"], source["answer"]))
                    sources.append(source)
                part, overlap, margin = match_part(item["question"], parts)
                rows.append({"id": f"{case['caseId']}#{index}#{order}", "type": case["questionType"],
                             "question": item["question"], "allowQuoteRepairFailure": item["allowQuoteRepairFailure"],
                             "customerQuestion": turn["fixture"]["question"] if message == "raw"
                             else item.get("customerMessage"), "part": part,
                             "partOverlap": overlap, "partMargin": margin,
                             "partGold": parts[part]["goldSlotIds"],
                             "otherGold": sorted({s for i, p in enumerate(parts) if i != part for s in p["goldSlotIds"]}),
                             "candidates": sources})
    io.open(out_path, "w", encoding="utf-8").write(json.dumps(rows, ensure_ascii=False, indent=1))
    print(json.dumps({"inputs": len(rows),
                      "partHasAnswer": sum(bool(r["partGold"]) for r in rows),
                      "goldInCandidates": sum(any(s["slotId"] in r["partGold"] for s in r["candidates"]) for r in rows),
                      "unclearPartMatch": [r["id"] for r in rows if r["partMargin"] < 0.15]}, ensure_ascii=False))


KEYS = ["정답 FAQ로 답함", "다른 부분의 FAQ로 답함", "엉뚱한 FAQ로 답함", "답 없는 부분에 FAQ로 답함",
        "정답 후보가 있는데 답 안 함", "정답 후보 없어 답 안 함", "답 없는 부분이라 답 안 함"]


def outcome(row, replay, pairs):
    gold, other = set(row["partGold"]), set(row["otherGold"])
    candidates = row["candidates"] + [dict(c, slotId=c.get("slotId") or pairs.get((c["question"], c["answer"])))
                                      for c in replay.get("addedCandidates") or []]
    picked = replay["pickedFaqId"]
    if picked is not None:
        slot = next((c["slotId"] for c in candidates if c["faqId"] == picked), None)
        if not gold:
            return KEYS[3]
        if slot in gold:
            return KEYS[0]
        return KEYS[1] if slot in other else KEYS[2]
    if not gold:
        return KEYS[6]
    return KEYS[4] if any(c["slotId"] in gold for c in candidates) else KEYS[5]


def score(inputs_path, replay_paths, out_path):
    inputs = {row["id"]: row for row in load(inputs_path)}
    pairs = slot_pairs()
    rows = []
    for path in replay_paths:
        with open_text(path) as source:
            rows += [json.loads(line) for line in source if line.strip()]
    groups = collections.defaultdict(list)
    for row in rows:
        groups[(row.get("variant", "P0"), row["model"])].append(row)
    out = io.open(out_path, "w", encoding="utf-8")
    print(f"입력 {len(inputs)}개, 답 있는 부분 {sum(bool(r['partGold']) for r in inputs.values())}개, "
          f"후보 10개에 정답이 있는 것 {sum(any(s['slotId'] in r['partGold'] for s in r['candidates']) for r in inputs.values())}개."
          " 칸은 회차별 값(gpt-oss는 3회)\n", file=out)
    order = sorted(groups)
    print("| 결과 | " + " | ".join(f"{v} {m}" for v, m in order) + " |", file=out)
    print("|---|" + "---|" * len(order), file=out)
    for key in KEYS:
        cells = []
        for group in order:
            per_run = collections.defaultdict(collections.Counter)
            for row in groups[group]:
                per_run[row["run"]][outcome(inputs[row["id"]], row, pairs)] += 1
            cells.append(" / ".join(str(per_run[run][key]) for run in sorted(per_run)))
        print(f"| {key} | " + " | ".join(cells) + " |", file=out)
    print("\n입력당 중앙값 ms:", file=out)
    for group in order:
        print(f"  {group}: {statistics.median(r['ms'] for r in groups[group])}ms, 오류 "
              f"{sum(len(r['errors']) for r in groups[group])}", file=out)
    print("\n틀리게 답한 것:", file=out)
    for row in rows:
        result = outcome(inputs[row["id"]], row, pairs)
        if result in KEYS[1:4]:
            item = inputs[row["id"]]
            print(f"- {row.get('variant')} {row['model']}{row['run']} {row['id']} [{result}] 하위 질문: {item['question']}"
                  f" / 고른 faqId {row['pickedFaqId']}", file=out)


if __name__ == "__main__":
    if sys.argv[1] == "extract":
        extract(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else "raw")
    elif sys.argv[1] == "fixture":
        fixture(sys.argv[2], sys.argv[3])
    else:
        score(sys.argv[2], sys.argv[3:-1], sys.argv[-1])
