#!/usr/bin/env python3

"""공공데이터 상가정보 CSV에서 좌표, 주소만 가져와 매장 가상 데이터 생성"""

from __future__ import annotations

import argparse
import csv
import json
import random
import re
from collections import Counter, defaultdict
from decimal import Decimal
from pathlib import Path

DEFAULT_OUT = Path(__file__).parent / "data" / "stores.json"
DEFAULT_COUNT = 150
# 컬럼명은 판본마다 달라 --lat-col 등으로 덮어쓸 수 있다
COLUMNS = {
    "lat": ["위도"],
    "lon": ["경도"],
    "address": ["도로명주소", "지번주소"],
    "sido": ["시도명"],
    "sigungu": ["시군구명"],
    "region_code": ["법정동코드", "행정동코드"],
    "industry": ["상권업종소분류명", "표준산업분류명", "상권업종중분류명"],
}
# 2026-06 판본의 통신 매장 업종명. 수리업·중고 소매업은 걸리지 않는다
DEFAULT_INDUSTRY = "핸드폰 소매|통신기기 소매|이동통신"
DEFAULT_MAX_PER_FILE = 3000  # 시도별 CSV가 수백 MB라 파일당 후보를 끊는다
SERVICE_CODES = ("NEW_LINE", "PORT_IN", "NAME_CHANGE", "USIM_REISSUE")
# 상호는 실데이터를 쓰지 않는다. 전화번호는 어느 대역을 쓰든 실제 가입자 번호와 겹칠 수 있어 아예 넣지 않는다
BRAND = "텔미"
# V2 샘플 매장 2곳의 region_code를 V7과 같은 10자리 법정동코드로 맞춘다.
# V2는 이미 develop에 머지돼 직접 고치면 Flyway 체크섬이 깨지므로 시드 SQL 끝에서 UPDATE한다.
# 값은 같은 판본 공공데이터에서 확인 (테헤란로 123 → 역삼동, 센텀중앙로 45 → 우동)
V2_SAMPLE_REGION_CODES = ((1, "1168010100"), (2, "2635010500"))

# 최근접 검색 검증에 밀집, 중거리, 공백이 다 필요해서 시도별로 배정
REGION_QUOTA = {
    "서울특별시": 0.33,
    "경기도": 0.17,
    "인천광역시": 0.06,
    "부산광역시": 0.07,
    "대구광역시": 0.05,
    "대전광역시": 0.05,
    "전남광주통합특별시": 0.05,  # 2026-06 판본은 전남+광주가 합쳐져 있다
    "울산광역시": 0.05,
}
SERVICE_MIX = ((4, 0.40), (3, 0.30), (2, 0.20), (1, 0.10))  # 가능 업무 개수별 비중
# 데모, 수동 테스트에서 자주 쓰는 상권은 반드시 넣는다
PREFERRED_SIGUNGU = ("강남구", "서초구", "마포구", "송파구", "해운대구", "성남시", "수원시")


def open_csv(path: Path, encoding: str | None):
    """수백 MB CSV를 메모리에 올리지 않고 스트리밍한다"""
    encodings = [encoding] if encoding else ["utf-8-sig", "cp949"]
    for enc in encodings:
        try:
            handle = path.open(encoding=enc, newline="")
            handle.readline()  # 인코딩 확인용으로 한 줄만
            handle.seek(0)
            return handle
        except UnicodeDecodeError:
            handle.close()
            continue
        except OSError as exc:
            raise SystemExit(f"{path}: 파일을 읽을 수 없습니다: {exc}") from None
    raise SystemExit(f"{path}: 인코딩을 못 읽었습니다. --encoding으로 지정하세요 (utf-8-sig / cp949)")


def pick_column(header: list[str], candidates: list[str], override: str | None, label: str) -> str:
    if override:
        if override not in header:
            raise SystemExit(f"CSV에 '{override}' 컬럼이 없습니다. 있는 컬럼: {', '.join(header[:12])} ...")
        return override
    for name in candidates:
        if name in header:
            return name
    raise SystemExit(f"{label} 컬럼을 찾지 못했습니다(후보: {', '.join(candidates)}). "
                     f"--{label}-col로 지정하세요. 있는 컬럼: {', '.join(header[:12])} ...")


