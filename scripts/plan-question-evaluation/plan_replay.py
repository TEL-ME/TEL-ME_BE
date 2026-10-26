"""TELME-182 요금제 질문 분류 비교(docs/02-features/plan/plan-question-model.md).

select:  Judge 평가셋(TELME-180의 v2.2, 흐름 v2)에서 질문에 요금제 낱말(분류기의 낱말 검사와 같은 정규식)이 든 대화를 고른다.
extract: 캡처(capture.json)의 실행 기록에서 분류 입력(planQuestionClassifications)을 꺼내 replay 입력을 만든다.
score:   replay 결과(jsonl)를 평가셋 answerSources 라벨로 턴 단위 채점한다.
.gz 파일은 압축을 풀지 않고 읽는다.

턴의 예상 분류(굵은 단위):
  PLAN_TABLE만      -> 요금제(PLAN_CATALOG, RECOMMENDATION)가 하나 이상, 내 정보(MY_*)는 없음
  MEMBER만          -> 내 정보가 하나 이상, 요금제는 없음
  FAQ+PLAN_TABLE    -> 내 정보 없음(요금제는 있어도 없어도 정답)
  FAQ, STORE, 빈 값 -> 모두 NONE
턴 안의 분류 입력은 앱처럼 원문 → 풀어쓴 문장 순서지만, 여기서는 입력마다 따로 분류하고 턴의 결과를 합친다.
"""

import collections
import gzip
import io
import json
import re
import sys

# PlanQuestionClassifier.RELATED와 같다
RELATED = re.compile("요금|플랜|데이터|통화|문자|사용량|약정|위약금|해지|가입|추천|5G|LTE|스탠다드|프리미엄|라이트|베이직|언리미티드|알뜰|청소년|시니어|갈아|바꾸|저렴|싼")

PLAN = {"PLAN_CATALOG", "RECOMMENDATION"}
MEMBER = {"MY_SUBSCRIPTION", "MY_PENALTY", "MY_USAGE"}


def coarse(value):
    return "PLAN" if value in PLAN else "MEMBER" if value in MEMBER else "NONE"


def open_text(path):
    return gzip.open(path, "rt", encoding="utf-8") if str(path).endswith(".gz") else io.open(path, encoding="utf-8")


def load(path):
    with open_text(path) as source:
        return json.load(source)


def select(fixture_paths, out_path):
    selected = []
    for path in fixture_paths:
        cases = load(path)
        picked = [case for case in cases if any(RELATED.search(turn["question"]) for turn in case["turns"])]
        print(f"{path}: {len(cases)} -> {len(picked)}")
        selected += picked
    io.open(out_path, "w", encoding="utf-8").write(json.dumps(selected, ensure_ascii=False, indent=1))


def sources_of(turn):
    return (turn.get("fixture") or {}).get("answerSources", [])


def turns(capture):
    for case in capture["cases"]:
        for index, turn in enumerate(case["turns"]):
            yield case, index, turn


def extract(capture_path, out_path):
    capture = load(capture_path)
    rows, seen = [], set()
    for case, index, turn in turns(capture):
        trace = turn.get("pipelineTrace") or {}
        for order, item in enumerate(trace.get("planQuestionClassifications") or []):
            rows.append({"id": f"{case['caseId']}#{index}#{order}", "question": item["question"],
                         "captureType": item["type"]})
    io.open(out_path, "w", encoding="utf-8").write(json.dumps(rows, ensure_ascii=False, indent=1))
    print(json.dumps({"turns": sum(1 for _ in turns(capture)), "inputs": len(rows),
                      "uniqueQuestions": len({row["question"] for row in rows})}, ensure_ascii=False))


def expected_ok(sources, found):
    sources = set(sources)
    if sources == {"PLAN_TABLE"}:
        return "PLAN" in found and "MEMBER" not in found
    if sources == {"MEMBER"}:
        return "MEMBER" in found and "PLAN" not in found
    if sources == {"FAQ", "PLAN_TABLE"}:
        return "MEMBER" not in found
    return not found


