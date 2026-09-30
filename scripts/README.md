# FAQ 생성, 검증, 측정 스크립트

기준 문서: `docs/POLICY.md`, `docs/FAQ_TAXONOMY.md`
측정 결과, 결정 근거: `docs/SEARCH_TUNING.md`, `docs/TOPK_LATENCY.md`(top-k별 정답률, 지연시간), `docs/SEARCH_FAILURE_ANALYSIS.md`(실패 원인 분류), `docs/EVAL_SET_SUPPLEMENT.md`(평가셋 보강, 이중 벡터 사전 검증)

| 스크립트 | 역할 | Ollama |
| --- | --- | --- |
| `generate_faq.py` | 카테고리 × 질문유형 × 페르소나 조합표 생성 (문장 없음) | - |
| `check_policy.py` | 답변 수치를 정책 항목 값과 대조 | - |
| `check_duplicates.py` | 임베딩 유사도로 중복 쌍 탐지 | 필요 |
| `check_eval_questions.py` | 평가셋 형식, 정답 매핑 검증 | `--live`만 |
| `check_eval_overlap.py` | 새 평가셋이 기존 평가셋과 겹치는지 검사 (질문 텍스트 유사도 + 정답 slot 겹침) | - |
| `measure_search_quality.py` | 평가셋을 검색 API에 돌려 Recall@k, MRR 계산 | 서버 경유 |
| `analyze_search_grid.py` | 원시 결과로 구성 × top-k × 임계값 격자 계산 | - |
| `classify_search_failures.py` | 원시 결과의 실패를 질문 쪽 / 문서 쪽으로 분류, 개선 전후 비교 | 필요 |
| `simulate_dual_vector.py` | `Q_A`, `QUESTION_ONLY` 원시 결과를 합쳐 이중 벡터를 임계값별로 시뮬레이션 | - |
| `make_selfretrieval_eval.py` | 자기검색 평가셋 생성 | - |
| `generate_stores.py` | 공공데이터 CSV → 매장 가상 데이터 + dev 시드 SQL | - |
| `check_stores.py` | 매장 데이터 제약, 분포 + 시드 SQL 대조 | - |
| `telme_docs.py` | 공통 문서 파서 | - |

## 준비

```bash
docker compose --profile ollama up -d        # 임베딩 쓰는 스크립트만
docker exec telme-ollama ollama pull bge-m3
pip install -r scripts/requirements.txt      # numpy (없어도 동작, 1,000건 이상에서 느림)
```

- 파이썬 3.11 이상
- 임베딩 캐시: `scripts/data/.embed_cache.json` (git 제외)

---

## 1. 조합표 생성

```bash
python3 scripts/generate_faq.py --summary
python3 scripts/generate_faq.py --out scripts/data/faq_slots_1150.json
python3 scripts/generate_faq.py --sample 30 --out scripts/data/faq_slots_30.json
```

- 출력: 조합별 목표 건수 + 인용할 정책 항목(`policy_ref`, 제목, 핵심 수치)
- `question`, `answer`는 사람이 채움
- `trigger`는 참고값. 정책 항목과 어색하면 무시 가능
- `COMPARE` 조합에만 `"extra_policy_refs": []`를 미리 넣어 줌

## 2. 정책 대조

```bash
python3 scripts/check_policy.py scripts/data/faq_sample_30.json
python3 scripts/check_policy.py --self-test
```

검사 항목

- 필수 필드 존재, 열거값이 문서 정의와 일치, `policy_ref`가 해당 카테고리 항목인지
- 답변 수치가 허용값 안에 있는지 - 허용값 = (`policy_ref` ∪ `extra_policy_refs`)의 정책 블록 ∪ 정책 값 색인 ∪ 공통 전제
- `extra_policy_refs`가 문자열 배열이고 `question_type`이 `COMPARE`인지, 실제로 인용했는지

수치 추출

- 단위가 붙은 값만 대상 (`7,700원` `2~3 영업일` `24개월` `50GB` `09:00` `5.9%`)
- 단위 없는 맨숫자 제외 (`5G` `114` `1588-0000`)
- 단위 목록은 `telme_docs.py`의 `_UNITS`. 교대(`|`) 앞쪽이 먼저 매칭되므로 `개월` > `월`, `영업일` > `일` 순서 유지

`COMPARE` 교차 인용