def round6(text: str) -> Decimal | None:
    """NUMERIC(9,6)에 맞춰 6자리로 반올림. 숫자가 아니면 None"""
    try:
        return Decimal(text).quantize(Decimal("0.000001"))
    except Exception:
        return None


def in_korea(lat: Decimal, lon: Decimal) -> bool:
    return Decimal("33") <= lat <= Decimal("38.7") and Decimal("124.6") <= lon <= Decimal("132")


def load_candidates(paths: list[Path], cols: dict, industry: re.Pattern,
                    max_per_file: int) -> list[dict]:
    encoding = cols.pop("_encoding", None)
    seen_coords: set[tuple] = set()
    candidates: list[dict] = []

    for path in paths:
        with open_csv(path, encoding) as handle:
            reader = csv.DictReader(handle)
            resolved = {key: pick_column(list(reader.fieldnames or []), COLUMNS[key], cols.get(key), key)
                        for key in COLUMNS}
            found = 0
            for row in reader:
                if found >= max_per_file:
                    break
                if not industry.search(row.get(resolved["industry"], "") or ""):
                    continue
                lat, lon = round6(row.get(resolved["lat"], "")), round6(row.get(resolved["lon"], ""))
                address = (row.get(resolved["address"]) or "").strip()
                if lat is None or lon is None or not in_korea(lat, lon) or not address:
                    continue
                # 같은 좌표가 층·호별로 중복돼 있어 좌표당 하나만 남긴다
                if (lat, lon) in seen_coords:
                    continue
                seen_coords.add((lat, lon))
                found += 1
                candidates.append({
                    "lat": lat,
                    "lon": lon,
                    "address": address[:255],
                    "sido": (row.get(resolved["sido"]) or "").strip(),
                    "sigungu": (row.get(resolved["sigungu"]) or "").strip(),
                    "region_code": (row.get(resolved["region_code"]) or "").strip()[:20],
                })
        print(f"  {path.name}: 후보 {found}건")

    if not candidates:
        raise SystemExit(f"업종 필터 '{industry.pattern}'에 걸리는 행이 없습니다. --industry로 조정하세요")
    return candidates


