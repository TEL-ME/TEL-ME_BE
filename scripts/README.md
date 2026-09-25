# FAQ 생성·검증·측정 스크립트

기준 문서: `docs/POLICY.md`, `docs/FAQ_TAXONOMY.md`
측정 결과·결정 근거: `docs/SEARCH_TUNING.md`, `docs/TOPK_LATENCY.md`(top-k별 정답률·지연시간), `docs/SEARCH_FAILURE_ANALYSIS.md`(실패 원인 분류)

| 스크립트 | 역할 | Ollama |
| --- | --- | --- |
| `generate_faq.py` | 카테고리 × 질문유형 × 페르소나 조합표 생성 (문장 없음) | - |
| `check_policy.py` | 답변 수치를 정책 항목 값과 대조 | - |
| `check_duplicates.py` | 임베딩 유사도로 중복 쌍 탐지 | 필요 |
| `check_eval_questions.py` | 평가셋 형식·정답 매핑 검증 | `--live`만 |
| `measure_search_quality.py` | 평가셋을 검색 API에 돌려 Recall@k·MRR 계산 | 서버 경유 |
| `analyze_search_grid.py` | 원시 결과로 구성 × top-k × 임계값 격자 계산 | - |
| `classify_search_failures.py` | 원시 결과의 실패를 질문 쪽 / 문서 쪽으로 분류, 개선 전후 비교 | 필요 |
| `make_selfretrieval_eval.py` | 자기검색 평가셋 생성 | - |
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
- `question`·`answer`는 사람이 채움
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
  - 실제로 답변이 사실상 같은 FAQ 233쌍이 `question`·`both` 검사를 통과한 사례 있음 (`docs/SEARCH_TUNING.md` 11절)
- `--threshold` 기본 0.95, `--batch` 기본 50
- 임계값 미만이어도 최고 유사도는 출력

## 4. 평가셋 검증

```bash
python3 scripts/check_eval_questions.py scripts/data/eval_questions_130.json --faq scripts/data/faq_full_1150.json
python3 scripts/check_eval_questions.py scripts/data/eval_questions_30.json --live
python3 scripts/check_eval_questions.py --self-test
```

정답 매핑

- 정답은 `faq_id`가 아니라 `expected_content_hash` = `SHA-256(question + answer)`
  - `faq_id`는 재적재 시 새로 발급되지만 `content_hash`는 문장 내용에만 의존
- 답변이 사실상 같은 FAQ가 여럿이면 배열로 기재 (모두 정답 처리)

정적 검사 (Ollama 불필요)

- `type` 값, 긍정 질문 해시의 실존 여부, 배열 내 중복
- `UNRELATED`의 해시·`expected_slot_id`가 `null`인지, `unrelated_kind`가 정의된 값인지
- `expected_slot_id`가 해시가 가리키는 FAQ와 일치하는지
- 대칭성: SIMILAR/VARIANT 건수 일치, 카테고리별 건수 균등

`--live` (Ollama 필요)

- 긍정 질문을 임베딩해 정답 FAQ가 최고 유사도인지 확인, `UNRELATED` 유사도 분포 출력

`--self-test`

- 일부러 틀린 픽스처로 지적 15종(`STATIC_KINDS`)이 모두 검출되는지 확인
- 검사 종류 추가 시 `STATIC_KINDS`와 픽스처에 함께 반영

## 5. 적재 (Java)

```bash
./gradlew bootJar
java -jar build/libs/telme-0.0.1-SNAPSHOT.jar \
  --faq.batch-load.enabled=true --faq.batch-load.path=scripts/data/faq_full_1150.json
```

- 해시 계산 규칙이 Python·Java로 갈라지지 않도록 적재는 애플리케이션이 담당
- 같은 파일 재실행 안전 (이미 적재된 `content_hash`는 건너뜀). 중간 실패 시 재실행하면 이어서 진행
- **단일 프로세스로만 실행.** 중복 판정 기준이 적재 직전 조회한 `content_hash` 목록이라 동시 실행 시 중복 적재 가능
- `slot_id`·`question_type`·`persona`·`trigger`·`extra_policy_refs`는 적재 시 제외

