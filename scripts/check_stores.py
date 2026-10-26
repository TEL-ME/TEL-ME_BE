#!/usr/bin/env python3

"""매장 데이터 검증: 스키마 제약 + 최근접 검색 검증에 필요한 분포를 확인"""

from __future__ import annotations

import argparse
import json
import math
import sys
from collections import Counter, defaultdict
from decimal import Decimal, InvalidOperation
from pathlib import Path

DEFAULT_PATH = Path(__file__).parent / "data" / "stores.json"
DEFAULT_SQL = (Path(__file__).parent.parent / "src" / "main" / "resources"
               / "db" / "dev-migration" / "V7__seed_stores.sql")
SERVICE_CODES = {"NEW_LINE", "PORT_IN", "NAME_CHANGE", "USIM_REISSUE"}
STATUSES = {"OPEN", "CLOSED_DOWN"}
# V1 스키마 상한. 넘으면 적재에서 잘린다
LIMITS = {"name": 100, "phone": 20, "address": 255, "region_code": 20}
LAT_RANGE = (Decimal("33"), Decimal("38.7"))
LON_RANGE = (Decimal("124.6"), Decimal("132"))
# 이 반경에 3개 이상 모인 곳이 있어야 최근접 정렬 순서를 검증할 수 있다
CLUSTER_KM = 1.0
CLUSTER_MIN = 3


def load(path: Path) -> list[dict]:
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise SystemExit(f"{path}: 파일을 읽을 수 없습니다: {exc}") from None
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path}: JSON 형식이 아닙니다: {exc}") from None
    if not isinstance(data, list) or not data:
        raise SystemExit(f"{path}: 비어 있지 않은 매장 배열이 필요합니다")
    return data


def distance_km(a: dict, b: dict) -> float:
    """하버사인. 최근접 검색과 같은 기준으로 분포를 본다"""
    lat1, lon1 = math.radians(float(a["latitude"])), math.radians(float(a["longitude"]))
    lat2, lon2 = math.radians(float(b["latitude"])), math.radians(float(b["longitude"]))
    h = (math.sin((lat2 - lat1) / 2) ** 2
         + math.cos(lat1) * math.cos(lat2) * math.sin((lon2 - lon1) / 2) ** 2)
    return 2 * 6371.0 * math.asin(math.sqrt(h))


def decimal_of(value, scale: int = 6) -> Decimal | None:
    """NUMERIC(9,6) 기준. 7자리부터는 적재에서 조용히 잘린다"""
    try:
        parsed = Decimal(str(value))
    except (InvalidOperation, TypeError):
        return None
    return parsed if -parsed.as_tuple().exponent <= scale else None


def check_fields(stores: list[dict], problems: list[str]) -> None:
    ids: Counter = Counter()
    coords: Counter = Counter()
    for index, store in enumerate(stores, 1):
        label = f"{index}번({store.get('name', '이름없음')})"
        for key in ("store_id", "name", "address", "latitude", "longitude", "status"):
            if not store.get(key):
                problems.append(f"{label}: {key} 없음")
        ids[store.get("store_id")] += 1

        for key, limit in LIMITS.items():
            value = store.get(key)
            if isinstance(value, str) and len(value) > limit:
                problems.append(f"{label}: {key} {len(value)}자 (상한 {limit})")

        lat, lon = decimal_of(store.get("latitude")), decimal_of(store.get("longitude"))
        if lat is None or lon is None:
            problems.append(f"{label}: 좌표가 숫자가 아니거나 소수 6자리를 넘음 "
                            f"({store.get('latitude')}, {store.get('longitude')})")
        else:
            if not LAT_RANGE[0] <= lat <= LAT_RANGE[1] or not LON_RANGE[0] <= lon <= LON_RANGE[1]:
                problems.append(f"{label}: 좌표가 한국 범위 밖 ({lat}, {lon})")
            coords[(str(lat), str(lon))] += 1

        if store.get("status") not in STATUSES:
            problems.append(f"{label}: status '{store.get('status')}' (허용 {sorted(STATUSES)})")

        # 어느 대역이든 무작위 번호는 실제 가입자 번호와 겹칠 수 있어 아예 넣지 않는다
        if store.get("phone") is not None:
            problems.append(f"{label}: phone '{store.get('phone')}' — 실제 번호와 겹치지 않도록 NULL이어야 함")

    for store_id, count in ids.items():
        if count > 1:
            problems.append(f"store_id {store_id}가 {count}번 나옴")
    for coord, count in coords.items():
        if count > 1:
            problems.append(f"좌표 {coord}가 {count}번 나옴 — 같은 자리에 매장이 겹친다")