def allocate(candidates: list[dict], count: int, rng: random.Random) -> list[dict]:
    """시도별 배정. 같은 시군구에 여러 건이 모이게 골라 밀집을 만든다"""
    by_sido: dict[str, list[dict]] = defaultdict(list)
    for row in candidates:
        by_sido[row["sido"]].append(row)

    picked: list[dict] = []
    for sido, share in REGION_QUOTA.items():
        pool = by_sido.get(sido, [])
        want = round(count * share)
        if not pool:
            continue
        groups = sorted({row["sigungu"] for row in pool})
        rng.shuffle(groups)
        groups.sort(key=lambda name: name not in PREFERRED_SIGUNGU)
        focus = set(groups[: max(1, want // 5)])
        focused = [row for row in pool if row["sigungu"] in focus]
        rng.shuffle(focused)
        picked.extend(focused[:want])

    # "근처에 매장 없음" 경로용 공백 지역
    rest = [row for row in candidates if row["sido"] not in REGION_QUOTA]
    rng.shuffle(rest)
    picked.extend(rest[: max(0, count - len(picked))])

    if len(picked) < count:
        pool = [row for row in candidates if row not in picked]
        rng.shuffle(pool)
        picked.extend(pool[: count - len(picked)])
    return picked[:count]


def make_hours(rng: random.Random) -> list[dict]:
    """1(월)~7(일) 7행을 모두 만든다. 빠진 요일은 PK 구조상 조회에서 사라진다"""
    open_hour = rng.choice([9, 10, 10, 11])
    close_hour = rng.choice([19, 20, 20, 21])
    pattern = rng.choices(["일요일휴무", "주말휴무", "연중무휴"], weights=[0.6, 0.2, 0.2])[0]
    hours = []
    for day in range(1, 8):
        closed = (pattern == "일요일휴무" and day == 7) or (pattern == "주말휴무" and day >= 6)
        if closed:
            hours.append({"day_of_week": day, "open_time": None, "close_time": None, "is_closed": True})
            continue
        close = close_hour - 2 if day >= 6 else close_hour
        hours.append({
            "day_of_week": day,
            "open_time": f"{open_hour:02d}:00",
            "close_time": f"{close:02d}:00",
            "is_closed": False,
        })
    return hours


def make_services(rng: random.Random) -> list[str]:
    size = rng.choices([n for n, _ in SERVICE_MIX], weights=[w for _, w in SERVICE_MIX])[0]
    return sorted(rng.sample(SERVICE_CODES, size))


def build(candidates: list[dict], count: int, start_id: int, seed: int) -> list[dict]:
    rng = random.Random(seed)
    picked = allocate(candidates, count, rng)
    if len(picked) < count:
        raise SystemExit(f"후보가 부족합니다: {len(picked)}건 (요청 {count}건). CSV 범위나 --industry를 넓히세요")

    seq: Counter = Counter()
    stores = []
    for offset, row in enumerate(picked):
        sigungu = row["sigungu"] or row["sido"] or "지점"
        seq[sigungu] += 1
        name = f"{BRAND} {sigungu}{seq[sigungu]}호점"[:100]
        stores.append({
            "store_id": start_id + offset,
            "name": name,
            "phone": None,
            "address": row["address"],
            "region_code": row["region_code"] or None,
            "latitude": str(row["lat"]),
            "longitude": str(row["lon"]),
            # 검색이 status로 거르는지 볼 케이스
            "status": "CLOSED_DOWN" if rng.random() < 0.05 else "OPEN",
            "sido": row["sido"],
            "sigungu": row["sigungu"],
            "services": make_services(rng),
            "hours": make_hours(rng),
        })
    return stores


def sql_literal(value) -> str:
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def to_sql(stores: list[dict]) -> str:
    """dev-migration용 INSERT. service_type_id는 code로 조회해 기존 마스터를 쓴다"""
    out = [
        "-- 매장 가상 데이터. 좌표·주소·법정동코드는 공공데이터(소상공인시장진흥공단 상가(상권)정보 2026-06)에서",
        "-- 가져왔고 상호·영업시간·가능업무는 실제 업체 정보X. 전화번호는 넣지 않는다(phone NULL)",
        "-- scripts/generate_stores.py --sql 로 생성한다. 손으로 고치지 말 것.",
        "-- day_of_week는 1(월)~7(일). V6가 CHECK로 고정한다.",
        "",
    ]
    rows = ",\n".join(
        "    ({}, {}, {}, {}, {}, {}, {}, {})".format(
            s["store_id"], sql_literal(s["name"]), sql_literal(s["phone"]), sql_literal(s["address"]),
            sql_literal(s["region_code"]), s["latitude"], s["longitude"], sql_literal(s["status"]))
        for s in stores)
    out.append("INSERT INTO stores (store_id, name, phone, address, region_code, latitude, longitude, status) VALUES")
    out.append(rows + ";")
    out.append("")

    pairs = ",\n".join(f"    ({s['store_id']}, {sql_literal(code)})"
                       for s in stores for code in s["services"])
    out.append("INSERT INTO store_services (store_id, service_type_id)")
    out.append("SELECT v.store_id, t.service_type_id")
    out.append("  FROM (VALUES")
    out.append(pairs)
    out.append("  ) AS v (store_id, code)")
    out.append("  JOIN store_service_types t ON t.code = v.code;")
    out.append("")

    hours = ",\n".join(
        "    ({}, {}, {}, {}, {})".format(
            s["store_id"], h["day_of_week"],
            sql_literal(h["open_time"]), sql_literal(h["close_time"]),
            "true" if h["is_closed"] else "false")
        for s in stores for h in s["hours"])
    out.append("INSERT INTO store_hours (store_id, day_of_week, open_time, close_time, is_closed) VALUES")
    out.append(hours + ";")
    out.append("")
    out.append("-- PK를 직접 지정했으므로 시퀀스를 맞춘다. 안 하면 다음 INSERT에서 PK 충돌")
    out.append("SELECT setval(pg_get_serial_sequence('stores', 'store_id'),"
               " (SELECT max(store_id) FROM stores));")
    out.append("")
    out.append("-- V2 샘플 매장의 region_code가 'SEOUL'/'BUSAN'이라 형식이 섞인다. V7과 같은 법정동코드로 맞춘다")
    for store_id, code in V2_SAMPLE_REGION_CODES:
        out.append(f"UPDATE stores SET region_code = {sql_literal(code)} WHERE store_id = {store_id};")
    return "\n".join(out) + "\n"


def self_test() -> int:
    rng = random.Random(0)
    checks = [
        (len(make_hours(rng)), 7),
        (sorted(h["day_of_week"] for h in make_hours(rng)), list(range(1, 8))),
        (all(h["open_time"] is None for h in make_hours(rng) if h["is_closed"]), True),
        (str(round6("37.1234567")), "37.123457"),
        (round6("없음"), None),
        (in_korea(Decimal("37.5"), Decimal("127.0")), True),
        (in_korea(Decimal("41.0"), Decimal("127.0")), False),
        (set(make_services(rng)) <= set(SERVICE_CODES), True),
        (sql_literal(None), "NULL"),
        (sql_literal("텔미 '강남'점"), "'텔미 ''강남''점'"),
    ]
    failed = [(i, got, want) for i, (got, want) in enumerate(checks, 1) if got != want]
    for i, got, want in failed:
        print(f"  FAIL #{i}: {got!r} (기대 {want!r})")
    print(f"자기 검증 {len(checks) - len(failed)}/{len(checks)} 통과")
    return 1 if failed else 0


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("csv", nargs="*", type=Path, help="공공데이터 상가정보 CSV (시도별 파일 여러 개 가능)")
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT, help=f"출력 JSON (기본 {DEFAULT_OUT.name})")
    ap.add_argument("--sql", type=Path, help="dev-migration용 SQL도 함께 생성 (예: V<다음버전>__seed_stores.sql)")
    ap.add_argument("--count", type=int, default=DEFAULT_COUNT, help=f"매장 건수 (기본 {DEFAULT_COUNT})")
    ap.add_argument("--start-id", type=int, default=101,
                    help="store_id 시작값 (기본 101 — V2 샘플 1·2번을 비켜 둔다)")
    ap.add_argument("--industry", default=DEFAULT_INDUSTRY, help=f"업종명 정규식 (기본 {DEFAULT_INDUSTRY})")
    ap.add_argument("--seed", type=int, default=20260928, help="난수 시드 (같은 값이면 같은 결과)")
    ap.add_argument("--encoding", help="CSV 인코딩 (기본: utf-8-sig → cp949 순으로 시도)")
    ap.add_argument("--max-per-file", type=int, default=DEFAULT_MAX_PER_FILE,
                    help=f"파일당 후보 상한 (기본 {DEFAULT_MAX_PER_FILE})")
    for key in COLUMNS:
        ap.add_argument(f"--{key}-col", dest=f"{key}_col", help=f"{key} 컬럼명 직접 지정")
    ap.add_argument("--self-test", action="store_true")
    args = ap.parse_args()

    if args.self_test:
        return self_test()
    if not args.csv:
        ap.error("CSV 경로 필요 (또는 --self-test)")

    cols = {key: getattr(args, f"{key}_col") for key in COLUMNS}
    cols["_encoding"] = args.encoding
    candidates = load_candidates(args.csv, cols, re.compile(args.industry), args.max_per_file)
    stores = build(candidates, args.count, args.start_id, args.seed)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(stores, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    by_sido = Counter(s["sido"] for s in stores)
    print(f"{args.out} — 매장 {len(stores)}건 (후보 {len(candidates)}건에서 선택)")
    print("  " + ", ".join(f"{sido} {n}" for sido, n in by_sido.most_common()))
    print(f"  폐업 {sum(1 for s in stores if s['status'] == 'CLOSED_DOWN')}건, "
          f"가능업무 4종 {sum(1 for s in stores if len(s['services']) == 4)}건")

    if args.sql:
        args.sql.parent.mkdir(parents=True, exist_ok=True)
        args.sql.write_text(to_sql(stores), encoding="utf-8")
        print(f"{args.sql} — INSERT 생성 (stores {len(stores)} / "
              f"store_services {sum(len(s['services']) for s in stores)} / "
              f"store_hours {len(stores) * 7})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
