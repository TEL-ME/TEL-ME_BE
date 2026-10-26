#!/usr/bin/env python3
# 근거 판정기 정확도를 Bedrock 모델로 측정
# EvidenceCheckProbe(B2)가 저장한 25건 근거를 그대로 쓴다.
# 검색을 다시 돌리지 않아 EXAONE 결과와 근거가 같다.
import argparse
import json
import os
import re
import statistics
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

DEFAULT_INPUT = ".measure/evidence-check-b2-20261007-131141.json"
REPEAT = 3
# 판정 JSON은 짧지만 gpt-oss는 생각 토큰도 출력에 포함된다
MAX_TOKENS = 1000

# USD / 100만 토큰 (입력, 출력). us-east-1 온디맨드 기준
# 바뀌면 https://aws.amazon.com/bedrock/pricing/ 보고 고친다
PRICES = {
    "openai.gpt-oss-120b-1:0": (0.15, 0.60),
    "openai.gpt-oss-20b-1:0": (0.07, 0.30),
    "qwen.qwen3-32b-v1:0": (0.15, 0.60),
}

# 비용 추정용 가정. 실제 값은 실행 후 usage로 다시 계산한다
CHARS_PER_TOKEN = 1.0  # 한국어는 대략 글자당 1토큰 이하. 넉넉히 잡는다
EST_OUTPUT_TOKENS = 400

# EvidenceCheckProbe.B2_SYSTEM_PROMPT와 같은 문자열
B2_PROMPT_VERSION = "evidence-check-b2"
B2_SYSTEM_PROMPT = """당신은 통신사 FAQ 검색 결과를 검수합니다.
고객 질문에 답할 정보가 FAQ에 있는지 아래 순서대로 판단하십시오.

1. asked: 고객이 알고 싶어 하는 것을 항목별로 짧게 적습니다.
   질문에 나온 행위(신청, 해지, 변경 등)와 알고 싶은 정보의 종류(비용, 소요 시간, 마감 기한,
   방법, 장소, 가능 여부, 혜택, 기준 등)를 바꾸지 말고 그대로 적습니다.
2. found: asked의 각 항목이 FAQ에 있으면 true, 없으면 false를 같은 순서로 적습니다.
   - 행위가 다르면 없는 것입니다.
   - 정보의 종류가 다르면 없는 것입니다. 주제가 같아도 마찬가지입니다.
   - 묻는 값의 단위나 표현이 FAQ와 달라도, FAQ에 적힌 값을 환산하거나 계산해 답을 만들 수 있으면
     있는 것입니다. 이것은 정보의 종류가 다른 경우가 아닙니다.
3. answerable: found에 true가 하나라도 있으면 true, 모두 false면 false.

JSON만 출력하십시오.
{"asked": ["..."], "found": [true 또는 false], "answerable": true 또는 false}
"""

# B3 = B2 + 대상 일치 규칙
# 회색 지대에서 주제만 비슷한 FAQ를 통과시켜서 추가했다
# 평가 케이스가 새지 않게 예시 없이 규칙만 적는다
B3_PROMPT_VERSION = "evidence-check-b3"
B3_SYSTEM_PROMPT = B2_SYSTEM_PROMPT.replace(
    "   방법, 장소, 가능 여부, 혜택, 기준 등)를 바꾸지 말고 그대로 적습니다.\n",
    "   방법, 장소, 가능 여부, 혜택, 기준 등)를 바꾸지 말고 그대로 적습니다.\n"
    "   질문 문장을 통째로 옮기지 말고, 항목마다 무엇에 대한 것인지(상품, 서비스, 상태, 상황)를 함께 적습니다.\n",
).replace(
    "   - 행위가 다르면 없는 것입니다.\n",
    "   - 행위가 다르면 없는 것입니다.\n"
    "   - 대상(상품, 서비스, 상태, 상황)이 다르면 없는 것입니다. 이름이나 주제가 비슷해도 마찬가지입니다.\n",
)

PROMPTS = {B2_PROMPT_VERSION: B2_SYSTEM_PROMPT, B3_PROMPT_VERSION: B3_SYSTEM_PROMPT}


def build_context(evidence):
    """AnswerContextConverter.toContext와 같은 형식."""
    return "\n\n".join(
        f"[{i}] Q: {e['question']}\nA: {e['answer']}" for i, e in enumerate(evidence, 1)
    )


def user_prompt(context, question):
    """EvidenceRelevancePromptTemplates.userPrompt와 같은 형식."""
    return f"[FAQ]\n{context}\n\n[고객 질문]\n{question}"


