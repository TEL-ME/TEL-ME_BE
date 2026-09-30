# 최근접 매장 검색 운영·확장 가이드 (TELME-68)

최근접 매장 검색을 RDS에 올리거나 조건을 더할 때 무엇을 해야 하는지 정리한다. 왜 이렇게 설계했는지는 `docs/STORE_NEAREST_SEARCH.md`(특히 11절)에 있다.

현재 구조를 한 줄로 요약하면 다음과 같다.

| 요청 | 경로 | 인덱스 |
| --- | --- | --- |
| 필터 없음 | `StoreNearbyQueryRepository.findNearest` (반경 없는 KNN) | `idx_stores_geog_open` (V15) |
| 정적 조건만 (업무 종류 등) | `findNearestMatching` (태그 인덱스 KNN → 반경) | `idx_stores_geog_tags` (V16) |
| 동적 조건 섞임 (영업 중) | `findNearestCandidatesMatching` → 모자라면 `findMatchingWithinRadius` | 위 두 인덱스 + `store_hours` 기본키 |

동적 조건(영업 중)은 구현돼 있지만 `store.search.open-now-filter-enabled=false`로 꺼져 있다(3절).

## 1. RDS에 적용하기 전: 확장 설치 (마스터 계정)

V15가 `postgis`, V16이 `intarray` 확장을 만든다. 앱(Flyway) 계정에는 확장 생성 권한이 없을 수 있어서, **마스터 계정으로 먼저 설치**한다. 두 마이그레이션 모두 `CREATE EXTENSION IF NOT EXISTS`라 미리 설치돼 있으면 그대로 지나간다.

```sql
-- 마스터 계정으로, 앱이 쓰는 DB에 접속해서 실행
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS intarray;

-- 확인: 두 줄이 나와야 한다
SELECT extname, extversion FROM pg_extension WHERE extname IN ('postgis', 'intarray');
SELECT postgis_full_version();
```

- 순서: 확장 설치 → 앱 배포(기동 시 Flyway가 V15, V16 적용)
- 설치를 빼먹으면 앱 기동 중 Flyway가 V15(또는 V16)에서 `permission denied to create extension` 으로 실패하고 앱이 뜨지 않는다
- 확장은 앱 계정의 `search_path`에 있는 스키마(기본 `public`)에 설치돼야 한다. 다른 스키마에 설치했다면 앱 계정의 `search_path`에 그 스키마를 넣는다
- `intarray`는 PostgreSQL 기본 배포(contrib)에 들어 있어 별도 패키지가 필요 없다. 로컬(`docker/postgres/Dockerfile`)과 CI는 이미 준비돼 있다
- `intarray`를 설치하면 `int[]`의 `@>`, `&&` 같은 연산자가 intarray 쪽 연산자로 바뀐다. 현재 저장소에서 `int[]`에 이 연산자를 쓰는 곳은 매장 태그뿐이다. 나중에 `int[]` 컬럼에 GIN 인덱스(`array_ops`)를 만들면 인덱스를 못 쓸 수 있으니 그때 이 점을 확인한다
- 로컬(PostGIS 이미지)은 앱 계정이 슈퍼유저라 따로 할 일이 없다

## 2. 정적 조건 추가하기 (예: "주차 가능", "외국어 상담")

정적 조건은 매장 태그 배열(`stores.tags int[]`)의 번호 하나다. 조건을 더해도 **인덱스(`idx_stores_geog_tags`)는 바꾸지 않는다.** 조건 종류가 늘어도 인덱스 하나로 버티는 것은 가상 속성 30종·동시 10개 선택까지 실측했다(`STORE_NEAREST_SEARCH.md` 11.3).

### 2.1 바꿀 것 한눈에

