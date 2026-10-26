#!/usr/bin/env python3
"""Judge 검증셋을 만든다. TELME-100 V7의 실제 답변과 사람 판정(판정자 A)에서 단일 질문 답변을 층화해 고르고,
두 번째 판정자(B)가 A와 모델 판정을 보지 않고 채울 판정표를 함께 만든다.

입력은 TELME-100 브랜치의 V7 실험 폴더다(이 브랜치에는 원시 결과를 옮기지 않았다).
사용: python -X utf8 -m scripts.chat_judge.build_judge_validation_set --v7-dir <TELME-100>/docs/chat-judge/experiments/V7-bedrock-cross-judge
"""
import argparse
import gzip
import json
import random
import re
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge import evaluate_chat_pipeline as pipeline

ROOT = Path(__file__).resolve().parents[2]
DATA_OUT = ROOT / "scripts/chat_judge/data/judge_validation_v1.json"
FORM_OUT = ROOT / "docs/03-evaluation/chat-judge/judge-validation-v1-review-form.md"
# 질문과 정답 FAQ만 보고 별도 작성자가 쓴 필수 사실. 답변과 판정은 보지 않았다
REQUIRED_FACTS = ROOT / "scripts/chat_judge/data/validation_sources/required_facts.json"
HUMAN_FILE = "v7-three-model-full-302-human-review-lyj.md"
CAPTURE_FILE = "v7-capture.json.gz"
SEED = 180
# 층마다 뽑을 수. 근거 없는 답, 과도한 거절, 부분 답변처럼 Judge가 틀리기 쉬운 사례를 많이 담는다
STRATA = [
    ("UNSUPPORTED", lambda a: a["grounding"] == "UNSUPPORTED", 40),
    ("OVER_REFUSAL", lambda a: a["abstention"] == "OVER_REFUSAL", 10),
    ("SUPPORTED_PARTIAL_OR_MISSED", lambda a: a["grounding"] == "SUPPORTED" and a["quality"] in {"PARTIAL", "MISSED"}, 25),
    ("SUPPORTED_COMPLETE", lambda a: a["grounding"] == "SUPPORTED" and a["quality"] == "COMPLETE", 20),
    ("NO_CLAIM_REFUSAL", lambda a: a["grounding"] == "NOT_APPLICABLE" and a["abstention"] == "APPROPRIATE", 15),
    ("OTHER", lambda a: True, 10),
]
# 재확인한 12행은 칸 구분자 앞뒤에 공백이 없다
ROW = re.compile(r"^\| \d+ \| (?P<id>[A-Z0-9_-]+) \(turn (?P<turn>\d+)\) \|\s*(?P<grounding>[A-Z_]+)\s*\|"
                 r"\s*(?P<quality>[A-Z_]+)\s*\|\s*(?P<abstention>[A-Z_]+)\s*\|")


def human_labels(path):
    """판정자 A의 302건 표. 재확인 표에서 바뀐 판정은 개별 행에 이미 반영돼 있다."""
    labels = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = ROW.match(line)
        if match:
            labels[(match["id"], int(match["turn"]))] = {
                axis: match[axis] for axis in ("grounding", "quality", "abstention")}
    if len(labels) != 302:
        raise ValueError(f"사람 판정 302건을 읽지 못했습니다: {len(labels)}")
    return labels


def items(capture, labels):
    catalog = json.loads(judge.DEFAULT_CATALOG.read_text(encoding="utf-8"))
    enriched = pipeline.enrich_rag_sources(capture, catalog)
    result = []
    for case in enriched["cases"]:
        previous = []
        for index, turn in enumerate(case["turns"]):
            answer = (turn.get("outputMessage") or {}).get("content") or ""
            groups = pipeline.reference_groups(turn["fixture"])
            label = labels.get((case["caseId"], index))
            # 복합 질문은 사람(답변 전체)과 모델(하위 질문별)의 충족도 단위가 달라 제외한다
            if label and answer and len(groups) <= 1:
                sources, provenance = pipeline.generation_sources(turn)
                result.append({
                    "id": f"{case['caseId']}:{index}",
                    "category": case.get("category"),
                    "question": turn["fixture"]["question"],
                    "previousTurns": copy_previous(previous),
                    "answer": answer,
                    "messageType": (turn.get("outputMessage") or {}).get("messageType"),
                    "expectedBehavior": turn["fixture"]["expectedBehavior"],
                    "deliveredSources": [{"sourceId": row["sourceId"], "answer": row["answer"]} for row in sources],
                    "sourceProvenance": provenance,
                    "referenceGroups": groups,
                    "labels": {"A": label},
                })
            previous.append({"question": turn["fixture"]["question"], "answer": answer})
    return result