- `COMPARE` 답변은 정책 항목을 둘 이상 인용 (`FAQ_TAXONOMY.md` 2절)
- 대표 항목만 `policy_ref`에 적으면 나머지 항목의 정상 수치가 "정책에 없는 수치"로 검출됨
- 인용한 나머지를 `extra_policy_refs`에 기재하면 허용값에 포함

## 3. 중복 탐지

```bash
python3 scripts/check_duplicates.py scripts/data/faq_full_1150.json
python3 scripts/check_duplicates.py scripts/data/faq_full_1150.json --field answer
python3 scripts/check_duplicates.py --self-test
```

- `--field`: `question`(기본) / `answer` / `both` - **세 가지를 모두 실행**
  - 질문만 같은 중복과 답변만 같은 중복은 서로 검출되지 않음
  - 실제로 답변이 사실상 같은 FAQ 233쌍이 `question`, `both` 검사를 통과한 사례 있음 (`docs/SEARCH_TUNING.md` 11절)
- `--threshold` 기본 0.95, `--batch` 기본 50
- 임계값 미만이어도 최고 유사도는 출력

## 4. 평가셋 검증

```bash
python3 scripts/check_eval_questions.py scripts/data/eval_questions_130.json --faq scripts/data/faq_full_1150.json
python3 scripts/check_eval_questions.py scripts/data/eval_questions_supplement_50.json --faq scripts/data/faq_full_1150.json --live
python3 scripts/check_eval_questions.py scripts/data/eval_questions_30.json --live
python3 scripts/check_eval_questions.py --self-test
```

평가셋 파일

| 파일 | 구성 | 용도 |
| --- | --- | --- |
| `eval_questions_130.json` | SIMILAR 40 / VARIANT 40 / UNRELATED 50 | 확정값 기준 평가셋. 기존 문서 수치와 비교하려면 수정하지 않는다 |
| `eval_questions_supplement_50.json` | ANSWER 30 / UNRELATED(`ADJACENT_HARD`) 20 | 보강 평가셋 (`docs/EVAL_SET_SUPPLEMENT.md`). 130건과 함께 측정 |
| `eval_questions_holdout_90.json` | UNRELATED(`ADJACENT_HARD`) 90, 카테고리당 9 | 검색 확인용 세트 (`docs/DUAL_VECTOR_VS_RERANKER.md` 13.1절). 임계값 확정값을 처음 보는 질문으로 한 번만 확인한다. **임계값 재탐색에 쓰지 않는다** |

질문 유형

- `SIMILAR` / `VARIANT`: FAQ 질문의 가벼운 / 큰 변형
- `ANSWER`: FAQ 질문 변형이 아니라 **답변에만 있는 값, 용어로 묻는 질문**. 긍정 질문으로 집계
- `UNRELATED`: 답이 없어야 하는 질문. `unrelated_kind`는 `OFF_DOMAIN` / `ADJACENT` / `ADJACENT_HARD`

정답 매핑

- 정답은 `expected_slot_id` (TELME-73)
  - `faq_id`는 재적재 시 새로 발급되고, `content_hash`는 FAQ 내용을 고치면 바뀐다. `slot_id`는 둘 다 영향을 받지 않는다
- 답변이 사실상 같은 FAQ가 여럿이면 배열로 기재 (모두 정답 처리)
- `expected_content_hash`는 참고용으로 남겨 둔다. 적혀 있으면 정적 검사가 `slot_id`의 FAQ 내용과 맞는지 대조한다

정적 검사 (Ollama 불필요)

- `type` 값, 긍정 질문 `slot_id`의 실존 여부(`--faq` 파일 기준), 배열 내 중복, 배열 안 카테고리 일치
- `UNRELATED`의 `expected_slot_id`, 해시가 `null`인지, `unrelated_kind`가 정의된 값인지
- `expected_content_hash`가 적혀 있으면 `slot_id`가 가리키는 FAQ 내용과 일치하는지
- 대칭성: SIMILAR/VARIANT 건수 일치, 카테고리별 건수 균등. `ANSWER`는 짝이 없어 ANSWER끼리 카테고리별 건수만 따로 본다

`--live` (Ollama 필요)

