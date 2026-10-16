"""후보 모델로 사람이 판정한 302건을 V7 채점 도구 그대로 채점한다.

V7 코드는 수정하지 않고 모델 항목과 대상 턴만 바꿔 쓴다. 기본은 호출 없이 규모만 출력하며,
실제 호출에는 --allow-bedrock-call이 필요하다. 인증은 환경 변수 BEDROCK_API_KEY(또는 AWS_BEARER_TOKEN_BEDROCK)만 읽는다.
"""

import argparse
import json
import os
import threading
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as v6
from scripts.chat_judge.experiments.v7_cross_judge import bedrock, run_cross_judge
from scripts.chat_judge.experiments.v7_cross_judge.analyze_human302 import DEFAULT_HUMAN, read_human
from scripts.chat_judge.experiments.v7_cross_judge.build_v7_capture import V7_DIR
from scripts.chat_judge.experiments.v8_judge_model_selection.candidates import CANDIDATES, cost

V8_DIR = V7_DIR.parent / "V8-judge-model-selection"
CALLS_PER_TURN = 5  # 근거성 2, 충실도 1, 보류 판정 2


COPY_HINT = ("\nEvery quote must be copied character for character from assistantAnswer. Never paraphrase, "
             "shorten, or change endings or punctuation. If a sentence cannot be copied exactly, quote a "
             "shorter exact span instead.")


class LedgerClient:
    """converse 호출마다 토큰을 기록한다. 재시도와 실패도 남기고, prompt_json 모드는 스키마를 프롬프트로 요청한다."""

    def __init__(self, client, model_id, mode, copy_hint=False):
        self.client, self.model_id, self.mode, self.copy_hint = client, model_id, mode, copy_hint
        self.ledger, self.lock = [], threading.Lock()

    @staticmethod
    def _stage_name(request):
        schema = request.get("outputConfig", {}).get("textFormat", {}).get("structure", {}).get("jsonSchema", {})
        return schema.get("name")

    def converse(self, **request):
        stage = self._stage_name(request)
        if self.mode == "prompt_json" and "outputConfig" in request:
            schema = request["outputConfig"]["textFormat"]["structure"]["jsonSchema"]["schema"]
            request = {key: value for key, value in request.items() if key != "outputConfig"}
            request["inferenceConfig"] = {**request["inferenceConfig"], "maxTokens": 2048, "temperature": 0}
            request["system"] = [{"text": request["system"][0]["text"]
                                  + "\nReturn exactly one compact JSON object matching this JSON Schema. "
                                    "Do not include markdown, analysis, or text outside the JSON object. Schema: "
                                  + schema}]
        if self.copy_hint and stage == "claim_extraction":
            request = {**request, "system": [{"text": request["system"][0]["text"] + COPY_HINT}]}
        entry = {"modelId": self.model_id, "mode": self.mode, "copyHint": self.copy_hint}
        try:
            raw = self.client.converse(**request)
        except Exception as error:
            entry["error"] = f"{type(error).__name__}: {error}"
            with self.lock:
                self.ledger.append(entry)
            raise
        if self.mode == "prompt_json":
            # 일부 모델은 JSON을 ```json 코드 블록으로 감싸 보낸다. 내용은 그대로 두고 감싼 부분만 벗긴다
            for block in raw["output"]["message"]["content"]:
                text = block.get("text", "").strip()
                if text.startswith("```") and text.endswith("```"):
                    block["text"] = text.split("\n", 1)[1].rsplit("```", 1)[0].strip()
                    entry["fenceStripped"] = True
        usage = raw.get("usage") or {}
        entry.update(promptTokens=usage.get("inputTokens", 0), outputTokens=usage.get("outputTokens", 0),
                     stopReason=raw.get("stopReason"))
        with self.lock:
            self.ledger.append(entry)
        return raw


def make_client(region):
    key = os.environ.get("AWS_BEARER_TOKEN_BEDROCK") or os.environ.get("BEDROCK_API_KEY")
    if not key:
        raise SystemExit("BEDROCK_API_KEY 또는 AWS_BEARER_TOKEN_BEDROCK 환경 변수가 필요합니다")
    os.environ["AWS_BEARER_TOKEN_BEDROCK"] = key
    import boto3
    return boto3.client("bedrock-runtime", region_name=region)