def check_hours(stores: list[dict], problems: list[str]) -> None:
    for store in stores:
        label = f"{store.get('store_id')}번({store.get('name')})"
        hours = store.get("hours") or []
        days = [h.get("day_of_week") for h in hours]
        if sorted(days) != list(range(1, 8)):
            problems.append(f"{label}: 영업시간이 1~7 요일 7행이 아님 ({sorted(days)}) — "
                            "PK가 (store_id, day_of_week)라 빠진 요일은 조회에서 사라진다")
            continue
        for hour in hours:
            day = hour["day_of_week"]
            if hour.get("is_closed"):
                if hour.get("open_time") or hour.get("close_time"):
                    problems.append(f"{label} {day}요일: 휴무인데 영업시간이 있음")
                continue
            if not hour.get("open_time") or not hour.get("close_time"):
                problems.append(f"{label} {day}요일: 영업일인데 시간이 없음")
            elif hour["open_time"] >= hour["close_time"]:
                problems.append(f"{label} {day}요일: 개점({hour['open_time']}) ≥ 폐점({hour['close_time']})")


def check_services(stores: list[dict], problems: list[str]) -> None:
    for store in stores:
        label = f"{store.get('store_id')}번({store.get('name')})"
        services = store.get("services") or []
        if not services:
            problems.append(f"{label}: 가능 업무가 없음")
        unknown = set(services) - SERVICE_CODES
        if unknown:
            problems.append(f"{label}: 알 수 없는 업무 코드 {sorted(unknown)}")
        if len(services) != len(set(services)):
            problems.append(f"{label}: 업무 코드 중복 ({services}) - PK가 (store_id, service_type_id)다")


def check_generated_sql(stores: list[dict], sql_path: Path, problems: list[str]) -> None:
    """커밋된 시드 SQL이 지금 JSON에서 생성한 것과 같은지 본다.

    SQL은 손으로 고치지 않는 파일이라, 다르면 JSON만 고치고 재생성을 잊은 것이다.
    """
    sys.path.insert(0, str(Path(__file__).parent))
    try:
        from generate_stores import to_sql
    except ImportError as exc:
        problems.append(f"generate_stores.py를 불러올 수 없어 SQL 대조를 못 했습니다: {exc}")
        return

    if not sql_path.exists():
        problems.append(f"{sql_path}: 시드 SQL이 없습니다 (--sql로 경로를 지정하거나 --no-sql로 건너뛰세요)")
        return

    expected = to_sql(stores)
    actual = sql_path.read_text(encoding="utf-8")
    if expected == actual:
        print(f"  시드 SQL 대조: {sql_path.name} == to_sql({DEFAULT_PATH.name})")
        return

    exp_lines, act_lines = expected.splitlines(), actual.splitlines()
    first = next((i + 1 for i, (a, b) in enumerate(zip(exp_lines, act_lines)) if a != b),
                 min(len(exp_lines), len(act_lines)) + 1)
    problems.append(f"{sql_path.name}이 JSON에서 생성한 SQL과 다릅니다 (처음 다른 행 {first}). "
                    f"생성기로 다시 뽑으세요: python3 scripts/generate_stores.py ... --sql {sql_path}")


def report_distribution(stores: list[dict]) -> list[str]:
    """검증 케이스가 있는 분포인지 본다. 경고만 내고 실패로 세지 않는다"""
    warnings = []
    active = [s for s in stores if s.get("status") == "OPEN"]

    clusters = 0
    for anchor in active:
        near = sum(1 for other in active if other is not anchor and distance_km(anchor, other) <= CLUSTER_KM)
        if near >= CLUSTER_MIN - 1:
            clusters += 1
    print(f"  밀집({CLUSTER_KM}km 안에 {CLUSTER_MIN}개 이상): 중심이 될 수 있는 매장 {clusters}건")
    if clusters == 0:
        warnings.append(f"{CLUSTER_KM}km 안에 {CLUSTER_MIN}개 이상 모인 곳이 없음 - 최근접 정렬 순서를 검증할 케이스가 없다")

    nearest = sorted(min(distance_km(a, b) for b in active if b is not a) for a in active) if len(active) > 1 else []
    if nearest:
        print(f"  최근접 이웃 거리: 최소 {nearest[0]:.2f}km / 중앙 {nearest[len(nearest) // 2]:.2f}km / "
              f"최대 {nearest[-1]:.2f}km")
        if nearest[-1] < 10:
            warnings.append("모든 매장이 서로 10km 안에 있음 - \"근처에 매장 없음\" 경로를 검증할 공백 지역이 없다")

    by_sido = Counter(s.get("sido") or "미기재" for s in stores)
    print("  시도 분포: " + ", ".join(f"{k} {v}" for k, v in by_sido.most_common()))
    if len(by_sido) < 5:
        warnings.append(f"시도가 {len(by_sido)}종뿐 - 지역 검색(region_code) 경로를 검증하기 어렵다")

    sizes = Counter(len(s.get("services") or []) for s in stores)
    print("  가능 업무 수: " + ", ".join(f"{k}종 {v}건" for k, v in sorted(sizes.items())))
    if len(sizes) < 2:
        warnings.append("모든 매장의 가능 업무 수가 같음 - 업무 조건 필터가 결과를 줄이는지 검증할 수 없다")

    closed = sum(1 for s in stores if s.get("status") == "CLOSED_DOWN")
    print(f"  폐업(CLOSED_DOWN) {closed}건")
    if closed == 0:
        warnings.append("폐업 매장이 없음 - 검색이 status로 거르는지 검증할 케이스가 없다")

    # 한 지역에서 가능 업무가 갈려야 조건 필터가 순위를 바꾸는 걸 볼 수 있다
    by_sigungu: dict[str, set] = defaultdict(set)
    for store in active:
        by_sigungu[store.get("sigungu") or ""].add(tuple(sorted(store.get("services") or [])))
    mixed = sum(1 for combos in by_sigungu.values() if len(combos) > 1)
    print(f"  같은 시군구에서 가능 업무가 갈리는 지역 {mixed}곳")
    if mixed == 0:
        warnings.append("한 지역 안에서 가능 업무가 모두 같음 - 조건 필터 검증 케이스가 없다")
    return warnings


