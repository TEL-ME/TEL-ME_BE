"""TELME-184 비교 근거 검증 재확인 채점(docs/EVIDENCE_RESOLVER_COMPARISON.md "비교 근거 검증 재확인" 절).

score: 재생 결과를 채점한다.
  블라인드(BLIND-CMP): 양쪽 답 있음이면 답에 쓴 FAQ가 양쪽 정답(FAQ 원본 slot_id, 문답 완전 일치로 보충)을 모두 덮고
                       정답 밖의 FAQ가 없어야 맞음(FAQ 하나가 양쪽을 함께 답하기도 한다).
                       한쪽만 답 있음, 둘 다 없음은 답하지 않아야 맞음.
  #167 질문: 정답 FAQ(goldFaqIds)가 있는 것은 답에 쓴 FAQ가 정답을 모두 포함해야 맞음. 정답이 없는 것은 답한 수만 센다.
.gz 파일은 압축을 풀지 않고 읽는다.
"""

import collections
import gzip
import io
import json
import sys
from pathlib import Path

CATALOG = Path(__file__).resolve().parents[2] / "scripts" / "data" / "faq_full_1150.json"


def open_text(path):
    return gzip.open(path, "rt", encoding="utf-8") if str(path).endswith(".gz") else io.open(path, encoding="utf-8")


def load(path):
    with open_text(path) as source:
        return json.load(source)


def outcome(item, row, pairs):
    slots = [s.get("slotId") or pairs.get((s["question"], s["answer"])) for s in row["sources"]]
    ids = {s["faqId"] for s in row["sources"]}
    if item["suite"] == "blind_comparison":
        if item["type"] != "BOTH_ANSWERABLE":
            return "답 없는 쪽이 있는데 답함(틀림)" if row["answered"] else "답 없는 쪽이 있어 답 안 함"
        if not row["answered"]:
            return "양쪽 답 있음: 답 안 함"
        left, right = set(item["comparison"]["left"]["goldSlotIds"]), set(item["comparison"]["right"]["goldSlotIds"])
        # FAQ 하나가 양쪽을 함께 답하기도 한다. 쓴 FAQ가 양쪽 정답을 모두 덮고 정답 밖의 FAQ가 없으면 맞음
        used = set(slots)
        ok = bool(used & left) and bool(used & right) and used <= (left | right)
        return "양쪽 답 있음: 맞는 FAQ로 답함" if ok else "양쪽 답 있음: 다른 FAQ로 답함(틀림)"
    if not item["goldFaqIds"]:
        return "#167 정답 없음: 답함" if row["answered"] else "#167 정답 없음: 답 안 함"
    if not row["answered"]:
        return "#167 정답 있음: 답 안 함"
    return "#167 정답 있음: 정답 FAQ로 답함" if set(item["goldFaqIds"]) <= ids else "#167 정답 있음: 다른 FAQ로 답함"


KEYS = ["양쪽 답 있음: 맞는 FAQ로 답함", "양쪽 답 있음: 다른 FAQ로 답함(틀림)", "양쪽 답 있음: 답 안 함",
        "답 없는 쪽이 있는데 답함(틀림)", "답 없는 쪽이 있어 답 안 함",
        "#167 정답 있음: 정답 FAQ로 답함", "#167 정답 있음: 다른 FAQ로 답함", "#167 정답 있음: 답 안 함",
        "#167 정답 없음: 답함", "#167 정답 없음: 답 안 함"]


def score(inputs_path, replay_paths, out_path):
    inputs = {r["id"]: r for r in load(inputs_path)}
    pairs = {(f["question"], f["answer"]): f["slot_id"] for f in load(CATALOG)}
    rows = []
    for path in replay_paths:
        with open_text(path) as source:
            rows += [json.loads(line) for line in source if line.strip()]
    groups = collections.defaultdict(lambda: collections.defaultdict(collections.Counter))
    for row in rows:
        groups[row["model"]][row["run"]][outcome(inputs[row["id"]], row, pairs)] += 1
    out = io.open(out_path, "w", encoding="utf-8")
    types = collections.Counter((r["suite"], r["type"]) for r in inputs.values())
    print(f"입력 {len(inputs)}개: {dict(types)}. 칸은 회차별 값\n", file=out)
    models = sorted(groups)
    print("| 결과 | " + " | ".join(models) + " |", file=out)
    print("|---|" + "---|" * len(models), file=out)
    for key in KEYS:
        cells = [" / ".join(str(groups[m][run][key]) for run in sorted(groups[m])) for m in models]
        print(f"| {key} | " + " | ".join(cells) + " |", file=out)
    print("\n오류·실패:", file=out)
    for m in models:
        bad = [r for r in rows if r["model"] == m and (r["errors"] or r["failure"])]
        print(f"  {m}: {len(bad)}건 {sorted({r['id'] for r in bad})}", file=out)
    print("\n틀린 답:", file=out)
    for row in rows:
        result = outcome(inputs[row["id"]], row, pairs)
        if "틀림" in result or "다른 FAQ" in result:
            item = inputs[row["id"]]
            used = [f"{s['faqId']}:{s['question']}" for s in row["sources"]]
            print(f"- {row['model']}{row['run']} {row['id']} [{result}] {item['question']} / 쓴 FAQ {used}", file=out)


if __name__ == "__main__":
    score(sys.argv[1], sys.argv[2:-1], sys.argv[-1])
