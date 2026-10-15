#!/usr/bin/env python3
"""판정 시험 반복: 판정 시험 25건을 FAQ 하나씩 1회 판정으로 여러 번 돌려 회차마다 합격하는지 본다.

운영은 판정을 한 번만 하므로, 경계에 있는 질문은 회차마다 판정이 갈려 합격 여부까지 바뀔 수 있다.
기본은 판정기 정확도 시험이다. 근거를 지금 임계값(질문 0.88, Q_A 0.72)을 통과한 상위 3개로 고정해 모두 판정하고,
하나라도 답 됨이면 답할 수 있음으로 본다. --served는 운영과 같이 회색 구간 설정으로 판정할 후보를 고르고
판정 없이 통과하는 후보와 판정 실패 처리까지 따른다(JudgingFaqGrayZoneSelector).
합격 기준은 evidence_check_bedrock.py와 같다: 잘못 막음 1건 이하, 막음 4건 이상.

    set -a; . ./.env; set +a
    python3 scripts/judge_stability_bedrock.py                    # 예상 비용만
    python3 scripts/judge_stability_bedrock.py --runs 5 --run     # 실행
    python3 scripts/judge_stability_bedrock.py --prompt <후보> --runs 5 --run
    python3 scripts/judge_stability_bedrock.py --served --prompt v4a --runs 1 --run   # 운영과 같은 방식

로컬 DB(telme-postgres)와 Ollama bge-m3가 떠 있어야 한다. 결과는 .measure/judge-stability-<프롬프트>-<시각>.json
"""
from __future__ import annotations

import argparse
import json
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from pathlib import Path

import gray_zone_upper_band_bedrock as g

ROOT = Path(__file__).resolve().parent.parent
CASES = ROOT / "scripts/data/evidence_check_cases_25.json"
MEASURE = ROOT / ".measure"

# 판정 프롬프트 후보. v3는 이전 운영(B3), v4a는 지금 운영(FaqEvidencePromptTemplates v4)과 같은 문자열이다
# 후보는 규칙을 하나씩 더해 어느 규칙이 효과를 냈는지 알 수 있게 한다. 평가 케이스가 새지 않게 예시 없이 규칙만 적는다
CALCULATION_RULE = ("   - 묻는 값의 단위나 표현이 FAQ와 달라도, FAQ에 적힌 값을 환산하거나 계산해 답을 만들 수 있으면\n"
                    "     있는 것입니다. 이것은 정보의 종류가 다른 경우가 아닙니다.\n")
COMPARISON_RULE = ("   - 비교하는 대상들의 값이 FAQ에 모두 있으면, 같은지 다른지, 몇 배인지, 얼마나 차이 나는지도\n"
                   "     그 값으로 답할 수 있으므로 있는 것입니다.\n")
V4A = g.B3_SYSTEM_PROMPT.replace(CALCULATION_RULE, CALCULATION_RULE + COMPARISON_RULE)
# 판정기가 질문의 세부 사항을 더 넓은 항목에 합쳐 적고(예: 따로 묻는 부대 비용을 본 비용에) 있다고 보는 경우를 막는다
ASKED_LINE = "   질문 문장을 통째로 옮기지 말고, 항목마다 무엇에 대한 것인지(상품, 서비스, 상태, 상황)를 함께 적습니다.\n"
ASKED_SCOPE_RULE = ("   고객이 물은 범위를 넓히거나 다른 항목에 합치지 않습니다. 질문이 따로 묻는 세부 사항은 그 자체를\n"
                    "   한 항목으로 적습니다.\n")
V4B = V4A.replace(ASKED_LINE, ASKED_LINE + ASKED_SCOPE_RULE)
# 적히지 않은 것을 다른 내용으로 짐작해 있다고 보는 경우를 막는다. 계산·비교(v4a)는 짐작이 아니라고 함께 적는다
NO_GUESS_RULE = ("   - 그 항목이 FAQ에 적혀 있지 않으면, 다른 내용으로 미루어 짐작할 수 있어도 없는 것입니다.\n"
                 "     적힌 값을 환산하거나 계산하거나 비교하는 것은 짐작이 아닙니다.\n")
V4C = V4B.replace(COMPARISON_RULE, COMPARISON_RULE + NO_GUESS_RULE)

