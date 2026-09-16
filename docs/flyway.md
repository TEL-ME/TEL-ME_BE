# Flyway 마이그레이션 가이드

이 프로젝트는 `spring.jpa.hibernate.ddl-auto: none`이라, **스키마를 바꾸는 유일한 방법은 `src/main/resources/db/` 아래에 SQL 마이그레이션 파일을 추가하는 것**입니다. JPA 엔티티만 고쳐서는 DB에 아무 변화도 생기지 않습니다.

## 1. 파일이 어디에 있는지

| 위치 | 내용 | 운영(AWS RDS)에도 적용되나 |
| --- | --- | --- |
| `src/main/resources/db/migration` | 실제 스키마 (테이블·제약조건·인덱스·트리거) | 적용됨 |
| `src/main/resources/db/dev-migration` | 로컬 개발용 샘플 데이터 (`V7__seed_sample_data.sql`) | **적용 안 됨** |

`application-prod.yml`이 `spring.flyway.locations`를 `classpath:db/migration`만 남기도록 덮어써서, `dev-migration`은 로컬(기본 프로파일)에서만 붙습니다. 운영 DB에 테스트용 가짜 유저/매장 데이터가 들어갈 걱정은 안 해도 됩니다.

파일명 규칙은 `V{버전}__{설명}.sql`이고, 버전 번호는 두 폴더를 합쳐서 유일해야 합니다(현재 1~7 사용 중, 다음 스키마 변경은 `V8__...`).

## 2. 평소에 적용하는 법

그냥 앱을 실행하면 됩니다. Spring Boot가 기동할 때 Flyway를 자동으로 돌립니다.

```bash
./gradlew bootRun
```

로그에 이렇게 뜨면 성공입니다.

```
Migrating schema "public" to version "7 - seed sample data"
Successfully applied 7 migrations to schema "public", now at version v7
...
Started TelmeApplication in ...
```

이미 다 적용된 상태로 다시 실행하면 `Schema "public" is up to date. No migration necessary.`만 뜨는 게 정상입니다.

## 3. Flyway CLI를 직접 쓰고 싶을 때 (info / validate / clean)

이 프로젝트엔 Flyway Gradle 플러그인을 안 넣어서 `./gradlew flywayMigrate` 같은 태스크는 없습니다. 대신 podman으로 공식 Flyway CLI 이미지를 그때그때 띄워서 씁니다 (버전은 `build.gradle`의 `hibernate-vector`처럼 Spring Boot 3.5.16이 관리하는 `flyway.version`인 **11.7.2**에 맞춥니다).

매번 옵션을 다 치기 번거로우면 쉘 함수로 등록해두세요 (`~/.zshrc` 등에 추가):

```bash
flyway() {
  podman run --rm --network host \
    -v "$(pwd)/src/main/resources/db/migration:/flyway/sql/migration:ro" \
    -v "$(pwd)/src/main/resources/db/dev-migration:/flyway/sql/dev-migration:ro" \
    flyway/flyway:11.7.2 \
    -url=jdbc:postgresql://localhost:5432/telme -user=telme -password=telme \
    -locations=filesystem:/flyway/sql/migration,filesystem:/flyway/sql/dev-migration \
    "$@"
}
```

(반드시 프로젝트 루트에서 실행. `.env`를 커스텀했다면 `-url`/`-user`/`-password`도 거기에 맞춰서 바꿔주세요.)

자주 쓰는 명령:

```bash
flyway info      # 지금까지 적용된 버전 / 앞으로 적용될 버전 표 형태로 확인
flyway validate  # 이미 적용된 마이그레이션 파일이 그 사이에 수정되지 않았는지 체크섬 검증
flyway migrate   # bootRun 없이 마이그레이션만 적용 (DB만 먼저 세팅해두고 싶을 때)
```

## 4. 중간에 꼬였을 때 — DB 비우고 V1부터 다시

**가벼운 리셋 (스키마/롤/확장은 그대로, 테이블만 전부 삭제 후 재생성)**

```bash
flyway -cleanDisabled=false clean migrate
```
`clean`은 `public` 스키마의 테이블·함수·트리거를 전부 지우는 명령이라 기본적으로 막혀 있습니다(`-cleanDisabled=false`를 명시적으로 줘야 동작). **로컬 전용 명령이고, 이 프로젝트에서 AWS RDS를 이 CLI로 직접 가리킬 일은 없어야 합니다.**

**완전 초기화 (컨테이너 볼륨째 삭제 — Postgres 계정/확장까지 전부 새로)**

```bash
podman-compose down -v   # 컨테이너 + 볼륨 삭제
podman-compose up -d postgres
./gradlew bootRun        # V1부터 순서대로 다시 적용됨
```

무엇을 쓸지 기준: 마이그레이션 SQL 자체를 실험하다 꼬였다 → `clean migrate`. Postgres 컨테이너/볼륨 상태 자체가 의심스럽다 → 볼륨째 삭제.

## 5. 새 마이그레이션 추가할 때 지켜야 할 것

- **이미 적용된 버전 파일(V1~V7)은 절대 내용을 고치지 마세요.** `flyway validate`가 체크섬 불일치로 바로 실패합니다. 고칠 게 있으면 새 버전 파일(`V8__...sql`)을 추가하세요. Flyway Community Edition은 자동 롤백(undo)을 지원하지 않아서, 되돌리기도 "새 마이그레이션으로 되돌리는 SQL을 추가"하는 방식입니다.
- 정말로 체크섬이 틀어졌을 때만(예: 협업 중 실수로 이미 커밋된 파일을 고쳤다가 원상복구한 경우) `flyway repair`로 `flyway_schema_history`의 체크섬을 현재 파일 기준으로 다시 계산시킬 수 있습니다. 평소 워크플로우에서 쓸 일은 거의 없어야 합니다.
- 여러 명이 동시에 브랜치 작업할 때 버전 번호가 겹칠 수 있습니다. PR 올리기 직전에 `develop` 기준으로 다음 버전 번호가 맞는지 한 번 더 확인하세요.

## 6. AWS RDS에 적용할 때

명령 자체는 동일하고, 접속 정보만 RDS로 바꾸면 됩니다.

```bash
flyway() {
  podman run --rm --network host \
    -v "$(pwd)/src/main/resources/db/migration:/flyway/sql/migration:ro" \
    flyway/flyway:11.7.2 \
    -url="jdbc:postgresql://<RDS 엔드포인트>:5432/<DB명>?sslmode=require" \
    -user=<RDS 마스터 유저> -password=<비밀번호> \
    -locations=filesystem:/flyway/sql/migration \
    "$@"
}
```

`dev-migration`(샘플 데이터)은 마운트/locations에서 아예 뺐습니다 — 운영에는 스키마만 적용해야 합니다. 비밀번호를 커맨드라인에 평문으로 넘기지 말고 `-password`는 빼고 실행 시 `FLYWAY_PASSWORD` 환경변수로 주입하는 걸 권장합니다 (AWS Secrets Manager/SSM에서 받아와 그 자리에서 export).
