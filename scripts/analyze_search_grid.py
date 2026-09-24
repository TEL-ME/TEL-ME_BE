#!/usr/bin/env python3

"""임베딩 구성 × top-k × 임계값 격자 분석: measure_search_quality.py --dump-json 결과만 읽는다"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

DEFAULT_TOP_KS = (1, 3, 5, 10)
DEFAULT_THRESHOLDS = tuple(round(0.55 + 0.01 * i, 2) for i in range(31))  # 0.55 ~ 0.85
DEFAULT_MIN_REJECTION = 0.95


def load(path: Path) -> dict:
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise SystemExit(f"{path}: 파일을 읽을 수 없습니다: {exc}") from None
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path}: JSON 형식이 아닙니다: {exc}") from None
    if not isinstance(raw, dict) or not raw.get("items"):
        raise SystemExit(f"{path}: --dump-json으로 만든 파일이 아닙니다")
    return raw


def score_of(item: dict, top_k: int, threshold: float) -> float | None:
    """정답이 top_k 안에 있고 임계값을 통과하면 그 score, 못 찾으면 None."""
    expected = item["expected_content_hash"]
    wanted = set(expected if isinstance(expected, list) else [expected])
    for result in item["results"]:
        rank, score = result["rank"], result["score"]
        if rank is None or rank > top_k:
            continue
        # 결과는 score 내림차순이라 정답이 임계값을 넘으면 앞 순위도 전부 넘는다
        if result["content_hash"] in wanted:
            return score if score is not None and score >= threshold else None
    return None


def rejected(item: dict, threshold: float) -> bool:
    """무관 질문이 아무것도 반환하지 않으면(= top-1이 임계값 미달) 거부 성공."""
    top = next((r["score"] for r in item["results"] if r["rank"] == 1), None)
    return top is None or top < threshold


def evaluate(items: list[dict], top_k: int, threshold: float, k_values=(1, 3, 5)) -> dict:
    positives = [i for i in items if i["type"] != "UNRELATED"]
    negatives = [i for i in items if i["type"] == "UNRELATED"]
    ranks = []
    for item in positives:
        hit = score_of(item, top_k, threshold)
        if hit is None:
            ranks.append(None)
            continue
        expected = item["expected_content_hash"]
        wanted = set(expected if isinstance(expected, list) else [expected])
        ranks.append(next(r["rank"] for r in item["results"] if r["content_hash"] in wanted))

    # 측정 대상이 없으면 키를 넣지 않는다
    out = {}
    if positives:
        for k in k_values:
            if k > top_k:
                continue
            out[f"recall@{k}"] = sum(1 for r in ranks if r is not None and r <= k) / len(positives)
        out["mrr"] = sum(1 / r if r else 0.0 for r in ranks) / len(positives)

    if negatives:
        out["rejection"] = sum(1 for i in negatives if rejected(i, threshold)) / len(negatives)
        for kind in sorted({i.get("unrelated_kind") for i in negatives if i.get("unrelated_kind")}):
            group = [i for i in negatives if i.get("unrelated_kind") == kind]
            out[f"rejection:{kind}"] = sum(1 for i in group if rejected(i, threshold)) / len(group)
    return out


def auc(items: list[dict]) -> float:
    """정답 점수가 무관 질문 top-1 점수보다 높을 확률"""
    pos, neg = [], []
    for item in items:
        if item["type"] == "UNRELATED":
            top = next((r["score"] for r in item["results"] if r["rank"] == 1), None)
            if top is not None:
                neg.append(top)
            continue
        hit = score_of(item, top_k=10, threshold=0.0)
        if hit is not None:
            pos.append(hit)
    if not pos or not neg:
        return float("nan")
    wins = sum((1.0 if p > n else 0.5 if p == n else 0.0) for p in pos for n in neg)
    return wins / (len(pos) * len(neg))


def best_row(rows: list[dict], min_rejection: float) -> dict | None:
    """거부율 제약을 지키는 조합 중 Recall@3 최대. 동률이면 MRR, 그다음 낮은 임계값."""
    ok = [r for r in rows if r.get("rejection", 0.0) >= min_rejection and "recall@3" in r]
    if not ok:
        return None
    return max(ok, key=lambda r: (r["recall@3"], r["mrr"], -r["threshold"]))


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("paths", nargs="+", type=Path, help="raw-*.json (구성마다 하나)")
    ap.add_argument("--min-rejection", type=float, default=DEFAULT_MIN_REJECTION,
                    help=f"무관 질문 거부율 하한 (기본 {DEFAULT_MIN_REJECTION})")
    ap.add_argument("--top-ks", type=int, nargs="+", default=list(DEFAULT_TOP_KS))
    ap.add_argument("--sensitivity", type=float, nargs="+", default=[0.90, 0.95, 1.00],
                    help="제약을 바꿔가며 최적점이 어떻게 움직이는지 볼 값들")
    args = ap.parse_args()

    grids: dict[str, list[dict]] = {}
    for path in args.paths:
        data = load(path)
        name = path.stem.replace("raw-", "")
        rows = []
        for top_k in args.top_ks:
            if top_k > data["top_k"]:
                raise SystemExit(f"{path}: top_k {top_k}는 수집 당시 top_k({data['top_k']})보다 큽니다")
            for threshold in DEFAULT_THRESHOLDS:
                row = {"variant": name, "top_k": top_k, "threshold": threshold}
                row.update(evaluate(data["items"], top_k, threshold))
                rows.append(row)
        grids[name] = rows
        # 수집이 온전한지
        short = [i["eval_id"] for i in data["items"] if len(i["results"]) < data["top_k"]]
        warn = f"  반환 부족 {len(short)}문항 (예: {short[:3]}) - 재수집 권장" if short else ""
        print(f"{path} - {len(data['items'])}건, 조합 {len(rows)}개, AUC {auc(data['items']):.4f}{warn}")

    all_rows = [r for rows in grids.values() for r in rows]

    print(f"\n## 최적 조합 (거부율 ≥ {args.min_rejection}, Recall@3 최대)\n")
    best = best_row(all_rows, args.min_rejection)
    if best is None:
        best_rejection = max((r.get("rejection", 0.0) for r in all_rows), default=0.0)
        print(f"  제약을 만족하는 조합이 없습니다 (최고 거부율 {best_rejection:.3f})")
    else:
        print(f"  {best['variant']} / top-{best['top_k']} / threshold {best['threshold']}")
        print(f"  Recall@1 {best['recall@1']:.3f}  Recall@3 {best['recall@3']:.3f}  "
              f"MRR {best['mrr']:.3f}  거부율 {best['rejection']:.3f}")

    print(f"\n## 구성별 최선 (거부율 ≥ {args.min_rejection})\n")
    print("| 구성 | top-k | 임계값 | Recall@1 | Recall@3 | MRR | 거부율 |")
    print("| --- | --- | --- | --- | --- | --- | --- |")
    for name, rows in grids.items():
        row = best_row(rows, args.min_rejection)
        if row is None:
            print(f"| {name} | — | — | — | — | — | 제약 불가 |")
            continue
        print(f"| {name} | {row['top_k']} | {row['threshold']:.2f} | {row['recall@1']:.3f} | "
              f"{row['recall@3']:.3f} | {row['mrr']:.3f} | {row['rejection']:.3f} |")

    print("\n## 민감도 - 거부율 하한을 바꾸면 최적점이 어디로 가는가\n")
    print("| 하한 | 구성 | top-k | 임계값 | Recall@3 | MRR | 거부율 |")
    print("| --- | --- | --- | --- | --- | --- | --- |")
    for limit in args.sensitivity:
        row = best_row(all_rows, limit)
        if row is None:
            print(f"| {limit:.2f} | 제약을 만족하는 조합 없음 | | | | | |")
            continue
        print(f"| {limit:.2f} | {row['variant']} | {row['top_k']} | {row['threshold']:.2f} | "
              f"{row['recall@3']:.3f} | {row['mrr']:.3f} | {row['rejection']:.3f} |")

    if best is not None:
        print(f"\n## {best['variant']} / top-{best['top_k']} 임계값별 추이\n")
        print("| 임계값 | Recall@1 | Recall@3 | MRR | 거부 전체 | 완전무관 | 인접 | 인접(경계) |")
        print("| --- | --- | --- | --- | --- | --- | --- | --- |")
        for row in grids[best["variant"]]:
            if row["top_k"] != best["top_k"] or not (0.60 <= row["threshold"] <= 0.80):
                continue
            cells = [f"{row.get(f'rejection:{k}', float('nan')):.3f}"
                     for k in ("OFF_DOMAIN", "ADJACENT", "ADJACENT_HARD")]
            mark = " ←" if row["threshold"] == best["threshold"] else ""
            print(f"| {row['threshold']:.2f} | {row['recall@1']:.3f} | {row['recall@3']:.3f} | "
                  f"{row['mrr']:.3f} | {row['rejection']:.3f} | {' | '.join(cells)} |{mark}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