def label(sources):
    sources = set(sources)
    if sources == {"PLAN_TABLE"}:
        return "PLAN_TABLE"
    if sources == {"MEMBER"}:
        return "MEMBER"
    if sources == {"FAQ", "PLAN_TABLE"}:
        return "FAQ+PLAN_TABLE"
    return "NONE(FAQ/STORE/답 없음)"


def score(capture_path, replay_path, out_path):
    capture = load(capture_path)
    with open_text(replay_path) as source:
        rows = [json.loads(line) for line in source if line.strip()]
    by_run = collections.defaultdict(dict)
    for row in rows:
        by_run[(row["model"], row["run"])][row["id"]] = row
    out = io.open(out_path, "w", encoding="utf-8")
    runs = sorted(by_run)
    table = collections.defaultdict(lambda: collections.Counter())
    misses = collections.defaultdict(list)
    for case, index, turn in turns(capture):
        trace = turn.get("pipelineTrace") or {}
        items = trace.get("planQuestionClassifications") or []
        group = label(sources_of(turn))
        for run in runs:
            found = set()
            types = []
            for order, _ in enumerate(items):
                row = by_run[run].get(f"{case['caseId']}#{index}#{order}")
                if row:
                    types.append(row["type"])
                    if coarse(row["type"]) != "NONE":
                        found.add(coarse(row["type"]))
            ok = expected_ok(sources_of(turn), found)
            table[(run, group)]["ok" if ok else "ng"] += 1
            table[(run, "전체")]["ok" if ok else "ng"] += 1
            if not ok:
                misses[(case["caseId"], index)].append((run, types))
    print("| 묶음 | " + " | ".join(f"{m} {r}" for m, r in runs) + " |", file=out)
    print("|---|" + "---|" * len(runs), file=out)
    for group in ["PLAN_TABLE", "MEMBER", "FAQ+PLAN_TABLE", "NONE(FAQ/STORE/답 없음)", "전체"]:
        cells = []
        for run in runs:
            counter = table[(run, group)]
            total = counter["ok"] + counter["ng"]
            cells.append(f"{counter['ok']}/{total}" if total else "-")
        print(f"| {group} | " + " | ".join(cells) + " |", file=out)
    print("", file=out)
    stats = collections.defaultdict(collections.Counter)
    for row in rows:
        key = (row["model"], row["run"])
        stats[key]["inputs"] += 1
        stats[key]["called"] += row["called"]
        stats[key]["error"] += bool(row["error"])
        stats[key]["emptyOrUnreadable"] += row["called"] and not row["error"] and not _readable(row["raw"])
    print("호출 통계:", file=out)
    for key in runs:
        print(f"  {key}: {dict(stats[key])}", file=out)
    gpt = [run for run in runs if run[0] == "gptoss"]
    if len(gpt) > 1:
        ids = set.intersection(*(set(by_run[run]) for run in gpt))
        same = sum(len({by_run[run][i]["type"] for run in gpt}) == 1 for i in ids)
        print(f"gpt-oss 반복 일치(입력 단위): {same}/{len(ids)}", file=out)
    print("\n틀린 턴:", file=out)
    questions = {(case["caseId"], index): turn for case, index, turn in turns(capture)}
    for key, values in sorted(misses.items()):
        turn = questions[key]
        items = (turn.get("pipelineTrace") or {}).get("planQuestionClassifications") or []
        print(f"- {key[0]}#{key[1]} [{label(sources_of(turn))}] {turn['fixture']['question']}", file=out)
        print(f"    입력: {[item['question'] for item in items]}", file=out)
        for run, types in values:
            print(f"    {run[0]} {run[1]}: {types}", file=out)


def _readable(raw):
    if not raw:
        return False
    try:
        return isinstance(json.loads(raw), dict)
    except ValueError:
        return False


if __name__ == "__main__":
    if sys.argv[1] == "select":
        select(sys.argv[2:-1], sys.argv[-1])
    elif sys.argv[1] == "extract":
        extract(sys.argv[2], sys.argv[3])
    else:
        score(sys.argv[2], sys.argv[3], sys.argv[4])
