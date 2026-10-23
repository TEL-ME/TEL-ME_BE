"""TELME-227 추천 대상 채점(SubscriberReplayProbe 결과). 사용: python score_subscriber.py <블라인드 문장> <결과>
예) python score_subscriber.py results/telme227/blind-sentences.json.gz:boundary_age_blind \n      results/telme227/probe-results.json.gz:subscriber/boundary/two_calls_age_v2
develop은 규칙만(rule), 브랜치는 규칙이 없을 때 모델(model). 기대값과 같으면 정답. 사용량(dataGb)과 본인 여부(user)가 기록돼 있으면 함께 채점한다."""
import gzip
import json
import statistics
import sys
from collections import defaultdict

OPTION = {"만 14~18세": "YOUTH", "만 19~64세": "GENERAL", "만 65세 이상": "SENIOR"}


def load_json(spec):
    """파일 경로, 또는 results/telme227의 묶음 파일과 키("blind-sentences.json.gz:labels_plan_catalog")"""
    if ".json.gz:" in spec:
        path, key = spec.split(".json.gz:", 1)
        with gzip.open(path + ".json.gz", "rt", encoding="utf-8") as handle:
            return json.load(handle)[key]
    if spec.endswith(".jsonl"):
        return [json.loads(line) for line in open(spec, encoding="utf-8") if line.strip()]
    return json.load(open(spec, encoding="utf-8"))


def main():
    cases = {c["id"]: c for c in load_json(sys.argv[1])["cases"]}
    rows = defaultdict(list)
    for row in load_json(sys.argv[2]):
        rows[row["id"]].append(row)
    dev_ok = br_ok = total = 0
    usage = {"dev": 0, "br": 0, "n": 0}
    user_ok = user_n = 0
    by_expect = defaultdict(lambda: [0, 0, 0])
    ms = [row["modelMs"] for rs in rows.values() for row in rs if row.get("modelMs") is not None]
    details = []
    for cid, case in cases.items():
        expect = case.get("expect") or case.get("group")
        for row in rows[cid]:
            rule = OPTION.get(row["rule"]) if row["rule"] else None
            dev = rule or "ASK"
            br = rule or row.get("model") or "ASK"
            total += 1
            dev_ok += dev == expect
            br_ok += br == expect
            by_expect[expect][0] += dev == expect
            by_expect[expect][1] += br == expect
            by_expect[expect][2] += 1
            if "dataGb" in case and "ruleData" in row:
                usage["n"] += 1
                want = case["dataGb"]
                dev_data = row["ruleData"]
                br_data = row["ruleData"] if row["ruleData"] is not None else row.get("modelData")
                usage["dev"] += same(dev_data, want)
                usage["br"] += same(br_data, want)
                if not same(br_data, want):
                    details.append("%s data want %s got rule=%s model=%s  %s" % (
                        cid, want, row["ruleData"], row.get("modelData"), case["question"]))
            if "user" in case and "modelUser" in row and row.get("modelUser") is not None:
                user_n += 1
                user_ok += row["modelUser"] == case["user"]
            if br != expect:
                details.append("%s want %s dev %s br %s  %s" % (cid, expect, dev, br, case["question"]))
    print("나이대 develop %d/%d  branch %d/%d" % (dev_ok, total, br_ok, total))
    for expect, (d, b, n) in sorted(by_expect.items()):
        print("  %-8s develop %d/%d  branch %d/%d" % (expect, d, n, b, n))
    if usage["n"]:
        print("사용량 develop %d/%d  branch %d/%d" % (usage["dev"], usage["n"], usage["br"], usage["n"]))
    if user_n:
        print("본인 여부 %d/%d" % (user_ok, user_n))
    if ms:
        print("모델 호출 ms p50 %d max %d (n=%d)" % (statistics.median(ms), max(ms), len(ms)))
    print()
    for line in sorted(set(details)):
        print(line)


def same(got, want):
    if want is None:
        return got is None
    return got is not None and abs(float(got) - float(want)) < 0.01


if __name__ == "__main__":
    main()