- SIMILAR/VARIANT: 정답 FAQ(배열이면 그중 하나)의 질문이 최고 유사도인지 확인
- `ANSWER`: 실패로 거르지 않고 "FAQ 질문으로도 커버됨 / 답변에만 있음"으로 참고 분류만 출력
  - 이 분류로 문항을 고르거나 고치지 않는다. 정답 FAQ 질문 유사도는 `QUESTION_ONLY` 검색 점수와 같은 계산이라, 이 값으로 고르면 평가셋이 그 구성에 불리하게 기운다
- `UNRELATED` 유사도 분포 출력

`--self-test`

- 일부러 틀린 픽스처로 지적 17종(`STATIC_KINDS`)이 모두 검출되는지 확인
- ANSWER + UNRELATED만 있는 정상 평가셋에서 지적이 없는지(오탐) 확인
- 검사 종류 추가 시 `STATIC_KINDS`와 픽스처에 함께 반영

## 5. 적재 (Java)

```bash
./gradlew bootJar
java -jar build/libs/telme-0.0.1-SNAPSHOT.jar \
  --faq.batch-load.enabled=true --faq.batch-load.path=scripts/data/faq_full_1150.json
```

- 해시 계산 규칙이 Python, Java로 갈라지지 않도록 적재는 애플리케이션이 담당
- 같은 파일 재실행 안전 (이미 적재된 `slot_id`는 건너뜀). 중간 실패 시 재실행하면 이어서 진행
- **이미 있는 `slot_id`는 JSON 내용이 달라도 덮어쓰지 않음.** FAQ 원본은 DB(관리자 수정)라서, JSON을 고쳐도 적재된 DB에는 반영되지 않는다
- `slot_id`가 없는 기존 행(TELME-73 이전 적재)은 `content_hash`가 같으면 새로 넣지 않고 `slot_id`만 채움. 벡터는 그대로
- `slot_id`가 비어 있는 항목이 하나라도 있으면 DB를 건드리기 전에 실패
- **단일 프로세스로만 실행.** 중복 판정 기준이 적재 직전 조회한 `slot_id` 목록이라 동시 실행 시 중복 적재 가능
- `slot_id`는 `faqs.slot_id`에 저장. `question_type`, `persona`, `trigger`, `extra_policy_refs`는 적재 시 제외

### 전량 재임베딩

```bash
java -jar build/libs/telme-0.0.1-SNAPSHOT.jar --server.port=0 \
  --faq.reembed.enabled=true --faq.embedding-text.variant=Q_A
```

- 임베딩 텍스트 구성(`faq.embedding-text.variant`)을 바꾸면 기존 벡터가 전부 무효
- `content_hash`는 질문, 답변에서만 나오므로 적재 로더로는 갱신되지 않음
- **1,150건 약 140초.** 벡터를 두 벌 만들어서다(아래 절). 건너뛰기 없음, 재실행은 처음부터
- **질문 벡터가 빈 DB는 이 명령으로 채운다.** `embedding_question`이 NULL인 행은 이중 벡터 검색에서 빠진다
- 단일 프로세스로만 실행
- ⚠ **dev 시드 FAQ 2건(`faq_id` 1, 2)도 덮어씀**
  - 시드 임베딩은 고정 패턴이고 `FaqEmbeddingRepositoryTest`, `FaqSearchApiIntegrationTest`가 이를 전제
  - 로컬에서 두 테스트가 깨지면 `dev-migration/V2__seed_sample_data.sql`의 벡터를 다시 넣을 것
  - CI는 DB를 새로 생성하므로 영향 없음

### FAQ 벡터는 두 벌이다

FAQ 한 건마다 벡터를 둘 저장한다. 검색이 둘 다 조회해 합친다(이중 벡터, TELME-77/79/76/83).

| 컬럼 | 임베딩한 텍스트 | 잘 잡는 질문 |
| --- | --- | --- |
| `embedding` | `faq.embedding-text.variant` 구성(기본 `Q_A` = 질문 + 답변) | 답변 내용을 묻는 질문 |
| `embedding_question` | 질문만(`QUESTION_ONLY` 고정) | 표현을 바꿔 묻는 질문 |

**왜 두 벌인가.** `Q_A`는 답변이 훨씬 길어 벡터가 답변 단어에 끌려간다. 뜻은 같은데 말투가 다른 질문을 놓친다. 질문끼리 비교하는 벡터를 하나 더 두면 그걸 잡는다. 전환이 아니라 병행인 이유는, `QUESTION_ONLY`로 바꾸면 답변에만 있는 값을 묻는 질문을 잃기 때문이다. 근거는 `docs/SEARCH_TUNING.md` 15절.