### 전량 재임베딩

```bash
java -jar build/libs/telme-0.0.1-SNAPSHOT.jar --server.port=0 \
  --faq.reembed.enabled=true --faq.embedding-text.variant=Q_A
```

- 임베딩 텍스트 구성(`faq.embedding-text.variant`)을 바꾸면 기존 벡터가 전부 무효
- `content_hash`는 질문·답변에서만 나오므로 적재 로더로는 갱신되지 않음
- 1,150건 기준 약 70초. 건너뛰기 없음 → 재실행은 처음부터
- 단일 프로세스로만 실행
- ⚠ **dev 시드 FAQ 2건(`faq_id` 1, 2)도 덮어씀**
  - 시드 임베딩은 고정 패턴이고 `FaqEmbeddingRepositoryTest`·`FaqSearchApiIntegrationTest`가 이를 전제
  - 로컬에서 두 테스트가 깨지면 `dev-migration/V2__seed_sample_data.sql`의 벡터를 다시 넣을 것
  - CI는 DB를 새로 생성하므로 영향 없음

## 6. 검색 품질 측정

```bash
# 측정용 서버 (임계값 해제)
FAQ_SEARCH_TEST_API_ENABLED=true SEARCH_SIMILARITY_THRESHOLD=0 \
  java -jar build/libs/telme-0.0.1-SNAPSHOT.jar --server.port=18090

python3 scripts/measure_search_quality.py scripts/data/eval_questions_130.json \
  --api-url http://localhost:18090/api/v1/faq/search --top-k 10 \
  --by-category --dump-json .measure/raw-1150-Q_A.json

python3 scripts/measure_search_quality.py --self-test
```

- **순위 실험은 `SEARCH_SIMILARITY_THRESHOLD=0`으로 측정.** 임계값을 켜면 랭킹 품질과 임계값 컷이 한 숫자에 혼재

주요 옵션

| 옵션 | 설명 |
| --- | --- |
| `--top-k` | 기본 3 |
| `--api-url` | 기본 `http://localhost:8080/api/v1/faq/search` |
| `--timeout` | 기본 20초 (서버 `embedding.search-read-timeout` 15초보다 커야 함) |
| `--by-category` | `expected_slot_id` 접두사로 묶어 카테고리별 Recall·MRR 출력 |
| `--dump-json <경로>` | 질문별 top-k 원시 결과 저장. 임계값·top-k 스윕을 오프라인 계산할 때 필수 |
| `--experiment` / `--change` / `--owner` | 실험 기록표용 한 줄 출력 |

출력 진단

- 임계값 0 측정 시 "정답 hit score(최소·중앙값)"와 "UNRELATED top-1 최댓값" 출력
  - 정답 최소 > 무관 최댓값이면 그 사이가 임계값 후보. 겹치면 분리 가능한 단일 값 없음
- `unrelated_kind`별 거부율 분리 출력
- 정답 미검출 질문을 `eval_id`로 나열. 같은 정답을 공유하는 질문이 모두 실패하면 별도 표시
  - "적재 누락"과 "top-k 밖으로 밀림" 구분은 `content_hash`로 `faqs` 조회 필요
- 요청마다 응답 시간(초)도 재서 평균·p95(ms)를 같이 찍는다(요청 전송~응답 수신 구간만, JSON 파싱
  등은 제외). topK 값을 바꿔가며 Recall 개선폭과 지연시간 증가폭을 같이 비교할 때 쓴다(TELME-59).

## 7. 격자 분석

```bash
python3 scripts/analyze_search_grid.py .measure/raw-1150-*.json
```

- 입력: `--dump-json` 결과만. DB·Ollama 불필요
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

