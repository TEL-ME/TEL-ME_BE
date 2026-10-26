# RDS PostgreSQL 16 이 제공하는 pgvector 버전에 맞추고, 매장 검색(V15)이 쓰는 PostGIS를 더한다
FROM pgvector/pgvector:0.8.2-pg16
RUN apt-get update \
 && apt-get install -y --no-install-recommends postgresql-16-postgis-3 \
 && rm -rf /var/lib/apt/lists/*