def self_test() -> int:
    ok = {"store_id": 1, "name": "텔미 강남1호점", "phone": None,
          "address": "서울 강남구 테헤란로 1", "region_code": "1168010100",
          "latitude": "37.500000", "longitude": "127.030000", "status": "OPEN",
          "services": ["NEW_LINE"], "hours": [
              {"day_of_week": d, "open_time": "10:00", "close_time": "20:00", "is_closed": False}
              for d in range(1, 8)]}

    def problems_for(**changes) -> list[str]:
        store = json.loads(json.dumps(ok))
        store.update(changes)
        found: list[str] = []
        check_fields([store], found)
        check_hours([store], found)
        check_services([store], found)
        return found

    checks = [
        ("정상", problems_for(), 0),
        ("좌표 7자리", problems_for(latitude="37.1234567"), 1),
        ("좌표 범위 밖", problems_for(latitude="41.000000"), 1),
        ("status 오타", problems_for(status="CLOSED"), 1),
        ("이름 초과", problems_for(name="가" * 101), 1),
        ("전화번호 있음", problems_for(phone="070-8123-4567"), 1),
        ("요일 6개", problems_for(hours=ok["hours"][:6]), 1),
        ("요일 중복", problems_for(hours=ok["hours"][:6] + [ok["hours"][0]]), 1),
        ("휴무인데 시간 있음",
         problems_for(hours=ok["hours"][:6] + [{"day_of_week": 7, "open_time": "10:00",
                                                "close_time": "20:00", "is_closed": True}]), 1),
        ("개점 ≥ 폐점",
         problems_for(hours=[{"day_of_week": d, "open_time": "20:00", "close_time": "10:00",
                              "is_closed": False} for d in range(1, 8)]), 7),
        ("업무 없음", problems_for(services=[]), 1),
        ("업무 코드 오타", problems_for(services=["NEWLINE"]), 1),
        ("업무 중복", problems_for(services=["NEW_LINE", "NEW_LINE"]), 1),
    ]
    failed = []
    for label, found, expected in checks:
        if len(found) != expected:
            failed.append((label, len(found), expected, found[:2]))
    for label, got, want, sample in failed:
        print(f"  FAIL {label}: 검출 {got}건 (기대 {want}건) {sample}")

    # 중복 검사는 두 건을 같이 넣어야 걸린다
    dup: list[str] = []
    check_fields([ok, json.loads(json.dumps(ok))], dup)
    if len(dup) != 2:
        failed.append(("store_id·좌표 중복", len(dup), 2, dup[:2]))
        print(f"  FAIL store_id·좌표 중복: 검출 {len(dup)}건 (기대 2건)")

    total = len(checks) + 1
    print(f"자기 검증 {total - len(failed)}/{total} 통과")
    return 1 if failed else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("path", nargs="?", type=Path, default=DEFAULT_PATH,
                    help=f"매장 JSON (기본 {DEFAULT_PATH.name})")
    ap.add_argument("--sql", type=Path, default=DEFAULT_SQL,
                    help=f"대조할 시드 SQL (기본 {DEFAULT_SQL.name})")
    ap.add_argument("--no-sql", action="store_true", help="시드 SQL 대조를 건너뛴다")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        return self_test()

    stores = load(args.path)
    problems: list[str] = []
    check_fields(stores, problems)
    check_hours(stores, problems)
    check_services(stores, problems)

    print(f"{args.path} — 매장 {len(stores)}건 검증")
    if not args.no_sql:
        check_generated_sql(stores, args.sql, problems)
    warnings = report_distribution(stores)

    if problems:
        print(f"\n제약 위반 {len(problems)}건")
        for problem in problems[:40]:
            print(f"  - {problem}")
        if len(problems) > 40:
            print(f"  ... 외 {len(problems) - 40}건")
    else:
        print("\n제약 위반 없음")

    if warnings:
        print(f"\n분포 경고 {len(warnings)}건 (적재는 되지만 검증 케이스가 빈다)")
        for warning in warnings:
            print(f"  - {warning}")

    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
