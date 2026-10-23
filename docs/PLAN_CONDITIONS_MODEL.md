# 요금제 목록 조건과 추천 대상을 분류 모델로 읽기

TELME-227. 요금제 표 답변의 목록 조건(망, 대상, 무제한, 뺄 요금제)과, 요금제 추천의 쓸 사람(본인 또는 가족), 그 사람의 나이대와 한 달 데이터 사용량을 질문의 낱말 대신 분류 모델(gpt-oss)이 읽게 했다. 모델을 못 쓰면(꺼짐, 호출 실패, 응답을 못 읽음) 지금처럼 낱말 규칙으로 읽는다.

## 배경

- 요금제 표 답변(`PlanCatalogAnswerer`)은 망, 대상, 무제한, 제외를 낱말 위치로 읽었다. 그래서 이유로 꺼낸 말과 뺀다는 말을 구분하지 못했다.
  - "LTE 요금제 몇 개예요? 5G는 안 터지는 동네라서요"를 5G와 LTE 7종으로 셈
  - "알뜰 같은 건 됐고요"에서 "됐고"를 제외로 못 읽어 알뜰만 보여줌
  - "무제한은 비싸서요 5G 언리미티드 아닌 걸로"에서 무제한 조건이 켜져 "찾지 못했어요"로 끝남
- 추천(`StatedRecommendationAnswers`)은 질문에 딸, 아버지 같은 가족 낱말이 있으면 나이와 사용량을 읽지 않았다. "고딩 딸 폰 새로 해주려는데 요금제 추천", "60대 초반이신 아버지 요금제"도 나이를 다시 물었다.
- 로그인한 회원이 가족 요금제를 물으면 추천이 회원 본인의 생년월일, 가입 요금제, 사용량으로 계산됐다.

## 바꾼 것

### 요금제 표 질문의 목록 조건
- 첫 분류 호출(`plan-question-v5`)은 그대로 둔다.
- 요금제를 고르지 않았고 목록에 없는 요금제도 아닌 요금제 표 질문에만, 목록 조건을 한 번 더 묻는다(`plan-list-conditions-v2`).
- 답변은 이 조건이 있으면 낱말 대신 그 값으로 거른다. 개수와 "가장 싼/비싼" 답에서 대상을 물었는지도 이 조건으로 본다. 가격 조건("5만원 이하", "3만원대")은 숫자라 지금처럼 낱말로 읽는다.

### 추천 질문의 쓸 사람, 나이대, 사용량
- 규칙이 질문에서 나이를 읽으면 본인 요금제로 보고 모델을 부르지 않는다. 규칙은 가족 이야기가 섞이면 읽지 않기 때문이다.
- 그 밖에는 두 호출을 동시에 보낸다.
  - 나이대: `plan-subscriber-age-v2`. 만 나이 경계(13~14세, 18~19세, 64~65세)에 걸치거나 범위만 말하면 모른다고 답하게 한다.
  - 쓸 사람과 한 달 사용량: `plan-subscriber-usage-v1`
- 모델 값은 규칙과 되묻기로 받은 값이 없을 때만 쓴다. 저장하지 않고, 되묻기 다음 턴에도 처음 질문으로 다시 읽는다(규칙과 같은 방식).
- 다른 사람이 쓸 요금제면 `MemberRecommendationAnswerer.recommendForOther`로 추천한다.
  - 회원의 생년월일, 가입 요금제, 사용량, 이 채팅방에서 앞서 한 답을 쓰지 않는다.
  - 가입·변경 링크와 회원 사용량 안내는 회원 본인 회선 기준이라 붙이지 않는다.
- 모델이 읽은 나이대로 추천하면 답 끝에 기준 나이대를 밝힌다. 예: "말씀하신 내용을 보고 만 65세 이상 기준으로 추천했어요. 나이대가 다르면 나이와 함께 다시 물어봐 주세요." 생년월일이 있어 그 나이대를 쓰지 않았으면 밝히지 않는다.
- 실행 기록: 목록 조건은 `planQuestionClassifications`의 `conditions`, 추천 대상은 `planSubscribers`.

## 측정 방법

| 항목 | 내용 |
|---|---|
| 기준 | develop `379ac076` |
| 모델 | Bedrock gpt-oss-120b(도쿄) |
| 블라인드 | 구현을 보지 않은 에이전트 5명이 세트마다 따로 씀(요금제 목록 48문장, 묶음 가격 24문장, 추천 나이 40문장, 가족 사용량 32문장, 경계 나이 30문장). TELME-217 블라인드 첫 턴 120문장도 함께 씀 |
| 분류와 답 | `PlanCatalogReplayProbe`(분류기와 요금제 표 답), `SubscriberReplayProbe`(규칙과 모델의 추천 대상). 3회씩 |
| 앱 | 품질셋 307턴 1회, 흐름셋 93턴 1회. 측정 DB(5440, FAQ와 임베딩 1,152개) |

