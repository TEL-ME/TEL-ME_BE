"""Rejudge the frozen V6 answers with three Bedrock models; preserve each model's raw output."""

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import copy
from datetime import datetime, timezone
import json
from pathlib import Path
import threading

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as v6
from scripts.chat_judge.experiments.v6_live_chat_pipeline import compare_human_review as human_review
from scripts.chat_judge.experiments.v7_cross_judge import bedrock
from scripts.chat_judge.experiments.v7_cross_judge.bedrock import BedrockJudge, MODELS
from scripts.chat_judge.experiments.v7_cross_judge.build_v7_capture import V7_DIR


def decision(item, axis):
    record = item.get(axis, {})
    value = record.get("result")
    if value is None:
        return None
    if axis == "grounding":
        return (value["overall"], tuple((claim["claim"], claim["verdict"])
                                         for claim in value["claims"]))
    if axis == "quality":
        return tuple(group["outcome"] for group in value["groups"])
    label = (item.get("abstentionDecision") or {}).get("label")
    if label is None:
        return None
    return (label,
            tuple(part["evidenceAnswerability"] for part in record.get("questionParts", [])))


def consensus(model_items):
    if set(model_items) != set(MODELS):
        return {"status": "HUMAN_REVIEW", "reason": "missing_model", "axes": {}}
    axes = {}
    for axis in ("grounding", "quality", "abstention"):
        values = {name: decision(item, axis) for name, item in model_items.items()}
        if any(value is None for value in values.values()):
            axes[axis] = {"status": "HUMAN_REVIEW", "reason": "unscored", "byModel": values}
        elif len(set(values.values())) != 1:
            axes[axis] = {"status": "HUMAN_REVIEW", "reason": "disagreement", "byModel": values}
        elif any(item.get("requiresReview") for item in model_items.values()):
            axes[axis] = {"status": "HUMAN_REVIEW", "reason": "model_review", "byModel": values}
        else:
            axes[axis] = {"status": "AGREED", "decision": next(iter(values.values())), "byModel": values}
    status = "AGREED" if all(axis["status"] == "AGREED" for axis in axes.values()) else "HUMAN_REVIEW"
    return {"status": status, "axes": axes}


def apply_regression_gate(row, gold):
    if not gold or row["consensus"]["status"] != "AGREED":
        return row
    representative = next(iter(row["models"].values()))
    observed = {
        "grounding": representative["grounding"]["result"]["overall"],
        "quality": [part["outcome"] for part in representative["quality"]["result"]["groups"]],
        "abstention": representative["abstentionDecision"]["label"],
        "answerability": representative["abstention"]["result"]["evidenceAnswerability"],
    }
    differences = [axis for axis, expected in gold.items() if axis in observed
                   and expected != "NEEDS_HUMAN_REVIEW"
                   and (not isinstance(expected, list) or "NEEDS_HUMAN_REVIEW" not in expected)
                   and observed[axis] != expected]
    if differences:
        row["consensus"].update(status="HUMAN_REVIEW", reason="known_human_disagreement",
                                regressionMismatches=differences)
    return row


def preflight(clients):
    request = {"messages": [{"role": "system", "content": "Return a JSON object with ok true."},
                            {"role": "user", "content": "Connection check."}],
               "response_format": {"json_schema": {"schema": {"type": "object", "properties": {
                   "ok": {"type": "boolean"}}, "required": ["ok"], "additionalProperties": False}}}}
    return {name: client.stage("connection_check", request) for name, client in clients.items()}


def build_jobs(capture, catalog):
    judge.validate_capture(capture)
    enriched = v6.enrich_rag_sources(capture, catalog)
    jobs = []
    for case in enriched["cases"]:
        previous = []
        for index, turn in enumerate(case["turns"]):
            sources, provenance = v6.generation_sources(turn)
            item = {"caseId": case["caseId"], "turnIndex": index, "category": case.get("category"),
                    "suite": case.get("suite", "legacy_capture"), "question": turn["fixture"]["question"],
                    "expectedBehavior": turn["fixture"]["expectedBehavior"],
                    "executionStatus": turn["executionStatus"], "pipelineDurationMs": turn.get("durationMs"),
                    "answer": (turn.get("outputMessage") or {}).get("content"), "sources": sources,
                    "sourceProvenance": provenance, "savedSources": turn.get("savedSources", [])}
            jobs.append((turn, copy.deepcopy(previous), item))
            previous.append({"question": item["question"], "answer": item["answer"] or ""})
    return jobs