| 층 | 파일 / 객체 | 할 일 |
| --- | --- | --- |
| DB 원천 | 새 테이블 (예: `store_attributes(store_id, code)`) | 조건의 원천 데이터를 둔다. `store_id` 컬럼이 있어야 2.2의 트리거를 그대로 쓸 수 있다 |
| DB 함수 | `store_tags(BIGINT)` (V16) | 새 마이그레이션에서 `CREATE OR REPLACE FUNCTION`으로 원천 테이블을 `UNION ALL`하고 새 태그 번호로 바꾼다 |
| DB 트리거 | `sync_store_tags()` (V16) | 함수는 그대로 쓰고, 새 원천 테이블에 같은 트리거를 하나 더 건다. 함수가 태그를 고치는 동안에는 매장 수정 시각(`updated_at`)을 바꾸지 않는다 |
| DB 데이터 | `stores.tags` | 새 마이그레이션 끝에서 기존 매장 태그를 다시 계산한다 |
| Java 태그 | `store/repository/StoreTag.java` | 새 상수와 번호를 더한다(예: `PARKING(101)`). 이미 쓴 번호는 절대 바꾸지 않는다 |
| Java 요청 | `store/dto/req/StoreNearbySearchRequest.java` | 새 조건을 받을 필드를 더한다(예: `Set<StoreAttribute.Code> attributes`) |
| Java 서비스 | `store/service/StoreSearchService.toConditions()` | 새 필드를 `StoreTag`로 바꿔 업무 종류 태그와 **한 목록으로 합친 뒤** `StoreTagCondition.of(...)`를 한 번만 만든다. null 원소 검증도 업무 종류처럼 한다 |
| Java 오류 | `store/exception/StoreErrorCode.java` | 입력 검증 오류가 필요하면 다음 번호(`STORE400-5`)를 더한다 |
| 테스트 | `StoreTagConditionTest`, `StoreNearbyQueryRepositoryDatabaseTest`, `StoreSearchServiceTest` | 번호 중복, 트리거 동기화(`트리거가_태그를_맞춘다` 참고), Java·DB 번호 일치(`태그_번호는_Java와_DB가_같다` 참고), 요청 → 조건 변환 |
| 관리자 | 관리자 매장 API (TELME-71 계열) | 원천 테이블 입력·수정 화면과 API는 관리자 파트와 맞춘다 |

`StoreNearbyQueryRepository`, `StoreTagCondition`, 인덱스는 바꾸지 않는다.

### 2.2 마이그레이션 예시

번호는 `docs/flyway.md` 1절의 명령으로 직접 센다.

```sql
-- V{다음}__add_store_attributes.sql
CREATE TABLE store_attributes (
    store_id BIGINT      NOT NULL REFERENCES stores (store_id) ON DELETE CASCADE,
    code     VARCHAR(40) NOT NULL,          -- PARKING / FOREIGN_LANGUAGE ...
    PRIMARY KEY (store_id, code)
);

-- 태그 번호는 StoreTag(Java)와 같아야 한다. 업무 종류는 1~4, 매장 속성은 101부터 쓴다
CREATE OR REPLACE FUNCTION store_tags(target_store_id BIGINT) RETURNS INT[]
    LANGUAGE sql STABLE AS
$$
SELECT COALESCE(array_agg(DISTINCT t.tag ORDER BY t.tag), '{}')
FROM (SELECT CASE sst.code
                 WHEN 'NEW_LINE' THEN 1
                 WHEN 'PORT_IN' THEN 2
                 WHEN 'NAME_CHANGE' THEN 3
                 WHEN 'USIM_REISSUE' THEN 4
                 END AS tag
      FROM store_services ss
      JOIN store_service_types sst ON sst.service_type_id = ss.service_type_id
      WHERE ss.store_id = target_store_id
      UNION ALL
      SELECT CASE sa.code
                 WHEN 'PARKING' THEN 101
                 WHEN 'FOREIGN_LANGUAGE' THEN 102
                 END
      FROM store_attributes sa
      WHERE sa.store_id = target_store_id) t
WHERE t.tag IS NOT NULL
$$;

CREATE TRIGGER trg_store_attributes_tags
    AFTER INSERT OR UPDATE OR DELETE ON store_attributes
    FOR EACH ROW
EXECUTE FUNCTION sync_store_tags();

UPDATE stores s SET tags = store_tags(s.store_id);
```

- 원천이 별도 테이블이 아니라 `stores`의 컬럼이면(예: `stores.parking_available`) `sync_store_tags()`를 그 컬럼에 걸면 안 된다. 함수가 `stores`를 다시 UPDATE해 트리거가 자기 자신을 부른다. 이 경우 `BEFORE INSERT OR UPDATE OF parking_available ON stores` 트리거에서 `NEW.tags`를 직접 계산한다. 가능하면 별도 테이블을 권한다
- 매장당 태그가 수백 개로 늘면 `gist__int_ops`가 커진 배열을 받지 못한다(`input array is too big`). 그때는 인덱스를 `gist__intbig_ops`로 다시 만들고 다시 측정한다. 지금(업무 4종 + 수십 종)은 필요 없다

