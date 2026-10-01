#!/usr/bin/env python3
"""Adjudicate candidate FAQ alternatives with a local Ollama model.

Each decision answers whether the candidate FAQ is a valid, sufficient source
for the primary FAQ question. The model never writes directly to gold labels;
all raw exchanges and per-pair verdicts are saved for review.
"""

import argparse
import hashlib
import json
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
INPUT = ROOT / "scripts/data/faq_equivalence_review_candidates_v2.json"
OUTPUT = ROOT / "scripts/data/faq_equivalence_adjudication_v2.json"
MODEL = "exaone3.5:7.8b"
SCHEMA = {
    "type": "object",
    "properties": {
        "decisions": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "primaryId": {"type": "string"},
                    "candidateId": {"type": "string"},
                    "equivalent": {"type": "boolean"},
                    "reason": {"type": "string"},
                },
                "required": ["primaryId", "candidateId", "equivalent", "reason"],
            },
        }
    },
    "required": ["decisions"],
}


FIRST_PASS_SYSTEM = """너는 통신사 FAQ 검색 평가셋의 대체 정답 라벨 검토자다.
각 항목에서 primary 질문에 답할 때 candidate FAQ 하나만 근거로 사용해도 충분하고 안전한지 판단한다.

equivalent=true는 candidate의 질문과 답변이 primary 질문에 직접 답하고, 핵심 사실과 조건이 맞을 때만 허용한다.
다음 중 하나라도 다르면 false로 판단한다: 예/아니요 방향, 대상/자격, 요금/수치, 기간/시점, 신청·처리 절차, 예외, 지역·상품 조건.
같은 주제이거나 표현만 비슷한 것은 동등 정답이 아니다. candidate 답변이 primary 답의 일부만 다루거나, 추가 조건 때문에 모든 경우에 적용할 수 없거나, 질문만 비슷하고 답변이 다른 경우에도 false다.
근거가 불충분하거나 조금이라도 애매하면 false로 둔다. 문서의 사실을 추측하거나 외부 지식을 보태지 않는다.
짧은 한국어 이유를 쓰고, 입력된 ID를 그대로 반환한다."""

STRICT_SYSTEM = """너는 통신 FAQ 검색 평가셋에서 대체 정답을 보수적으로 검토한다.
판단 질문: 사용자가 primary 질문을 했을 때 candidate FAQ 하나만 검색돼도 primary FAQ와 동등한 근거라고 인정해도 되는가?

true는 candidate 질문과 답변이 primary의 실제 질문 범위와 맞고, 질문에 답하는 데 필요한 핵심 사실과 조건을 빠짐없이 제공할 때만 허용한다. primary 답변에서 그 질문에 직접 관련된 핵심 정보가 candidate 답변에 없으면 false다.
예/아니요 방향, 대상·자격, 금액·수치, 기간·시점, 절차, 예외, 상품·지역 조건이 다르거나 빠지면 false다. 더 좁은 상황에만 맞는 후보를 일반 질문의 대체 근거로 인정하지 않는다. 추가 정보는 primary 질문에 맞고 모순되지 않을 때만 허용한다.
같은 주제, 비슷한 질문, 일부 사실의 일치만으로는 true가 아니다. 질문과 답변을 함께 보고, 추측 없이 판단한다. 애매하면 false다.
짧은 한국어 이유를 쓰고 입력된 ID를 그대로 반환한다."""


def sha256(value):
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True,
                     separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def prompt_for(batch):
    items = []
    for row in batch:
        items.append({
            "primaryId": row["primaryId"],
            "primaryQuestion": row["primary"]["question"],
            "primaryAnswer": row["primary"]["answer"],
            "candidateId": row["candidateId"],
            "candidateQuestion": row["candidate"]["question"],
            "candidateAnswer": row["candidate"]["answer"],
        })
    return json.dumps({"items": items}, ensure_ascii=False, indent=2)


