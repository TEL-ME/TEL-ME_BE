#!/usr/bin/env python3
# 회색 지대 판정으로 검색 임계값을 얼마나 낮출 수 있는지 오프라인 측정
import argparse
import hashlib
import json
import os
import sys
import time
from datetime import datetime
from pathlib import Path

from check_eval_questions import expected_slots
from evidence_check_bedrock import (
    B2_PROMPT_VERSION,
    PRICES,
    PROMPTS,
    build_context,
    call_model,
    cost_usd,
    parse_answerable,
    user_prompt,
)
from simulate_dual_vector import load_runs, signature

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_QA = [ROOT / ".measure/raw-1150-Q_A-dual.json", ROOT / ".measure/raw-supplement50-Q_A-dual.json"]
DEFAULT_QO = [ROOT / ".measure/raw-1150-QO-dual.json", ROOT / ".measure/raw-supplement50-QO-dual.json"]
DEFAULT_MULTIGOLD = ROOT / "scripts/data/eval_questions_180_multigold.json"
DEFAULT_FAQ = ROOT / "scripts/data/faq_full_1150.json"

QA_UPPER = 0.72
QO_UPPER = 0.88
# FaqSearchAnswerProvider가 LLM에 넘기는 검색 결과 수
TOP_K = 3
STEP = 0.02

# E1 측정(25건, 근거 3개 묶음)의 실제 호출당 출력 토큰은 약 134
EST_OUTPUT_TOKENS = 250
CHARS_PER_TOKEN = 1.0


def content_hash(question, answer):
    # scripts/README.md: content_hash = SHA-256(question + answer), 구분자 없음
    return hashlib.sha256((question + answer).encode("utf-8")).hexdigest()


def lines(upper, floor):
    """upper부터 floor까지 STEP 간격. upper 자체(= 회색 지대 없음, 현행)를 포함한다."""
    values = []
    value = upper
    while value >= floor - 1e-9:
        values.append(round(value, 2))
        value -= STEP
    return values


def gray_pairs(qa, qo, qa_floor, qo_floor):
    """가장 낮은 아래 선에서 회색 지대에 들어오는 (eval_id, slot_id). 판정 호출 대상 전체."""
    pairs = set()
    for eval_id in qa:
        for results, upper, floor in ((qa[eval_id]["results"], QA_UPPER, qa_floor),
                                      (qo[eval_id]["results"], QO_UPPER, qo_floor)):
            for r in results[:TOP_K]:
                # slot_id 없는 시드·관리자 FAQ는 JSON에 본문이 없어 판정할 수 없다(verdict에서 거부로 본다)
                if floor <= r["score"] < upper and r["slot_id"] is not None:
                    pairs.add((eval_id, r["slot_id"]))
    return sorted(pairs)


def unjudgeable(qa, qo, qa_floor, qo_floor):
    """회색 지대에 들어오지만 slot_id가 없어 판정 못 하는 후보 수."""
    return sum(1 for eval_id in qa
               for results, upper, floor in ((qa[eval_id]["results"], QA_UPPER, qa_floor),
                                             (qo[eval_id]["results"], QO_UPPER, qo_floor))
               for r in results[:TOP_K]
               if floor <= r["score"] < upper and r["slot_id"] is None)


def served(qa_results, qo_results, qa_line, qo_line, verdict):
    """simulate_dual_vector.merge와 같은 순서(QUESTION_ONLY 우선, 중복 제거, TOP_K 컷)에 회색 지대 판정만 더한다.

    verdict(slot_id) -> 판정기가 통과시켰는지. 회색 지대 후보에만 부른다.
    """
    def keep(r, upper, line):
        if r["score"] >= upper:
            return True
        return r["score"] >= line and verdict(r["slot_id"])

    a = [r["slot_id"] for r in qa_results[:TOP_K] if keep(r, QA_UPPER, qa_line)]
    b = [r["slot_id"] for r in qo_results[:TOP_K] if keep(r, QO_UPPER, qo_line)]
    merged = []
    for slot in b + a:
        if slot not in merged:
            merged.append(slot)
    return merged[:TOP_K]