def parse_answerable(text):
    """EvidenceRelevanceChecker.parse와 같은 fail-open 규칙. 비었거나 깨졌거나 필드가 없으면 True.

    두 번째 값은 파싱 성공 여부. fail-open으로 True가 된 건지 따로 세려고 돌려준다.
    """
    if not text or not text.strip():
        return True, False
    match = re.search(r"\{.*\}", text, re.S)
    if not match:
        return True, False
    try:
        data = json.loads(match.group())
    except json.JSONDecodeError:
        return True, False
    if "answerable" not in data:
        return True, False
    value = data["answerable"]
    if isinstance(value, bool):
        return value, True
    if isinstance(value, str) and value.strip().lower() in ("true", "false"):
        return value.strip().lower() == "true", True
    return True, False


def judged_cases(cases):
    """근거가 없는 케이스는 판정기를 안 부르므로 제외한다 (TELME-122 집계와 같음)."""
    return [c for c in cases if c["evidence"]]


def summarize(cases, run_key="runs"):
    """케이스별 3회 다수결로 잘못 막음 / 막음 / 맞힘을 센다."""
    wrong_block = blocked = correct = flips = fail_open = 0
    elapsed = []
    for case in judged_cases(cases):
        runs = case[run_key]
        votes = [r["answerable"] for r in runs]
        predicted = sum(votes) * 2 > len(votes)
        expected = case["expectedAnswerable"]
        if expected and not predicted:
            wrong_block += 1
        if not expected and not predicted:
            blocked += 1
        if expected == predicted:
            correct += 1
        if len(set(votes)) > 1:
            flips += 1
        fail_open += sum(1 for r in runs if r.get("parsed") is False)
        elapsed += [r["elapsedMs"] for r in runs if r.get("elapsedMs") is not None]
    return {
        "wrongBlock": wrong_block,
        "blocked": blocked,
        "correct": correct,
        "flippedCases": flips,
        "failOpenRuns": fail_open,
        "passed": wrong_block <= 1 and blocked >= 4,
        "elapsedMsP50": int(statistics.median(elapsed)) if elapsed else None,
        "elapsedMsP95": int(sorted(elapsed)[max(0, int(len(elapsed) * 0.95) - 1)]) if elapsed else None,
    }


def cost_usd(model, tokens_in, tokens_out):
    price_in, price_out = PRICES[model]
    return (tokens_in * price_in + tokens_out * price_out) / 1_000_000


def estimate(model, cases, repeat, system=B2_SYSTEM_PROMPT):
    calls = len(judged_cases(cases)) * repeat
    chars = sum(
        len(system) + len(user_prompt(build_context(c["evidence"]), c["question"]))
        for c in judged_cases(cases)
    ) * repeat
    tokens_in = int(chars / CHARS_PER_TOKEN)
    tokens_out = calls * EST_OUTPUT_TOKENS
    return calls, tokens_in, tokens_out, cost_usd(model, tokens_in, tokens_out)


