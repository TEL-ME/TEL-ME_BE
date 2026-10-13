#!/usr/bin/env python3
# 예상 질문 생성 전처리 오프라인 측정
# FAQ마다 고객 말투 예상 질문을 Bedrock으로 만들고 로컬 bge-m3로 임베딩
import argparse
import json
import os
import sys
from datetime import datetime
from pathlib import Path

from check_duplicates import embed_all
from check_eval_questions import expected_slots
from evidence_check_bedrock import PRICES, call_model, cost_usd
from gray_zone_threshold_bedrock import (
    DEFAULT_FAQ,
    DEFAULT_MULTIGOLD,
    DEFAULT_QA,
    DEFAULT_QO,
    QA_UPPER,
    QO_UPPER,
    ROOT,
    TOP_K,
    content_hash,
)
from simulate_dual_vector import load_runs, merge, signature

try:
    import numpy as np
except ImportError:
    sys.exit("numpy가 필요합니다: pip install -r scripts/requirements.txt")

PER_FAQ = 4
RAW_TOP = 10
QO_LINES = [0.80, 0.82, 0.84, 0.86, 0.88, 0.89, 0.90, 0.91, 0.92, 0.94]
EST_INPUT_EXTRA = 50
EST_OUTPUT_TOKENS = 350
CHARS_PER_TOKEN = 1.0

PROMPT_VERSION = "faq-generated-questions-v3"
SYSTEM_PROMPT = f"""당신은 통신사 고객센터 채팅에 들어오는 고객 질문을 예상합니다.
주어진 FAQ의 답변(A)으로 답할 수 있는 고객 질문을 {PER_FAQ}개 만드십시오.

- 답변(A)에 담긴 서로 다른 사실을 나눠서 묻습니다. FAQ 질문(Q)이 묻지 않는 사실이 답변에 있으면 그것을 묻는 질문을 반드시 포함합니다.
- FAQ 질문(Q)의 문장을 다듬거나 단어 순서만 바꾼 질문은 최대 1개입니다. 나머지는 고객이 쓸 법한 다른 표현과 다른 상황으로 씁니다.
- 질문마다 무엇에 대한 것인지(업무, 상품, 서비스 이름)를 넣습니다. "신청", "이거"처럼 대상이 빠진 질문은 만들지 않습니다.
- FAQ 질문에 있는 부수적인 인물이나 사정(가족 관계 등)은 질문마다 그대로 따라 쓰지 않습니다.
- 답변(A)의 한 문장이 그대로 답이 되는 질문만 만듭니다. 답변에 없는 방법, 절차, 조건, 추가 요금, 예외를 묻지 않습니다.
- 답변에 나온 숫자, 시간, 금액을 질문에 미리 넣지 않습니다. 고객은 그 값을 모르고 묻습니다.
- FAQ 질문에서 업무가 드러나지 않으면(예: "신청") 답변과 질문 전체를 보고 어떤 업무인지 판단해 적습니다.
- 실제 고객이 채팅창에 치는 짧은 구어체로, 40자 이내로 씁니다.

JSON만 출력하십시오.
{{"questions": ["...", "..."]}}
"""


def user_prompt(faq):
    return f"Q: {faq['question']}\nA: {faq['answer']}"


def parse_questions(text):
    if not text:
        return []
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end <= start:
        return []
    try:
        data = json.loads(text[start:end + 1])
    except json.JSONDecodeError:
        return []
    questions = data.get("questions") if isinstance(data, dict) else None
    if not isinstance(questions, list):
        return []
    cleaned = []
    for q in questions:
        if isinstance(q, str) and q.strip() and q.strip() not in cleaned:
            cleaned.append(q.strip())
    return cleaned[:PER_FAQ]


def unit(vectors):
    matrix = np.array(vectors, dtype=np.float64)
    return matrix / np.linalg.norm(matrix, axis=1, keepdims=True)


