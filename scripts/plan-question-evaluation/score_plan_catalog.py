"""TELME-227 요금제 표 답 채점(PlanCatalogReplayProbe 결과). 사용:
  python score_plan_catalog.py <labels> <develop 결과> <branch 결과>
예) python score_plan_catalog.py results/telme227/blind-sentences.json.gz:labels_plan_catalog \n      results/telme227/probe-results.json.gz:plan_catalog/develop results/telme227/probe-results.json.gz:plan_catalog/conditions_v2
labels: [{id, group, question, expect}]"""
import gzip
import json
import re
import sys
from collections import defaultdict

PLAN_NAMES = ["5G 언리미티드", "5G 프리미엄", "5G 스탠다드", "5G 라이트", "LTE 맥스", "LTE 플러스", "LTE 베이직",
              "알뜰 세이브", "알뜰 미니", "5G 청소년", "LTE 청소년", "알뜰 청소년", "5G 시니어", "LTE 시니어", "알뜰 시니어"]
NOT_SOLD = "말씀하신 요금제는 판매 중인 요금제에 없어요"
NOT_FOUND = "조건에 맞는 요금제를 찾지 못했어요"
MEMBER_TYPES = {"MY_SUBSCRIPTION", "MY_PENALTY", "MY_USAGE"}


def load_json(spec):
    """파일 경로, 또는 results/telme227의 묶음 파일과 키("blind-sentences.json.gz:labels_plan_catalog")"""
    if ".json.gz:" in spec:
        path, key = spec.split(".json.gz:", 1)
        with gzip.open(path + ".json.gz", "rt", encoding="utf-8") as handle:
            return json.load(handle)[key]
    if spec.endswith(".jsonl"):
        return [json.loads(line) for line in open(spec, encoding="utf-8") if line.strip()]
    return json.load(open(spec, encoding="utf-8"))


def shown(answer):
    if not answer:
        return set(), None
    listed = set(re.findall(r"^- (.+?): 월 ", answer, re.M))
    if listed:
        return listed, "list"
    detailed = {line.strip() for line in answer.splitlines() if line.strip() in PLAN_NAMES}
    return detailed, "detail" if detailed else None


def judge(expect, row):
    kind = expect["type"]
    answer = row.get("answer") or ""
    plans, form = shown(answer)
    if kind == "MEMBER":
        return row["type"] == expect["kind"], row["type"]
    if kind == "RECOMMEND":
        return row["type"] == "RECOMMENDATION", row["type"]
    if row["type"] != "PLAN_CATALOG":
        return False, "type=" + row["type"]
    if kind == "PLAN_NOT_SOLD":
        return answer.startswith(NOT_SOLD), answer[:40]
    if answer.startswith(NOT_SOLD) or answer.startswith(NOT_FOUND):
        return False, answer[:20]
    include = set(expect.get("include", []))
    exclude = set(expect.get("exclude", []))
    reasons = []
    if kind == "PLAN_DETAIL":
        want = set(expect.get("plans", []))
        if form != "detail":
            reasons.append("form=" + str(form))
        if not want <= plans:
            reasons.append("missing " + ",".join(sorted(want - plans)))
        extra = plans & exclude if exclude else plans - want
        if extra:
            reasons.append("extra " + ",".join(sorted(extra)))
        return not reasons, "; ".join(reasons)
    if kind == "PLAN_COUNT":
        head = answer.split("\n", 1)[0]
        if "%d종" % expect["count"] not in head:
            reasons.append("head=" + head)
    if form != "list" and kind == "PLAN_LIST" and not (include and include <= plans and form == "detail"):
        reasons.append("form=" + str(form))
    if not include <= plans:
        reasons.append("missing " + ",".join(sorted(include - plans)))
    if plans & exclude:
        reasons.append("extra " + ",".join(sorted(plans & exclude)))
    return not reasons, "; ".join(reasons)


def load(spec):
    rows = defaultdict(list)
    for row in load_json(spec):
        rows[row["id"]].append(row)
    return rows


def main():
    labels = load_json(sys.argv[1])
    sides = {"develop": load(sys.argv[2]), "branch": load(sys.argv[3])}
    totals = defaultdict(lambda: defaultdict(lambda: [0, 0]))
    detail_lines = []
    for case in labels:
        marks = {}
        for side, rows in sides.items():
            results = [judge(case["expect"], row) for row in rows[case["id"]]]
            ok = sum(1 for passed, _ in results if passed)
            totals[side][case["group"]][0] += ok
            totals[side][case["group"]][1] += len(results)
            marks[side] = (ok, len(results), [why for passed, why in results if not passed])
        if marks["develop"][0] != marks["branch"][0] or marks["branch"][0] < marks["branch"][1]:
            detail_lines.append("%s [%s] dev %d/%d br %d/%d  %s\n    dev: %s\n    br : %s" % (
                case["id"], case["group"], marks["develop"][0], marks["develop"][1], marks["branch"][0],
                marks["branch"][1], case["question"], " | ".join(sorted(set(marks["develop"][2])))[:200],
                " | ".join(sorted(set(marks["branch"][2])))[:200]))
    for group in sorted(totals["develop"]):
        d, b = totals["develop"][group], totals["branch"][group]
        print("%-12s develop %3d/%3d  branch %3d/%3d" % (group, d[0], d[1], b[0], b[1]))
    print("%-12s develop %3d/%3d  branch %3d/%3d" % ("ALL", sum(v[0] for v in totals["develop"].values()),
          sum(v[1] for v in totals["develop"].values()), sum(v[0] for v in totals["branch"].values()),
          sum(v[1] for v in totals["branch"].values())))
    print()
    print("\n".join(detail_lines))


if __name__ == "__main__":
    main()
