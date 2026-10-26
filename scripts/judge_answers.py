#!/usr/bin/env python3
"""TELME-54 답변 품질 채점.

기준선 probe가 남긴 JSON을 읽어 답변을 문장 단위로 쪼개고,
Bedrock 모델에게 "이 문장이 근거에 있는가"를 물어 채점한다.

사용법
  python3 scripts/judge_answers.py .measure/baseline-20260925-0000.json
  python3 scripts/judge_answers.py <입력> --model openai.gpt-oss-20b-1:0 --out .measure/judge.json

환경변수 (.env)
  BEDROCK_API_KEY   Bedrock API 키
  AWS_REGION        기본 us-east-1
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DEFAULT_MODEL = "openai.gpt-oss-120b-1:0"
LABELS = ("supported", "unsupported", "irrelevant")

# 답변이 근거를 못 받은 경우는 채점 대상이 아니다
JUDGED_STATUS = {"GROUNDED"}

PROMPT = """당신은 통신사 FAQ 챗봇의 답변을 검수합니다.

아래 [근거]만을 사실로 인정하세요. 상식이나 일반적인 통신 지식으로 보충하지 마세요.

[근거]
{context}

[고객 질문]
{question}

[검수할 문장]
{sentence}

이 문장을 셋 중 하나로 판정하세요.

supported   문장의 내용이 근거 안에 있다. 표현이 달라도 뜻이 같으면 supported이다
unsupported 근거에 없거나 근거와 다른 내용이다. 다음도 모두 unsupported이다
            - 근거의 숫자를 더하거나 곱해서 만든 값
            - 근거에 "~하면", "~시" 같은 조건이 붙어 있는데 조건을 빼고 단정한 경우
              (근거 "본인 인증을 하면 100만 원" / 문장 "100만 원까지 가능합니다" -> unsupported)
            - 근거에 없는 절차, 기간, 장소, 채널, 웹페이지를 안내한 경우
            - 근거에 언급이 없는데 "불가능하다" "제공되지 않는다"고 단정한 경우
            - 근거에 각각의 값만 있는데 둘을 비교해 우열이나 장단점을 말한 경우
irrelevant  사실을 주장하지 않는 문장. 다음이 모두 해당한다
            - 인사말, 공감 표현, 되묻기 ("옮기고 싶으시군요", "도움이 되셨길 바랍니다")
            - 근거에 정보가 없다고 밝히는 문장
              ("위약금은 안내에 없습니다", "해당 정보는 근거에 포함되어 있지 않습니다")
              이런 문장은 지어낸 것이 아니라 올바른 행동이므로 unsupported로 보지 않는다
            - 근거 내용을 되풀이하며 행동을 권하는 마무리 ("편한 방식을 선택하시면 됩니다")