def call_ollama(url, model, batch, timeout, system):
    user_prompt = prompt_for(batch)
    payload = {
        "model": model,
        "stream": False,
        "format": SCHEMA,
        "think": False,
        "options": {"temperature": 0, "num_ctx": 8192},
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user_prompt},
        ],
    }
    request = urllib.request.Request(
        url.rstrip("/") + "/api/chat",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    started = time.monotonic()
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw = json.load(response)
    elapsed = round(time.monotonic() - started, 3)
    content = raw.get("message", {}).get("content", "")
    parsed = json.loads(content)
    return payload, raw, parsed, elapsed


def read_existing(path):
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    return {"decisions": {}, "batches": []}


def save(path, data):
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n",
                         encoding="utf-8")
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ollama-url", default="http://localhost:11434")
    parser.add_argument("--model", default=MODEL)
    parser.add_argument("--batch-size", type=int, default=6)
    parser.add_argument("--timeout", type=int, default=240)
    parser.add_argument("--policy-mode", choices=("candidate", "strict"), default="candidate")
    parser.add_argument("--input", type=Path, default=INPUT)
    parser.add_argument("--out", type=Path, default=OUTPUT)
    args = parser.parse_args()

    candidate_file = json.loads(args.input.read_text(encoding="utf-8"))
    candidates = candidate_file["candidates"]
    system = FIRST_PASS_SYSTEM if args.policy_mode == "candidate" else STRICT_SYSTEM
    state = read_existing(args.out)
    if state.get("candidateFileSha256") not in (None, sha256(candidate_file)):
        raise SystemExit("Candidate input changed; use a new output file to preserve provenance.")
    state.update({
        "schemaVersion": 1,
        "candidateFile": args.input.name,
        "candidateFileSha256": sha256(candidate_file),
        "model": args.model,
        "policyMode": args.policy_mode,
        "policy": system,
        "createdAt": state.get("createdAt", datetime.now(timezone.utc).isoformat()),
        "decisions": state.get("decisions", {}),
        "batches": state.get("batches", []),
    })

    pending = [row for row in candidates
               if state["decisions"].get(f"{row['primaryId']}|{row['candidateId']}", {})
               .get("status") != "SCORED"]
    batches = [pending[index:index + args.batch_size]
               for index in range(0, len(pending), args.batch_size)]
    print(f"{len(candidates)} candidates; {len(pending)} pending; {len(batches)} batches")
    for batch_number, batch in enumerate(batches, 1):
        started_iso = datetime.now(timezone.utc).isoformat()
        try:
            request_payload, raw, result, elapsed = call_ollama(
                args.ollama_url, args.model, batch, args.timeout, system)
            expected = {(row["primaryId"], row["candidateId"]) for row in batch}
            returned = result.get("decisions", [])
            keyed = {}
            for decision in returned:
                key = (decision.get("primaryId"), decision.get("candidateId"))
                if key in expected and key not in keyed \
                        and isinstance(decision.get("equivalent"), bool) \
                        and isinstance(decision.get("reason"), str):
                    keyed[key] = decision
            for primary_id, candidate_id in expected:
                key = f"{primary_id}|{candidate_id}"
                decision = keyed.get((primary_id, candidate_id))
                state["decisions"][key] = {
                    "primaryId": primary_id,
                    "candidateId": candidate_id,
                    "status": "SCORED" if decision else "UNSCORED",
                    "equivalent": decision.get("equivalent") if decision else None,
                    "reason": decision.get("reason") if decision else "Missing or invalid model verdict",
                    "batch": len(state["batches"]),
                }
            state["batches"].append({
                "startedAt": started_iso,
                "candidateIds": [f"{row['primaryId']}|{row['candidateId']}" for row in batch],
                "request": request_payload,
                "response": raw,
                "parsed": result,
                "elapsedSeconds": elapsed,
                "status": "OK",
            })
        except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, KeyError, ValueError) as error:
            state["batches"].append({
                "startedAt": started_iso,
                "candidateIds": [f"{row['primaryId']}|{row['candidateId']}" for row in batch],
                "request": {"prompt": prompt_for(batch)},
                "status": "ERROR",
                "error": f"{type(error).__name__}: {error}",
            })
            for row in batch:
                key = f"{row['primaryId']}|{row['candidateId']}"
                state["decisions"][key] = {
                    "primaryId": row["primaryId"],
                    "candidateId": row["candidateId"],
                    "status": "UNSCORED",
                    "equivalent": None,
                    "reason": "Model request or output failed",
                    "batch": len(state["batches"]) - 1,
                }
        save(args.out, state)
        scored = sum(1 for item in state["decisions"].values() if item["status"] == "SCORED")
        print(f"batch {batch_number}/{len(batches)} saved; scored={scored}; output={args.out}", flush=True)

    scored_rows = [item for item in state["decisions"].values() if item["status"] == "SCORED"]
    true_count = sum(1 for item in scored_rows if item["equivalent"] is True)
    false_count = sum(1 for item in scored_rows if item["equivalent"] is False)
    unscored_count = len(candidates) - len(scored_rows)
    state["summary"] = {
        "candidateCount": len(candidates),
        "scoredCount": len(scored_rows),
        "equivalentCandidateCount": true_count,
        "rejectedCandidateCount": false_count,
        "unscoredCount": unscored_count,
        "modelVerdictsAreProposalsOnly": True,
    }
    save(args.out, state)
    print(json.dumps(state["summary"], ensure_ascii=False))


if __name__ == "__main__":
    main()