- 입력: `--dump-json` 결과(임계값 0 수집). 평가셋 경로는 원시 결과에 기록된 값을 쓰고, 정답 FAQ 질문은 `--faq`(기본 `faq_full_1150.json`)에서 `content_hash`로 찾음. DB·서버 불필요
- **저장소 루트에서 실행.** 원시 결과에 평가셋 경로가 상대경로로 기록돼 있음. 다른 위치에서 실행하면 `--eval`로 지정
- 결과가 빈 문항이 있으면(임계값을 켠 채 수집) 분류하지 않고 멈춤
- 그룹: 1등 정답 여부 × 1등 점수 ≥ `--threshold`(기본 0.72) → A 정상 / B 정답인데 점수 미달 / C 1등부터 오답 / D 오답인데 통과
- 원인: 사용자 질문과 정답 FAQ 질문의 유사도가 기준선 이상이면 문서 쪽(B는 답변 희석, C·D는 비슷한 FAQ에 밀림), 미만이면 질문 쪽
- 기준선: 생략하면 첫 파일의 A 그룹 최솟값. **전후 비교 때는 `--cutoff`로 고정** — 기준선이 움직이면 비교가 안 됨
- 파일을 여러 개 넣으면 원인별 건수 비교표와 원인이 바뀐 문항 목록 출력
- 결과·해석: `docs/SEARCH_FAILURE_ANALYSIS.md`

---

## 자기 검증

| 스크립트 | 케이스 |
| --- | --- |
| `check_policy.py` | 20건 |
| `check_duplicates.py` | 2건 |
| `check_eval_questions.py` | 15종 |
| `measure_search_quality.py` | 12건 (Recall/MRR 7 + 카테고리 2 + 지연시간 3) |
| `classify_search_failures.py` | 16건 (그룹 판정 5 + 원인 판정 6 + 정답 전달 판정 3 + top-k 범위 2, 경계값 포함) |

- 통과만으로는 검사가 실제로 도는지 알 수 없어 일부러 틀린 건을 넣어 검출 여부를 확인
- 문서 파싱에서 표를 못 찾거나 행 수가 기대와 다르면 0건 처리 대신 `DocumentError` 발생

## 산출물

| 파일 | 내용 |
| --- | --- |
| `data/faq_slots_1150.json` | 1,150건 조합표(문장 없음). `slot_id`로 추적 |
| `data/faq_full_300.json` | 1차 300건. `faq_sample_30.json` 30건을 문자 그대로 포함 |
| `data/faq_full_1150.json` | 전체 1,150건. 앞 300건은 `faq_full_300.json`과 동일 |
| `data/faq_sample_30.json` | 샘플 30건. 카테고리 10종 × 3건 |
| `data/eval_questions_30.json` | 평가 질문 30건. 긍정 20 + 무관 10 |
| `data/eval_questions_130.json` | 평가 질문 130건. 긍정 80 + 무관 50(완전무관 16 / 도메인인접 24 / 경계 10). 정답은 배열 |
| `data/eval_selfretrieval_1150.json` | 자기검색 평가셋 1,150건 |
| `data/eval_smoke.json` | API 통신 확인용 더미 2건. 품질 측정용 아님 |

데이터 규칙

- `faq_full_300.json` 유지 이유: 건수 증가에 따른 Recall 변화 측정 (300 → 1,150)
- 1차 300건은 2차에서 수정 금지. 수정 시 `content_hash`가 바뀌어 평가셋 매핑이 끊김
- `slot_id`·`question_type`·`persona`·`trigger`·`extra_policy_refs`는 생성·검증용 메타데이터. `faqs` 테이블 제외
- `version`은 적재 시 1, `content_hash`는 적재 시 애플리케이션이 계산
- 평가셋 선택 기준: 코퍼스 규모 비교에는 `eval_questions_30.json` 사용. `eval_questions_130.json`은 대상 FAQ 40건 중 6건만 300건 부분집합에 포함되어 규모 비교 불가
