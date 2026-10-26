"""구성별 180문항 결과를 비교한다. 앱 응답, 실행 trace의 판정 차단, 라우터 호출 기록만 센다.
사용: python compare_configs.py <run_config.sh 출력 디렉터리 또는 results.json.gz> <구성 이름...>"""
import gzip
import json
import statistics
import sys
from pathlib import Path

SOURCE = Path(sys.argv[1])
NAMES = sys.argv[2:]
BUNDLE = json.load(gzip.open(SOURCE, "rt", encoding="utf-8"))["configs"] if SOURCE.suffix == ".gz" else None


def load(name):
    """(chat 행, guard 행, 라우터 호출 행)"""
    if BUNDLE is not None:
        config = BUNDLE[name]
        return config["chat"], config["guard"] or [], config["router"]
    run = SOURCE / name
    router = run / "router.jsonl"
    return (json.loads((run / "chat.json").read_text(encoding="utf-8")),
            json.loads((run / "guard.json").read_text(encoding="utf-8") or "[]") or [],
            [json.loads(l) for l in router.read_text(encoding="utf-8").splitlines()] if router.exists() else [])


def pct(values, p):
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))] if values else 0


def gold_found(row):
    found = {s for x in row["searches"] if x["status"] == "FOUND" for s in x["slots"]}
    return bool(found & set(row["gold"]))


table, details = {}, {}
for name in NAMES:
    rows, guard, calls = load(name)
    blocked_questions = {g["question"] for g in guard if g["blocked"]}
    answerable = [r for r in rows if r["gold"]]
    unrelated = [r for r in rows if not r["gold"]]
    blocked = [r for r in rows if r["question"] in blocked_questions]
    wrong_block = [r for r in blocked if r["gold"] and gold_found(r)]
    judge_calls = [c for c in calls if c.get("kind") == "judge"]
    judge_ms = [c["elapsedMs"] for c in judge_calls if c["status"] == 200]
    elapsed = [r["elapsed"] for r in rows]
    table[name] = {
        "답할 수 있는 110 중 정답 FAQ 검색": sum(gold_found(r) for r in answerable),
        "답할 수 있는 110 중 근거 답변": sum(r["answerBasis"] == "GROUNDED" for r in answerable),
        "답할 수 있는 110 중 되묻기": sum(r["messageType"] == "CLARIFICATION" for r in answerable),
        "답이 없는 70 중 근거 답변(낮을수록 좋음)": sum(r["answerBasis"] == "GROUNDED" for r in unrelated),
        "판정기 차단 (답할 수 있는 / 답이 없는)": f"{sum(1 for r in blocked if r['gold'])} / {sum(1 for r in blocked if not r['gold'])}",
        "그중 정답 FAQ를 찾고도 막은 수": len(wrong_block),
        "판정 호출 / 실패": f"{len(judge_calls)} / {sum(1 for c in judge_calls if c['status'] != 200)}",
        "판정 1회 중앙값 / p95": f"{statistics.median(judge_ms) / 1000:.2f}s / {pct(judge_ms, 95) / 1000:.2f}s" if judge_ms else "-",
        "응답 시간 중앙값 / p95": f"{statistics.median(elapsed):.2f}s / {pct(elapsed, 95):.2f}s",
        "응답 없음": sum(1 for r in rows if r["messageType"] is None),
    }
    details[name] = {"blocked": [(r["id"], bool(r["gold"]), gold_found(r), r["question"]) for r in blocked],
                     "rows": {r["id"]: r for r in rows}}

keys = list(next(iter(table.values())))
print("| 항목 | " + " | ".join(NAMES) + " |")
print("|---" * (len(NAMES) + 1) + "|")
for key in keys:
    print(f"| {key} | " + " | ".join(str(table[n][key]) for n in NAMES) + " |")

for name in NAMES:
    if details[name]["blocked"]:
        print(f"\n[{name}] 판정기가 막은 질문")
        for eval_id, answerable, found, question in details[name]["blocked"]:
            tag = "답할 수 있음" + (", 정답 FAQ 찾음" if found else "") if answerable else "답이 없음"
            print(f"  - {eval_id} ({tag}) {question}")
