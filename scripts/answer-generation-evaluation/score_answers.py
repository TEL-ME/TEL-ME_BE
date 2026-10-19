"""AnswerGenerationProbe 결과(jsonl 또는 jsonl.gz)를 모델별, 평가셋별로 집계한다. 모델을 부르지 않는다.

사용: python3 -I scripts/answer-generation-evaluation/score_answers.py <결과...>
A와 B는 입력이 달라 따로 집계한다(A: answers-*.jsonl.gz, B: answers-b-*.jsonl.gz).
채점이 아니라 가드를 거친 결과의 객관 지표(가드 처리, 근거 없음 비율, 잘림, 토큰, 반복 일치)만 센다.
"""

import gzip
import json
import statistics
import sys
from collections import Counter, defaultdict

# USD / 100만 토큰 (입력, 출력). AWS 요금표 Sydney 표기값이며 실제 청구액과 다를 수 있다
GPT_OSS_PRICE = (0.1545, 0.618)


def load(paths):
    rows = []
    for path in paths:
        opener = gzip.open if path.endswith(".gz") else open
        with opener(path, "rt", encoding="utf-8") as stream:
            rows.extend(json.loads(line) for line in stream if line.strip())
    return rows


def share(counter, key, total):
    return f"{counter[key] / total:.0%}"


def line(label, rows):
    total = len(rows)
    guard = Counter(r.get("guardOutcome") for r in rows)
    basis = Counter(r.get("answerBasis") for r in rows)
    lengths = [len(r.get("answer") or "") for r in rows]
    suspected = sum(1 for r in rows if r.get("truncationSuspected"))
    print(f"  {label:8s} {total:4d}건 | 가드 KEPT {share(guard, 'KEPT', total)} MODIFIED {share(guard, 'MODIFIED', total)} "
          f"REPLACED {share(guard, 'REPLACED', total)} | 근거없음 {share(basis, 'NO_EVIDENCE', total)} | "
          f"잘림의심 {suspected} | 최종 답변 길이 중앙값 {int(statistics.median(lengths))}자")


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    rows = load(sys.argv[1:])
    models = sorted({r["model"] for r in rows})
    # B는 입력이 모두 set=B라서 묶음(group)별로 나눠 센다
    def bucket(r):
        return f"B:{r['meta']['group']}" if r["meta"]["set"] == "B" else r["meta"]["set"]

    sets = sorted({bucket(r) for r in rows})
    errors = Counter(r["model"] for r in rows if r.get("error"))
    for name in sets:
        print(f"[{name}]")
        for model in models:
            line(model, [r for r in rows if r["model"] == model and bucket(r) == name])
    print("[전체]")
    for model in models:
        line(model, [r for r in rows if r["model"] == model])
    print(f"오류 행: {dict(errors) or 0}")
    for model in models:
        calls = [c for r in rows if r["model"] == model for c in r.get("calls", [])]
        if not calls or "stopReason" not in calls[0]:
            continue
        prompt = sum(c.get("promptTokens") or 0 for c in calls)
        output = sum(c.get("outputTokens") or 0 for c in calls)
        cost = prompt / 1e6 * GPT_OSS_PRICE[0] + output / 1e6 * GPT_OSS_PRICE[1]
        stops = Counter(c.get("stopReason") for c in calls)
        times = sorted(c["ms"] for c in calls)
        print(f"[{model} 호출] {len(calls)}건 실패 {sum('error' in c for c in calls)} 종료사유 {dict(stops)} | "
              f"입력 {prompt:,} 출력 {output:,} 토큰 약 ${cost:.4f} | 출력토큰 중앙값 "
              f"{int(statistics.median(c['outputTokens'] for c in calls))} 최대 {max(c['outputTokens'] for c in calls)} "
              f"(한도 {sorted({c['maxTokens'] for c in calls})}) | 응답시간 중앙값 {times[len(times) // 2]}ms 최대 {times[-1]}ms")
        runs = defaultdict(list)
        for r in rows:
            if r["model"] == model:
                runs[r["id"]].append(r)
        repeated = {k: v for k, v in runs.items() if len(v) > 1}
        if repeated:
            same_basis = sum(1 for v in repeated.values() if len({x.get("answerBasis") for x in v}) == 1)
            same_text = sum(1 for v in repeated.values() if len({x.get("answer") for x in v}) == 1)
            print(f"  같은 입력 반복({len(next(iter(repeated.values())))}회): 근거 여부 일치 {same_basis}/{len(repeated)}, "
                  f"최종 답변 문자열까지 일치 {same_text}/{len(repeated)}")


if __name__ == "__main__":
    main()