def run_identity(capture, catalog, selected_turns, regression_hash):
    return {"captureSha256": judge.sha256(capture), "catalogSha256": judge.sha256(catalog),
            "judgeCodeSha256": judge.sha256(Path(judge.__file__).read_bytes()),
            "runnerCodeSha256": judge.sha256(Path(__file__).read_bytes()),
            "bedrockCodeSha256": judge.sha256(Path(bedrock.__file__).read_bytes()),
            "models": MODELS, "selectedTurns": selected_turns,
            "regressionHumanSha256": regression_hash}


def evaluate(capture, catalog, clients, *, workers=3, prior=None, on_checkpoint=None, limit=0,
             regression_human=None, regression_hash=None):
    jobs = build_jobs(capture, catalog)
    if limit:
        jobs = jobs[:limit]
    identity = run_identity(capture, catalog, len(jobs), regression_hash)
    if prior and prior.get("identity") != identity:
        previous_identity = dict(prior.get("identity", {}))
        current_identity = dict(identity)
        previous_identity.pop("runnerCodeSha256", None)
        current_identity.pop("runnerCodeSha256", None)
        if previous_identity != current_identity:
            raise ValueError("Resume input, judge, model, or rubric changed")
    result = prior or {"schemaVersion": 1, "kind": "V7_BEDROCK_CROSS_JUDGE",
                       "createdAt": datetime.now(timezone.utc).isoformat(), "identity": identity,
                       "datasetScope": "frozen_V6_challenge_set_not_live_user_distribution", "turns": []}
    existing = {(row["caseId"], row["turnIndex"]): row for row in result["turns"]}
    positions = {(row["caseId"], row["turnIndex"]): index
                 for index, row in enumerate(result["turns"])}
    labels = {(item["caseId"], item["turnIndex"]): item for item in (regression_human or {}).values()}
    lock = threading.Lock()
    completed = [0]

    def run(job):
        turn, previous, base = job
        key = (base["caseId"], base["turnIndex"])
        row = copy.deepcopy(existing[key]) if key in existing else {**base, "models": {}}
        if turn["executionStatus"] == "COMPLETED" and turn.get("outputMessage"):
            for name, client in clients.items():
                if row.get("models", {}).get(name, {}).get("judgeStatus") == "SCORED":
                    continue
                item = copy.deepcopy(base)
                grounding_data = judge.prompt_data(turn, previous, "grounding")
                grounding_data["sources"] = base["sources"]
                item["grounding"] = client.grounding(grounding_data)
                item["quality"] = client.quality(turn, previous)
                abstention_data = judge.prompt_data(turn, previous, "adequacy")
                abstention_data["sources"] = base["sources"]
                abstention_data["questionParts"] = [group["questionPart"] for group in
                                                   turn["fixture"].get("qualityReferenceGroups", [])]
                item["abstention"] = client.abstention(abstention_data)
                row["models"][name] = v6.finalize_turn(turn, item)
                row["consensus"] = consensus(row["models"])
                apply_regression_gate(row, labels.get(key))
        else:
            row["consensus"] = {"status": "NOT_APPLICABLE", "reason": "pipeline_execution_failed"}
        with lock:
            if key in positions:
                result["turns"][positions[key]] = row
            else:
                positions[key] = len(result["turns"])
                result["turns"].append(row)
            existing[key] = row
            completed[0] += 1
            if on_checkpoint and completed[0] % 10 == 0:
                on_checkpoint(result)
        return row

    with ThreadPoolExecutor(max_workers=workers) as pool:
        futures = [pool.submit(run, job) for job in jobs]
        for future in as_completed(futures):
            future.result()
    order = {(base["caseId"], base["turnIndex"]): i for i, (_, _, base) in enumerate(jobs)}
    result["turns"].sort(key=lambda row: order[(row["caseId"], row["turnIndex"])])
    result["completedAt"] = datetime.now(timezone.utc).isoformat()
    result["summary"] = {
        "turns": len(result["turns"]),
        "agreed": sum(row["consensus"]["status"] == "AGREED" for row in result["turns"]),
        "humanReview": sum(row["consensus"]["status"] == "HUMAN_REVIEW" for row in result["turns"]),
        "pipelineFailed": sum(row["consensus"]["status"] == "NOT_APPLICABLE" for row in result["turns"]),
        "byModelScored": {name: sum(row.get("models", {}).get(name, {}).get("judgeStatus") == "SCORED"
                                    for row in result["turns"]) for name in MODELS},
    }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", type=Path, default=V7_DIR / "v7-capture.json.gz")
    parser.add_argument("--catalog", type=Path, default=judge.DEFAULT_CATALOG)
    parser.add_argument("--out", type=Path, default=V7_DIR / "v7-bedrock-judged-raw.json.gz")
    parser.add_argument("--profile", default="telme-bedrock-eval")
    parser.add_argument("--workers", type=int, default=3)
    parser.add_argument("--models", nargs="+", choices=tuple(MODELS), default=list(bedrock.DEFAULT_MODELS))
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--preflight-only", action="store_true")
    parser.add_argument("--resume", action="store_true")
    parser.add_argument("--skip-preflight", action="store_true",
                        help="Skip the extra billable connectivity request when resuming a verified run")
    parser.add_argument("--allow-bedrock-call", action="store_true",
                        help="Explicitly authorize billable Bedrock model calls")
    parser.add_argument("--no-regression-gate", action="store_true",
                        help="Only for isolated tests; full V7 scoring must use preserved human labels")
    args = parser.parse_args()
    if args.workers < 1 or args.workers > 8 or args.limit < 0:
        parser.error("workers must be 1..8 and limit nonnegative")
    if args.out.exists() and not args.resume and not args.preflight_only:
        parser.error("Output exists; preserve it or use --resume")
    if not args.allow_bedrock_call:
        parser.error("Bedrock calls are disabled; explicit --allow-bedrock-call is required")
    if args.skip_preflight and (not args.resume or not args.out.exists()):
        parser.error("--skip-preflight is allowed only when resuming an existing result")
    prior = v6.load_json(args.out) if args.resume and args.out.exists() else None
    regression_human = regression_hash = None
    if not args.no_regression_gate:
        v6_dir = V7_DIR.parent / "V6-live-chat-pipeline"
        regression_human, regression_hash = human_review.load_human_review(
            v6_dir / "20261005-heldout40-human-review-lyj.md",
            v6.load_json(v6_dir / "20261005-heldout40-human-review-key.json"))
    capture, catalog = v6.load_json(args.capture), v6.load_json(args.catalog)
    jobs = build_jobs(capture, catalog)
    selected_turns = min(args.limit, len(jobs)) if args.limit else len(jobs)
    identity = run_identity(capture, catalog, selected_turns, regression_hash)
    if prior and prior.get("identity") != identity:
        old_identity = dict(prior.get("identity", {}))
        old_runner_hash = old_identity.pop("runnerCodeSha256", None)
        comparable_identity = {key: value for key, value in identity.items()
                              if key != "runnerCodeSha256"}
        if old_identity != comparable_identity or not old_runner_hash:
            parser.error("Resume inputs, judge, model, or rubric changed; no Bedrock call made")
    clients = {name: BedrockJudge(name, args.profile) for name in args.models}
    preflight_result = None
    if not args.skip_preflight:
        preflight_result = preflight(clients)
        if any("result" not in row or row["result"] != {"ok": True} for row in preflight_result.values()):
            print(json.dumps({name: row.get("error", row.get("result")) for name, row in preflight_result.items()},
                             ensure_ascii=False, indent=2))
            raise SystemExit("Bedrock preflight failed; no batch was run")
        if args.preflight_only:
            print("Selected Bedrock models passed the structured-output preflight")
            return
    result = evaluate(capture, catalog, clients,
                      workers=args.workers, prior=prior, limit=args.limit,
                      regression_human=regression_human, regression_hash=regression_hash,
                      on_checkpoint=lambda value: v6.checkpoint(args.out, value))
    if preflight_result is not None:
        result["preflight"] = preflight_result
    v6.checkpoint(args.out, result)
    print(json.dumps(result["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