def question_vector_results(eval_ids, eval_vectors, faq_slots, faq_vectors, raw_qo, generated=None):
    """질문 벡터 top 10을 원시 결과 형식으로 만든다.

    generated: {faq 인덱스: 예상 질문 벡터 행렬}. 있으면 FAQ 점수 = max(FAQ 질문, 예상 질문들).
    """
    scores = eval_vectors @ faq_vectors.T
    if generated:
        for index, matrix in generated.items():
            scores[:, index] = np.maximum(scores[:, index], (eval_vectors @ matrix.T).max(axis=1))
    out = {}
    for row, eval_id in enumerate(eval_ids):
        top = np.argsort(-scores[row])[:RAW_TOP]
        results = [{"slot_id": faq_slots[j], "score": float(scores[row, j])} for j in top]
        # slot_id 없는 시드 FAQ는 JSON에 없어 원시 결과 점수로 끼워 넣는다(예상 질문 없음)
        results += [{"slot_id": None, "score": r["score"]} for r in raw_qo[eval_id]["results"]
                    if r["slot_id"] is None]
        out[eval_id] = sorted(results, key=lambda r: -r["score"])[:RAW_TOP]
    return out


def evaluate(qa, qo_results, labels, qo_line):
    answered = {name: set() for name in labels}
    unrelated = {}
    for eval_id, item in qa.items():
        served = merge(qa[eval_id]["results"], qo_results[eval_id], QA_UPPER, qo_line, TOP_K)
        if item["type"] == "UNRELATED":
            if served:
                unrelated.setdefault(item["source"], set()).add(eval_id)
            continue
        for name, label in labels.items():
            if set(served) & label[eval_id]:
                answered[name].add(eval_id)
    return {"qoLine": qo_line, "answered": answered, "unrelatedPassed": unrelated}


def pick(base, rows, sources, slack):
    """slack=0: 평가셋별 현행 이하(현행 규칙). slack=1: 합계 현행 +1(완화안). 그 안에서 공식 답변 최대, 같으면 높은 선."""
    def n(r, s):
        return len(r["unrelatedPassed"].get(s, ()))

    if slack == 0:
        keep = [r for r in rows if all(n(r, s) <= n(base, s) for s in sources)]
    else:
        keep = [r for r in rows if sum(n(r, s) for s in sources) <= sum(n(base, s) for s in sources) + slack]
    return max(keep, key=lambda r: (len(r["answered"]["공식"]), r["qoLine"])) if keep else None


def generate_all(faqs, cache, cache_path, model, krw):
    api_key = os.environ.get("BEDROCK_API_KEY")
    if not api_key:
        sys.exit("BEDROCK_API_KEY가 없습니다. .env를 읽고 실행하세요.")
    region = os.environ.get("AWS_REGION", "us-east-1")
    todo = [f for f in faqs if cache.get(f["slot_id"], {}).get("hash") != content_hash(f["question"], f["answer"])]
    tokens_in = tokens_out = 0
    failed = []
    for index, faq in enumerate(todo, 1):
        text, error, usage = call_model(api_key, region, model, SYSTEM_PROMPT, user_prompt(faq))
        if error and error.startswith("HTTP 4"):
            save(cache_path, model, cache)
            sys.exit(f"요청 오류로 중단 (지금까지는 캐시에 저장): {error}")
        tokens_in += usage.get("inputTokens", 0)
        tokens_out += usage.get("outputTokens", 0)
        questions = parse_questions(text)
        # 오류나 빈 결과를 캐시하면 해시가 같아 다시 생성하지 않으므로 남기지 않는다
        if error or not questions:
            failed.append(faq["slot_id"])
        else:
            cache[faq["slot_id"]] = {"hash": content_hash(faq["question"], faq["answer"]),
                                     "questions": questions, "raw": text, "error": error}
        if index % 50 == 0 or index == len(todo):
            save(cache_path, model, cache)
            spent = cost_usd(model, tokens_in, tokens_out)
            print(f"  {index}/{len(todo)} FAQ | 지금까지 ${spent:.4f} ≈ {spent * krw:,.0f}원")
    if todo:
        spent = cost_usd(model, tokens_in, tokens_out)
        print(f"토큰 입력 {tokens_in:,} / 출력 {tokens_out:,} | 실제 비용 ${spent:.4f} ≈ {spent * krw:,.0f}원")
    if failed:
        sys.exit(f"생성 실패 {len(failed)}개는 캐시에 저장하지 않았습니다 (예: {failed[:5]}). "
                 f"다시 실행하면 그 FAQ만 생성합니다")


