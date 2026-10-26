#!/usr/bin/env python3
# 쌍둥이 FAQ 정리 오프라인 측정
# 답변 유사도 0.95 이상(TELME-58, bge-m3)인 FAQ를 묶음으로 보고,
# 저장된 원시 결과(top 10)에서 정리한 코퍼스를 흉내 낸다.

import argparse
import json
import sys
from datetime import datetime
from pathlib import Path

from check_duplicates import embed_all, similar_pairs
from check_eval_questions import expected_slots
from generated_questions_eval import pick
from gray_zone_threshold_bedrock import (
    DEFAULT_FAQ,
    DEFAULT_MULTIGOLD,
    DEFAULT_QA,
    DEFAULT_QO,
    QA_UPPER,
    QO_UPPER,
    ROOT,
    TOP_K,
)
from simulate_dual_vector import load_runs, merge, signature

TWIN_THRESHOLD = 0.95
QA_LINES = [0.66, 0.68, 0.70, 0.72, 0.74, 0.76]
QO_LINES = [0.80, 0.82, 0.84, 0.86, 0.88, 0.90, 0.92]


def clusters(slots, pairs):
    """유사 쌍을 이어 묶음을 만든다. {slot: 대표 slot}. 대표 = 묶음에서 파일 순서가 가장 앞선 FAQ."""
    parent = list(range(len(slots)))

    def root(i):
        while parent[i] != i:
            parent[i] = parent[parent[i]]
            i = parent[i]
        return i

    for a, b, _ in pairs:
        ra, rb = root(a), root(b)
        parent[max(ra, rb)] = min(ra, rb)
    return {slot: slots[root(i)] for i, slot in enumerate(slots)}


def deduped(results, rep, mode):
    """원시 결과(점수 내림차순)를 정리한 코퍼스 기준으로 바꾼다. slot_id 없는 시드 FAQ는 묶음이 없어 그대로 둔다."""
    out, seen = [], set()
    for r in results:
        slot = r["slot_id"]
        if slot is None:
            out.append(r)
            continue
        if mode == "삭제" and rep[slot] != slot:
            continue
        target = rep[slot]
        if target in seen:
            continue
        seen.add(target)
        out.append({"slot_id": target, "score": r["score"]})
    return out


def short_lists(runs, line):
    """정리 후 top 10 안에 남은 후보가 TOP_K보다 적은데, 잘린 10위 점수가 아래 선 이상인 문항.
    원시 결과 밖(11위 이하)에 선을 넘는 후보가 있을 수 있어 결과가 과소평가될 수 있다."""
    return sorted(e for e, (raw, new) in runs.items()
                  if len(new) < TOP_K and len(raw) >= 10 and raw[-1]["score"] >= line)


def evaluate(qa, qa_results, qo_results, labels, qa_line, qo_line):
    answered = {name: set() for name in labels}
    unrelated = {}
    for eval_id, item in qa.items():
        served = merge(qa_results[eval_id], qo_results[eval_id], qa_line, qo_line, TOP_K)
        if item["type"] == "UNRELATED":
            if served:
                unrelated.setdefault(item["source"], set()).add(eval_id)
            continue
        for name, label in labels.items():
            if set(served) & label[eval_id]:
                answered[name].add(eval_id)
    return {"qaLine": qa_line, "qoLine": qo_line, "answered": answered, "unrelatedPassed": unrelated}