PROMPTS = {
    "v3": g.B3_SYSTEM_PROMPT,
    # v3 + 비교·비율 규칙. 채택해 운영 프롬프트 v4(g.B4_SYSTEM_PROMPT)가 됐다
    "v4a": V4A,
    # v4a + 물은 범위를 넓히거나 합치지 않기
    "v4b": V4B,
    # v4b + 적히지 않은 것을 짐작하지 않기
    "v4c": V4C,
}

ANSWERABLE, NOT_ANSWERABLE, FAILED = "ANSWERABLE", "NOT_ANSWERABLE", "FAILED"

# --served: 운영 회색 구간 설정(application.yml search.gray-zone)과 판정 붙이기 전 임계값.
# (아래 선, 판정 없이 통과하는 선, 판정 실패 시 통과시키는 선)
SERVED_BANDS = {"qo": (0.80, 0.90, g.QO_UPPER), "qa": (0.69, 0.80, g.QA_UPPER)}

# 회차마다 판정이 갈린 경계 쌍(질문, 근거 FAQ, 이 FAQ로 답할 수 있어야 하는가). v3 5회 반복(10-15)에서 골랐다.
# 짧은 시간에 연달아 보내면 같은 답이 반복되므로(10-15 확인) 쌍을 돌아가며 한 번씩 보내 답 됨 비율을 본다
BORDERLINE = [
    ("HALLU-004", "BILLING-0067", True),   # 30만 → 100만, 몇 배인지 계산
    ("HALLU-011", "USIM-0001", True),      # 매장·온라인 모두 7,700원, 같은지 비교
    ("HALLU-010", "BILLING-0140", True),
    ("HALLU-018", "BILLING-0039", False),  # 한도 상향 신청 장소는 FAQ에 없음
    ("HALLU-019", "ROAMING-0017", False),  # 출국 며칠 전 마감은 FAQ에 없음
    ("HALLU-019", "ROAMING-0007", False),
    ("HALLU-020", "USIM-0009", False),     # 택배비 별도 여부는 FAQ에 없음
    ("HALLU-020", "USIM-0029", False),
    ("BX-002", "BILLING-0104", False),     # 환불 접수 마감은 FAQ에 없음
]


def parse_verdict(text: str | None) -> str:
    """운영 FaqEvidenceJudge.parse와 같다. 실험 스크립트(fail-open)와 달리 해석 실패는 답 됨이 아니라 실패다."""
    if not text:
        return FAILED
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end < start:
        return FAILED
    try:
        value = json.loads(text[start:end + 1]).get("answerable")
    except (json.JSONDecodeError, AttributeError):
        return FAILED
    if isinstance(value, str):
        value = {"true": True, "false": False}.get(value.strip().lower())
    if isinstance(value, bool):
        return ANSWERABLE if value else NOT_ANSWERABLE
    return FAILED


def predicted(verdicts: list[str]) -> bool:
    """근거 FAQ 판정 중 하나라도 답 됨이면 답할 수 있음."""
    return ANSWERABLE in verdicts


def score_run(cases: list[dict], verdicts_by_case: dict[str, list[str]]) -> dict:
    """한 회차 결과. 근거가 없는 케이스는 판정하지 않으므로 뺀다."""
    wrong_block = blocked = correct = failed = 0
    for case in cases:
        verdicts = verdicts_by_case.get(case["case_id"])
        if not verdicts:
            continue
        answer = predicted(verdicts)
        expected = case["expected_answerable"]
        wrong_block += expected and not answer
        blocked += (not expected) and (not answer)
        correct += expected == answer
        failed += FAILED in verdicts
    return {"wrongBlock": wrong_block, "blocked": blocked, "correct": correct, "failedCases": failed,
            "passed": wrong_block <= 1 and blocked >= 4}


def unstable_cases(runs: list[dict[str, list[str]]]) -> list[str]:
    """회차마다 답할 수 있음 여부가 달라진 케이스."""
    ids = sorted({case_id for run in runs for case_id in run})
    return [case_id for case_id in ids if len({predicted(run.get(case_id, [])) for run in runs}) > 1]


def served_band(score: float, vector: str) -> str:
    """운영과 같은 구간 이름: pass(판정 없이 통과), judge(판정), drop(버림)."""
    floor, passed, _ = SERVED_BANDS[vector]
    return "pass" if score >= passed else "drop" if score < floor else "judge"