def save(path, model, cache):
    path.write_text(json.dumps({"model": model, "promptVersion": PROMPT_VERSION, "faqs": cache},
                               ensure_ascii=False, indent=1))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="openai.gpt-oss-120b-1:0", choices=sorted(PRICES))
    parser.add_argument("--krw-per-usd", type=float, default=1450.0)
    parser.add_argument("--run", action="store_true")
    parser.add_argument("--sample", type=int, help="카테고리별로 고르게 N개 FAQ만 생성해 결과를 보고 끝낸다 (품질 확인용)")
    parser.add_argument("--out-dir", type=Path, default=ROOT / ".measure")
    args = parser.parse_args()

    qa, qo = load_runs(DEFAULT_QA, "QA"), load_runs(DEFAULT_QO, "QUESTION")
    if signature(qa) != signature(qo):
        sys.exit("--qa와 --qo의 평가셋이 다릅니다")
    multigold = {e["eval_id"]: e for e in json.loads(DEFAULT_MULTIGOLD.read_text())}
    faqs = json.loads(DEFAULT_FAQ.read_text())
    targets = faqs
    if args.sample:
        # 같은 카테고리만 뽑히지 않게 간격을 두고 고른다
        step = max(1, len(faqs) // args.sample)
        targets = faqs[::step][: args.sample]

    slug = args.model.split(".", 1)[1].split(":")[0]
    cache_path = args.out_dir / f"e4-generated-questions-{slug}.json"
    cache = {}
    if cache_path.exists():
        saved = json.loads(cache_path.read_text())
        # 프롬프트가 바뀌면 예전 생성 결과를 쓰지 않는다
        if saved.get("promptVersion") == PROMPT_VERSION:
            cache = saved["faqs"]
    todo = [f for f in targets if cache.get(f["slot_id"], {}).get("hash") != content_hash(f["question"], f["answer"])]
    chars = sum(len(SYSTEM_PROMPT) + len(user_prompt(f)) for f in todo)
    est = cost_usd(args.model, int(chars / CHARS_PER_TOKEN) + EST_INPUT_EXTRA * len(todo),
                   EST_OUTPUT_TOKENS * len(todo))
    print(f"FAQ {len(targets)}개 중 생성할 것 {len(todo)}개 × 예상 질문 {PER_FAQ}개 | 모델 {args.model}")
    print(f"예상 비용 ${est:.4f} ≈ {est * args.krw_per_usd:,.0f}원 (환율 {args.krw_per_usd:,.0f})")
    if todo and not args.run:
        print("예상만 출력했습니다. 실제로 돌리려면 --run 을 붙이세요.")
        return
    if todo:
        args.out_dir.mkdir(parents=True, exist_ok=True)
        generate_all(targets, cache, cache_path, args.model, args.krw_per_usd)
    if args.sample:
        for faq in targets:
            print(f"\n[{faq['slot_id']}] Q: {faq['question']}\n  A: {faq['answer'][:100]}")
            for q in cache[faq["slot_id"]]["questions"]:
                print(f"  - {q}")
        return

    empty = [f["slot_id"] for f in faqs if not cache[f["slot_id"]]["questions"]]
    total = sum(len(cache[f["slot_id"]]["questions"]) for f in faqs)
    print(f"예상 질문 {total}개 (생성 실패 FAQ {len(empty)}개{': ' + str(empty[:5]) if empty else ''})")

    eval_ids = list(multigold)
    embed_cache = ROOT / "scripts/data/.embed_cache.json"
    generated_cache = args.out_dir / "e4-embed-cache.json"
    faq_vectors = unit(embed_all([f["question"] for f in faqs], 32, embed_cache))
    eval_vectors = unit(embed_all([multigold[e]["question"] for e in eval_ids], 32, embed_cache))
    flat = [(i, q) for i, f in enumerate(faqs) for q in cache[f["slot_id"]]["questions"]]
    gen_vectors = unit(embed_all([q for _, q in flat], 32, generated_cache))
    generated = {}
    for (index, _), vector in zip(flat, gen_vectors):
        generated.setdefault(index, []).append(vector)
    generated = {i: np.array(v) for i, v in generated.items()}
    slots = [f["slot_id"] for f in faqs]

    # 로컬 재계산이 DB 점수와 같은지 확인
    # 다르면 비교 자체가 성립하지 않는다
    current = question_vector_results(eval_ids, eval_vectors, slots, faq_vectors, qo)
    drift = max(abs(r["score"] - s["score"]) for e in eval_ids
                for r in current[e] for s in qo[e]["results"] if r["slot_id"] and r["slot_id"] == s["slot_id"])
    if drift > 1e-6:
        sys.exit(f"로컬 임베딩 점수가 원시 결과와 다릅니다 (최대 차이 {drift}) - bge-m3 버전 확인")
    with_generated = question_vector_results(eval_ids, eval_vectors, slots, faq_vectors, qo, generated)

    labels = {"공식": {e: expected_slots(i) for e, i in qa.items()},
              "다중 정답": {e: expected_slots(i) for e, i in multigold.items()}}
    sources = sorted({i["source"] for i in qa.values() if i["type"] == "UNRELATED"})
    base = evaluate(qa, current, labels, QO_UPPER)
    raw_base = evaluate(qa, {e: qo[e]["results"] for e in qa}, labels, QO_UPPER)
    if any(len(base["answered"][k]) != len(raw_base["answered"][k]) for k in labels) or \
            base["unrelatedPassed"] != raw_base["unrelatedPassed"]:
        sys.exit("로컬 재계산 기준선이 원시 결과 기준선과 다릅니다")
    rows = [evaluate(qa, with_generated, labels, line) for line in QO_LINES]
    strict, relaxed = pick(base, rows, sources, 0), pick(base, rows, sources, 1)

    positives = sum(1 for i in qa.values() if i["type"] != "UNRELATED")
    print(f"\n질문 벡터 = max(FAQ 질문, 예상 질문 {PER_FAQ}개), Q_A {QA_UPPER} 고정, 판정기 없음")
    print("| 구성 | 질문 선 | 답변(공식) | 답변(다중 정답) | " + " | ".join(f"무관 {s}" for s in sources) + " |")
    print("|---" * (4 + len(sources)) + "|")

    def line(label, r):
        mark = (" ★현행규칙" if r is strict else "") + (" ☆완화안" if r is relaxed else "")
        print(f"| {label}{mark} | {r['qoLine']:.2f} | {len(r['answered']['공식'])}/{positives} | "
              f"{len(r['answered']['다중 정답'])}/{positives} | "
              + " | ".join(str(len(r["unrelatedPassed"].get(s, ()))) for s in sources) + " |")

    line("현행", base)
    for r in rows:
        line("예상 질문", r)
    for name, best in (("현행 규칙", strict), ("완화안(+1)", relaxed)):
        if best is None:
            print(f"\n{name}: 고를 수 있는 선 없음")
            continue
        print(f"\n{name}: 질문 선 {best['qoLine']:.2f} → 공식 {len(base['answered']['공식'])}→"
              f"{len(best['answered']['공식'])}, 다중 {len(base['answered']['다중 정답'])}→"
              f"{len(best['answered']['다중 정답'])}")
        print(f"  살아난 문항: {sorted(best['answered']['공식'] - base['answered']['공식'])}")
        print(f"  놓친 문항: {sorted(base['answered']['공식'] - best['answered']['공식']) or '없음'}")

    def dump(r):
        return {"qoLine": r["qoLine"], "answered": {k: sorted(v) for k, v in r["answered"].items()},
                "unrelatedPassed": {k: sorted(v) for k, v in r["unrelatedPassed"].items()}}

    out = args.out_dir / f"e4-generated-questions-result-{slug}-{datetime.now():%Y%m%d-%H%M%S}.json"
    out.write_text(json.dumps({
        "measuredAt": datetime.now().isoformat(), "model": args.model, "promptVersion": PROMPT_VERSION,
        "perFaq": PER_FAQ, "generatedQuestions": total, "failedFaqs": empty,
        "baseline": dump(base), "rows": [dump(r) for r in rows],
        "strict": dump(strict) if strict else None, "relaxed": dump(relaxed) if relaxed else None,
        # 판정기(E3)와 합칠 때 쓰려고 예상 질문 반영 질문 벡터 결과를 남긴다
        "questionVectorResults": with_generated,
    }, ensure_ascii=False, indent=1))
    print(f"저장: {out}")


if __name__ == "__main__":
    main()