def best(base, rows, sources, slack):
    """generated_questions_eval.pick은 qoLine만 본다. 같은 공식 답변이면 Q_A 선이 높은 쪽을 고르도록 감싼다."""
    picked = pick(base, [dict(r, qoLine=(r["qoLine"], r["qaLine"])) for r in rows], sources, slack)
    if picked is None:
        return None
    return next(r for r in rows if (r["qoLine"], r["qaLine"]) == picked["qoLine"])


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--threshold", type=float, default=TWIN_THRESHOLD)
    parser.add_argument("--out-dir", type=Path, default=ROOT / ".measure")
    args = parser.parse_args()

    qa, qo = load_runs(DEFAULT_QA, "QA"), load_runs(DEFAULT_QO, "QUESTION")
    if signature(qa) != signature(qo):
        sys.exit("--qa와 --qo의 평가셋이 다릅니다")
    multigold = {e["eval_id"]: e for e in json.loads(DEFAULT_MULTIGOLD.read_text())}
    faqs = json.loads(DEFAULT_FAQ.read_text())
    slots = [f["slot_id"] for f in faqs]

    vectors = embed_all([f["answer"] for f in faqs], 32, ROOT / "scripts/data/.embed_cache.json")
    pairs, _ = similar_pairs(vectors, args.threshold)
    rep = clusters(slots, pairs)
    groups = {}
    for slot, r in rep.items():
        groups.setdefault(r, []).append(slot)
    twins = {r: g for r, g in groups.items() if len(g) > 1}
    print(f"답변 유사도 {args.threshold} 이상 {len(pairs)}쌍 → 묶음 {len(twins)}개, "
          f"정리하면 FAQ {len(faqs)} → {len(groups)}개 ({len(faqs) - len(groups)}개 줄어듦)")

    def moved(label):
        return {e: {rep.get(s, s) for s in slots_} for e, slots_ in label.items()}

    # 코퍼스를 그대로 둘 때는 대표로 옮기지 않고 묶음 전체를 정답으로 넓힌다(검색 결과가 원래 slot_id라서)
    def widened(label):
        return {e: {m for s in slots_ for m in groups.get(rep.get(s, s), [s])} for e, slots_ in label.items()}

    official = {e: expected_slots(i) for e, i in qa.items()}
    multi = {e: expected_slots(i) for e, i in multigold.items()}
    in_twins = sum(1 for e, s in official.items() if any(rep.get(x, x) in twins for x in s))
    print(f"공식 정답이 쌍둥이 묶음에 걸린 문항 {in_twins}개")

    raw_qa = {e: qa[e]["results"] for e in qa}
    raw_qo = {e: qo[e]["results"] for e in qa}
    sources = sorted({i["source"] for i in qa.values() if i["type"] == "UNRELATED"})
    positives = sum(1 for i in qa.values() if i["type"] != "UNRELATED")
    current_labels = {"공식": official, "다중 정답": multi}
    moved_labels = {"공식": moved(official), "다중 정답": moved(multi)}
    base = evaluate(qa, raw_qa, raw_qo, current_labels, QA_UPPER, QO_UPPER)

    configs = {"라벨만 넓힘": (raw_qa, raw_qo, {"공식": widened(official), "다중 정답": widened(multi)})}
    for mode in ("삭제", "통합"):
        configs[mode] = ({e: deduped(raw_qa[e], rep, mode) for e in qa},
                         {e: deduped(raw_qo[e], rep, mode) for e in qa}, moved_labels)

    def row_text(label, r, mark=""):
        return (f"| {label}{mark} | {r['qaLine']:.2f} / {r['qoLine']:.2f} | {len(r['answered']['공식'])}/{positives} | "
                f"{len(r['answered']['다중 정답'])}/{positives} | "
                + " | ".join(str(len(r["unrelatedPassed"].get(s, ()))) for s in sources) + " |")

    header = "| 구성 | Q_A / 질문 선 | 답변(공식) | 답변(다중 정답) | " + " | ".join(f"무관 {s}" for s in sources) + " |"
    print("\n판정기 없음. 같은 선에서 비교\n" + header + "\n" + "|---" * (4 + len(sources)) + "|")
    print(row_text("현행", base))
    results = {}
    for name, (qa_r, qo_r, labels) in configs.items():
        rows = [evaluate(qa, qa_r, qo_r, labels, a, b) for a in QA_LINES for b in QO_LINES]
        same = next(r for r in rows if (r["qaLine"], r["qoLine"]) == (QA_UPPER, QO_UPPER))
        results[name] = {"rows": rows, "same": same, "strict": best(base, rows, sources, 0),
                         "relaxed": best(base, rows, sources, 1)}
        print(row_text(name, same))

    print("\n선택 규칙으로 고른 선 (현행 규칙 = 평가셋별 무관 현행 이하 / 완화안 = 합계 +1)\n" + header
          + "\n" + "|---" * (4 + len(sources)) + "|")
    for name, res in results.items():
        for rule in ("strict", "relaxed"):
            r = res[rule]
            if r is None:
                print(f"| {name} {rule} | 고를 수 있는 선 없음 |")
                continue
            print(row_text(name, r, " ★현행규칙" if rule == "strict" else " ☆완화안"))

    for name in ("삭제", "통합"):
        qa_r, qo_r, _ = configs[name]
        short = set(short_lists({e: (raw_qa[e], qa_r[e]) for e in qa}, min(QA_LINES)))
        short |= set(short_lists({e: (raw_qo[e], qo_r[e]) for e in qa}, min(QO_LINES)))
        print(f"\n{name}: 정리 후 top 10이 모자랄 수 있는 문항 {len(short)}개{' ' + str(sorted(short)[:10]) if short else ''}")
        same = results[name]["same"]
        print(f"  현행 선에서 살아난 문항: {sorted(same['answered']['공식'] - base['answered']['공식'])}")
        print(f"  현행 선에서 놓친 문항: {sorted(base['answered']['공식'] - same['answered']['공식']) or '없음'}")

    def dump(r):
        if r is None:
            return None
        return {"qaLine": r["qaLine"], "qoLine": r["qoLine"],
                "answered": {k: sorted(v) for k, v in r["answered"].items()},
                "unrelatedPassed": {k: sorted(v) for k, v in r["unrelatedPassed"].items()}}

    args.out_dir.mkdir(parents=True, exist_ok=True)
    out = args.out_dir / f"e4-twin-faq-result-{datetime.now():%Y%m%d-%H%M%S}.json"
    out.write_text(json.dumps({
        "measuredAt": datetime.now().isoformat(), "twinThreshold": args.threshold, "pairs": len(pairs),
        "groups": {r: g for r, g in twins.items()}, "baseline": dump(base),
        "configs": {name: {"same": dump(res["same"]), "strict": dump(res["strict"]),
                           "relaxed": dump(res["relaxed"]), "rows": [dump(r) for r in res["rows"]]}
                    for name, res in results.items()},
    }, ensure_ascii=False, indent=1))
    print(f"\n저장: {out}")


if __name__ == "__main__":
    main()