def served_evidence(qo: list, qa: list, verdict_of) -> list[str]:
    """운영 JudgingFaqGrayZoneSelector + DualVectorMerger와 같은 근거.

    판정 구간은 판정 결과를 따르고, 판정 실패는 판정 붙이기 전 임계값 이상만 남긴다. 질문 벡터 먼저, 중복 빼고 3개.
    """
    picked = []
    for vector, results in (("qo", qo), ("qa", qa)):
        for slot, score in results:
            band = served_band(score, vector)
            if band == "drop" or slot in picked:
                continue
            if band == "judge":
                verdict = verdict_of(slot)
                if verdict == NOT_ANSWERABLE or (verdict == FAILED and score < SERVED_BANDS[vector][2]):
                    continue
            picked.append(slot)
    return picked[:g.TOP_K]


def slots_to_judge(qo: list, qa: list) -> list[str]:
    """운영에서 판정을 받는 slot(두 벡터에 다 나오면 한 번)."""
    slots = [slot for slot, score in qo if served_band(score, "qo") == "judge"]
    slots += [slot for slot, score in qa if served_band(score, "qa") == "judge"]
    return [slot for slot in dict.fromkeys(slots) if slot is not None]


def pair_rates(verdicts_by_pair: dict[tuple[str, str], list[str]]) -> dict[tuple[str, str], float]:
    """쌍별 답 됨 비율. 판정 실패는 분모에서 뺀다."""
    rates = {}
    for pair, verdicts in verdicts_by_pair.items():
        judged = [v for v in verdicts if v != FAILED]
        rates[pair] = sum(v == ANSWERABLE for v in judged) / len(judged) if judged else float("nan")
    return rates


