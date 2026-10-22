"""TELME-218 FAQ 전수 분류: 정책 A로 이 FAQ를 받은 고객에게 답하기 전에 되물어야 하는지 Judge 후보 모델이 판정한다.

usage: python scripts/condition-extraction-evaluation/faq_ask_census.py --model qwen|deepseek-v3.2-usw2 --faqs <faqs.tsv>
       --out <jsonl> [--limit N] [--workers 8]
faqs.tsv는 score_capture.py와 같은 형식이다. 이미 판정한 묶음은 건너뛴다.
AWS 프로필 telme-bedrock-eval이 필요하고 Bedrock 비용이 든다(1,152개를 두 모델로 약 0.06달러).
"""
import argparse
import concurrent.futures as cf
import json
import re
import threading
from pathlib import Path

import boto3

MODELS = {
    "qwen": ("ap-northeast-1", "qwen.qwen3-235b-a22b-2507-v1:0", (0.2651, 1.0605)),
    "deepseek-v3.2-usw2": ("us-west-2", "deepseek.v3.2", (0.62, 1.85)),
}
BATCH = 8

SYSTEM = """당신은 통신사 고객센터 챗봇의 상담 정책 검토자입니다.
챗봇은 고객 질문에 맞는 FAQ를 찾은 뒤, 답하기 전에 고객 상황을 되물을지 정합니다. 아래 정책으로 각 FAQ를 판정하십시오.

[정책]
- FAQ 답을 그대로 전해 질문에 답이 되면 되묻지 않는다(ANSWER).
- FAQ 답이 경우를 나눠 말해도 한두 문장 안에 경우별 결과를 모두 담고 있으면 그대로 전하면 된다(ANSWER).
  예: "기본 한도는 월 30만 원, 본인 인증을 하면 100만 원" -> 인증 여부를 묻지 않는다
- 아래 경우에만 되묻는다(ASK).
  - 고객 상황에 따라 결과가 셋 이상으로 갈려, 함께 말하면 답이 길고 헷갈린다
  - 경우마다 준비물이나 절차가 크게 달라, 고객 상황을 알아야 한 가지로 안내할 수 있다

판정 대상 고객은 FAQ의 질문(Q)을 그대로 물었고, 자기 상황은 말하지 않았다고 보십시오.

[출력]
JSON만 출력하십시오. 설명을 덧붙이지 마십시오.
{"items":[{"id":<FAQ 번호>,"verdict":"ANSWER" 또는 "ASK","caseCount":<고객 상황에 따라 갈리는 경우의 수, 갈리지 않으면 1>,
"proceduresDiffer":<경우마다 준비물이나 절차가 크게 다르면 true>,"askQuestion":"ASK일 때 고객에게 물을 한 문장, ANSWER면 빈 문자열",
"reason":"한 문장"}]}"""


def user_prompt(rows):
    return "\n\n".join(f"[FAQ {r['id']}]\nQ. {r['question']}\nA. {r['answer']}" for r in rows)


def parse(text):
    text = text.strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[1].rsplit("```", 1)[0]
    match = re.search(r"\{.*\}", text, re.S)
    return json.loads(match.group(0))["items"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, choices=MODELS)
    parser.add_argument("--faqs", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--workers", type=int, default=8)
    args = parser.parse_args()
    region, model_id, price = MODELS[args.model]
    rows = []
    for line in args.faqs.read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) >= 4:
            rows.append({"id": int(parts[0]), "category": parts[1], "question": parts[2], "answer": parts[3]})
    if args.limit:
        rows = rows[:args.limit]
    done = set()
    if args.out.exists():
        for line in args.out.read_text(encoding="utf-8").splitlines():
            done.update(item["id"] for item in json.loads(line)["items"])
    batches = [rows[i:i + BATCH] for i in range(0, len(rows), BATCH)]
    batches = [b for b in batches if not all(r["id"] in done for r in b)]
    client = boto3.Session(profile_name="telme-bedrock-eval", region_name=region).client("bedrock-runtime")
    lock = threading.Lock()
    usage = {"in": 0, "out": 0, "failed": 0}

    def run(batch):
        for attempt in range(3):
            try:
                response = client.converse(
                    modelId=model_id,
                    system=[{"text": SYSTEM}],
                    messages=[{"role": "user", "content": [{"text": user_prompt(batch)}]}],
                    inferenceConfig={"maxTokens": 3000, "temperature": 0})
                text = "".join(c.get("text", "") for c in response["output"]["message"]["content"])
                items = parse(text)
                ids = {r["id"] for r in batch}
                if {i["id"] for i in items} != ids:
                    raise ValueError("id mismatch")
                with lock:
                    usage["in"] += response["usage"]["inputTokens"]
                    usage["out"] += response["usage"]["outputTokens"]
                    with args.out.open("a", encoding="utf-8") as f:
                        f.write(json.dumps({"items": items}, ensure_ascii=False) + "\n")
                return
            except Exception as error:  # noqa: BLE001 - 재시도 후 실패만 센다
                last = error
        with lock:
            usage["failed"] += 1
            print("FAILED", [r["id"] for r in batch], repr(last)[:200], flush=True)

    with cf.ThreadPoolExecutor(args.workers) as pool:
        list(pool.map(run, batches))
    cost = usage["in"] * price[0] / 1e6 + usage["out"] * price[1] / 1e6
    print(f"{args.model} batches={len(batches)} in={usage['in']} out={usage['out']} failed={usage['failed']} "
          f"cost=${cost:.3f}", flush=True)


if __name__ == "__main__":
    main()