def evaluate(qa, qo, labels, qa_line, qo_line, verdicts):
    """한 아래 선 조합의 결과. labels: {이름: {eval_id: 정답 slot 집합}}."""
    answered = {name: set() for name in labels}
    unrelated_passed = {}
    judged_questions = set()
    calls = 0
    for eval_id, item in qa.items():
        def verdict(slot, eval_id=eval_id):
            return slot is not None and verdicts[(eval_id, slot)]

        slots = served(qa[eval_id]["results"], qo[eval_id]["results"], qa_line, qo_line, verdict)
        # slot_id 없는 후보는 판정 없이 거부하므로 판정 호출로 세지 않는다
        gray = {r["slot_id"] for r in qa[eval_id]["results"][:TOP_K]
                if qa_line <= r["score"] < QA_UPPER and r["slot_id"] is not None}
        gray |= {r["slot_id"] for r in qo[eval_id]["results"][:TOP_K]
                 if qo_line <= r["score"] < QO_UPPER and r["slot_id"] is not None}
        if gray:
            judged_questions.add(eval_id)
            calls += len(gray)
        if item["type"] == "UNRELATED":
            if slots:
                unrelated_passed.setdefault(item["source"], set()).add(eval_id)
            continue
        for name, label in labels.items():
            if set(slots) & label[eval_id]:
                answered[name].add(eval_id)
    return {
        "qaLine": qa_line,
        "qoLine": qo_line,
        "answered": answered,
        "unrelatedPassed": unrelated_passed,
        "judgedQuestions": len(judged_questions),
        "judgeCalls": calls,
    }


def pick(base, results, sources):
    """THRESHOLD_LOWERING_EXPERIMENT_PLAN.md 2절 선택 규칙 (공식 라벨).

    1. 무관 통과가 평가셋별로 현행 이하  2. 그중 답변 수 최대  3. 같으면 판정 호출이 적은 쪽(= 선이 높은 쪽)
    """
    def unrelated(r, src):
        return len(r["unrelatedPassed"].get(src, ()))

    keep = [r for r in results
            if all(unrelated(r, src) <= unrelated(base, src) for src in sources)]
    if not keep:
        return None
    return max(keep, key=lambda r: (len(r["answered"]["공식"]), -r["judgeCalls"], r["qaLine"], r["qoLine"]))


def load_cache(path, prompt_version):
    if not path.exists():
        return {}
    data = json.loads(path.read_text())
    if data.get("promptVersion") != prompt_version:
        sys.exit(f"{path}: 다른 프롬프트({data.get('promptVersion')})로 만든 캐시입니다")
    return data["judgments"]


def input_hash(prompt):
    """판정에 넣은 사용자 프롬프트(평가 질문 + FAQ 본문)의 해시. 질문 문장이 바뀐 캐시를 걸러낸다."""
    return hashlib.sha256(prompt.encode("utf-8")).hexdigest()


def reusable(entry, prompt, repeat):
    """같은 입력을 같은 반복 수로 판정한 캐시만 다시 쓴다. 해시가 없는 예전 캐시도 다시 판정한다."""
    return (entry is not None
            and entry.get("inputHash") == input_hash(prompt)
            and len(entry["runs"]) == repeat)


def save_cache(path, model, prompt_version, cache):
    path.write_text(json.dumps(
        {"model": model, "promptVersion": prompt_version, "judgments": cache},
        ensure_ascii=False, indent=1))


