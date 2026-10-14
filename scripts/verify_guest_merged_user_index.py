#!/usr/bin/env python3
"""로컬 전용 DB의 임시 스키마에서 기존 승계 쿼리의 결과와 실행 계획을 비교한다."""

import argparse
import json
import os
import re
import statistics
import subprocess
import textwrap
import uuid
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
NOW = "'2026-10-14T00:00:00Z'::timestamptz"
INDEX_NAME = "idx_guest_merged_user"


def run_sql(dsn, sql):
    environment = os.environ.copy()
    # URI의 로컬 주소를 별도 libpq 접속 설정으로 덮어쓰지 않도록 한다.
    for name in ("PGHOSTADDR", "PGSERVICE", "PGSERVICEFILE"):
        environment.pop(name, None)
    result = subprocess.run(
        ["psql", dsn, "-X", "-qAt", "-v", "ON_ERROR_STOP=1"],
        input=sql, text=True, capture_output=True, check=True, env=environment,
    )
    return result.stdout.strip()


def java_sql(fragment):
    # 운영 SQL을 그대로 읽어 검증 스크립트의 복사본과 실제 쿼리가 달라지는 것을 피한다.
    tokens = re.findall(r'"""(.*?)"""|("(?:\\.|[^"\\])*")', fragment, re.DOTALL)
    return "".join(textwrap.dedent(block) if block else json.loads(literal)
                   for block, literal in tokens).strip()


def queries():
    status = (ROOT / "src/main/java/com/telme/chat/guard/JdbcInputGuardStatusStore.java").read_text()
    store = (ROOT / "src/main/java/com/telme/chat/guard/JdbcInputGuardStore.java").read_text()
    cohort_method = store.split("private String cohort(", 1)[1].split("private Object[] parameters", 1)[0]
    cohort = java_sql(cohort_method.split("?", 1)[1].split(': "s.guest_id=?"', 1)[0])
    status_sql = java_sql(status.split("return jdbc.query(", 1)[1].split("(rs, row)", 1)[0])
    inherit_sql = java_sql(store.split("var active =", 1)[1].split("this::state", 1)[0]) + cohort
    # cohort는 연결식의 중간에 위치하므로 ORDER BY를 뒤로 복원한다.
    order = " ORDER BY s.restriction_until DESC LIMIT 1"
    inherit_sql = inherit_sql.replace(order, "") + order
    count_method = store.split("public int count(", 1)[1].split("public State restrict(", 1)[0]
    count_sql = java_sql(count_method.split("return jdbc.queryForObject(", 1)[1]
                        .split("Integer.class", 1)[0]) + cohort
    return {"status": status_sql, "inherit": inherit_sql, "count": count_sql}


def bind_parameters(sql):
    parts = sql.split("?")
    return parts[0] + "".join(f"${i}{part}" for i, part in enumerate(parts[1:], 1))


def decode_plans(raw):
    plans = []
    decoder = json.JSONDecoder()
    remaining = raw.strip()
    while remaining:
        value, end = decoder.raw_decode(remaining)
        plans.append(value[0])
        remaining = remaining[end:].lstrip()
    return plans


def index_names(node):
    names = [node["Index Name"]] if "Index Name" in node and node.get("Actual Loops", 0) > 0 else []
    for child in node.get("Plans", []):
        names.extend(index_names(child))
    return sorted(set(names))


def measure(dsn, schema, query, types, arguments, repeats, mode):
    prefix = f"SET search_path TO {schema}, public; SET plan_cache_mode = {mode};"
    prepare = f"PREPARE measured ({types}) AS {bind_parameters(query)};"
    explain = f"EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) EXECUTE measured ({arguments});"
    plans = decode_plans(run_sql(dsn, prefix + prepare + explain * (repeats + 1)))
    samples = plans[1:]
    snapshot = run_sql(dsn, prefix + f"PREPARE snapshot ({types}) AS "
                       + "SELECT coalesce(jsonb_agg(to_jsonb(q)), '[]'::jsonb) FROM ("
                       + bind_parameters(query) + f") q; EXECUTE snapshot ({arguments});")
    return {
        "result": json.loads(snapshot),
        "median_execution_ms": statistics.median(p["Execution Time"] for p in samples),
        "indexes": index_names(samples[-1]["Plan"]),
        "plan": samples[-1],
    }


