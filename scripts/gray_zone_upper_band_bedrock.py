#!/usr/bin/env python3
"""TELME-156 회색 구간 판정 위로 넓히기 측정.

TELME-122(#139)의 회색 구간 판정에 더해, 지금은 판정 없이 통과하는 Q_A 0.72 이상 구간도
판정기(Bedrock gpt-oss-120b, 프롬프트 B3)가 확인하게 했을 때를 잰다.

  Q_A ≥ 위 선, 질문 ≥ 0.88            → 판정 없이 통과
  Q_A 아래 선 ~ 위 선, 질문 0.80~0.88  → 판정기가 통과시켜야 근거로 씀
  그 아래                              → 버림

Q_A 위 선이 0.72면 TELME-122와 같은 구조다. 판정 프롬프트·모델·3회 다수결·fail-open 해석은 #139의
evidence_check_bedrock.py와 같다. #139와 독립적으로 돌도록 필요한 부분만 이 파일에 둔다.

검색은 앱과 같은 쿼리(필터·HNSW)로 로컬 DB에서 벡터별 상위 3개를 읽는다.
질문 임베딩은 로컬 Ollama bge-m3로 만든다.

사용법
  # 1) 예상 비용만 확인 (캐시에 없거나 입력이 바뀐 쌍만 셈, 호출 안 함)
  python3 scripts/gray_zone_upper_band_bedrock.py
  # 2) 판정 실행 후 결과 표
  python3 scripts/gray_zone_upper_band_bedrock.py --run

환경변수 (.env)
  BEDROCK_API_KEY   Bedrock API 키 (--run일 때만)
  AWS_REGION        기본 us-east-1
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "scripts/data"
DEFAULT_EVAL = DATA / "eval_questions_180_multigold.json"
DEFAULT_OFFICIAL = [DATA / "eval_questions_130.json", DATA / "eval_questions_supplement_50.json"]
DEFAULT_HOLDOUT = DATA / "eval_questions_holdout_90.json"
DEFAULT_CACHE = ROOT / ".measure/gray-upper-judgments-b3.json"

MODEL = "openai.gpt-oss-120b-1:0"
PROMPT_VERSION = "evidence-check-b3"
QA_UPPER = 0.72  # 지금 Q_A 임계값
QO_UPPER = 0.88  # 지금 질문 벡터 임계값
TOP_K = 3
REPEAT = 3
MAX_TOKENS = 1000
# USD / 100만 토큰 (입력, 출력). us-east-1 온디맨드 기준
PRICE_IN, PRICE_OUT = 0.15, 0.60
EST_INPUT_TOKENS, EST_OUTPUT_TOKENS = 500, 150

# #139 evidence_check_bedrock.py의 B2_SYSTEM_PROMPT에 B3 두 줄을 넣은 문자열과 같다. 저쪽을 고치면 함께 고친다
B3_SYSTEM_PROMPT = """당신은 통신사 FAQ 검색 결과를 검수합니다.
고객 질문에 답할 정보가 FAQ에 있는지 아래 순서대로 판단하십시오.

1. asked: 고객이 알고 싶어 하는 것을 항목별로 짧게 적습니다.
   질문에 나온 행위(신청, 해지, 변경 등)와 알고 싶은 정보의 종류(비용, 소요 시간, 마감 기한,
   방법, 장소, 가능 여부, 혜택, 기준 등)를 바꾸지 말고 그대로 적습니다.
   질문 문장을 통째로 옮기지 말고, 항목마다 무엇에 대한 것인지(상품, 서비스, 상태, 상황)를 함께 적습니다.
