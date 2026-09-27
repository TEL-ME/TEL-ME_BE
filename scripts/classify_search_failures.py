#!/usr/bin/env python3

"""검색 실패를 질문 쪽 / 문서 쪽으로 분류: measure_search_quality.py --dump-json 결과를 읽는다"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path

from check_duplicates import DEFAULT_BATCH, cosine, embed_all
from check_eval_questions import content_hash

DEFAULT_THRESHOLD = 0.72
# ChatPipelineProcessor·FaqSearchAnswerProvider가 LLM에 넘기는 검색 결과 수
DEFAULT_SERVICE_TOP_K = 3
DEFAULT_FAQ = Path(__file__).parent / "data" / "faq_full_1150.json"
GROUPS = ("A", "B", "C", "D")
GROUP_LABEL = {
    "A": "정상 통과",
    "B": "1등 정답, 점수 미달",
    "C": "1등부터 오답, 점수 미달",
    "D": "1등 오답, 점수 통과",
}
CAUSES = ("정상", "문서: 답변 섞여 점수 희석", "문서: 비슷한 FAQ에 밀림", "질문: 표현이 FAQ 원문과 멂")


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise SystemExit(f"{path}: 파일을 읽을 수 없습니다: {exc}") from None
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path}: JSON 형식이 아닙니다: {exc}") from None


# 그룹은 1등만 본다(top-1 분석). 서비스가 실제로 정답을 넘겼는지는 delivered()로 따로 센다
def group_of(item: dict, threshold: float) -> str:
    wanted = set(item["expected_content_hash"])
    top = item["results"][0]
    correct = top["content_hash"] in wanted
    passed = top["score"] >= threshold
    if correct:
        return "A" if passed else "B"
    return "D" if passed else "C"


# PgvectorFaqSearchService와 같은 규칙: 점수순으로 보다가 임계값 미만이 나오면 거기서 끊는다
def delivered(item: dict, top_k: int, threshold: float) -> bool:
    wanted = set(item["expected_content_hash"])
    for result in item["results"][:top_k]:
        if result["score"] < threshold:
            return False
        if result["content_hash"] in wanted:
            return True
    return False


# qq: 사용자 질문과 정답 FAQ 질문 문장의 유사도 — 검색 방식을 바꿔도 변하지 않는 문항 고유값
# cutoff 이상인데 실패했다면 질문은 성공 사례만큼 가까웠다는 뜻이라 문서 쪽으로 추정한다
def cause_of(group: str, qq: float, cutoff: float) -> str:
    if group == "A":
        return "정상"
    if qq >= cutoff:
        return "문서: 답변 섞여 점수 희석" if group == "B" else "문서: 비슷한 FAQ에 밀림"
    return "질문: 표현이 FAQ 원문과 멂"


def signature(dump: dict) -> dict:
    return {i["eval_id"]: (i["type"], tuple(sorted(i["expected_content_hash"] or []))) for i in dump["items"]}


def analyze(dump: dict, eval_by_id: dict, faq_by_hash: dict, threshold: float, top_k: int,
            batch: int, cache: Path | None) -> list[dict]:
    cases = []
    for item in dump["items"]:
        if item["type"] not in ("SIMILAR", "VARIANT"):
            continue
        missing = [h for h in item["expected_content_hash"] if h not in faq_by_hash]
        if missing:
            raise SystemExit(f"{item['eval_id']}: 정답 해시가 FAQ 파일에 없습니다 ({missing[0][:12]}...)")
        top = item["results"][0]
        cases.append({
            "eval_id": item["eval_id"],
            "type": item["type"],
            "question": eval_by_id[item["eval_id"]]["question"],
            "expected": item["expected_content_hash"],
            "group": group_of(item, threshold),
            "delivered": delivered(item, top_k, threshold),
            "top1_score": top["score"],
            "top1_category": faq_by_hash.get(top["content_hash"], {}).get("category"),
            "correct_rank": next((r["rank"] for r in item["results"]
                                  if r["content_hash"] in set(item["expected_content_hash"])), None),
        })

    faq_hashes = sorted({h for c in cases for h in c["expected"]})
    vectors = embed_all([c["question"] for c in cases] + [faq_by_hash[h]["question"] for h in faq_hashes],
                        batch, cache)
    faq_vec = dict(zip(faq_hashes, vectors[len(cases):]))
    for case, vec in zip(cases, vectors):
        case["qq"] = max(cosine(vec, faq_vec[h]) for h in case["expected"])
        case["correct_category"] = faq_by_hash[case["expected"][0]]["category"]
    return cases


def tag(case: dict) -> str:
    return "D, 정답 포함" if case["group"] == "D" and case["delivered"] else case["group"]


def report(name: str, cases: list[dict], threshold: float, top_k: int, cutoff: float, show_list: bool) -> None:
    fails = [c for c in cases if c["group"] != "A"]
    groups = Counter(c["group"] for c in cases)

    print(f"\n## {name} — 긍정 {len(cases)}건, top-1 기준 비정상 {len(fails)}건 "
          f"(임계값 {threshold}, 기준선 {cutoff:.4f})\n")
    print("| 그룹 | 뜻 | 건수 |")
    print("| --- | --- | --- |")
    for g in GROUPS:
        print(f"| {g} | {GROUP_LABEL[g]} | {groups[g]} |")

    ok = sum(c["delivered"] for c in cases)
    d_ok = sum(c["delivered"] for c in cases if c["group"] == "D")
    print(f"\n- 서비스 기준(top-{top_k}, 임계값 통과): 정답 전달 {ok}건 (1위 {groups['A']} + 2–{top_k}위 {d_ok}), "
          f"실패 {len(cases) - ok}건 (결과 없음 {groups['B'] + groups['C']} + 정답 없이 오답만 {groups['D'] - d_ok})")

    print("\n| 추정 원인 | 건수 | SIMILAR | VARIANT |")
    print("| --- | --- | --- | --- |")
    for cause in CAUSES[1:]:
        subset = [c for c in fails if c["cause"] == cause]
        types = Counter(c["type"] for c in subset)
        print(f"| {cause} | {len(subset)} | {types['SIMILAR']} | {types['VARIANT']} |")

    pushed = [c for c in fails if c["cause"] == "문서: 비슷한 FAQ에 밀림"]
    if pushed:
        same_cat = sum(c["top1_category"] == c["correct_category"] for c in pushed)
        in_topk = sum(c["correct_rank"] is not None for c in pushed)
        in_service = sum(c["delivered"] for c in pushed)
        print(f"\n- 밀림 {len(pushed)}건 중 경쟁 FAQ가 같은 카테고리 {same_cat}건, 정답이 수집 top-k 안 {in_topk}건"
              f"(그중 서비스 top-{top_k}로 전달 {in_service}건)")
    near = [c["eval_id"] for c in fails if abs(c["qq"] - cutoff) < 0.02]
    print(f"- 기준선 ±0.02 안의 경계 문항 {len(near)}건 {near}")

    if show_list:
        print("\n| eval_id | 유형 | 그룹 | 정답 전달 | 질문-질문 | 추정 원인 | 질문 |")
        print("| --- | --- | --- | --- | --- | --- | --- |")
        for c in sorted(fails, key=lambda c: -c["qq"]):
            print(f"| {c['eval_id']} | {c['type']} | {c['group']} | {'O' if c['delivered'] else 'X'} | "
                  f"{c['qq']:.3f} | {c['cause']} | {c['question']} |")


# 초안: 하이브리드가 아직 없어 개선 결과로는 검증하지 않았다. QUESTION_ONLY 결과로 5절 수치 재현까지만 확인
def compare(names: list[str], runs: list[list[dict]], top_k: int) -> None:
    print("\n## 비교 (같은 기준선)\n")
    print("| 항목 | " + " | ".join(names) + " |")
    print("| --- |" + " --- |" * len(names))
    for g in GROUPS:
        counts = [sum(c["group"] == g for c in run) for run in runs]
        print(f"| {g} {GROUP_LABEL[g]} | " + " | ".join(str(n) for n in counts) + " |")
    counts = [sum(c["delivered"] for c in run) for run in runs]
    print(f"| 서비스 top-{top_k} 정답 전달 | " + " | ".join(str(n) for n in counts) + " |")
    for cause in CAUSES[1:]:  # "정상"은 A와 같은 수라 생략
        counts = [sum(c["cause"] == cause for c in run) for run in runs]
        print(f"| {cause} | " + " | ".join(str(n) for n in counts) + " |")

    # 추정 원인이 그대로여도 그룹(C → D)이나 정답 전달 여부가 바뀔 수 있어 셋 다 본다
    key = lambda c: (c["group"], c["cause"], c["delivered"])
    first, last = {c["eval_id"]: c for c in runs[0]}, {c["eval_id"]: c for c in runs[-1]}
    moved = [e for e in first if e in last and key(first[e]) != key(last[e])]
    print(f"\n그룹·추정 원인·정답 전달이 바뀐 문항 {len(moved)}건 ({names[0]} → {names[-1]})")
    for eval_id in moved:
        before, after = first[eval_id], last[eval_id]
        print(f"  {eval_id}: [{tag(before)}] {before['cause']} → [{tag(after)}] {after['cause']}")


def self_test() -> int:
    item = lambda top_hash, score: {"expected_content_hash": ["ok"],
                                    "results": [{"content_hash": top_hash, "score": score}]}
    ranked = lambda *pairs: {"expected_content_hash": ["ok"],
                             "results": [{"content_hash": h, "score": s} for h, s in pairs]}
    checks = [
        (group_of(item("ok", 0.80), 0.72), "A"),
        (group_of(item("ok", 0.70), 0.72), "B"),
        (group_of(item("no", 0.70), 0.72), "C"),
        (group_of(item("no", 0.80), 0.72), "D"),
        (group_of(item("ok", 0.72), 0.72), "A"),  # 경계값은 통과
        (cause_of("A", 0.10, 0.8), "정상"),
        (cause_of("B", 0.90, 0.8), "문서: 답변 섞여 점수 희석"),
        (cause_of("D", 0.90, 0.8), "문서: 비슷한 FAQ에 밀림"),
        (cause_of("C", 0.90, 0.8), "문서: 비슷한 FAQ에 밀림"),
        (cause_of("B", 0.79, 0.8), "질문: 표현이 FAQ 원문과 멂"),
        (cause_of("D", 0.80, 0.8), "문서: 비슷한 FAQ에 밀림"),  # 기준선과 같으면 문서 쪽
        (delivered(ranked(("no", 0.80), ("ok", 0.75)), 3, 0.72), True),  # D지만 2위 정답이 전달됨
        (delivered(ranked(("no", 0.80), ("ok", 0.70)), 3, 0.72), False),  # 2위 정답이 임계값 미달
        (delivered(ranked(("n1", 0.9), ("n2", 0.9), ("n3", 0.9), ("ok", 0.9)), 3, 0.72), False),  # top-k 밖
    ]
    for invalid in ("0", "11"):
        try:
            service_top_k(invalid)
            checks.append((f"top-k {invalid} 통과", "거부"))
        except argparse.ArgumentTypeError:
            checks.append(("거부", "거부"))
    failed = [(i, got, want) for i, (got, want) in enumerate(checks) if got != want]
    for i, got, want in failed:
        print(f"  FAIL #{i}: {got!r} (기대 {want!r})")
    print(f"자기 검증 {len(checks) - len(failed)}/{len(checks)} 통과")
    return 1 if failed else 0


# 공백 구분 여러 값으로 받으면 뒤따르는 파일 경로까지 삼키므로 쉼표 한 덩어리로 받는다
def threshold_list(text: str) -> list[float]:
    try:
        return [float(v) for v in text.split(",")]
    except ValueError:
        raise argparse.ArgumentTypeError(f"숫자를 쉼표로 이어 주세요 (예: 0.72,0.80): {text}") from None


def service_top_k(text: str) -> int:
    try:
        value = int(text)
    except ValueError:
        raise argparse.ArgumentTypeError(f"--top-k는 1~10 정수여야 합니다: {text}") from None
    if not 1 <= value <= 10:
        raise argparse.ArgumentTypeError(f"--top-k는 1~10이어야 합니다: {value}")
    return value


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("paths", nargs="*", type=Path, help="raw-*.json. 여러 개면 첫 파일 기준선으로 비교(초안)")
    ap.add_argument("--eval", type=Path, help="평가셋 JSON (기본: 원시 결과에 기록된 경로)")
    ap.add_argument("--faq", type=Path, default=DEFAULT_FAQ, help="정답 FAQ 질문을 찾을 FAQ JSON")
    ap.add_argument("--threshold", type=threshold_list, default=[DEFAULT_THRESHOLD],
                    help=f"성공/실패를 가르는 검색 임계값 (기본 {DEFAULT_THRESHOLD}). "
                         "하나면 모든 파일에, 쉼표로 여러 개(예: 0.72,0.80)면 파일 순서대로 적용")
    ap.add_argument("--top-k", type=service_top_k, default=DEFAULT_SERVICE_TOP_K,
                    help=f"서비스가 LLM에 넘기는 검색 결과 수 (기본 {DEFAULT_SERVICE_TOP_K})")
    ap.add_argument("--cutoff", type=float,
                    help="질문/문서를 가르는 질문-질문 유사도. 생략하면 첫 파일의 정상 통과 문항 최솟값")
    ap.add_argument("--list", action="store_true", help="실패 문항 전체 목록 출력")
    ap.add_argument("--batch", type=int, default=DEFAULT_BATCH)
    ap.add_argument("--cache", default=str(Path(__file__).parent / "data" / ".embed_cache.json"),
                    help="임베딩 캐시 경로 (--cache '' 로 비활성)")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        return self_test()
    if not args.paths:
        ap.error("원시 결과 JSON 경로 필요 (또는 --self-test)")

    # 점수 분포가 다른 검색 방식끼리 같은 임계값을 쓰면 비교가 기운다 — 각자 거부율을 맞춘 값을 준다
    thresholds = args.threshold * len(args.paths) if len(args.threshold) == 1 else args.threshold
    if len(thresholds) != len(args.paths):
        ap.error(f"--threshold는 1개이거나 파일 수({len(args.paths)})만큼 줘야 합니다")

    cache = Path(args.cache) if args.cache else None
    faq_by_hash = {content_hash(f["question"], f["answer"]): f for f in load_json(args.faq)}

    names, runs, base = [], [], None
    for path, threshold in zip(args.paths, thresholds):
        dump = load_json(path)
        if not isinstance(dump, dict) or not dump.get("items"):
            raise SystemExit(f"{path}: --dump-json으로 만든 파일이 아닙니다")
        empty = [i["eval_id"] for i in dump["items"] if not i["results"]]
        if empty:
            raise SystemExit(f"{path}: 결과가 빈 문항 {len(empty)}건 (예: {empty[:3]}) - "
                             "임계값 0(SEARCH_SIMILARITY_THRESHOLD=0)으로 수집한 파일이 아닙니다")
        sig = signature(dump)
        if base is None:
            base = (path, sig)
        elif sig != base[1]:
            diff = sorted(e for e in set(sig) | set(base[1]) if sig.get(e) != base[1].get(e))
            raise SystemExit(f"{path}: {base[0]}와 평가셋이 다릅니다 (eval_id·type·정답 해시가 다른 문항 "
                             f"{len(diff)}건, 예: {diff[:3]}) - 같은 평가셋으로 수집한 파일끼리만 비교할 수 있습니다")
        eval_path = args.eval or Path(dump["path"])
        eval_by_id = {e["eval_id"]: e for e in load_json(eval_path)}
        names.append(path.stem.replace("raw-", ""))
        runs.append(analyze(dump, eval_by_id, faq_by_hash, threshold, args.top_k, args.batch, cache))

    passed = [c["qq"] for c in runs[0] if c["group"] == "A"]
    if args.cutoff is None and not passed:
        raise SystemExit("정상 통과 문항이 없어 기준선을 정할 수 없습니다. --cutoff를 지정하세요")
    cutoff = args.cutoff if args.cutoff is not None else min(passed)

    for name, run, threshold in zip(names, runs, thresholds):
        for case in run:
            case["cause"] = cause_of(case["group"], case["qq"], cutoff)
        report(name, run, threshold, args.top_k, cutoff, args.list)
    if len(runs) > 1:
        compare([f"{n} ({t})" for n, t in zip(names, thresholds)], runs, args.top_k)
        if len(set(thresholds)) == 1:
            print("\n주의: 모든 파일에 같은 임계값을 썼습니다. 검색 방식이 다르면 점수 분포도 달라서, "
                  "무관 거부율을 맞춘 임계값을 파일마다 따로 주세요")
    if args.cutoff is None:
        print(f"\n기준선 {cutoff:.4f}는 {names[0]}에서 계산했습니다. 이후 비교할 때는 --cutoff {cutoff:.4f}로 고정하세요")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
