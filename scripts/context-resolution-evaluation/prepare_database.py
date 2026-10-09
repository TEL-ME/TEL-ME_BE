"""초기화한 독립 평가 DB에 로컬 FAQ와 벡터만 복사한다."""
import argparse
import hashlib
import json
import pathlib
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True)
    parser.add_argument("--target", required=True)
    parser.add_argument("--container", default="telme-flow-postgres")
    args = parser.parse_args()
    if not args.target.startswith("telme_139_") or args.target == args.source:
        raise ValueError("복사 대상은 별도의 telme_139_ 평가 DB여야 합니다.")

    def sql(database, query, data=None):
        return subprocess.run(["docker", "exec", "-i", args.container, "psql", "-U", "telme",
                               "-d", database, "-v", "ON_ERROR_STOP=1", "-Atc", query],
                              input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout

    if int(sql(args.target, "SELECT count(*) FROM faqs")) != 0:
        raise ValueError("대상의 FAQ가 비어 있지 않아 복사를 중단합니다.")
    # 먼저 두 테이블의 호환 여부를 확인한 뒤 복사를 시작한다.
    snapshots = []
    for table in ["faqs", "faq_embeddings"]:
        columns = sql(args.target, "SELECT column_name FROM information_schema.columns "
                      f"WHERE table_name='{table}' ORDER BY ordinal_position").decode().splitlines()
        source_columns = sql(args.source, "SELECT column_name FROM information_schema.columns "
                             f"WHERE table_name='{table}' ORDER BY ordinal_position").decode().splitlines()
        if not columns or any(column not in source_columns for column in columns):
            raise ValueError(f"{table}의 필요한 컬럼이 원본 DB에 없습니다.")
        names = ",".join(columns)
        where = "created_by IS NULL AND updated_by IS NULL" if table == "faqs" else (
            "faq_id IN (SELECT faq_id FROM faqs WHERE created_by IS NULL AND updated_by IS NULL)")
        data = sql(args.source, f"COPY (SELECT {names} FROM {table} WHERE {where} ORDER BY faq_id) TO STDOUT WITH CSV")
        snapshots.append((table, names, data))
    report = dict(source=args.source, target=args.target, tables={})
    for table, names, data in snapshots:
        sql(args.target, f"COPY {table} ({names}) FROM STDIN WITH CSV", data)
        report["tables"][table] = dict(count=int(sql(args.target, f"SELECT count(*) FROM {table}")),
                                      sha256=hashlib.sha256(data).hexdigest(), columns=names)
    sql(args.target, "SELECT setval(pg_get_serial_sequence('faqs','faq_id'),(SELECT max(faq_id) FROM faqs))")
    destination = pathlib.Path(".measure/telme139/database.json")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False))


if __name__ == "__main__":
    main()