품질셋의 develop 기준은 TELME-225에서 develop `86c9df2a`로 잰 캡처다(TELME-225 결과의 `captures.tar.xz`). 그 뒤 develop에 들어온 것은 길찾기 변경(#207)뿐이다. 흐름셋은 develop `379ac076`으로 다시 쟀다.

## 결과

### 요금제 표 (3회 합)

| 묶음 | develop | 브랜치 |
|---|---|---|
| 새 블라인드 목록 | 37/87 | 58/87 |
| 새 블라인드 개수 | 8/33 | 23/33 |
| 새 블라인드 이름 상세 | 18/24 | 18/24 |
| 묶음 가격 블라인드 | 42/72 | 62/72 |
| 217 블라인드 이름 상세 | 66/72 | 66/72 |
| 217 블라인드 판매 안 하는 요금제 | 48/54 | 47/54 |
| 217 블라인드 개수 / 목록 | 42/54, 35/45 | 45/54, 42/45 |

- 개수가 23/33으로 줄어든 회차 차이는 대부분 첫 분류 호출이 흔들린 것이다. 이 호출은 develop과 같은 프롬프트이고, develop 측정에서도 같은 문장이 같은 식으로 흔들렸다.
- 앱 품질셋에서 요금제 표나 추천 답이 바뀐 턴 중 나빠진 것은 없었다. 좋아진 예: "일반 요금제는 몇 개씩, 청소년이나 시니어도 포함되는지"에 "총 15종" 대신 "일반 9종, 청소년 3종, 시니어 3종은 따로".

### 추천 대상 (3회 합)

| 묶음 | develop(규칙만) | 브랜치 |
|---|---|---|
| 추천 나이 블라인드 | 63/120 | 107/120 |
| 가족 사용량 블라인드, 나이대 | 48/96 | 93/96 |
| 가족 사용량 블라인드, 사용량 | 63/96 | 90/96 |
| 가족 사용량 블라인드, 본인 여부 | 없음 | 84/84 |
| 경계 나이 블라인드 | 51/90 | 70/90 |

- 흐름셋: "60대 초반이신 아버지 요금제"는 나이를 묻지 않고 추천한다. "곧 대학 가는 동생"과 "중2 아들"은 develop처럼 나이를 묻는다. 다른 사람 요금제라 "로그인하고 요금제에 가입하시면…" 안내가 빠진다.

### 시도했다가 바꾼 것
- 목록 조건을 첫 분류 응답에 칸으로 붙임: 이름을 말한 요금제를 조건으로 옮기거나("언리미티드 요금제 월 얼마임" → 무제한 목록), 목록에 없는 요금제 질문을 NONE으로 바꾸는 일이 늘었다(217 이름 상세 66 → 57/72, 판매 안 함 48 → 38/54). 그래서 호출을 나눴다.
- 목록 조건 v1은 목록과 개수만 말해 "시니어 요금제는 한 달에 얼마고 데이터는 몇 기가"의 조건을 비웠다(품질셋). v2에서 묶음의 가격과 데이터 질문도 조건으로 받게 했다.
- 추천 대상을 한 호출로 받음: 나이를 알 수 없는 가족("엄마가 60대", "시어머니", "중1 아들")에도 나이대를 넣었다(다시 물어야 할 문장 36 → 25/45). 그래서 나이대를 따로 묻는다.
- 나이대 v1은 "곧 대학 가는 동생"(만 18세)을 GENERAL로 넣어 일반 요금제를 추천했다(흐름셋). v2에서 경계를 모름으로 두게 했다.

## 남은 한계

- 경계 나이 블라인드에서 다시 물어야 할 42회 중 18회는 나이대를 넣었다("엄마가 60대 후반", "작년에 정년퇴직", "군대 가기 전"). 실제 비율보다 높을 것으로 보지만(경계만 모은 세트), 틀리면 다른 나이대 요금제를 추천한다. 그래서 답에 기준 나이대를 밝힌다.
- 사용량을 다른 사람 것으로 읽는 경우가 있다("저는 한 달에 30기가 쓰는데 엄마 요금제"에서 30GB를 엄마 사용량으로 읽은 회차).
- 첫 분류 호출의 흔들림(이름을 말한 요금제를 빈칸으로 냄, 목록에 없는 요금제를 NONE으로 냄)은 고치지 않았다.
- 되묻기 중에 앞 답을 고치는 말("데이터 말고 나이 20이요")의 해석은 다루지 않았다.

## 응답 시간과 비용

- 목록 조건 호출은 요금제를 고르지 않은 요금제 표 질문에서만 늘어난다. 품질셋, 흐름셋 기준 p50 0.45초, 최대 1.5초.
- 추천 대상 호출은 규칙이 나이를 못 읽은 추천 질문에서만 늘어난다. 두 호출을 동시에 보내 앱 캡처 p50 0.35~0.37초, 최대 0.91초. probe에서는 최대 5.4초가 있었다.
- Bedrock 비용은 측정 전체 약 $3.4로 추정한다. 앱 캡처 7벌이 $2.15(토큰 기록 × 도쿄 단가), probe가 약 $1.3(호출 수로 추정)이다. 실제 청구액은 확인하지 않았다.

## 재현

```bash
# 측정 DB 확인: 1152가 아니면 멈춘다
docker exec telme-flow-postgres psql -U telme -d <DB> -Atc "select count(embedding_question) from faq_embeddings"
# 요금제 표 (입력은 [{id, question}] JSON)
POSTGRES_PORT=5440 POSTGRES_DB=<DB> AWS_PROFILE=telme-bedrock-eval LLM_PROVIDER=ollama LLM_BEDROCK_ENABLED=true \
LLM_BEDROCK_REGION=ap-northeast-1 PLAN_CATALOG_PROBE=true PLAN_CATALOG_RUNS=3 \
PLAN_CATALOG_INPUT=<절대 경로>/input.json PLAN_CATALOG_OUT=<절대 경로>/out.jsonl \
  ./gradlew test --tests '*PlanCatalogReplayProbe' --rerun
# 추천 대상
POSTGRES_PORT=5440 POSTGRES_DB=<DB> AWS_PROFILE=telme-bedrock-eval LLM_PROVIDER=ollama LLM_BEDROCK_ENABLED=true \
LLM_BEDROCK_REGION=ap-northeast-1 SUBSCRIBER_PROBE=true SUBSCRIBER_RUNS=3 \
SUBSCRIBER_INPUT=<절대 경로>/input.json SUBSCRIBER_OUT=<절대 경로>/out.jsonl \
  ./gradlew test --tests '*SubscriberReplayProbe' --rerun
# 채점 (저장된 결과로 바로 다시 계산할 수 있다)
cd scripts/plan-question-evaluation
python -I score_plan_catalog.py results/telme227/blind-sentences.json.gz:labels_plan_catalog \
  results/telme227/probe-results.json.gz:plan_catalog/develop results/telme227/probe-results.json.gz:plan_catalog/conditions_v2
python -I score_subscriber.py results/telme227/blind-sentences.json.gz:boundary_age_blind \
  results/telme227/probe-results.json.gz:subscriber/boundary/two_calls_age_v2
# 앱 캡처 비교 (captures.tar.xz를 풀어서)
python -I compare_plan_capture.py <develop 캡처 폴더들(,로)> <branch 캡처 폴더들(,로)>
```

앱 캡처는 `scripts/chat_judge/run_chat_pipeline_eval.py --capture-only --record-raw`로 찍었다(품질셋 `chat_pipeline_quality_v2_2.json`을 셋으로 나눠 동시에, 흐름셋 `chat_pipeline_flows_v2.json`).

## 파일

| 파일 | 내용 |
|---|---|
| `src/test/java/com/telme/probe/PlanCatalogReplayProbe.java` | 분류기와 요금제 표 답을 회차별로 |
| `src/test/java/com/telme/consult/service/SubscriberReplayProbe.java` | 규칙과 모델이 읽은 추천 대상을 회차별로 |
| `scripts/plan-question-evaluation/score_plan_catalog.py` | 요금제 표 답 채점 |
| `scripts/plan-question-evaluation/score_subscriber.py` | 추천 대상 채점 |
| `scripts/plan-question-evaluation/compare_plan_capture.py` | 앱 캡처 비교(요금제 답이 바뀐 턴, 추가 호출 시간, 비용) |
| `scripts/plan-question-evaluation/results/telme227/blind-sentences.json.gz` | 블라인드 문장과 라벨 |
| `scripts/plan-question-evaluation/results/telme227/probe-results.json.gz` | probe 결과(develop, 시도한 변형, 최종) |
| `scripts/plan-question-evaluation/results/telme227/captures.tar.xz` | 앱 캡처. 품질셋 `m227-b-*`(목록 조건 v1), `m227-b2-*`(v2), `m227-b3-*`(v2와 추천 대상, 나이대 v1). 흐름셋 `m227-d-flows`(develop), `m227-b3-flows`(나이대 v1), `m227-b4-flows`(나이대 v2). `m227-b-blind`(목록 조건 v1, 새 블라인드 48문장). 기준 나이대 안내를 넣은 마지막 커밋은 단위 테스트로만 확인했다 |