2. found: asked의 각 항목이 FAQ에 있으면 true, 없으면 false를 같은 순서로 적습니다.
   - 행위가 다르면 없는 것입니다.
   - 대상(상품, 서비스, 상태, 상황)이 다르면 없는 것입니다. 이름이나 주제가 비슷해도 마찬가지입니다.
   - 정보의 종류가 다르면 없는 것입니다. 주제가 같아도 마찬가지입니다.
   - 묻는 값의 단위나 표현이 FAQ와 달라도, FAQ에 적힌 값을 환산하거나 계산해 답을 만들 수 있으면
     있는 것입니다. 이것은 정보의 종류가 다른 경우가 아닙니다.
3. answerable: found에 true가 하나라도 있으면 true, 모두 false면 false.

JSON만 출력하십시오.
{"asked": ["..."], "found": [true 또는 false], "answerable": true 또는 false}
"""


# ---------- 순수 계산 (단위 테스트 대상) ----------

def keep(score, upper, floor, verdict):
    """위 선 이상은 통과, 아래 선 미만은 버림, 그 사이는 판정 결과를 따른다."""
    if score >= upper:
        return True
    if score < floor:
        return False
    return bool(verdict)


def served(qo_results, qa_results, qo_floor, qa_floor, qa_top, verdict_of):
    """앱처럼 질문 벡터 결과를 먼저 합치고 중복을 빼 상위 3개를 근거로 쓴다.

    qo_results, qa_results: [(slot_id, score)] 벡터별 상위 결과. slot_id가 없는 시드 FAQ는 None
    verdict_of: slot_id -> 판정(True/False). 판정 구간 밖이면 부르지 않는다
    slot_id가 없으면 본문을 특정할 수 없어 판정 구간에서는 판정 없이 거부한다(#139와 같음)
    """
    picked = []
    for results, upper, floor in ((qo_results, QO_UPPER, qo_floor), (qa_results, qa_top, qa_floor)):
        for slot, score in results:
            if slot in picked:
                continue
            judged = floor <= score < upper
            if keep(score, upper, floor, verdict_of(slot) if judged and slot is not None else None):
                picked.append(slot)
    return picked[:TOP_K]


def needs_judgment(qo_results, qa_results, qo_floor, qa_floor, qa_top):
    """판정 구간에 든 slot 목록(중복 제거). slot_id 없는 후보는 판정하지 않으므로 뺀다."""
    slots = [s for s, v in qo_results if qo_floor <= v < QO_UPPER and s is not None]
    slots += [s for s, v in qa_results if qa_floor <= v < qa_top and s is not None]
    return list(dict.fromkeys(slots))


def majority(votes):
    return sum(1 for v in votes if v) * 2 > len(votes)


def parse_answerable(text):
    """#139와 같은 fail-open: 비었거나 깨졌거나 필드가 없으면 True."""
    if not text or not text.strip():
        return True
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end < start:
        return True
    try:
        value = json.loads(text[start:end + 1]).get("answerable")
    except (json.JSONDecodeError, AttributeError):
        return True
    if isinstance(value, bool):
        return value
    if isinstance(value, str) and value.strip().lower() in ("true", "false"):
        return value.strip().lower() == "true"
    return True


def input_hash(prompt):
    """판정에 실제로 보내는 입력(모델, 시스템 프롬프트, 사용자 프롬프트). 하나라도 바뀌면 다시 판정한다."""
    text = json.dumps([MODEL, B3_SYSTEM_PROMPT, prompt], ensure_ascii=False)
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def pairs_to_judge(pairs, cached, hashes):
    """캐시에 없거나 입력이 바뀐 쌍."""
    return sorted(key for key in pairs if cached.get(key, {}).get("inputHash") != hashes[key])


def cache_entry(runs, prompt_hash):
    """호출 오류가 섞이면 None. fail-open으로 '답 됨'이 저장되지 않게 그 쌍은 저장하지 않고 다음 실행에 다시 판정한다.
    응답은 왔는데 JSON이 깨진 경우는 #139와 같이 fail-open을 유지한다."""
    if any(run["error"] for run in runs):
        return None
    return {"answerable": majority([run["answerable"] for run in runs]), "inputHash": prompt_hash, "runs": runs}


def score_eval(rows, official, multi, unrelated_ids, config, verdicts):
    """개선용 180: 정답(공식/다중), 무관 통과(기존 EVAL-081~130 / 보강 EVAL-161~180), 판정 붙는 질문 수."""
    qo_floor, qa_floor, qa_top = config
    answered_official = answered_multi = unrelated_old = unrelated_supplement = judged_questions = 0
    for row in rows:
        eval_id = row["id"]
        judged_questions += bool(needs_judgment(row["qo"], row["qa"], qo_floor, qa_floor, qa_top))
        picked = set(served(row["qo"], row["qa"], qo_floor, qa_floor, qa_top,
                            lambda slot: verdicts[f"{eval_id}|{slot}"]))
        if eval_id in unrelated_ids:
            if picked:
                number = int(eval_id.split("-")[1])
                unrelated_old += number <= 130
                unrelated_supplement += number >= 161
        else:
            answered_official += bool(picked & official[eval_id])
            answered_multi += bool(picked & multi[eval_id])
    return {"official": answered_official, "multi": answered_multi, "unrelatedOld": unrelated_old,
            "unrelatedSupplement": unrelated_supplement, "judgedQuestions": judged_questions}


def score_holdout(rows, config, verdicts):
    qo_floor, qa_floor, qa_top = config
    return sum(1 for row in rows if served(row["qo"], row["qa"], qo_floor, qa_floor, qa_top,
                                          lambda slot, i=row["id"]: verdicts[f"{i}|{slot}"]))


# ---------- 입출력 ----------

def psql(container, sql):
    out = subprocess.run(["docker", "exec", "-i", container, "psql", "-U", "telme", "-d", "telme", "-At",
                          "-F", "\x1f", "-R", "\x1e"], input=sql, capture_output=True, text=True, check=True)
    return [row.split("\x1f") for row in out.stdout.split("\x1e") if row.strip()]


def embed(text, ollama_url):
    request = urllib.request.Request(f"{ollama_url}/api/embed",
                                     data=json.dumps({"model": "bge-m3", "input": text}).encode(),
                                     headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)["embeddings"][0]


def nearest(container, column, vector):
    """FaqEmbeddingRepository.findNearest와 같은 필터·정렬."""
    literal = "[" + ",".join(f"{x:.7f}" for x in vector) + "]"
    sql = (f"select f.slot_id, 1 - (e.{column} <=> '{literal}'::vector) from faq_embeddings e "
           "join faqs f on f.faq_id = e.faq_id where f.status = 'ACTIVE' and e.sync_status = 'SYNCED' "
           f"and e.faq_version = f.version and e.model_name = 'bge-m3' and e.{column} is not null "
           f"order by e.{column} <=> '{literal}'::vector limit {TOP_K}")
    # psql -At는 NULL을 빈 문자열로 내보낸다. slot_id 없는 시드 FAQ끼리 섞이지 않게 None으로 둔다
    return [(slot or None, float(score)) for slot, score in psql(container, sql)]


def search_rows(items, container, ollama_url):
    rows = []
    for item in items:
        vector = embed(item["question"], ollama_url)
        rows.append({"id": item["eval_id"], "question": item["question"],
                     "qo": nearest(container, "embedding_question", vector),
                     "qa": nearest(container, "embedding", vector)})
    return rows


def call_model(api_key, region, prompt, retries=3):
    url = (f"https://bedrock-runtime.{region}.amazonaws.com/model/"
           f"{urllib.parse.quote(MODEL, safe='')}/converse")
    body = json.dumps({
        "system": [{"text": B3_SYSTEM_PROMPT}],
        "messages": [{"role": "user", "content": [{"text": prompt}]}],
        "inferenceConfig": {"maxTokens": MAX_TOKENS, "temperature": 0},
        "additionalModelRequestFields": {"reasoning_effort": "low"},
    }).encode()
    last = None
    for attempt in range(1, retries + 1):
        request = urllib.request.Request(url, data=body, headers={
            "Authorization": f"Bearer {api_key}", "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                payload = json.load(response)
            text = "".join(b["text"] for b in payload["output"]["message"]["content"] if "text" in b)
            return text.strip(), None, payload.get("usage", {})
        except urllib.error.HTTPError as error:
            last = f"HTTP {error.code} {error.read()[:200].decode(errors='replace')}"
            # 권한·요청 오류는 다시 보내도 같다
            if error.code in (400, 401, 403, 404):
                return None, last, {}
        except Exception as error:  # 네트워크 오류
            last = str(error)
        time.sleep(2 * attempt)
    return None, last, {}


def user_prompt(faq_question, faq_answer, question):
    """#139 evidence_check_bedrock.py의 build_context + user_prompt와 같은 형식(FAQ 1개)."""
    return f"[FAQ]\n[1] Q: {faq_question}\nA: {faq_answer}\n\n[고객 질문]\n{question}"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--qo-floor", type=float, default=0.80)
    parser.add_argument("--qa-floors", type=float, nargs="+", default=[0.71, 0.70, 0.69])
    parser.add_argument("--qa-tops", type=float, nargs="+", default=[0.72, 0.74, 0.76, 0.78, 0.80])
    parser.add_argument("--cache", type=Path, default=DEFAULT_CACHE)
    parser.add_argument("--container", default="telme-postgres")
    parser.add_argument("--ollama-url", default="http://localhost:11434")
    parser.add_argument("--workers", type=int, default=4)
    parser.add_argument("--krw-per-usd", type=float, default=1450.0)
    parser.add_argument("--run", action="store_true", help="없으면 예상 비용만 출력하고 끝낸다")
    args = parser.parse_args()

    eval_items = json.loads(DEFAULT_EVAL.read_text())
    holdout_items = json.loads(DEFAULT_HOLDOUT.read_text())
    official = {item["eval_id"]: set(item["expected_slot_id"] or [])
                for path in DEFAULT_OFFICIAL for item in json.loads(path.read_text())}
    multi = {item["eval_id"]: set(item["expected_slot_id"] or []) for item in eval_items}
    unrelated_ids = {item["eval_id"] for item in eval_items if item["type"] == "UNRELATED"}

    print("검색 중 (개선용 180 + holdout 90)...", flush=True)
    rows = search_rows(eval_items, args.container, args.ollama_url)
    holdout_rows = search_rows(holdout_items, args.container, args.ollama_url)

    widest = (args.qo_floor, min(args.qa_floors), max(args.qa_tops))
    questions = {row["id"]: row["question"] for row in rows + holdout_rows}
    pairs = {f"{row['id']}|{slot}" for row in rows + holdout_rows
             for slot in needs_judgment(row["qo"], row["qa"], *widest)}
    faqs = {slot: (question, answer) for slot, question, answer in
            psql(args.container, "select slot_id, question, answer from faqs "
                     "where status = 'ACTIVE' and slot_id is not null")}
    prompts = {}
    for key in pairs:
        eval_id, slot = key.split("|", 1)
        prompts[key] = user_prompt(*faqs[slot], questions[eval_id])
    hashes = {key: input_hash(prompt) for key, prompt in prompts.items()}
    cache = json.loads(args.cache.read_text()) if args.cache.exists() else {}
    verdicts = cache.get("pairs", {})
    todo = pairs_to_judge(pairs, verdicts, hashes)
    changed = sum(1 for key in todo if key in verdicts)
    calls = len(todo) * REPEAT
    usd = calls * (EST_INPUT_TOKENS * PRICE_IN + EST_OUTPUT_TOKENS * PRICE_OUT) / 1_000_000
    print(f"판정 쌍 {len(pairs)}개 중 새로 판정할 {len(todo)}개(입력이 바뀐 {changed}개 포함) × {REPEAT}회 = "
          f"{calls}호출, 예상 ${usd:.4f} ≈ {usd * args.krw_per_usd:,.0f}원")

    if todo:
        if not args.run:
            print("예상만 출력했습니다. 실제로 돌리려면 --run 을 붙이세요.")
            return
        api_key = os.environ.get("BEDROCK_API_KEY")
        if not api_key:
            sys.exit("BEDROCK_API_KEY가 없습니다. .env를 읽고 실행하세요.")
        region = os.environ.get("AWS_REGION", "us-east-1")
        tokens = [0, 0]

        def judge(key):
            runs = []
            for _ in range(REPEAT):
                text, error, usage = call_model(api_key, region, prompts[key])
                if error and error.startswith("HTTP 4"):
                    raise SystemExit(f"요청 오류로 중단: {error}")
                runs.append({"answerable": parse_answerable(text), "error": error, "raw": text})
                tokens[0] += usage.get("inputTokens", 0)
                tokens[1] += usage.get("outputTokens", 0)
            return key, runs

        failed = []
        args.cache.parent.mkdir(parents=True, exist_ok=True)
        with ThreadPoolExecutor(max_workers=args.workers) as pool:
            for done, (key, runs) in enumerate(pool.map(judge, todo), 1):
                entry = cache_entry(runs, hashes[key])
                if entry is None:
                    failed.append(key)
                else:
                    verdicts[key] = entry
                    args.cache.write_text(json.dumps({"model": MODEL, "promptVersion": PROMPT_VERSION,
                                                      "pairs": verdicts}, ensure_ascii=False))
                if done % 25 == 0:
                    print(f"  {done} / {len(todo)}", flush=True)
        spent = (tokens[0] * PRICE_IN + tokens[1] * PRICE_OUT) / 1_000_000
        print(f"토큰 입력 {tokens[0]:,} / 출력 {tokens[1]:,} | 실제 ${spent:.4f} ≈ {spent * args.krw_per_usd:,.0f}원")
        if failed:
            sys.exit(f"호출 오류로 {len(failed)}쌍을 저장하지 않았습니다(예: {failed[0]}). "
                     "--run 을 다시 하면 그 쌍만 다시 판정합니다.")

    judged = {key: value["answerable"] for key, value in verdicts.items()}
    flips = sum(1 for key in pairs if len({r["answerable"] for r in verdicts[key]["runs"]}) > 1)
    print(f"\n판정 {len(pairs)}쌍 중 3회 안에서 갈린 쌍 {flips}")
    print(f"\n질문 벡터 아래 선 {args.qo_floor:.2f} 고정 (위 선 {QO_UPPER})")
    print("Q_A 아래 선 ~ 위 선 | 정답 공식 / 다중 (110) | 무관 기존 / 보강 (70) | holdout (90) | 판정 붙는 질문")
    baseline = score_eval(rows, official, multi, unrelated_ids, (QO_UPPER, QA_UPPER, QA_UPPER), judged)
    print(f"  현행 (판정 없음)      | {baseline['official']} / {baseline['multi']} | "
          f"{baseline['unrelatedOld']} / {baseline['unrelatedSupplement']} | "
          f"{score_holdout(holdout_rows, (QO_UPPER, QA_UPPER, QA_UPPER), judged)} | 0")
    for qa_floor in sorted(args.qa_floors, reverse=True):
        for qa_top in sorted(args.qa_tops):
            config = (args.qo_floor, qa_floor, qa_top)
            result = score_eval(rows, official, multi, unrelated_ids, config, judged)
            print(f"  {qa_floor:.2f} ~ {qa_top:.2f}        | {result['official']} / {result['multi']} | "
                  f"{result['unrelatedOld']} / {result['unrelatedSupplement']} | "
                  f"{score_holdout(holdout_rows, config, judged)} | "
                  f"{result['judgedQuestions'] / len(rows):.0%}")


if __name__ == "__main__":
    main()
