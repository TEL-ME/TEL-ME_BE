#!/usr/bin/env python3

"""이중 벡터(Q_A + QUESTION_ONLY) 오프라인 시뮬레이션: 두 코퍼스의 --dump-json 결과를 합쳐 계산한다"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from check_eval_questions import expected_slots, require_slot_dump

DEFAULT_QA_THRESHOLD = 0.72
DEFAULT_QO_THRESHOLDS = [0.85, 0.87, 0.88, 0.89, 0.90, 0.92, 0.95]
# ChatPipelineProcessor·FaqSearchAnswerProvider가 LLM에 넘기는 검색 결과 수
DEFAULT_SERVICE_TOP_K = 3
POSITIVE_TYPES = ("SIMILAR", "VARIANT", "ANSWER")
GROUPS = (
    ("기존 긍정", lambda i, cov: i["type"] in ("SIMILAR", "VARIANT")),
    ("ANSWER", lambda i, cov: i["type"] == "ANSWER"),
    ("ANSWER 답변에만", lambda i, cov: i["type"] == "ANSWER" and not cov),
    ("무관", lambda i, cov: i["type"] == "UNRELATED"),
    ("경계 무관", lambda i, cov: i.get("unrelated_kind") == "ADJACENT_HARD"),
)


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise SystemExit(f"{path}: 파일을 읽을 수 없습니다: {exc}") from None
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path}: JSON 형식이 아닙니다: {exc}") from None


# 두 점수는 분포가 달라 섞어 정렬할 수 없으므로 우선순위로 합친다. 합친 뒤에도 top_k로 잘라야
# LLM에 넘어가는 개수가 현행과 같아지고 Recall@top_k로 비교할 수 있다
def merge(qa: list[dict], qo: list[dict], qa_threshold: float, qo_threshold: float | None,
          top_k: int, order: str = "qo") -> list[str]:
    a = [r["slot_id"] for r in qa[:top_k] if r["score"] >= qa_threshold]
    b = [r["slot_id"] for r in qo[:top_k] if r["score"] >= qo_threshold] if qo_threshold is not None else []
    merged: list[str] = []
    for slot in (b + a if order == "qo" else a + b):
        if slot not in merged:
            merged.append(slot)
    return merged[:top_k]


# 정답 FAQ 중 하나의 질문이 사용자 질문과 가장 가까우면 "FAQ 질문으로도 커버됨".
# QUESTION_ONLY 검색 1위가 곧 FAQ 질문 유사도 1위라 원시 결과만으로 가른다(check_eval_questions.py --live와 같은 분류)
def covered(qo_item: dict) -> bool:
    return bool(qo_item["results"]) and qo_item["results"][0]["slot_id"] in expected_slots(qo_item)


def success(item: dict, served: list[str]) -> bool:
    if item["type"] == "UNRELATED":
        return not served
    return bool(set(served) & expected_slots(item))


# --vector로 수집한 원시 결과에는 수집 벡터가 기록된다(TELME-84). 기록이 있으면 파일 이름보다 이 값을 믿는다
# --vector 없이 수집한 파일은 기록이 null이라 파일 이름 경고(variant_warnings)만 적용된다
def vector_mismatch(dump: dict, expected: str) -> str | None:
    recorded = dump.get("vector")
    if recorded is None or recorded == expected:
        return None
    return f"--vector {recorded}로 수집한 파일입니다(필요: {expected})"


def load_runs(paths: list[Path], expected_vector: str) -> dict[str, dict]:
    items: dict[str, dict] = {}
    for path in paths:
        dump = load_json(path)
        if not isinstance(dump, dict) or not dump.get("items"):
            raise SystemExit(f"{path}: --dump-json으로 만든 파일이 아닙니다")
        mismatch = vector_mismatch(dump, expected_vector)
        if mismatch:
            raise SystemExit(f"{path}: {mismatch} - --qa에는 QA, --qo에는 QUESTION으로 수집한 파일을 넣으세요")
        require_slot_dump(dump, path)
        empty = [i["eval_id"] for i in dump["items"] if not i["results"]]
        if empty:
            # 벡터마다 임계값 설정이 따로라, 풀어야 하는 값도 벡터에 따라 다르다
            setting = ("SEARCH_DUAL_VECTOR_QUESTION_THRESHOLD=0" if expected_vector == "QUESTION"
                       else "SEARCH_SIMILARITY_THRESHOLD=0")
            raise SystemExit(f"{path}: 결과가 빈 문항 {len(empty)}건 (예: {empty[:3]}) - "
                             f"임계값 0({setting})으로 수집한 파일이 아닙니다")
        for item in dump["items"]:
            if item["eval_id"] in items:
                raise SystemExit(f"{path}: eval_id 중복 {item['eval_id']} - 같은 평가셋을 두 번 넣었는지 확인하세요")
            # 무관 통과를 평가셋마다 따로 제한하려고 출처 파일을 남긴다(pick_threshold)
            items[item["eval_id"]] = {**item, "source": path.name}
    return items


def signature(items: dict[str, dict]) -> dict:
    return {e: (i["type"], i.get("unrelated_kind"), tuple(sorted(expected_slots(i))))
            for e, i in items.items()}


def tally(qa: dict, qo: dict, cov: dict, qa_threshold: float, qo_threshold: float | None,
          top_k: int, order: str) -> dict[str, tuple[int, int, set[str]]]:
    """그룹별 (성공 수, 전체 수, 성공 eval_id 집합). "무관 평가셋별"은 출처 파일별 (거부 수, 전체 수)."""
    out = {}
    for name, belongs in GROUPS:
        ids = [e for e, i in qa.items() if belongs(i, cov[e])]
        ok = {e for e in ids
              if success(qa[e], merge(qa[e]["results"], qo[e]["results"], qa_threshold, qo_threshold, top_k, order))}
        out[name] = (len(ok), len(ids), ok)
    by_source: dict[str, list[int]] = {}
    for e, i in qa.items():
        if i["type"] == "UNRELATED":
            counts = by_source.setdefault(i.get("source", ""), [0, 0])
            counts[0] += e in out["무관"][2]
            counts[1] += 1
    out["무관 평가셋별"] = {src: tuple(c) for src, c in by_source.items()}
    return out


# DUAL_VECTOR_VS_RERANKER.md 3.3절 규칙(리랭커 등 다른 구성과 같은 규칙).
# - 무관 거부는 평가셋(입력 파일)마다 현행 이상. 합계만 보면 한 평가셋의 증가가 다른 평가셋의 감소로 가려진다
# - ANSWER 정답 수는 현행 이상. 문항 단위 손실은 고르는 조건이 아니라 결과에 따로 적는다(main의 "놓친 문항")
# - 그중 정답 합계(기존 긍정 + ANSWER)가 최대인 값, 같으면 더 높은 t(무관 쪽으로 안전)
# 보강 평가셋만 넣으면 기존 긍정이나 무관이 0건일 수 있어, 그때는 규칙이 성립하지 않으므로 고르지 않는다
def pick_threshold(base: dict, runs: dict) -> tuple[float | None, str | None]:
    if base["기존 긍정"][1] == 0:
        return None, "SIMILAR/VARIANT 질문이 없습니다. eval_questions_130 원시 결과를 함께 넣으세요"
    if base["무관"][1] == 0:
        return None, "무관 질문이 없어 거부율 조건을 확인할 수 없습니다"
    base_unrelated = base["무관 평가셋별"]
    keep = [t for t, r in runs.items()
            if all(r["무관 평가셋별"][src][0] >= rejected for src, (rejected, _) in base_unrelated.items())
            and r["ANSWER"][0] >= base["ANSWER"][0]]
    if not keep:
        return None, "평가셋마다 현행 거부율을 지키면서 ANSWER 정답 수가 줄지 않는 t가 후보에 없습니다. 더 높은 값을 넣어 보세요"
    return max(keep, key=lambda t: (runs[t]["기존 긍정"][0] + runs[t]["ANSWER"][0], t)), None


# 원시 결과에 코퍼스 구성이 기록되지 않아 --qa와 --qo를 바꿔 넣어도 평가셋 검사는 통과한다.
# 구성 라벨이 생기기 전까지는 파일 이름으로라도 확인한다(EVAL_SET_SUPPLEMENT.md 4.1절 지문 확인이 실제 근거)
def variant_warnings(qa_paths: list[Path], qo_paths: list[Path]) -> list[str]:
    warnings = []
    for path in qa_paths:
        if "QUESTION_ONLY" in path.name or "Q_A" not in path.name:
            warnings.append(f"--qa {path.name}: 파일 이름에 Q_A가 없거나 QUESTION_ONLY가 들어 있습니다")
    for path in qo_paths:
        if "QUESTION_ONLY" not in path.name:
            warnings.append(f"--qo {path.name}: 파일 이름에 QUESTION_ONLY가 없습니다")
    return warnings


def row(label: str, t: dict) -> str:
    return f"| {label} | " + " | ".join(f"{t[n][0]}/{t[n][1]}" for n, _ in GROUPS) + " |"


def self_test() -> int:
    r = lambda *pairs: [{"slot_id": slot, "score": s} for slot, s in pairs]
    qa = r(("a1", 0.80), ("a2", 0.75), ("a3", 0.70))
    qo = r(("b1", 0.90), ("a1", 0.89), ("b3", 0.80))
    unrelated = {"type": "UNRELATED", "expected_slot_id": None}
    checks = [
        (merge(qa, qo, 0.72, None, 3), ["a1", "a2"]),                    # Q_A 단독: 임계값 미만(a3) 제외
        (merge(qa, qo, 0.72, 0.88, 3), ["b1", "a1", "a2"]),              # 질문만 우선, a1 중복 제거
        (merge(qa, qo, 0.72, 0.88, 3, "qa"), ["a1", "a2", "b1"]),        # Q_A 우선
        (merge(qa, qo, 0.72, 0.89, 3), ["b1", "a1", "a2"]),              # 경계값(0.89)은 통과
        (merge(qa, qo, 0.72, 0.895, 3), ["b1", "a1", "a2"]),             # a1은 Q_A 쪽으로 여전히 들어옴
        (merge(qa, r(("b1", 0.9), ("b2", 0.9), ("b3", 0.9)), 0.72, 0.85, 3), ["b1", "b2", "b3"]),  # 3개 컷
        (merge(qa, qo, 0.72, 0.80, 3, "qa"), ["a1", "a2", "b1"]),        # b3도 통과했지만 3개 컷에서 빠짐
        (merge(r(("x", 0.9), ("y", 0.9), ("z", 0.9), ("ok", 0.9)), [], 0.72, None, 3), ["x", "y", "z"]),  # top-k 밖
        (success(unrelated, []), True),
        (success(unrelated, ["b1"]), False),
        (success({"type": "ANSWER", "expected_slot_id": ["a2", "zz"]}, ["b1", "a2"]), True),  # 배열 정답
        (success({"type": "SIMILAR", "expected_slot_id": "a2"}, ["b1", "a2"]), True),  # 문자열 정답(30건 평가셋)
        (covered({"expected_slot_id": ["b1"], "results": qo}), True),
        (covered({"expected_slot_id": ["a1"], "results": qo}), False),
        (covered({"expected_slot_id": "b1", "results": qo}), True),  # 문자열 정답
    ]
    # 최적 t 선택: 무관은 평가셋별 (거부 수, 전체 수)를 따로 본다. 기본은 130건 47/50 + 보강 15/20 = 62/70
    t = lambda pos, unrel=(47, 15), ans=("A1", "A2"): {
        "기존 긍정": pos, "무관": (sum(unrel), 70, set()), "ANSWER": (len(ans), 3, set(ans)),
        "무관 평가셋별": {"130": (unrel[0], 50), "supp": (unrel[1], 20)}}
    base = t((30, 80))
    runs = {0.85: t((40, 80), (47, 13)), 0.88: t((37, 80)), 0.92: t((36, 80))}
    checks += [
        (pick_threshold(base, runs)[0], 0.88),  # 보강 평가셋 무관이 줄어든 0.85는 제외, 남은 값 중 정답 최대
        (pick_threshold(base, {**runs, 0.90: t((37, 80))})[0], 0.90),  # 정답 수가 같으면 높은 t
        (pick_threshold(base, {**runs, 0.87: t((38, 80), (46, 16))})[0], 0.88),  # 합계 62는 같아도 한 평가셋이 줄면 제외
        (pick_threshold(base, {**runs, 0.89: t((36, 80), ans=("A1", "A2", "A3"))})[0], 0.89),  # ANSWER까지 합친 정답 최대
        (pick_threshold(t((0, 0)), runs)[0], None),  # 기존 긍정 0건(보강 평가셋만)
        (pick_threshold({**t((30, 80)), "무관": (0, 0, set())}, runs)[0], None),  # 무관 0건
        (pick_threshold(base, {**runs, 0.88: t((37, 80), ans=("A1",))})[0], 0.92),  # ANSWER 정답 수가 줄면 제외
        (pick_threshold(base, {**runs, 0.88: t((37, 80), ans=("A1", "A3"))})[0], 0.88),  # 하나 잃고 하나 얻으면 수는 같아 통과(놓친 문항은 결과에 따로 적음)
    ]
    checks += [
        (variant_warnings([Path("raw-1150-Q_A.json")], [Path("raw-1150-QUESTION_ONLY.json")]), []),
        (len(variant_warnings([Path("raw-1150-QUESTION_ONLY.json")], [Path("raw-1150-Q_A.json")])), 2),  # 뒤바뀜
        (vector_mismatch({"vector": "QA"}, "QA"), None),
        (vector_mismatch({"vector": None}, "QUESTION"), None),  # --vector 없이 수집한 파일은 이름 경고로만
        (vector_mismatch({"vector": "QA"}, "QUESTION") is not None, True),  # --qo에 QA 결과를 넣음
        (vector_mismatch({"vector": "DUAL"}, "QA") is not None, True),  # 이미 합친 결과
    ]
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
        raise argparse.ArgumentTypeError(f"숫자를 쉼표로 이어 주세요 (예: 0.85,0.88): {text}") from None


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--qa", type=Path, nargs="+", help="Q_A 코퍼스 원시 결과 (여러 평가셋이면 여러 개)")
    ap.add_argument("--qo", type=Path, nargs="+", help="QUESTION_ONLY 코퍼스 원시 결과 (--qa와 같은 평가셋)")
    ap.add_argument("--qa-threshold", type=float, default=DEFAULT_QA_THRESHOLD,
                    help=f"Q_A 쪽 임계값 (기본 {DEFAULT_QA_THRESHOLD})")
    ap.add_argument("--qo-thresholds", type=threshold_list, default=DEFAULT_QO_THRESHOLDS,
                    help="QUESTION_ONLY 쪽 임계값 후보, 쉼표 구분 (기본 "
                         + ",".join(str(t) for t in DEFAULT_QO_THRESHOLDS) + ")")
    ap.add_argument("--order", choices=("qo", "qa"), default="qo", help="합칠 때 먼저 넣을 쪽 (기본 qo)")
    ap.add_argument("--top-k", type=int, default=DEFAULT_SERVICE_TOP_K,
                    help=f"서비스가 LLM에 넘기는 검색 결과 수 (기본 {DEFAULT_SERVICE_TOP_K})")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        return self_test()
    if not args.qa or not args.qo:
        ap.error("--qa와 --qo 원시 결과 경로 필요 (또는 --self-test)")

    for warning in variant_warnings(args.qa, args.qo):
        print(f"주의: {warning} - EVAL_SET_SUPPLEMENT.md 4.1절 지문으로 코퍼스 구성을 확인하세요")
    qa, qo = load_runs(args.qa, "QA"), load_runs(args.qo, "QUESTION")
    if signature(qa) != signature(qo):
        diff = sorted(e for e in set(qa) | set(qo) if signature(qa).get(e) != signature(qo).get(e))
        raise SystemExit(f"--qa와 --qo의 평가셋이 다릅니다 (eval_id·type·정답 slot_id가 다른 문항 {len(diff)}건, "
                         f"예: {diff[:3]}) - 같은 평가셋으로 수집한 파일끼리만 합칠 수 있습니다")
    cov = {e: i["type"] == "ANSWER" and covered(qo[e]) for e, i in qo.items()}

    base = tally(qa, qo, cov, args.qa_threshold, None, args.top_k, args.order)
    answer_ids = [e for e, i in qa.items() if i["type"] == "ANSWER"]
    if answer_ids:
        covered_ids = sorted(e for e in answer_ids if cov[e])
        print(f"ANSWER 참고 분류: FAQ 질문으로도 커버됨 {len(covered_ids)}건 / 답변에만 있음 "
              f"{len(answer_ids) - len(covered_ids)}건")
        print(f"  커버됨: {covered_ids}\n")

    print(f"Q_A ≥ {args.qa_threshold} + QUESTION_ONLY ≥ t, 중복 제거 → {args.order.upper()} 우선 → "
          f"{args.top_k}개 컷 (무관·경계 무관은 거부 수)\n")
    print("| 구성 | " + " | ".join(n for n, _ in GROUPS) + " |")
    print("|---" * (len(GROUPS) + 1) + "|")
    print(row("Q_A 단독 (현행)", base))
    runs = {}
    for t in args.qo_thresholds:
        runs[t] = tally(qa, qo, cov, args.qa_threshold, t, args.top_k, args.order)
        print(row(f"이중 벡터 t = {t}", runs[t]))

    best, reason = pick_threshold(base, runs)
    if best is None:
        print(f"\n최적 t를 고르지 않습니다: {reason}")
        return 0
    b, r = base, runs[best]
    unrelated = ", ".join(f"{src} {b['무관 평가셋별'][src][0]} → {rejected}/{total}"
                          for src, (rejected, total) in r["무관 평가셋별"].items())
    print(f"\n평가셋마다 무관 거부를 현행 이상으로 지키고 ANSWER 정답 수가 줄지 않는 t 중 정답 합계 최대"
          f"(같으면 높은 t): t = {best}")
    print(f"  기존 긍정 {b['기존 긍정'][0]} → {r['기존 긍정'][0]} (Recall@{args.top_k} "
          f"{b['기존 긍정'][0] / b['기존 긍정'][1]:.3f} → {r['기존 긍정'][0] / r['기존 긍정'][1]:.3f}), "
          f"ANSWER {b['ANSWER'][0]} → {r['ANSWER'][0]}")
    print(f"  무관 거부(평가셋별): {unrelated}")
    print(f"  살아나는 긍정: {sorted(r['기존 긍정'][2] - b['기존 긍정'][2])}")
    lost = sorted((b["기존 긍정"][2] | b["ANSWER"][2]) - (r["기존 긍정"][2] | r["ANSWER"][2]))
    print(f"  현행 성공 중 놓친 문항: {lost}")
    below = [t for t in args.qo_thresholds if t < best]
    if below:
        lower = runs[max(below)]
        newly = sorted(b["무관"][2] - lower["무관"][2])
        print(f"  바로 아래 t = {max(below)}에서 새로 통과하는 무관: {newly}")
    print("\n주의: t는 이 평가셋에서 고른 값이다. 실제 구현 후 재측정으로 확정한다")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