### 2.3 성능 확인

인덱스 구조가 그대로라 조건을 하나 더할 때마다 다시 잴 필요는 없다. 최악은 "태그 하나하나는 흔한데 그 조합을 가진 매장이 없을 때"로, 전국 4만 곳에서 약 4.3ms다(11.3). 매장 수 상한(4만 곳)이 바뀌거나 매장당 태그 수가 크게 늘 때만 다시 잰다.

## 3. 동적 조건(영업 중) 켜기

구현과 측정(40,000곳 최악 약 21ms, `STORE_NEAREST_SEARCH.md` 11.6)은 끝났다. 켜지 않은 이유는 **데이터**다. `store_hours`는 요일별 7행뿐이라 공휴일·임시 휴무를 표현할 수 없고, 영업시간이 없는 매장은 "영업 중 아님"으로 빠진다.

### 3.1 켜기 전 확인할 데이터

```sql
-- 영업시간 입력률: 7행이 모두 있는 영업 매장의 비율
SELECT count(*) FILTER (WHERE h.cnt = 7) * 100.0 / count(*) AS full_week_pct,
       count(*) FILTER (WHERE h.cnt IS NULL) AS no_hours
FROM stores s
LEFT JOIN (SELECT store_id, count(*) AS cnt FROM store_hours GROUP BY store_id) h ON h.store_id = s.store_id
WHERE s.status = 'OPEN';

-- 휴무가 아닌데 시각이 비어 있는 행(영업 중 판단에서 빠진다)
SELECT count(*) FROM store_hours WHERE NOT is_closed AND (open_time IS NULL OR close_time IS NULL);
```

### 3.2 공휴일·임시 휴무를 더할 때 바꿀 것

| 층 | 파일 / 객체 | 할 일 |
| --- | --- | --- |
| DB 테이블 | 새 테이블 `store_hour_exceptions(store_id, exception_date, open_time, close_time, is_closed)`, 기본키 `(store_id, exception_date)` | 날짜별 예외 영업시간. 공휴일은 매장마다 행을 넣거나(관리자·배치), 전국 공휴일 테이블 + 매장별 공휴일 정책 컬럼으로 나눌 수 있다. 기획과 정한다 |
| Java 조건 | `store/repository/OpenNowCondition.java` | 레코드에 날짜(`LocalDate date`)를 더한다(`at(LocalDateTime)`이 이미 날짜를 갖고 있다). `toSql()`은 "오늘 날짜 예외 행이 있으면 그 행으로, 없으면 요일 행으로" 판단하도록 바꾼다. 자정을 넘기는 영업은 전날 날짜 예외 행도 본다. `parameters()`에 `openNowDate`, `openNowPreviousDate`를 더한다 |
| Java 서비스 | `store/service/StoreSearchService.openNowCondition()` | 바꿀 것 없음. 이미 한국 시각(`STORE_ZONE`)으로 `LocalDateTime`을 넘긴다 |
| 설정 | `application.yml`(환경별) `store.search.open-now-filter-enabled: true` | 켜는 스위치. 기본값은 `StoreSearchProperties.openNowFilterEnabled`(`false`) |
| API | 사용자 매장 검색 API(컨트롤러 미구현) | 요청의 `openNow` 필드(`StoreNearbySearchRequest`)를 노출한다. 꺼져 있을 때는 `STORE400-4`로 거부한다 |
| 테스트 | `OpenNowConditionTest`, `StoreNearbyQueryRepositoryDatabaseTest`의 `영업_중_*` 테스트 | 예외 행 우선, 예외 휴무, 예외 날짜 자정 넘김, 예외 없는 날은 요일 행 그대로 |

### 3.3 켠 뒤 확인

- 실제 영업시간 데이터로 성능을 다시 잰다. 시간은 "가까운 후보 중 영업 중 비율"과 "반경 안 매장 수"로 정해지고, 반경 10km·6,000곳이 상한이다(측정 도구는 `STORE_NEAREST_SEARCH.md` 10절)
- `store_hour_exceptions`를 더했다면 조건이 매장마다 기본키 조회를 한 번 더 하므로 최악(전국 휴무) 시간이 늘어난다. 11.6의 최악 약 21ms와 비교한다