def candidates(cases: list[dict], container: str, ollama_url: str) -> dict[str, list[str]]:
    """케이스별 근거 slot. 판정 구간 없이 지금 임계값을 통과한 상위 3개다."""
    rows = g.search_rows([{"eval_id": c["case_id"], "question": c["question"]} for c in cases], container, ollama_url)
    out = {}
    for row in rows:
        slots = g.served(row["qo"], row["qa"], g.QO_UPPER, g.QA_UPPER, g.QA_UPPER, lambda slot: True)
        out[row["id"]] = [slot for slot in slots if slot is not None]
    return out


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--prompt", choices=sorted(PROMPTS), default="v3")
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--served", action="store_true",
                        help="운영과 같이 회색 구간 설정으로 판정할 후보를 고른다. 없으면 지금 임계값을 통과한 상위 3개를 모두 판정한다"
                             "(판정기 정확도)")
    parser.add_argument("--pair-repeat", type=int, default=0,
                        help="0보다 크면 시험 전체 대신 경계 쌍(BORDERLINE)만 쌍마다 이 횟수만큼 돌아가며 판정한다")
    parser.add_argument("--run", action="store_true", help="없으면 예상 비용만 출력하고 끝낸다")
    parser.add_argument("--container", default="telme-postgres")
    parser.add_argument("--ollama-url", default="http://localhost:11434")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--krw-per-usd", type=float, default=1450.0)
    args = parser.parse_args()

    cases = json.loads(CASES.read_text(encoding="utf-8"))["cases"]
    if args.pair_repeat:
        run_pairs(args, {case["case_id"]: case for case in cases})
        return
    if args.served:
        run_served(args, cases)
        return
    slots_by_case = candidates(cases, args.container, args.ollama_url)
    jobs = [(case, slot) for case in cases for slot in slots_by_case.get(case["case_id"], [])]
    estimate = len(jobs) * args.runs * (g.EST_INPUT_TOKENS * g.PRICE_IN + g.EST_OUTPUT_TOKENS * g.PRICE_OUT) / 1e6
    print(f"프롬프트 {args.prompt} | 회차당 판정 {len(jobs)}회 × {args.runs}회차 | "
          f"예상 약 {estimate * args.krw_per_usd:,.0f}원 (어림값)")
    if not args.run:
        return

    api_key, region = g.bedrock_credentials()
    faqs = {slot: (q, a) for slot, q, a in g.psql(
        args.container, "select slot_id, question, answer from faqs where status = 'ACTIVE' and slot_id is not null")}
    system = PROMPTS[args.prompt]
    tokens = [0, 0]

    def judge(job):
        case, slot = job
        text, error, usage = g.call_model(api_key, region, g.user_prompt(*faqs[slot], case["question"]), system=system)
        tokens[0] += usage.get("inputTokens", 0)
        tokens[1] += usage.get("outputTokens", 0)
        verdict = FAILED if error else parse_verdict(text)
        return case["case_id"], slot, verdict

    runs, scores = [], []
    for number in range(1, args.runs + 1):
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            results = list(pool.map(judge, jobs))
        by_case: dict[str, list[str]] = {}
        for case_id, _, verdict in results:
            by_case.setdefault(case_id, []).append(verdict)
        score = score_run(cases, by_case)
        runs.append(by_case)
        scores.append(score)
        print(f"{number}회차 잘못 막음 {score['wrongBlock']} / 막음 {score['blocked']} / 맞힘 {score['correct']} | "
              f"판정 실패 포함 {score['failedCases']} | {'합격' if score['passed'] else '불합격'}", flush=True)

    shaky = unstable_cases(runs)
    spent = (tokens[0] * g.PRICE_IN + tokens[1] * g.PRICE_OUT) / 1e6 * args.krw_per_usd
    expected = {c["case_id"]: c["expected_answerable"] for c in cases}
    print(f"\n합격 {sum(s['passed'] for s in scores)} / {args.runs}회차 | 비용 약 {spent:,.1f}원")
    print(f"회차마다 결과가 바뀐 케이스 {len(shaky)}개")
    for case_id in shaky:
        print(f"  {case_id:10} 기대 {'답 됨' if expected[case_id] else '막음':4} | "
              + " / ".join(",".join("됨" if v == ANSWERABLE else "X" if v == FAILED else "안됨" for v in run[case_id])
                           for run in runs))

    MEASURE.mkdir(exist_ok=True)
    out = MEASURE / f"judge-stability-{args.prompt}-{datetime.now():%Y%m%d-%H%M%S}.json"
    out.write_text(json.dumps({"prompt": args.prompt, "runs": runs, "scores": scores, "slots": slots_by_case,
                               "costKrw": round(spent, 1)}, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"\n{out.relative_to(ROOT)}에 저장했습니다")


def run_served(args, cases: list[dict]) -> None:
    """운영과 같은 방식: 판정 없이 통과하는 후보, 원래 임계값 아래에서 새로 판정받는 후보, 판정 실패 처리까지 같다.

    근거가 하나라도 남으면 답할 수 있음으로 본다. 재검색(#131)은 라우터가 필요해 다루지 않는다.
    """
    rows = {row["id"]: row for row in g.search_rows(
        [{"eval_id": c["case_id"], "question": c["question"]} for c in cases], args.container, args.ollama_url)}
    jobs = [(case, slot) for case in cases for slot in slots_to_judge(rows[case["case_id"]]["qo"], rows[case["case_id"]]["qa"])]
    estimate = len(jobs) * args.runs * (g.EST_INPUT_TOKENS * g.PRICE_IN + g.EST_OUTPUT_TOKENS * g.PRICE_OUT) / 1e6
    print(f"운영 방식 | 프롬프트 {args.prompt} | 회차당 판정 {len(jobs)}회 × {args.runs}회차 | "
          f"예상 약 {estimate * args.krw_per_usd:,.0f}원 (어림값)")
    if not args.run:
        return
    api_key, region = g.bedrock_credentials()
    slots = ",".join(sorted({f"'{slot}'" for _, slot in jobs}))
    faqs = {slot: (q, a) for slot, q, a in g.psql(
        args.container, f"select slot_id, question, answer from faqs where slot_id in ({slots})")}
    system = PROMPTS[args.prompt]
    tokens = [0, 0]

    def judge(job):
        case, slot = job
        text, error, usage = g.call_model(api_key, region, g.user_prompt(*faqs[slot], case["question"]), system=system)
        tokens[0] += usage.get("inputTokens", 0)
        tokens[1] += usage.get("outputTokens", 0)
        return case["case_id"], slot, FAILED if error else parse_verdict(text)

    expected = {c["case_id"]: c["expected_answerable"] for c in cases}
    scores, details = [], []
    for number in range(1, args.runs + 1):
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            verdicts = {(c, s): v for c, s, v in pool.map(judge, jobs)}
        evidence = {case_id: served_evidence(row["qo"], row["qa"], lambda slot, c=case_id: verdicts[(c, slot)])
                    for case_id, row in rows.items()}
        # 근거가 남으면 답할 수 있음. score_run은 판정 목록을 받으므로 근거 유무를 그 형식으로 넘긴다
        score = score_run(cases, {c: [ANSWERABLE if e else NOT_ANSWERABLE] for c, e in evidence.items()})
        wrong = [c for c, e in evidence.items() if expected[c] and not e]
        leaked = [c for c, e in evidence.items() if not expected[c] and e]
        scores.append(score)
        details.append({"verdicts": {f"{c} {s}": v for (c, s), v in verdicts.items()}, "evidence": evidence})
        print(f"{number}회차 잘못 막음 {score['wrongBlock']} {wrong} / 막음 {score['blocked']} / 못 막음 {leaked} | "
              f"{'합격' if score['passed'] else '불합격'}", flush=True)
    passed_without_judgment = {c: [s for s, v in row["qo"] if served_band(v, "qo") == "pass"]
                               + [s for s, v in row["qa"] if served_band(v, "qa") == "pass"]
                               for c, row in rows.items() if not expected[c]}
    print("막아야 하는데 판정 없이 통과하는 근거: "
          + ", ".join(f"{c} {s}" for c, s in passed_without_judgment.items() if s))
    spent = (tokens[0] * g.PRICE_IN + tokens[1] * g.PRICE_OUT) / 1e6 * args.krw_per_usd
    print(f"합격 {sum(s['passed'] for s in scores)} / {args.runs}회차 | 비용 약 {spent:,.1f}원")
    MEASURE.mkdir(exist_ok=True)
    out = MEASURE / f"judge-served-{args.prompt}-{datetime.now():%Y%m%d-%H%M%S}.json"
    out.write_text(json.dumps({"prompt": args.prompt, "scores": scores, "runs": details, "costKrw": round(spent, 1)},
                              ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{out.relative_to(ROOT)}에 저장했습니다")


def run_pairs(args, cases: dict[str, dict]) -> None:
    calls = len(BORDERLINE) * args.pair_repeat
    estimate = calls * (g.EST_INPUT_TOKENS * g.PRICE_IN + g.EST_OUTPUT_TOKENS * g.PRICE_OUT) / 1e6
    print(f"프롬프트 {args.prompt} | 경계 쌍 {len(BORDERLINE)}개 × {args.pair_repeat}회 = 판정 {calls}회 | "
          f"예상 약 {estimate * args.krw_per_usd:,.0f}원 (어림값)")
    if not args.run:
        return
    api_key, region = g.bedrock_credentials()
    slots = ",".join(f"'{slot}'" for _, slot, _ in BORDERLINE)
    faqs = {slot: (q, a) for slot, q, a in g.psql(
        args.container, f"select slot_id, question, answer from faqs where slot_id in ({slots})")}
    system = PROMPTS[args.prompt]
    tokens = [0, 0]
    verdicts: dict[tuple[str, str], list[str]] = {(case_id, slot): [] for case_id, slot, _ in BORDERLINE}
    # 같은 쌍을 연달아 보내지 않게 쌍을 한 바퀴씩 돈다
    for _ in range(args.pair_repeat):
        for case_id, slot, _ in BORDERLINE:
            text, error, usage = g.call_model(api_key, region, g.user_prompt(*faqs[slot], cases[case_id]["question"]),
                                              system=system)
            tokens[0] += usage.get("inputTokens", 0)
            tokens[1] += usage.get("outputTokens", 0)
            verdicts[(case_id, slot)].append(
                FAILED if error else parse_verdict(text))
    rates = pair_rates(verdicts)
    spent = (tokens[0] * g.PRICE_IN + tokens[1] * g.PRICE_OUT) / 1e6 * args.krw_per_usd
    print(f"{'쌍':26} {'기대':6} 답 됨 비율")
    for case_id, slot, should in BORDERLINE:
        rate = rates[(case_id, slot)]
        print(f"  {case_id + ' + ' + slot:24} {'됨' if should else '안 됨':6} {rate:4.0%}  "
              + "".join("O" if v == ANSWERABLE else "X" if v == FAILED else "." for v in verdicts[(case_id, slot)]))
    print(f"비용 약 {spent:,.1f}원 (O 답 됨, . 안 됨, X 판정 실패)")
    MEASURE.mkdir(exist_ok=True)
    out = MEASURE / f"judge-pairs-{args.prompt}-{datetime.now():%Y%m%d-%H%M%S}.json"
    out.write_text(json.dumps({"prompt": args.prompt, "region": region,
                               "pairs": [{"case": c, "slot": s, "expected": e, "verdicts": verdicts[(c, s)]}
                                         for c, s, e in BORDERLINE], "costKrw": round(spent, 1)},
                              ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"{out.relative_to(ROOT)}에 저장했습니다")


if __name__ == "__main__":
    main()