def ledger_summary(ledger, price):
    prompt = sum(item.get("promptTokens", 0) for item in ledger)
    output = sum(item.get("outputTokens", 0) for item in ledger)
    return {"calls": len(ledger), "failedCalls": sum("error" in item for item in ledger),
            "promptTokens": prompt, "outputTokens": output, "costUsd": round(cost(prompt, output, price), 4)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", choices=tuple(CANDIDATES), required=True)
    parser.add_argument("--mode", choices=("prompt_json", "structured"), default="prompt_json")
    parser.add_argument("--human", type=Path, default=DEFAULT_HUMAN)
    parser.add_argument("--capture", type=Path, default=V7_DIR / "v7-capture.json.gz")
    parser.add_argument("--out", type=Path)
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--limit", type=int, default=0, help="앞에서부터 N턴만 (연결 확인용 소량 실행)")
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--copy-hint", action="store_true",
                        help="주장 추출 단계에 원문 그대로 복사하라는 지시를 추가 (재시도용)")
    parser.add_argument("--preflight-only", action="store_true", help="호출 1건으로 연결과 JSON 응답만 확인")
    parser.add_argument("--allow-bedrock-call", action="store_true", help="유료 호출 허가")
    args = parser.parse_args()
    if not 1 <= args.workers <= 8 or args.limit < 0:
        parser.error("workers는 1~8, limit은 0 이상이어야 합니다")
    candidate = CANDIDATES[args.model]
    out = args.out or V8_DIR / f"{args.model}-raw.json.gz"

    capture, catalog = v6.load_json(args.capture), v6.load_json(judge.DEFAULT_CATALOG)
    human_keys = set(read_human(args.human))
    all_jobs = run_cross_judge.build_jobs(capture, catalog)
    jobs = [job for job in all_jobs if (job[2]["caseId"], job[2]["turnIndex"]) in human_keys]
    if len(jobs) != len(human_keys):
        raise SystemExit(f"사람 판정 {len(human_keys)}건 중 {len(jobs)}건만 캡처에서 찾았습니다")
    selected = jobs[:args.limit] if args.limit else jobs
    completed = sum(1 for turn, _, _ in selected
                    if turn["executionStatus"] == "COMPLETED" and turn.get("outputMessage"))
    print(json.dumps({"model": args.model, "modelId": candidate["modelId"], "region": candidate["region"],
                      "mode": args.mode, "selectedTurns": len(selected), "completedTurns": completed,
                      "expectedCalls": completed * CALLS_PER_TURN, "out": str(out)}, ensure_ascii=False))
    if not args.allow_bedrock_call:
        print("호출하지 않았습니다. 실제 실행에는 --allow-bedrock-call이 필요합니다.")
        return
    if out.exists() and not args.resume and not args.preflight_only:
        parser.error("결과 파일이 이미 있습니다. 보존하거나 --resume을 쓰세요")

    bedrock.MODELS[args.model] = {"region": candidate["region"], "modelId": candidate["modelId"]}
    run_cross_judge.build_jobs = lambda *_: selected
    wrapper = LedgerClient(make_client(candidate["region"]), candidate["modelId"], args.mode, args.copy_hint)
    judge_client = bedrock.BedrockJudge(args.model, client=wrapper)
    clients = {args.model: judge_client}
    if not args.resume or not out.exists():
        check = run_cross_judge.preflight(clients)[args.model]
        if check.get("result") != {"ok": True}:
            print(json.dumps(check.get("error", check.get("result")), ensure_ascii=False))
            print(json.dumps(ledger_summary(wrapper.ledger, candidate["price"])))
            raise SystemExit("연결 확인에 실패해 본 실행을 하지 않았습니다")
        if args.preflight_only:
            print("연결과 JSON 응답 확인 완료")
            print(json.dumps(ledger_summary(wrapper.ledger, candidate["price"])))
            return
    prior = v6.load_json(out) if args.resume and out.exists() else None
    result = run_cross_judge.evaluate(capture, catalog, clients, workers=args.workers, prior=prior,
                                      on_checkpoint=lambda value: v6.checkpoint(out, value))
    result["usageLedger"] = wrapper.ledger
    v6.checkpoint(out, result)
    print(json.dumps(ledger_summary(wrapper.ledger, candidate["price"]), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
