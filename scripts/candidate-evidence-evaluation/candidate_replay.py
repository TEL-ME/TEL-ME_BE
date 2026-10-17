"""TELME-182 후보 근거 검증 비교(docs/EVIDENCE_RESOLVER_COMPARISON.md "복합 질문 입력 확인" 절).

extract: 캡처의 실행 기록에서 근거 검증 입력(faqCandidateEvidenceInputs의 문장·인용문 재시도 허용)과
         같은 상담의 후보(searchResults의 FAQ_CANDIDATE)를 묶어 replay 입력을 만든다.
score:   replay 결과를 턴의 정답 FAQ(goldSourceGroups 전체)로 채점한다.
         DB에 slotId가 없는 FAQ는 원본 FAQ(scripts/data/faq_full_1150.json)의 문답 완전 일치로 slotId를 채운다(Judge와 같다).
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


def turns(capture):
    for case in capture["cases"]:
        for index, turn in enumerate(case["turns"]):
            yield case, index, turn


def gold_of(turn):
    return {slot for group in (turn.get("fixture") or {}).get("goldSourceGroups", []) for slot in group}


def extract(capture_path, out_path):
    capture = load(capture_path)
    pairs = slot_pairs()
    rows = []
    for case, index, turn in turns(capture):
        trace = turn.get("pipelineTrace") or {}
        candidates = {}
        for result in trace.get("searchResults") or []:
            if result.get("kind") == "FAQ_CANDIDATE":
                candidates[result.get("consultRequestId")] = result.get("sources") or []
        for order, item in enumerate(trace.get("faqCandidateEvidenceInputs") or []):
            sources = []
            for source in candidates.get(item.get("consultRequestId"), []):
                source = dict(source)
                if source.get("slotId") is None:
                    source["slotId"] = pairs.get((source["question"], source["answer"]))
                sources.append(source)
            rows.append({"id": f"{case['caseId']}#{index}#{order}", "suite": case["suite"],
                         "question": item["question"], "allowQuoteRepairFailure": item["allowQuoteRepairFailure"],
                         "customerQuestion": turn["fixture"]["question"], "gold": sorted(gold_of(turn)),
                         "candidates": sources})
    io.open(out_path, "w", encoding="utf-8").write(json.dumps(rows, ensure_ascii=False, indent=1))
    print(json.dumps({"turns": sum(1 for _ in turns(capture)), "inputs": len(rows),
                      "withGoldCandidate": sum(any(s["slotId"] in r["gold"] for s in r["candidates"]) for r in rows),
                      "noGold": sum(not r["gold"] for r in rows),
                      "bySuite": collections.Counter(r["suite"] for r in rows)}, ensure_ascii=False))


def outcome(row, replay):
    gold = set(row["gold"])
    has_gold_candidate = any(source["slotId"] in gold for source in row["candidates"])
    picked = replay["pickedFaqId"]
    if picked is not None:
        slot = next((s["slotId"] for s in row["candidates"] if s["faqId"] == picked), None)
        if not gold:
            return "답 없는 질문에 FAQ로 답함"
        return "정답 FAQ로 답함" if slot in gold else "다른 FAQ로 답함"
    if has_gold_candidate:
        return "정답 후보가 있는데 채택 안 함"
    return "채택 안 함(정답 후보 없음)" if gold else "채택 안 함(답 없는 질문)"


ORDER = ["정답 FAQ로 답함", "다른 FAQ로 답함", "답 없는 질문에 FAQ로 답함", "정답 후보가 있는데 채택 안 함",
         "채택 안 함(정답 후보 없음)", "채택 안 함(답 없는 질문)"]


def score(inputs_path, replay_path, out_path):
    inputs = {row["id"]: row for row in load(inputs_path)}
    with open_text(replay_path) as source:
        rows = [json.loads(line) for line in source if line.strip()]
    by_run = collections.defaultdict(dict)
    for row in rows:
        by_run[(row["model"], row["run"])][row["id"]] = row
    runs = sorted(by_run)
    out = io.open(out_path, "w", encoding="utf-8")
    print(f"입력 {len(inputs)}개, 정답 후보가 있는 입력 "
          f"{sum(any(s['slotId'] in r['gold'] for s in r['candidates']) for r in inputs.values())}개, "
          f"답 없는 질문 {sum(not r['gold'] for r in inputs.values())}개\n", file=out)
    print("| 결과 | " + " | ".join(f"{m} {r}" for m, r in runs) + " |", file=out)
    print("|---|" + "---|" * len(runs), file=out)
    counts = {run: collections.Counter(outcome(inputs[i], row) for i, row in by_run[run].items()) for run in runs}
    for name in ORDER:
        print(f"| {name} | " + " | ".join(str(counts[run][name]) for run in runs) + " |", file=out)
    print("\n호출 통계:", file=out)
    for run in runs:
        values = list(by_run[run].values())
        ms = [row["ms"] for row in values if row["calls"]]
        print(f"  {run}: 호출 {sum(row['calls'] for row in values)}, 오류 {sum(len(row['errors']) for row in values)}, "
              f"실패 {sum(bool(row['failure']) for row in values)}, 입력당 중앙값 {statistics.median(ms) if ms else '-'}ms",
              file=out)
    gpt = [run for run in runs if run[0] == "gptoss"]
    if len(gpt) > 1:
        same = sum(len({by_run[run][i]["pickedFaqId"] for run in gpt}) == 1 for i in inputs)
        print(f"gpt-oss 반복 일치(고른 FAQ): {same}/{len(inputs)}", file=out)
    print("\n입력별:", file=out)
    for i, row in inputs.items():
        results = {f"{m}{r}": outcome(row, by_run[(m, r)][i]) for m, r in runs}
        if len(set(results.values())) == 1 and next(iter(results.values())) in ("정답 FAQ로 답함", "채택 안 함(답 없는 질문)"):
            continue
        print(f"- {i} [{row['suite']}] 검증 문장: {row['question']} / 고객: {row['customerQuestion']}", file=out)
        print(f"    정답: {row['gold']} 후보에 정답: {any(s['slotId'] in row['gold'] for s in row['candidates'])}", file=out)
        for m, r in runs:
            replay = by_run[(m, r)][i]
            slot = next((s["slotId"] for s in row["candidates"] if s["faqId"] == replay["pickedFaqId"]), None)
            print(f"    {m} {r}: {outcome(row, replay)} picked={slot}", file=out)


if __name__ == "__main__":
    if sys.argv[1] == "extract":
        extract(sys.argv[2], sys.argv[3])
    else:
        score(sys.argv[2], sys.argv[3], sys.argv[4])