JSON만 출력하세요. 설명을 덧붙이지 마세요.
{{"verdict": "supported|unsupported|irrelevant", "reason": "20자 이내"}}"""


def split_sentences(text):
    """한국어 답변을 문장으로 나눈다. 마침표 뒤 공백 또는 끝을 경계로 본다."""
    if not text:
        return []
    parts = re.split(r"(?<=[.!?])\s+", text.strip())
    return [p.strip() for p in parts if p.strip()]


def build_context(sources):
    return "\n".join(f"- {s['question']} {s['answer']}" for s in sources)


def call_model(api_key, region, model, prompt, max_tokens=1024, retries=3):
    url = (
        f"https://bedrock-runtime.{region}.amazonaws.com/model/"
        f"{urllib.parse.quote(model, safe='')}/converse"
    )
    body = json.dumps(
        {
            "messages": [{"role": "user", "content": [{"text": prompt}]}],
            # 같은 문장을 다시 채점해도 같은 결과가 나와야 전후 비교가 된다
            "inferenceConfig": {"maxTokens": max_tokens, "temperature": 0},
            # 판정만 필요한데 기본값은 생각을 길게 해 토큰을 다 쓰고 답을 못 낸다
            "additionalModelRequestFields": {"reasoning_effort": "low"},
        }
    ).encode()

    last = None
    for attempt in range(1, retries + 1):
        request = urllib.request.Request(
            url,
            data=body,
            headers={
                "Authorization": f"Bearer {api_key}",
                "Content-Type": "application/json",
            },
        )
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                payload = json.load(response)
            break
        except urllib.error.HTTPError as error:
            last = f"HTTP {error.code} {error.read()[:200].decode(errors='replace')}"
        except Exception as error:  # 네트워크 오류
            last = str(error)
        time.sleep(2 * attempt)
    else:
        return None, last, {}

    text = ""
    for block in payload["output"]["message"]["content"]:
        if "text" in block:
            text += block["text"]
    return text.strip(), None, payload.get("usage", {})


def parse_verdict(text):
    """모델이 JSON 앞뒤에 다른 말을 붙여도 건져낸다."""
    if not text:
        return None, ""
    match = re.search(r"\{.*\}", text, re.S)
    if match:
        try:
            data = json.loads(match.group())
            verdict = str(data.get("verdict", "")).strip().lower()
            if verdict in LABELS:
                return verdict, str(data.get("reason", ""))[:60]
        except json.JSONDecodeError:
            pass
    labels = set(re.findall(
        r"(?<![a-z])(" + "|".join(sorted(LABELS, key=len, reverse=True)) + r")(?![a-z])",
        text.lower(),
    ))
    if len(labels) == 1:
        return labels.pop(), ""
    return None, text[:60]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input", help="기준선 probe가 남긴 JSON")
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument("--out", help="결과 JSON 경로. 기본은 입력 옆에 저장")
    parser.add_argument("--limit", type=int, help="앞에서 N문장만 채점 (시험용)")
    args = parser.parse_args()

    api_key = os.environ.get("BEDROCK_API_KEY")
    if not api_key:
        sys.exit("BEDROCK_API_KEY가 없습니다. .env를 읽고 실행하세요.")
    region = os.environ.get("AWS_REGION", "us-east-1")

    records = json.loads(Path(args.input).read_text())
    targets = []
    for record in records:
        if record["status"] not in JUDGED_STATUS or not record.get("sources"):
            continue
        context = build_context(record["sources"])
        for order, sentence in enumerate(split_sentences(record["answer"]), 1):
            targets.append((record, order, sentence, context))
    if args.limit:
        targets = targets[: args.limit]

    print(f"채점 대상 {len(targets)}문장 | 모델 {args.model}")
    results = []
    tally = {label: 0 for label in LABELS}
    tally["실패"] = 0
    tokens_in = tokens_out = 0

    for index, (record, order, sentence, context) in enumerate(targets, 1):
        prompt = PROMPT.format(context=context, question=record["question"], sentence=sentence)
        text, error, usage = call_model(api_key, region, args.model, prompt)
        verdict, reason = parse_verdict(text)
        tokens_in += usage.get("inputTokens", 0)
        tokens_out += usage.get("outputTokens", 0)
        tally[verdict if verdict else "실패"] += 1
        results.append(
            {
                "eval_id": record["eval_id"],
                "type": record["type"],
                "order": order,
                "question": record["question"],
                "sentence": sentence,
                "verdict": verdict,
                "reason": reason,
                "error": error,
                "context": context,
            }
        )
        mark = verdict or "실패"
        print(f"  [{index}/{len(targets)}] {record['eval_id']}-{order} {mark:12} {sentence[:40]}")

    out = Path(args.out) if args.out else Path(args.input).with_name(
        Path(args.input).stem + f"-judge-{args.model.split('.')[1].split('-1:')[0]}.json"
    )
    out.write_text(json.dumps(
        {"model": args.model, "summary": tally, "usage": {"in": tokens_in, "out": tokens_out},
         "results": results}, ensure_ascii=False, indent=2))

    print()
    print(f"=== 채점 결과 ({args.model}) ===")
    for label, count in tally.items():
        print(f"  {label:12} {count}")
    print(f"토큰 입력 {tokens_in} / 출력 {tokens_out}")
    print(f"저장: {out}")


if __name__ == "__main__":
    main()
