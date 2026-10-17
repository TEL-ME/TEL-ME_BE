"""TELME-184 개발용: 하위 질문 단위 라벨로 판정 방식(P0~P6)별 앱 채택 결과를 센다(docs/EVIDENCE_RESOLVER_COMPARISON.md).

사용: score_dev.py <라벨 json> <결과 jsonl ...> <출력 md>. .gz 파일은 압축을 풀지 않고 읽는다.
"""
import collections
import gzip
import io
import json
import statistics
import sys


def open_text(path):
    return gzip.open(path, "rt", encoding="utf-8") if path.endswith(".gz") else io.open(path, encoding="utf-8")

labels_path, replay_paths, out_path = sys.argv[1], sys.argv[2:-1], sys.argv[-1]
with open_text(labels_path) as source:
    labels = {l["id"]: l for l in json.load(source)}
rows = []
for path in replay_paths:
    for line in open_text(path):
        if line.strip():
            row = json.loads(line)
            row.setdefault("variant", "P0")
            rows.append(row)

KEYS = ["답 있음: 맞는 FAQ로 답함", "답 있음: 다른 FAQ로 답함", "답 있음: 답 안 함",
        "답 없음: 맞게 답 안 함", "답 없음: FAQ로 답함(틀림)"]


def outcome(row):
    ok = set(labels[row["id"]]["answeringFaqIds"])
    picked = row["pickedFaqId"]
    if ok:
        if picked is None:
            return KEYS[2]
        return KEYS[0] if picked in ok else KEYS[1]
    return KEYS[3] if picked is None else KEYS[4]


groups = collections.defaultdict(list)
for row in rows:
    groups[(row["variant"], row["model"])].append(row)
out = io.open(out_path, "w", encoding="utf-8")
answerable = sum(bool(l["answeringFaqIds"]) for l in labels.values())
print(f"라벨: 답 있음 {answerable}, 답 없음 {len(labels) - answerable}. 칸은 회차별 값(gpt-oss는 3회)\n", file=out)
order = sorted(groups)
print("| 결과 | " + " | ".join(f"{v} {m}" for v, m in order) + " |", file=out)
print("|---|" + "---|" * len(order), file=out)
for key in KEYS:
    cells = []
    for group in order:
        per_run = collections.defaultdict(collections.Counter)
        for row in groups[group]:
            per_run[row["run"]][outcome(row)] += 1
        cells.append(" / ".join(str(per_run[run][key]) for run in sorted(per_run)))
    print(f"| {key} | " + " | ".join(cells) + " |", file=out)
print("\n호출(입력당 중앙값 ms, 모델 호출 수 합계, 판정기 호출 수 합계):", file=out)
for group in order:
    values = groups[group]
    print(f"  {group}: {statistics.median(r['ms'] for r in values)}ms, 모델 {sum(r['calls'] for r in values)}, "
          f"판정기 {sum(len(r.get('verdicts') or []) for r in values)}, 오류 {sum(len(r['errors']) for r in values)}", file=out)
print("\n틀린 채택(다른 FAQ, 답 없음에 답함):", file=out)
for row in rows:
    if outcome(row) in (KEYS[1], KEYS[4]) and row["model"] == "gptoss":
        label = labels[row["id"]]
        print(f"- {row['variant']} gptoss{row['run']} {row['id']} 묻는 것: {label['partAsked']} / 라벨 {label['answeringFaqIds']} / 고름 {row['pickedFaqId']}", file=out)