두 번째 구성은 **설정으로 열지 않고 코드에 고정**했다. 임베딩 구성이 설정 축 2개가 되면 측정할 조합이 배로 늘어난다.

**쓰는 경로가 3곳이다.** 셋 다 두 벡터를 같은 트랜잭션에서 쓴다.

| 경로 | 언제 | 실행 |
| --- | --- | --- |
| 배치 적재 | JSON 최초 적재 | `faq.batch-load.enabled=true` |
| 전량 재임베딩 | 구성 변경, 질문 벡터 백필 | `faq.reembed.enabled=true` |
| 단건 동기화 | 관리자가 FAQ를 등록, 수정 | 관리자 API가 자동 호출 |

한 경로라도 빠뜨리면 그 경로로 들어온 행만 `embedding_question`이 NULL로 남고, 질문 벡터 검색에서 통째로 빠진다.

**비용.** 청크마다 임베딩을 2회 부른다(본문 1회 + 질문 1회). 한 호출당 건수는 50건 그대로다.

| 항목 | 이전 | 이후 |
| --- | --- | --- |
| 1,150건 재임베딩 | 약 70초 | **약 140초** |
| 청크당 HTTP 호출 | 1회 | 2회 |
| 검색 시 DB 조회 | 1회 | 2회 (임베딩 호출은 1회 그대로) |

`variant`가 이미 `QUESTION_ONLY`면 두 텍스트가 같아 1회만 부르고 같은 벡터를 양쪽에 쓴다(약 70초).

**정합성 확인.** 백필 후, 그리고 대량 적재 후에 돌린다.

```bash
docker exec telme-postgres psql -U telme -d telme -c \
  "select count(*) total,
          count(*) filter (where embedding is null)                   qa_null,
          count(*) filter (where embedding_question is null)          qo_null,
          count(*) filter (where embedding = embedding_question)      same,
          count(*) filter (where sync_status <> 'SYNCED')             not_synced
     from faq_embeddings e join faqs f using (faq_id) where f.status = 'ACTIVE';"
```

`total`을 뺀 나머지가 전부 **0**이어야 한다.

- `qo_null > 0`: 질문 벡터가 빈 행이 있다. 전량 재임베딩을 돌린다
- `same > 0`: 두 컬럼에 **같은 벡터**가 들어갔다. 이중 벡터가 성립하지 않는다. `variant`가 `QUESTION_ONLY`가 아닌데 이 값이 나오면 쓰기 경로 버그다
- `not_synced > 0`: 재임베딩이 중간에 끊겼다. 다시 돌린다

stale 벡터(FAQ는 수정됐는데 재임베딩이 안 끝난 상태)도 같이 본다.

```bash
docker exec telme-postgres psql -U telme -d telme -tAc \
  "select count(*) from faq_embeddings e join faqs f using (faq_id)
    where e.faq_version <> f.version;"
```

**코퍼스 지문.** 재임베딩은 `embedding`(`Q_A`)도 다시 계산한다. 기존 측정치와 비교하려면 지문이 같아야 한다. 기준값은 `docs/EVAL_SET_SUPPLEMENT.md` 4.1절.

```bash
docker exec telme-postgres psql -U telme -d telme -tAc \
  "select count(*), md5(string_agg(e.embedding::text, ',' order by e.faq_id))
     from faq_embeddings e join faqs f using (faq_id)
    where f.policy_ref not like 'POLICY-%';"
```

**되돌리기.** 이중 벡터 조회는 환경변수로 끈다. 저장된 벡터를 지우거나 다시 임베딩할 필요는 없지만, `search.dual-vector.enabled`는 기동 시 한 번 읽는 설정이라(`SearchProperties` record, 런타임 재적용 없음) **애플리케이션 재시작이 필요하다.**

```
SEARCH_DUAL_VECTOR_ENABLED=false
```


## 6. 검색 품질 측정