def call_model(api_key, region, model, system, prompt, retries=3):
    url = (
        f"https://bedrock-runtime.{region}.amazonaws.com/model/"
        f"{urllib.parse.quote(model, safe='')}/converse"
    )
    body = {
        "system": [{"text": system}],
        "messages": [{"role": "user", "content": [{"text": prompt}]}],
        # 프로브와 같은 temperature 0
        "inferenceConfig": {"maxTokens": MAX_TOKENS, "temperature": 0},
    }
    if model.startswith("openai.gpt-oss"):
        # 기본값은 생각을 길게 해 비용, 지연이 커짐
        # 판정은 low로도 합격
        body["additionalModelRequestFields"] = {"reasoning_effort": "low"}
    data = json.dumps(body).encode()

    last = None
    for attempt in range(1, retries + 1):
        request = urllib.request.Request(
            url,
            data=data,
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                payload = json.load(response)
            break
        except urllib.error.HTTPError as error:
            last = f"HTTP {error.code} {error.read()[:200].decode(errors='replace')}"
            # 권한, 요청 오류는 다시 보내도 같다
            if error.code in (400, 401, 403, 404):
                return None, last, {}
        except Exception as error:  # 네트워크 오류
            last = str(error)
        time.sleep(2 * attempt)
    else:
        return None, last, {}

    text = "".join(b["text"] for b in payload["output"]["message"]["content"] if "text" in b)
    return text.strip(), None, payload.get("usage", {})


def print_summary(label, summary):
    mark = "합격" if summary["passed"] else "불합격"
    print(
        f"  {label:28} 잘못 막음 {summary['wrongBlock']} / 막음 {summary['blocked']} / "
        f"맞힘 {summary['correct']} | 흔들림 {summary['flippedCases']} | "
        f"fail-open {summary['failOpenRuns']} | p50 {summary['elapsedMsP50']}ms | {mark}"
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", default=DEFAULT_INPUT, help="EvidenceCheckProbe(B2) 결과 JSON")
    parser.add_argument("--model", required=True, choices=sorted(PRICES))
    parser.add_argument("--prompt", default=B2_PROMPT_VERSION, choices=sorted(PROMPTS))
    parser.add_argument("--repeat", type=int, default=REPEAT)
    parser.add_argument("--limit", type=int, help="앞에서 N건만 (시험용)")
    parser.add_argument("--krw-per-usd", type=float, default=1450.0, help="원화 환산 환율")
    parser.add_argument("--run", action="store_true", help="없으면 예상 비용만 출력하고 끝낸다")
    parser.add_argument("--out-dir", default=".measure")
    args = parser.parse_args()

    source = json.loads(Path(args.input).read_text())
    if source.get("promptVersion") != B2_PROMPT_VERSION:
        sys.exit(f"입력이 B2 결과가 아닙니다: {source.get('promptVersion')}")
    system = PROMPTS[args.prompt]
    cases = source["cases"][: args.limit] if args.limit else source["cases"]

    calls, est_in, est_out, est_usd = estimate(args.model, cases, args.repeat, system)
    print(f"모델 {args.model} | 프롬프트 {args.prompt} | 판정 {len(judged_cases(cases))}건 × {args.repeat}회 = {calls}호출")
    print(
        f"예상 토큰 입력 {est_in:,} / 출력 {est_out:,} (출력은 호출당 {EST_OUTPUT_TOKENS} 가정)"
    )
    print(f"예상 비용 ${est_usd:.4f} ≈ {est_usd * args.krw_per_usd:,.0f}원 (환율 {args.krw_per_usd:,.0f})")
    if not args.run:
        print("예상만 출력했습니다. 실제로 돌리려면 --run 을 붙이세요.")
        return

    api_key = os.environ.get("BEDROCK_API_KEY")
    if not api_key:
        sys.exit("BEDROCK_API_KEY가 없습니다. .env를 읽고 실행하세요.")
    region = os.environ.get("AWS_REGION", "us-east-1")

    tokens_in = tokens_out = 0
    records = []
    for case in cases:
        runs = []
        if case["evidence"]:
            prompt = user_prompt(build_context(case["evidence"]), case["question"])
            for i in range(1, args.repeat + 1):
                start = time.monotonic()
                text, error, usage = call_model(api_key, region, args.model, system, prompt)
                elapsed_ms = int((time.monotonic() - start) * 1000)
                if error and error.startswith("HTTP 4"):
                    sys.exit(f"요청 오류로 중단: {error}")
                answerable, parsed = parse_answerable(text)
                tokens_in += usage.get("inputTokens", 0)
                tokens_out += usage.get("outputTokens", 0)
                runs.append({
                    "run": i,
                    "answerable": answerable,
                    "parsed": parsed,
                    "rawResponse": text,
                    "error": error,
                    "elapsedMs": elapsed_ms,
                    "usage": usage,
                })
        records.append({
            "caseId": case["caseId"],
            "group": case["group"],
            "expectedAnswerable": case["expectedAnswerable"],
            "question": case["question"],
            "evidence": case["evidence"],
            "runs": runs,
        })
        votes = [r["answerable"] for r in runs]
        print(f"  {case['caseId']:12} 기대 {str(case['expectedAnswerable']):5} 판정 {votes}")

    summary = summarize(records)
    spent = cost_usd(args.model, tokens_in, tokens_out)
    out = {
        "measuredAt": datetime.now().isoformat(),
        "model": args.model,
        "region": region,
        "promptVersion": args.prompt,
        "source": args.input,
        "labelVersion": source.get("labelVersion"),
        "repeat": args.repeat,
        "maxTokens": MAX_TOKENS,
        "summary": summary,
        "usage": {"inputTokens": tokens_in, "outputTokens": tokens_out, "costUsd": round(spent, 6)},
        "cases": records,
    }
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    slug = args.model.split(".", 1)[1].split(":")[0]
    path = out_dir / f"evidence-check-bedrock-{slug}-{args.prompt.rsplit('-', 1)[1]}-{datetime.now():%Y%m%d-%H%M%S}.json"
    path.write_text(json.dumps(out, ensure_ascii=False, indent=2))

    print()
    print("=== 판정 결과 (기준: 잘못 막음 ≤ 1, 막음 ≥ 4) ===")
    print_summary("EXAONE 3.5 7.8B (기준선)", summarize(cases))
    print_summary(f"{args.model} {args.prompt.rsplit('-', 1)[1]}", summary)
    print(
        f"토큰 입력 {tokens_in:,} / 출력 {tokens_out:,} | 실제 비용 ${spent:.4f} "
        f"≈ {spent * args.krw_per_usd:,.0f}원"
    )
    per_call = spent / max(1, sum(len(r["runs"]) for r in records))
    print(f"호출당 ${per_call:.6f} ≈ {per_call * args.krw_per_usd:.2f}원")
    print(f"저장: {path}")


if __name__ == "__main__":
    main()