def majority(runs):
    votes = [r["answerable"] for r in runs]
    return sum(votes) * 2 > len(votes)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="openai.gpt-oss-120b-1:0", choices=sorted(PRICES))
    parser.add_argument("--prompt", default=B2_PROMPT_VERSION, choices=sorted(PROMPTS))
    parser.add_argument("--qa", type=Path, nargs="+", default=DEFAULT_QA)
    parser.add_argument("--qo", type=Path, nargs="+", default=DEFAULT_QO)
    parser.add_argument("--multigold", type=Path, default=DEFAULT_MULTIGOLD)
    parser.add_argument("--faq", type=Path, default=DEFAULT_FAQ)
    parser.add_argument("--qa-floor", type=float, default=0.60)
    parser.add_argument("--qo-floor", type=float, default=0.80)
    parser.add_argument("--repeat", type=int, default=3, help="쌍마다 판정 반복 수 (다수결)")
    parser.add_argument("--krw-per-usd", type=float, default=1450.0)
    parser.add_argument("--run", action="store_true", help="없으면 예상 비용만 출력 (캐시가 다 차 있으면 결과 표도)")
    parser.add_argument("--out-dir", type=Path, default=ROOT / ".measure")
    args = parser.parse_args()

    qa, qo = load_runs(args.qa, "QA"), load_runs(args.qo, "QUESTION")
    if signature(qa) != signature(qo):
        sys.exit("--qa와 --qo의 평가셋이 다릅니다")
    multigold = {e["eval_id"]: e for e in json.loads(args.multigold.read_text())}
    if set(multigold) != set(qa):
        sys.exit(f"{args.multigold}: 원시 결과와 문항이 다릅니다")
    faqs = {f["slot_id"]: f for f in json.loads(args.faq.read_text())}

    pairs = gray_pairs(qa, qo, args.qa_floor, args.qo_floor)
    # 원시 결과 수집 당시 DB의 FAQ 내용과 지금 JSON이 같은지. 다르면 판정기가 엉뚱한 본문을 보게 된다
    hashes = {(e, r["slot_id"]): r["content_hash"]
              for e in qa for r in qa[e]["results"] + qo[e]["results"]}
    stale = [p for p in pairs
             if p[1] not in faqs
             or content_hash(faqs[p[1]]["question"], faqs[p[1]]["answer"]) != hashes[p]]
    if stale:
        sys.exit(f"FAQ 내용이 원시 결과와 다른 쌍 {len(stale)}건 (예: {stale[:3]})")

    slug = args.model.split(".", 1)[1].split(":")[0]
    # B2 캐시는 처음 만든 이름을 그대로 쓴다
    if args.prompt != B2_PROMPT_VERSION:
        slug += "-" + args.prompt.rsplit("-", 1)[1]
    system = PROMPTS[args.prompt]
    cache_path = args.out_dir / f"e3-gray-judgments-{slug}.json"
    cache = load_cache(cache_path, args.prompt)

    def prompt_of(pair):
        faq = faqs[pair[1]]
        return user_prompt(build_context([faq]), multigold[pair[0]]["question"])

    todo = [p for p in pairs if not reusable(cache.get(f"{p[0]}|{p[1]}"), prompt_of(p), args.repeat)]
    stale = sum(1 for p in todo if f"{p[0]}|{p[1]}" in cache)

    calls = len(todo) * args.repeat
    chars = sum(len(system) + len(prompt_of(p)) for p in todo) * args.repeat
    est = cost_usd(args.model, int(chars / CHARS_PER_TOKEN), calls * EST_OUTPUT_TOKENS)
    print(f"회색 지대 쌍 {len(pairs)}개 (질문 {len({p[0] for p in pairs})}개) | "
          f"Q_A {args.qa_floor}~{QA_UPPER}, 질문 {args.qo_floor}~{QO_UPPER}, top {TOP_K}")
    skipped = unjudgeable(qa, qo, args.qa_floor, args.qo_floor)
    if skipped:
        print(f"slot_id 없는 FAQ라 판정 못 하는 회색 지대 후보 {skipped}개 → 거부로 계산")
    print(f"캐시에 있음 {len(pairs) - len(todo)}개 → 새로 판정 {len(todo)}쌍 × {args.repeat}회 = {calls}호출")
    if stale:
        print(f"  그중 {stale}쌍은 캐시에 있지만 입력(질문·FAQ 문장)이나 반복 수가 달라 다시 판정")
    print(f"예상 비용 ${est:.4f} ≈ {est * args.krw_per_usd:,.0f}원 (모델 {args.model}, 프롬프트 {args.prompt}, 환율 {args.krw_per_usd:,.0f})")

    if todo and not args.run:
        print("예상만 출력했습니다. 실제로 돌리려면 --run 을 붙이세요.")
        return

    if todo:
        api_key = os.environ.get("BEDROCK_API_KEY")
        if not api_key:
            sys.exit("BEDROCK_API_KEY가 없습니다. .env를 읽고 실행하세요.")
        region = os.environ.get("AWS_REGION", "us-east-1")
        args.out_dir.mkdir(parents=True, exist_ok=True)
        tokens_in = tokens_out = 0
        failed = []
        for index, pair in enumerate(todo, 1):
            runs = []
            for i in range(1, args.repeat + 1):
                start = time.monotonic()
                text, error, usage = call_model(api_key, region, args.model, system, prompt_of(pair))
                if error and error.startswith("HTTP 4"):
                    save_cache(cache_path, args.model, args.prompt, cache)
                    sys.exit(f"요청 오류로 중단 (판정한 {index - 1}쌍은 캐시에 저장): {error}")
                answerable, parsed = parse_answerable(text)
                tokens_in += usage.get("inputTokens", 0)
                tokens_out += usage.get("outputTokens", 0)
                runs.append({"run": i, "answerable": answerable, "parsed": parsed, "rawResponse": text,
                             "error": error, "elapsedMs": int((time.monotonic() - start) * 1000)})
            # 재시도 후에도 남은 오류는 fail-open(true)으로 판정되므로 캐시에 남기지 않고 다음 실행에서 다시 판정한다
            if any(r["error"] for r in runs):
                failed.append(pair)
            else:
                cache[f"{pair[0]}|{pair[1]}"] = {"inputHash": input_hash(prompt_of(pair)), "runs": runs}
            if index % 20 == 0 or index == len(todo):
                save_cache(cache_path, args.model, args.prompt, cache)
                spent = cost_usd(args.model, tokens_in, tokens_out)
                print(f"  {index}/{len(todo)}쌍 | 지금까지 ${spent:.4f} ≈ {spent * args.krw_per_usd:,.0f}원")
        spent = cost_usd(args.model, tokens_in, tokens_out)
        print(f"토큰 입력 {tokens_in:,} / 출력 {tokens_out:,} | 실제 비용 ${spent:.4f} "
              f"≈ {spent * args.krw_per_usd:,.0f}원")
        if failed:
            sys.exit(f"오류가 남은 {len(failed)}쌍은 캐시에 저장하지 않았습니다 (예: {failed[:3]}). "
                     f"다시 실행하면 그 쌍만 판정합니다")

    verdicts = {p: majority(cache[f"{p[0]}|{p[1]}"]["runs"]) for p in pairs}
    labels = {
        "공식": {e: expected_slots(i) for e, i in qa.items()},
        "다중 정답": {e: expected_slots(i) for e, i in multigold.items()},
    }
    sources = sorted({i["source"] for i in qa.values() if i["type"] == "UNRELATED"})
    base = evaluate(qa, qo, labels, QA_UPPER, QO_UPPER, verdicts)
    results = [evaluate(qa, qo, labels, a, b, verdicts)
               for a in lines(QA_UPPER, args.qa_floor) for b in lines(QO_UPPER, args.qo_floor)]
    best = pick(base, results, sources)

    positives = sum(1 for i in qa.values() if i["type"] != "UNRELATED")
    unrelated_total = {s: sum(1 for i in qa.values() if i["type"] == "UNRELATED" and i["source"] == s)
                       for s in sources}
    passed_gray = sum(verdicts.values())
    print(f"\n회색 지대 쌍 {len(pairs)}개 중 판정 통과 {passed_gray}개")
    print(f"\n| Q_A 아래 선 | 질문 아래 선 | 답변(공식) | 답변(다중 정답) | "
          + " | ".join(f"무관 통과 {s}" for s in sources) + " | 판정 질문 수 | 판정 호출 |")
    print("|---" * (6 + len(sources) - 1) + "|")
    for r in results:
        mark = " ★" if r is best else ""
        print(f"| {r['qaLine']:.2f} | {r['qoLine']:.2f} | {len(r['answered']['공식'])}/{positives}"
              f" | {len(r['answered']['다중 정답'])}/{positives} | "
              + " | ".join(f"{len(r['unrelatedPassed'].get(s, ()))}/{unrelated_total[s]}" for s in sources)
              + f" | {r['judgedQuestions']} | {r['judgeCalls']}{mark} |")

    if best is None:
        print("\n무관 통과를 평가셋별로 현행 이하로 지키는 조합이 없습니다")
    else:
        gained = sorted(best["answered"]["공식"] - base["answered"]["공식"])
        lost = sorted(base["answered"]["공식"] - best["answered"]["공식"])
        print(f"\n선택(공식 라벨, 무관 통과 평가셋별 현행 이하 → 답변 최대 → 판정 호출 적은 쪽): "
              f"Q_A {best['qaLine']:.2f} / 질문 {best['qoLine']:.2f}")
        print(f"  답변 공식 {len(base['answered']['공식'])} → {len(best['answered']['공식'])}, "
              f"다중 정답 {len(base['answered']['다중 정답'])} → {len(best['answered']['다중 정답'])}")
        print(f"  살아난 문항: {gained}")
        print(f"  현행에서 맞았는데 놓친 문항: {lost or '없음'}")
        print(f"  판정이 필요한 질문 {best['judgedQuestions']}/{len(qa)} "
              f"({best['judgedQuestions'] / len(qa):.0%}), 판정 호출 {best['judgeCalls']}회")
    print("\n주의: 선택 값은 이 180건에서 고른 값이다. 확인용 세트(holdout 90)는 E6에서 한 번만 본다")

    def dump(r):
        return {**r, "answered": {k: sorted(v) for k, v in r["answered"].items()},
                "unrelatedPassed": {k: sorted(v) for k, v in r["unrelatedPassed"].items()}}

    out = args.out_dir / f"e3-gray-zone-{slug}-{datetime.now():%Y%m%d-%H%M%S}.json"
    out.write_text(json.dumps({
        "measuredAt": datetime.now().isoformat(),
        "model": args.model,
        "promptVersion": args.prompt,
        "repeat": args.repeat,
        "upper": {"qa": QA_UPPER, "qo": QO_UPPER},
        "topK": TOP_K,
        "grayPairs": len(pairs),
        "grayPassed": passed_gray,
        "baseline": dump(base),
        "best": dump(best) if best else None,
        "grid": [dump(r) for r in results],
    }, ensure_ascii=False, indent=2))
    print(f"저장: {out}")


if __name__ == "__main__":
    main()