```bash
# 측정용 서버 (임계값 해제). 질문 벡터(--vector QUESTION)는 임계값이 따로라 둘 다 0으로 푼다
FAQ_SEARCH_TEST_API_ENABLED=true SEARCH_SIMILARITY_THRESHOLD=0 SEARCH_DUAL_VECTOR_QUESTION_THRESHOLD=0 \
  java -jar build/libs/telme-0.0.1-SNAPSHOT.jar --server.port=18090

python3 scripts/measure_search_quality.py scripts/data/eval_questions_130.json \
  --api-url http://localhost:18090/api/v1/faq/search --top-k 10 \
  --by-category --dump-json .measure/raw-1150-Q_A.json

python3 scripts/measure_search_quality.py --self-test
```

- **순위 실험은 임계값을 0으로 풀고 측정.** 임계값을 켜면 랭킹 품질과 임계값 컷이 한 숫자에 혼재
  - `SEARCH_SIMILARITY_THRESHOLD`는 질문+답변 벡터(`QA`)에만 적용된다. 질문 벡터(`QUESTION`)는 `SEARCH_DUAL_VECTOR_QUESTION_THRESHOLD`(기본 0.88)로 잘리므로 함께 0으로 푼다
- 정답은 검색 응답의 `slotId`로 비교. 응답에 `slotId`가 없으면(TELME-73 이전 서버) 바로 중단
- 정답 `slot_id`가 있는 평가셋인데 검색 결과의 `slotId`가 전부 null이면(로더를 아직 안 돌린 DB) Recall 0.000을 내지 않고 중단. 7, 9절 스크립트도 이런 원시 결과는 거부
- 정답 `slot_id`가 없는 긍정 질문(`eval_smoke.json`)은 API 호출만 하고 Recall, MRR에서 뺀 뒤 건수만 표시

주요 옵션

| 옵션 | 설명 |
| --- | --- |
| `--top-k` | 기본 3 |
| `--api-url` | 기본 `http://localhost:8080/api/v1/faq/search` |
| `--timeout` | 기본 20초 (서버 `embedding.search-read-timeout` 15초보다 커야 함) |
| `--vector QA\|QUESTION\|DUAL` | 검색에 쓸 벡터. 생략하면 서버 설정(`search.dual-vector.enabled`)을 따름. 이중 벡터 임계값 재탐색은 `QA`, `QUESTION`을 따로 수집하고, 원시 결과에 수집 벡터가 기록됨 |
| `--by-category` | `expected_slot_id` 접두사로 묶어 카테고리별 Recall, MRR 출력 |
| `--dump-json <경로>` | 질문별 top-k 원시 결과(순위, 점수, `slot_id`, `content_hash`) 저장. 임계값, top-k 스윕을 오프라인 계산할 때 필수. TELME-73 이전에 수집한 파일은 `slot_id`가 없어 7, 9절 스크립트가 거부하므로 다시 수집 |
| `--experiment` / `--change` / `--owner` | 실험 기록표용 한 줄 출력 |

출력 진단

- 임계값 0 측정 시 "정답 hit score(최소, 중앙값)"와 "UNRELATED top-1 최댓값" 출력
  - 정답 최소 > 무관 최댓값이면 그 사이가 임계값 후보. 겹치면 분리 가능한 단일 값 없음
- `unrelated_kind`별 거부율 분리 출력
- 정답 미검출 질문을 `eval_id`로 나열. 같은 정답을 공유하는 질문이 모두 실패하면 별도 표시
  - "적재 누락"과 "top-k 밖으로 밀림" 구분은 `slot_id`로 `faqs` 조회 필요
- 요청마다 응답 시간(초)도 재서 평균, p95(ms)를 같이 찍는다(요청 전송~응답 수신 구간만, JSON 파싱
  등은 제외). topK 값을 바꿔가며 Recall 개선폭과 지연시간 증가폭을 같이 비교할 때 쓴다(TELME-59).

### 이중 벡터 임계값 재탐색

위 측정용 서버(두 임계값 0)에서 벡터별 원시 결과를 따로 모은 뒤, 오프라인으로 질문 벡터 임계값을 고른다. 130건과 보강 50건을 **함께** 넣어야 무관 거부와 답변형 손실 조건을 같이 본다.

