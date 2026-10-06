"""Compare labels on identical frozen answers; never interpret changes as accuracy gains alone."""

import argparse
from collections import Counter
from pathlib import Path

from scripts.chat_judge.experiments.v6_live_chat_pipeline.evaluate_chat_pipeline import load_json
from scripts.chat_judge.experiments.v7_cross_judge.build_v7_capture import V6_CAPTURE, V7_DIR


BASELINE = V6_CAPTURE.parent / "20261002-service-pipeline-judged-raw.json.gz"


def report(baseline, candidate):
    old = {(row["caseId"], row["turnIndex"]): row for row in baseline["turns"]}
    new = {(row["caseId"], row["turnIndex"]): row for row in candidate["turns"]}
    if not set(new) <= set(old):
        raise ValueError("V7 contains a turn absent from V6")
    if any(old[key]["answer"] != row["answer"] or old[key]["executionStatus"] != row["executionStatus"]
           for key, row in new.items()):
        raise ValueError("V6 and V7 do not use the same generated answers")
    lines = ["# V6와 V7의 동일 답변 재채점 비교", "",
             f"- 동일한 V6 답변 확인: {len(new)}/{len(old)}건",
             "- V7은 평가 기준과 판정 모델을 바꾼 실험이다. 라벨 변화 자체는 정확도 향상의 증거가 아니다.",
             "- 기존 사람 판정 40건과 신규 블라인드 40건을 따로 확인해야 한다.", ""]
    for model in ("qwen", "sonnet", "luna"):
        selected = {key: row["models"][model] for key, row in new.items() if model in row.get("models", {})}
        if not selected:
            lines += [f"## {model}", "", "모델 판정 없음.", ""]
            continue
        transitions = Counter()
        for key, row in selected.items():
            before = (old[key].get("grounding", {}).get("result") or {}).get("overall", "UNSCORED")
            after = (row.get("grounding", {}).get("result") or {}).get("overall", "UNSCORED")
            transitions[(before, after)] += 1
        lines += [f"## {model}", "",
                  f"- 판정된 답변: {len(selected)}건",
                  f"- 세 축 모두 채점: {sum(row.get('judgeStatus') == 'SCORED' for row in selected)}건",
                  "", "| V6 근거성 | V7 근거성 | 건수 |", "| --- | --- | ---: |"]
        lines.extend(f"| {before} | {after} | {count} |" for (before, after), count in sorted(transitions.items()))
        lines.append("")
    counts = Counter(row.get("consensus", {}).get("status", "UNKNOWN") for row in new.values())
    lines += ["## 교차 판정 상태", ""]
    lines.extend(f"- {name}: {count}건" for name, count in sorted(counts.items()))
    lines += ["", "세 모델 중 하나라도 빠진 경우 `HUMAN_REVIEW`이며 자동 확정 결과로 집계하지 않는다.", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=BASELINE)
    parser.add_argument("--judge", type=Path, default=V7_DIR / "v7-bedrock-judged-raw.json.gz")
    parser.add_argument("--out", type=Path, default=V7_DIR / "v7-vs-v6.md")
    args = parser.parse_args()
    args.out.write_text(report(load_json(args.baseline), load_json(args.judge)), encoding="utf-8", newline="\n")
    print(args.out)


if __name__ == "__main__":
    main()
