"""기존 통합 실험 DB의 FAQ와 벡터만 독립 TELME-121 DB에 복사한다."""
import hashlib
import json
import pathlib
import subprocess

DEST = pathlib.Path(__file__).resolve().parents[2] / ".measure" / "telme121" / "faq-snapshot"
SOURCE = "telme"
TARGET = "telme_121_verification"


def sql(database, query, data=None):
    return subprocess.run(["docker", "exec", "-i", "telme-flow-postgres", "psql", "-U", "telme",
                           "-d", database, "-v", "ON_ERROR_STOP=1", "-Atc", query],
                          input=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True).stdout


def main():
    if not TARGET.startswith("telme_121_") or SOURCE == TARGET:
        raise RuntimeError("독립 평가 DB만 대상으로 사용할 수 있습니다.")
    if int(sql(TARGET, "SELECT count(*) FROM faqs")) > 2:
        raise RuntimeError("FAQ 스냅샷이 이미 있거나 대상 DB가 비어 있지 않습니다.")
    DEST.mkdir(parents=True, exist_ok=True)
    results = {}
    for table in ("faqs", "faq_embeddings"):
        names = sql(SOURCE, "SELECT column_name FROM information_schema.columns "
                    f"WHERE table_name='{table}' ORDER BY ordinal_position").decode().splitlines()
        target_names = sql(TARGET, "SELECT column_name FROM information_schema.columns "
                           f"WHERE table_name='{table}' ORDER BY ordinal_position").decode().splitlines()
        if names != target_names:
            raise RuntimeError(f"{table} 스키마가 달라 자동 복사를 중단합니다.")
        columns = ",".join(names)
        condition = "created_by IS NULL AND updated_by IS NULL" if table == "faqs" else (
            "faq_id IN (SELECT faq_id FROM faqs WHERE created_by IS NULL AND updated_by IS NULL)")
        data = sql(SOURCE, f"COPY (SELECT {columns} FROM {table} WHERE {condition} ORDER BY faq_id) TO STDOUT WITH CSV")
        (DEST / f"{table}.csv").write_bytes(data)
        sql(TARGET, f"COPY {table} ({columns}) FROM STDIN WITH CSV", data)
        results[table] = {"sha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}
    sql(TARGET, "SELECT setval(pg_get_serial_sequence('faqs','faq_id'),(SELECT max(faq_id) FROM faqs))")
    results["sourceDatabase"] = SOURCE
    results["targetDatabase"] = TARGET
    results["copiedFaqCount"] = int(sql(SOURCE, "SELECT count(*) FROM faqs WHERE created_by IS NULL AND updated_by IS NULL"))
    (DEST / "manifest.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