```bash
for f in eval_questions_130:130 eval_questions_supplement_50:supp50; do
  python3 scripts/measure_search_quality.py scripts/data/${f%%:*}.json \
    --api-url http://localhost:18090/api/v1/faq/search --top-k 10 \
    --vector QA --dump-json .measure/raw-${f##*:}-Q_A.json
  python3 scripts/measure_search_quality.py scripts/data/${f%%:*}.json \
    --api-url http://localhost:18090/api/v1/faq/search --top-k 10 \
    --vector QUESTION --dump-json .measure/raw-${f##*:}-QUESTION_ONLY.json
done

python3 scripts/simulate_dual_vector.py \
  --qa .measure/raw-130-Q_A.json .measure/raw-supp50-Q_A.json \
  --qo .measure/raw-130-QUESTION_ONLY.json .measure/raw-supp50-QUESTION_ONLY.json \
  --qa-threshold 0.72 --qo-thresholds 0.85,0.87,0.88,0.89,0.90,0.92,0.95 \
  --order qo --top-k 3
```

- `--qa`와 `--qo`는 **같은 평가셋**이어야 한다(다르면 멈춘다). 원시 결과에 수집 벡터가 기록돼, 둘을 바꿔 넣어도 멈춘다
- **`--qo-thresholds`는 쉼표 구분이다.** 공백으로 나누면 인자 오류
- 출력: 임계값별 기존 긍정 / `ANSWER` / 무관 거부 / 경계 무관 거부, 그리고 선택 규칙에 맞는 t
- 선택 규칙(`docs/DUAL_VECTOR_VS_RERANKER.md` 3.3절): 무관 거부를 평가셋(입력 파일)마다 현행 이상으로 지키고 `ANSWER` 정답 수가 줄지 않는 t 중 정답 합계(기존 긍정 + `ANSWER`) 최대, 같으면 높은 t. 현행에서 맞았는데 놓친 문항은 고르는 조건이 아니라 결과 끝에 "주의"로 따로 출력
- 파일 이름에 `QUESTION_ONLY`가 없으면 경고가 뜬다. 코퍼스 구성을 `EVAL_SET_SUPPLEMENT.md` 4.1절 지문으로 확인하라는 뜻이다
- 고른 값은 운영 임계값으로 서버를 다시 띄워 `--vector DUAL`로 실측해 시뮬레이션과 맞는지 확인한다

## 7. 격자 분석

```bash
python3 scripts/analyze_search_grid.py .measure/raw-1150-*.json
```

- 입력: `--dump-json` 결과만. DB, Ollama 불필요
- 계산: 구성 × top-k(1/3/5/10) × 임계값(0.55~0.85) 조합 전체
- 출력: 최적 조합, 구성별 최선, 민감도(거부율 하한 0.90/0.95/1.00), 임계값별 추이
- 선택 규칙: 무관 거부율 하한 이상에서 Recall@3 최대 (`--min-rejection`으로 조정)
- 수집 오염 탐지: 임계값 0 수집인데 top-k보다 적게 반환된 문항이 있으면 경고

## 8. 자기검색 평가셋

```bash
python3 scripts/make_selfretrieval_eval.py
```

- `faq_full_1150.json`의 `question`을 그대로 쿼리로 사용하는 평가셋 생성 (카테고리당 65~160건)
- 용도: 카테고리 내부 혼동도 측정 (유사 질문 견고성 아님)
- 제약: `QUESTION_ONLY` 구성에서는 쿼리와 문서가 같은 문자열이라 유사도 1.0으로 포화

## 9. 실패 원인 분류

```bash
python3 scripts/classify_search_failures.py .measure/raw-1150-Q_A.json --list
python3 scripts/classify_search_failures.py .measure/raw-before.json .measure/raw-after.json \
  --threshold 0.72,<개선 후 임계값> --cutoff 0.8152
python3 scripts/classify_search_failures.py --self-test
```

- 입력: `--dump-json` 결과(임계값 0 수집). 평가셋 경로는 원시 결과에 기록된 값을 쓰고, 정답 FAQ 질문은 `--faq`(기본 `faq_full_1150.json`)에서 `slot_id`로 찾음. DB, 서버 불필요
- **저장소 루트에서 실행.** 원시 결과에 평가셋 경로가 상대경로로 기록돼 있음. 다른 위치에서 실행하면 `--eval`로 지정
- 결과가 빈 문항이 있으면(임계값을 켠 채 수집) 분류하지 않고 멈춤
- 그룹: 1등 정답 여부 × 1등 점수 ≥ `--threshold`(기본 0.72) → A 정상 / B 정답인데 점수 미달 / C 1등부터 오답 / D 오답인데 통과
- 원인: 사용자 질문과 정답 FAQ 질문의 유사도가 기준선 이상이면 문서 쪽(B는 답변 희석, C, D는 비슷한 FAQ에 밀림), 미만이면 질문 쪽
- 기준선: 생략하면 첫 파일의 A 그룹 최솟값. **전후 비교 때는 `--cutoff`로 고정**: 기준선이 움직이면 비교가 안 됨
- 파일을 여러 개 넣으면 원인별 건수 비교표와 원인이 바뀐 문항 목록 출력
- 결과, 해석: `docs/SEARCH_FAILURE_ANALYSIS.md`