def fixtures(schema, rows):
    guard_schema = (ROOT / "src/main/resources/db/migration/V25__add_chat_input_guard.sql").read_text()
    return f"""
        CREATE SCHEMA {schema};
        SET search_path TO {schema}, public;
        CREATE TABLE users (user_id bigint PRIMARY KEY);
        CREATE TABLE guests (LIKE public.guests INCLUDING DEFAULTS INCLUDING CONSTRAINTS);
        ALTER TABLE guests ADD PRIMARY KEY (guest_id);
        CREATE INDEX idx_guest_expires ON guests (expires_at) WHERE merged_user_id IS NULL;
        CREATE TABLE chat_sessions (session_id bigint PRIMARY KEY);
        CREATE TABLE chat_messages (message_id bigint PRIMARY KEY);
        {guard_schema}
        INSERT INTO users SELECT generate_series(1, 1000);
        INSERT INTO users VALUES (1001);
        INSERT INTO guests (guest_id, expires_at, merged_user_id)
        SELECT md5(n::text)::uuid, {NOW} + interval '1 day',
            CASE WHEN n % 10 = 0 THEN ((n / 10 - 1) % 1000) + 1 END
        FROM generate_series(1, {rows}) n;
        INSERT INTO chat_input_guard_states (
            user_id, counting_from_at, restriction_started_at, restriction_until, updated_at
        ) SELECT user_id, to_timestamp(0), {NOW}, {NOW} + interval '10 seconds', {NOW}
        FROM users WHERE user_id <= 1000;
        INSERT INTO chat_input_guard_states (
            guest_id, counting_from_at, restriction_started_at, restriction_until, updated_at
        ) SELECT guest_id, to_timestamp(0), {NOW},
            {NOW} + ((row_number() OVER (ORDER BY guest_id) % 89) + 20) * interval '1 second', {NOW}
        FROM guests WHERE merged_user_id IS NOT NULL OR guest_id = md5('11')::uuid;
        INSERT INTO chat_input_guard_events (
            guard_state_id, detected_at, sanitized_content, detections, actions,
            counted_violation, violation_count, policy_version, response_snapshot
        ) SELECT guard_state_id, {NOW} - interval '1 second', '합성 검증 입력', '[]', '[]',
            true, 1, 'index-verification', '{{}}' FROM chat_input_guard_states;
        ANALYZE guests;
        ANALYZE chat_input_guard_states;
        ANALYZE chat_input_guard_events;
    """


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dsn", required=True, help="작업 전용 로컬 DB의 PostgreSQL URI")
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--rows", type=int, default=100000)
    parser.add_argument("--repeat", type=int, default=7)
    args = parser.parse_args()
    connection = urlparse(args.dsn)
    if connection.scheme not in {"postgresql", "postgres"} or connection.hostname not in {
        "localhost", "127.0.0.1", "::1",
    } or connection.query or connection.fragment:
        parser.error("공유·원격 DB 대신 작업 전용 로컬 DB URI를 지정하세요.")
    if args.rows < 10000 or args.rows % 10000 != 0 or args.repeat < 1:
        parser.error("rows는 10000의 양수 배수, repeat는 1 이상이어야 합니다.")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    schema = "guest_index_verify_" + uuid.uuid4().hex
    sql = queries()
    migration_files = list((ROOT / "src/main/resources/db/migration").glob("V*__add_guest_merged_user_index.sql"))
    if len(migration_files) != 1:
        raise RuntimeError("승계 인덱스 마이그레이션이 정확히 하나 있어야 합니다.")
    migration = migration_files[0].read_text()
    guest = "md5('10')::uuid"
    unmerged = "md5('11')::uuid"
    cases = {
        "status_member": (sql["status"], "bigint, uuid, uuid", "1, NULL, NULL"),
        "status_merged_guest": (sql["status"], "bigint, uuid, uuid", f"NULL, {guest}, {guest}"),
        "status_unmerged_guest": (sql["status"], "bigint, uuid, uuid", f"NULL, {unmerged}, {unmerged}"),
        "status_missing_member": (sql["status"], "bigint, uuid, uuid", "1001, NULL, NULL"),
        "inherit_member": (sql["inherit"], "timestamptz, bigint, bigint", f"{NOW}, 1, 1"),
        "count_member": (sql["count"], "timestamptz, timestamptz, timestamptz, bigint, bigint",
                         f"{NOW} - interval '10 minutes', to_timestamp(0), {NOW}, 1, 1"),
    }
    report = {"rows": args.rows, "members": 1001, "merged_ratio": 0.1, "repeats": args.repeat,
              "postgresql": run_sql(args.dsn, "SELECT version();"),
              "migration": migration_files[0].name, "queries": sql, "measurements": {}}
    try:
        run_sql(args.dsn, fixtures(schema, args.rows))
        empty_member = run_sql(args.dsn, f"""
            SET search_path TO {schema}, public;
            SELECT count(*) FROM users u WHERE u.user_id = 1001
                AND NOT EXISTS (SELECT 1 FROM guests g WHERE g.merged_user_id = u.user_id)
                AND NOT EXISTS (SELECT 1 FROM chat_input_guard_states s WHERE s.user_id = u.user_id);
        """)
        if empty_member != "1":
            raise AssertionError("승계·제한 이력이 없는 회원 표본이 올바르지 않습니다.")
        for stage in ("before", "after"):
            if stage == "after":
                run_sql(args.dsn, f"SET search_path TO {schema}, public; {migration} ANALYZE guests;")
                report["index_bytes"] = int(run_sql(args.dsn, f"SELECT pg_relation_size('{schema}.{INDEX_NAME}');"))
            for mode in ("auto", "force_generic_plan"):
                measurements = report["measurements"].setdefault(mode, {})
                for name, (query, types, arguments) in cases.items():
                    measurements.setdefault(name, {})[stage] = measure(
                        args.dsn, schema, query, types, arguments, args.repeat, mode,
                    )
        for mode, measurements in report["measurements"].items():
            for name, comparison in measurements.items():
                if comparison["before"]["result"] != comparison["after"]["result"]:
                    raise AssertionError(f"{mode}/{name}: 인덱스 전후 조회 결과가 다릅니다.")
            expected_count = args.rows // 10000 + 1
            member_result = measurements["status_member"]["after"]["result"]
            merged_guest_result = measurements["status_merged_guest"]["after"]["result"]
            if len(member_result) != expected_count or member_result != merged_guest_result:
                raise AssertionError(f"{mode}: 회원·승계 게스트의 조회 범위가 다릅니다.")
            if measurements["status_missing_member"]["after"]["result"]:
                raise AssertionError(f"{mode}: 승계·제한 이력이 없는 회원에게 제한이 반환됩니다.")
            if measurements["count_member"]["after"]["result"] != [{"count": expected_count}]:
                raise AssertionError(f"{mode}: 회원·승계 게스트의 위반 횟수가 다릅니다.")
            for name in ("status_member", "status_merged_guest", "inherit_member", "count_member"):
                if INDEX_NAME not in measurements[name]["after"]["indexes"]:
                    raise AssertionError(f"{mode}/{name}: 승계 인덱스가 실행 계획에 없습니다.")
        report["verified"] = True
    finally:
        # 이 실행에서 생성한 임시 스키마만 제거하며 public 스키마에는 쓰지 않는다.
        run_sql(args.dsn, f"DROP SCHEMA IF EXISTS {schema} CASCADE;")
        (args.output_dir / "comparison.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"verified": report["verified"], "output": str(args.output_dir / "comparison.json"),
                      "auto_medians_ms": {name: {stage: data["median_execution_ms"]
                                                  for stage, data in comparison.items()}
                                          for name, comparison in report["measurements"]["auto"].items()}},
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
