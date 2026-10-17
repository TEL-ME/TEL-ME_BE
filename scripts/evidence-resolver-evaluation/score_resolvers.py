"""EvidenceResolverClientProbe 결과(jsonl)를 모델별로 집계한다. 모델을 부르지 않는다.

사용: python3 -I scripts/evidence-resolver-evaluation/score_resolvers.py <결과.jsonl(.gz)> [<결과2.jsonl(.gz)> ...]
여러 파일은 이어 붙여 모델별로 합친다.
"""

import gzip
import json
import re
import sys
from collections import defaultdict


def load(paths):
    rows = []
    for path in paths:
        opener = gzip.open if path.endswith(".gz") else open
        with opener(path, "rt", encoding="utf-8") as stream:
            rows.extend(json.loads(line) for line in stream if line.strip())
    return rows


def parse_json(text):
    try:
        return json.loads(text)
    except (TypeError, ValueError):
        found = re.search(r"\{.*\}", text or "", re.S)
        if found:
            try:
                return json.loads(found.group(0))
            except ValueError:
                return None
    return None


def faq_id(value):
    found = re.search(r"\d+", str(value or ""))
    return int(found.group(0)) if found else None


def pool_answers(row):
    """질문에서 모델이 볼 수 있었던 FAQ 답변. 비교는 원문 검색과 대상별 검색, 후보는 후보 목록이다."""
    pools = {}
    groups = [row.get("originalSources", []), row.get("candidates", [])]
    groups += [search["sources"] for search in row.get("targetedSearches", [])]
    for group in groups:
        for source in group:
            pools[source["faqId"]] = source["answer"]
    return pools


def quote_checks(row):
    """호출마다 모델이 복사한 인용문이 해당 FAQ 답변 원문에 그대로 있는지. (확인한 인용 수, 그대로 있는 수)"""
    pools = pool_answers(row)
    checked = exact = 0
    for call in row["calls"]:
        output = parse_json(call.get("output"))
        if not isinstance(output, dict):
            continue
        pairs = [(output.get("faqId"), output.get("quote")),
                 (output.get("leftFaqId"), output.get("leftQuote")),
                 (output.get("rightFaqId"), output.get("rightQuote"))]
        for faq, quote in pairs:
            if not quote:
                continue
            checked += 1
            answer = pools.get(faq_id(faq))
            if answer is not None and quote in answer:
                exact += 1
    return checked, exact


def percent(part, whole):
    return f"{part}/{whole} ({part / whole:.0%})" if whole else "0/0"


def score_comparison(rows):
    by_model = defaultdict(list)
    for row in rows:
        if row["kind"] == "comparison":
            by_model[row["model"]].append(row)
    for model, items in sorted(by_model.items()):
        total = len(items)
        verified = sum(1 for r in items if r.get("answerMethod") == "VERIFIED_FAQ_QUOTE")
        errors = sum(1 for r in items if r.get("error"))
        gold_rows = [r for r in items if r.get("goldFaqIds")]
        gold_ok = sum(1 for r in gold_rows
                      if set(r["goldFaqIds"]) <= {s["faqId"] for s in r.get("selectedSources", [])})
        checked = exact = 0
        for r in items:
            c, e = quote_checks(r)
            checked += c
            exact += e
        stable = stability(items)
        truncated = sum(1 for r in items for c in r["calls"] if c.get("stopReason") == "max_tokens")
        print(f"[비교 근거 검증 / {model}] 시도 {total}회  출력 한도로 잘린 호출: {truncated}")
        print(f"  검증 통과로 답이 만들어짐: {percent(verified, total)}  오류: {errors}")
        print(f"  정답 FAQ가 있는 질문에서 정답을 모두 고름: {percent(gold_ok, len(gold_rows))}")
        print(f"  모델이 복사한 인용문이 원문과 그대로 같음: {percent(exact, checked)}")
        print(f"  같은 질문의 반복 결과 일치(선택 FAQ 기준): {stable}")


def stability(items):
    runs = defaultdict(list)
    for r in items:
        if r["kind"] == "comparison":
            key = tuple(sorted(s["faqId"] for s in r.get("selectedSources", [])))
        else:
            key = (model_choice(r) or {}).get("faqId")
        runs[r["id"]].append(key)
    repeated = {i: v for i, v in runs.items() if len(v) > 1}
    if not repeated:
        return "반복 없음"
    same = sum(1 for v in repeated.values() if len(set(v)) == 1)
    return percent(same, len(repeated))


def model_choice(row):
    """모델이 첫 호출에서 고른 후보. 앱이 모델 답을 받은 뒤 하는 핵심 단어 검사와 인용 검사 전의 선택이다."""
    for call in row["calls"]:
        if call.get("promptVersion") != "faq-candidate-evidence-v2":
            continue
        output = parse_json(call.get("output"))
        if not isinstance(output, dict) or output.get("answerable") is not True:
            return None
        chosen = faq_id(output.get("faqId"))
        for source in row.get("candidates", []):
            if source["faqId"] == chosen:
                return source
        return None
    return None


def score_candidate(rows):
    by_model = defaultdict(list)
    for row in rows:
        if row["kind"] == "candidate":
            by_model[row["model"]].append(row)
    for model, items in sorted(by_model.items()):
        answerable = [r for r in items if r.get("goldSlotIds")]
        unrelated = [r for r in items if not r.get("goldSlotIds")]
        retrievable = [r for r in answerable
                       if {s["slotId"] for s in r.get("candidates", [])} & set(r["goldSlotIds"])]
        correct = sum(1 for r in retrievable if (model_choice(r) or {}).get("slotId") in r["goldSlotIds"])
        wrong = sum(1 for r in retrievable
                    if model_choice(r) and model_choice(r)["slotId"] not in r["goldSlotIds"])
        missed = sum(1 for r in retrievable if not model_choice(r))
        false_pick = sum(1 for r in unrelated if model_choice(r))
        accepted = sum(1 for r in items if r.get("picked"))
        errors = sum(1 for r in items if r.get("error"))
        truncated = sum(1 for r in items for c in r["calls"] if c.get("stopReason") == "max_tokens")
        checked = exact = 0
        for r in items:
            c, e = quote_checks(r)
            checked += c
            exact += e
        print(f"[FAQ 후보 근거 검증 / {model}] 시도 {len(items)}회  오류: {errors}  출력 한도로 잘린 호출: {truncated}")
        print("  <모델이 고른 FAQ 기준 (앱의 핵심 단어·인용 검사 전)>")
        print(f"  정답이 후보에 있는 질문({len(retrievable)}회 시도) 중 정답 선택: {percent(correct, len(retrievable))}, "
              f"다른 FAQ 선택: {wrong}, 선택 안 함: {missed}")
        print(f"  정답이 없는 질문 중 FAQ를 잘못 고른 횟수: {percent(false_pick, len(unrelated))}")
        print(f"  모델이 복사한 인용문이 원문과 그대로 같음: {percent(exact, checked)}")
        print(f"  <앱이 최종 채택> 앱 검사를 통과해 채택된 횟수: {percent(accepted, len(items))}")
        print(f"  같은 질문의 반복 결과 일치(모델이 고른 FAQ 기준): {stability(items)}")


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    rows = load(sys.argv[1:])
    score_comparison(rows)
    score_candidate(rows)


if __name__ == "__main__":
    main()