---

## 10. 매장 가상 데이터

```bash
# 1) 공공데이터 CSV → 매장 JSON + dev 시드 SQL
python3 scripts/generate_stores.py <시도별 CSV...> \
  --out scripts/data/stores.json --sql src/main/resources/db/dev-migration/V<다음버전>__seed_stores.sql

# 2) 검증 (제약 + 분포 + 시드 SQL 대조)
python3 scripts/check_stores.py
python3 scripts/check_stores.py --self-test

# 3) JSON만 손본 경우 SQL 재생성 (CSV 불필요)
python3 -c "import json,sys; sys.path.insert(0,'scripts'); import generate_stores as g; \
  open('src/main/resources/db/dev-migration/V7__seed_stores.sql','w',encoding='utf-8')\
  .write(g.to_sql(json.load(open('scripts/data/stores.json',encoding='utf-8'))))"
```

**데이터 출처**

- 소상공인시장진흥공단 상가(상권)정보, **2026-06 판본** (`소상공인시장진흥공단_상가(상권)정보_20260630.zip`)
- 공공데이터포털 <https://www.data.go.kr/data/15083033/fileData.do> - 회원가입 후 파일 다운로드
- 압축을 풀면 시도별 CSV 16개(강원, 경기, 경남, 경북, 대구, 대전, 부산, 서울, 세종, 울산, 인천, 전남광주, 전북, 제주, 충남, 충북). 합계 약 1.5GB라 저장소에 넣지 않는다
- 가져오는 값은 좌표, 도로명주소, 법정동코드뿐이다. 상호는 `텔미 {시군구}{n}호점`으로 만들고, 영업시간, 가능 업무는 시드로 생성하며, 전화번호는 넣지 않는다(`phone` NULL - `check_stores.py`가 NULL인지 검사한다)
- 주소, 좌표는 공공데이터 기반이며 실제 TEL-ME 매장이 아니다. 상호, 영업시간, 가능 업무는 실제 업체 정보와 무관하다

**입력 순서와 재현성**

- 파일 인자 순서대로 읽고 파일당 앞 `--max-per-file`(기본 3000)건의 업종 조건에 걸리는 후보만 모은다
- 따라서 입력 파일 목록이나 순서가 다르면 같은 `--seed`로도 결과가 달라진다. 시도 파일명 오름차순(위 목록 순서)을 기준으로 둔다
- 업종 필터 기본값은 `핸드폰 소매|통신기기 소매|이동통신` (`--industry`로 변경). 수리업, 중고 소매업은 걸리지 않는다
- 컬럼명은 판본마다 달라 못 찾으면 `--lat-col` 등으로 지정한다
- 커밋된 `scripts/data/stores.json`은 위 기준으로 생성한 뒤 `phone`만 NULL로 정리한 판본이다. CSV에서 다시 생성하면 난수 스트림이 달라져 업무, 영업시간, 상태 배분이 바뀐다. 지금 데이터의 분포를 유지해야 하면 3)으로 SQL만 다시 뽑는다
- 시드 SQL은 손으로 고치지 않는다. `check_stores.py`가 `to_sql(stores.json)`과 파일을 대조해 불일치를 잡는다
- 이미 머지된 시드 SQL은 재생성하지 않는다. Flyway 체크섬이 깨져 앱이 기동하지 않는다. 데이터가 바뀌면 새 버전 파일을 추가한다 (`docs/flyway.md`)

---

## 자기 검증