def copy_previous(previous):
    return [dict(turn) for turn in previous]


def select(candidates):
    rng = random.Random(SEED)
    chosen, used = [], set()
    for name, condition, count in STRATA:
        pool = [item for item in candidates if item["id"] not in used and condition(item["labels"]["A"])]
        rng.shuffle(pool)
        for item in pool[:count]:
            chosen.append({**item, "stratum": name})
            used.add(item["id"])
    return sorted(chosen, key=lambda item: item["id"])


def review_form(selected):
    lines = ["# Judge 검증셋 v1 판정표 (판정자 B)", "",
             "답변과 실제로 생성에 전달된 FAQ 답변만 보고 판정한다. 다른 판정자나 모델의 판정은 보지 않는다.",
             "판정 기준은 [평가 기준](chat-pipeline-quality-criteria.md) 2절의 세 축을 따른다. FAQ 질문 문구는 정책 근거가 아니다.", "",
             "- 근거성: `SUPPORTED` / `UNSUPPORTED` / `NOT_APPLICABLE`(사실 주장 없음) / `REVIEW`",
             "- 충족도: `COMPLETE` / `PARTIAL` / `MISSED` / `NOT_APPLICABLE`(범위 밖) / `REVIEW` — 정답 FAQ와 비교",
             "- 보류: `NOT_APPLICABLE` / `SHOULD_ABSTAIN` / `OVER_REFUSAL` / `APPROPRIATE` / `REVIEW`", ""]
    for number, item in enumerate(selected, 1):
        lines += [f"## {number}. `{item['id']}`", "", f"- 질문: {item['question']}"]
        for turn in item["previousTurns"]:
            lines.append(f"- 이전 질문: {turn['question']}")
        lines += ["- 답변:", "", "  > " + item["answer"].replace("\n", "\n  > "), "", "- 생성에 전달된 FAQ 답변:"]
        lines += [f"  - `{source['sourceId']}`: {source['answer']}" for source in item["deliveredSources"]] or ["  - 없음"]
        lines.append("- 정답 FAQ 답변(충족도 판정용):")
        lines += [f"  - `{alternative['sourceId']}`: {alternative['answer']}"
                  for group in item["referenceGroups"] for alternative in group["alternatives"]] or ["  - 없음"]
        lines += ["- 근거성: ", "- 충족도: ", "- 보류: ", "- 메모: ", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--v7-dir", type=Path, required=True)
    args = parser.parse_args()
    labels = human_labels(args.v7_dir / HUMAN_FILE)
    with gzip.open(args.v7_dir / CAPTURE_FILE, "rt", encoding="utf-8") as stream:
        capture = json.load(stream)
    selected = select(items(capture, labels))
    facts = {row["id"]: row["requiredFacts"] for row in json.loads(REQUIRED_FACTS.read_text(encoding="utf-8"))}         if REQUIRED_FACTS.exists() else {}
    for item in selected:
        item["requiredFacts"] = facts.get(item["id"], [])
    data = {"version": 1, "seed": SEED,
            "source": {"humanFile": HUMAN_FILE, "humanSha256": judge.sha256((args.v7_dir / HUMAN_FILE).read_bytes()),
                       "captureFile": CAPTURE_FILE, "captureSha256": judge.sha256(capture),
                       "telme100Commit": "ae637974770f03fa8620c6b5540877becf1ff0c5"},
            "annotators": {"A": "TELME-100 V7 302건 판정(이용재)", "B": "미작성"},
            "strata": {name: sum(item["stratum"] == name for item in selected) for name, _, _ in STRATA},
            "items": selected}
    DATA_OUT.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    FORM_OUT.write_text(review_form(selected) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(data["strata"], ensure_ascii=False), len(selected))


if __name__ == "__main__":
    main()