| 스크립트 | 케이스 |
| --- | --- |
| `check_policy.py` | 20건 |
| `check_duplicates.py` | 2건 |
| `check_eval_questions.py` | 17종 + 오탐 2건(정상 문항, 정상 평가셋) + 정답 slot 집합 3건(문자열, 배열, null) + slot 미적재 검사 4건(전부 null, smoke, 일부 null, 결과 없음) |
| `check_eval_overlap.py` | 16건 (토큰 2 + 텍스트 유사도 5: 동일, 무관, 빈 문자열, 실제 제외 쌍 2 + 정답 겹침 4 + 정답 집합 3: 다중 라벨 합산, 문자열, null + 최근접 탐색 1 + 내부 중복 1) |
| `measure_search_quality.py` | 14건 (Recall/MRR 9 + 카테고리 2 + 지연시간 3) |
| `generate_stores.py` | 10건 (영업시간 3 + 좌표 3 + 업무 1 + SQL 이스케이프 2 + 범위 1) |
| `check_stores.py` | 14건 (필드 6 + 영업시간 4 + 업무 3 + 중복 1) |
| `classify_search_failures.py` | 17건 (그룹 판정 6: 문자열 정답 포함 + 원인 판정 6 + 정답 전달 판정 3 + top-k 범위 2, 경계값 포함) |
| `simulate_dual_vector.py` | 29건 (합치기 8: 우선순위, 중복 제거, 3개 컷, 임계값 경계 + 성공 판정 4 + 커버됨 분류 3, 문자열, 배열 정답 모두 + 최적 t 선택 8: 정상, 동점이면 높은 t, 평가셋별 무관 상쇄 제외, ANSWER 포함 합계, 기존 긍정 0건, 무관 0건, ANSWER 수 감소 제외, 잃고 얻으면 수 기준 통과 + 구성 경고 2 + 수집 벡터 확인 4) |

- 통과만으로는 검사가 실제로 도는지 알 수 없어 일부러 틀린 건을 넣어 검출 여부를 확인
- 문서 파싱에서 표를 못 찾거나 행 수가 기대와 다르면 0건 처리 대신 `DocumentError` 발생

## 산출물

| 파일 | 내용 |
| --- | --- |
| `data/faq_slots_1150.json` | 1,150건 조합표(문장 없음). `slot_id`로 추적 |
| `data/faq_full_300.json` | 1차 300건. `faq_sample_30.json` 30건을 문자 그대로 포함 |
| `data/faq_full_1150.json` | 전체 1,150건. 앞 300건은 `faq_full_300.json`과 동일 |
| `data/faq_sample_30.json` | 샘플 30건. 카테고리 10종 × 3건. `slot_id`는 1,150건 파일의 같은 FAQ와 동일 (TELME-73 전에는 `BILLING-S01` 형식) |
| `data/eval_questions_30.json` | 평가 질문 30건. 긍정 20 + 무관 10. 샘플 30, 300, 1,150건 어느 코퍼스로도 측정 가능 |
| `data/eval_questions_130.json` | 평가 질문 130건. 긍정 80 + 무관 50(완전무관 16 / 도메인인접 24 / 경계 10). 정답은 배열 |
| `data/eval_questions_holdout_90.json` | 검색 확인용 90건. 경계 무관만. `H-001~100`이 1차 60건, `H-101~130`이 2차 보강 30건 |
| `data/eval_selfretrieval_1150.json` | 자기검색 평가셋 1,150건 |
| `data/eval_smoke.json` | API 통신 확인용 더미 2건. 품질 측정용 아님. 정답이 dev 시드 FAQ(`slot_id` 없음)라 Recall 집계에서 빠짐 |

데이터 규칙

- `faq_full_300.json` 유지 이유: 건수 증가에 따른 Recall 변화 측정 (300 → 1,150)
- 1차 300건은 2차에서 수정 금지. 로더가 이미 있는 `slot_id`를 건너뛰어 수정이 적재된 DB에 반영되지 않고, 평가셋의 `expected_content_hash`(참고용)와도 어긋남
- 같은 FAQ는 모든 파일에서 같은 `slot_id`. 다르면 이어서 적재할 때 같은 내용이 새 행으로 한 번 더 들어감
- `question_type`, `persona`, `trigger`, `extra_policy_refs`는 생성, 검증용 메타데이터. `faqs` 테이블 제외 (`slot_id`는 `faqs.slot_id`에 저장)
- `version`은 적재 시 1, `content_hash`는 적재 시 애플리케이션이 계산
- 평가셋 선택 기준: 코퍼스 규모 비교에는 `eval_questions_30.json` 사용. `eval_questions_130.json`은 대상 FAQ 40건 중 6건만 300건 부분집합에 포함되어 규모 비교 불가
