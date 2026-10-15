# 라우팅 LLM 호출별 EXAONE·gpt-oss 비교 (중간 정리)

라우팅 단계의 LLM 호출을 호출 순서대로 하나씩 EXAONE 3.5 7.8B와 Bedrock gpt-oss-120b로 바꿔 보고, 호출마다 어느 모델을 쓸지 정하는 작업(TELME-175)의 중간 정리다. "이득이 없는 호출은 바꾸지 말자"는 팀 의견에 따라 호출별 근거를 남긴다. 앞 단계에서 정한 모델은 다음 단계에서 고정한다.

| 단계 | 호출 | 언제 도는가 | 상태 |
| --- | --- | --- | --- |
| 1 | 의도 분류 (`ROUTING_SYSTEM_PROMPT`) | 새 질문마다 | **gpt-oss 확정** |
| 2 | 요청 개수 판정 (`REQUEST_INVENTORY_PROMPT`) | 의도 분류가 FAQ 하위 질문을 2개 이상 만들었을 때 | **gpt-oss 확정**, 프롬프트 v4로 수정 |
| 3 | 검색어 검증 (`FAQ_QUERY_FAITHFULNESS_PROMPT`) | 복합 하위 질문, 원문 검색 결과가 없어 다시 검색할 때 | 남음 |
| 4 | 후속 분석 (`FOLLOW_UP_SYSTEM_PROMPT`) | 되물은 질문에 고객이 답했을 때 | 남음 |

판정 기준은 "gpt-oss가 확실히 나쁘지 않으면 gpt-oss"로 잡았다. 이 기준은 팀 확인이 남아 있다.

## 측정 방식

- 앱 코드의 라우팅 서비스(`QueryRoutingService`)를 그대로 쓰는 프로브([RoutingEvaluationProbe](../src/test/java/com/telme/probe/RoutingEvaluationProbe.java))로 잰다. 호출마다 보낼 주소를 따로 정할 수 있다(`TELME_ROUTING_INTENT_URL`, `TELME_ROUTING_INVENTORY_URL`, `TELME_ROUTING_FAITHFULNESS_URL`, 나머지는 `TELME_ROUTING_BASE_URL`).
- EXAONE은 로컬 Ollama, gpt-oss는 평가용 라우터(`scripts/judge-serving-evaluation/judge_router.py --chat bedrock --judge bedrock`, 도쿄)로 보낸다.
- gpt-oss는 같은 입력에도 결과가 흔들려 3회를 동시에 돌린다(`TELME_ROUTING_REPEATS=3`). EXAONE은 1회다.
- 1단계 채점은 앱의 UNKNOWN 재확인(#145)을 재현한다. 모델이 UNKNOWN이라 한 질문을 검색 테스트 API로 다시 검색해 걸리면 FAQ로 본다. 검색 임계값은 확정값 0.69/0.80이다.
- 원시 결과는 단계마다 `scripts/routing-call-comparison/results/<단계>/results.json.gz` 하나에 실행별로 담는다.

## 통합 평가셋 (365문항)

라우팅 평가 자료가 여러 파일로 나뉘어 있어 하나로 모았다([eval_routing_unified.json](../scripts/data/eval_routing_unified.json), 생성 스크립트 [build_eval_set.py](../scripts/routing-call-comparison/build_eval_set.py)). 출처는 검색 평가 180문항, 라우팅 32·홀드아웃 20·문맥 3, FAQ+매장 복합 16, 띄어쓰기·요청 개수(#120), 비교 14, 복합 FAQ 7이다.

| 의도 | 문항 | tuning | holdout | general |
| --- | --- | --- | --- | --- |
| FAQ | 210 (여러 요청 57) | 86 | 63 | 61 |
| STORE | 52 | 9 | 43 | 0 |
| BOTH | 51 | 25 | 26 | 0 |
| UNKNOWN | 52 | 9 | 25 | 18 |

- tuning은 라우팅 프롬프트를 고칠 때 보던 문장, holdout은 보지 않은 문장, general은 검색 평가용으로 만든 문장이다.
- 뺀 문항 235개와 이유는 파일의 `excluded`에 있다.
  - 띄어쓰기만 달라 모델 입력이 같은 문장: 26개. 앱이 라우팅 전에 한글 주변 공백을 지운다.
  - 프롬프트 예시와 같은 문장: 20개.
  - 뜻과 표현이 같은 쌍: 2개.
  - FAQ 비율 축소: 187개. 검색 평가 문항과 #120 FAQ 변형만 유형·업무 분류별 층화 무작위(seed 175)로 줄였다. 모델 결과를 보고 고르지 않았다.
- 새로 쓴 문항 137개는 holdout이다. STORE·BOTH·UNKNOWN 보강 77, 맥락 9, FAQ 복합 질문 30, 요청 개수 프롬프트 확인용 21이다. 라벨은 사람 검토 전이다(`TELME-175 신규(사람 검토 전)` 태그).
- 정답이 둘 이상일 수 있는 11문항은 `acceptable_intents`, `acceptable_request_counts`로 허용 정답을 적었다. 채점은 이 값으로 한다. 예: 지역 없는 "매장은 보통 몇 시까지"는 FAQ·STORE 모두 정답.
- 맥락 문항은 요약 문장이 아니라 이전 고객 발언(`previous_questions`)과 연결 여부(`context_linked`)로 적었다. 라우팅 프롬프트는 상담 요약을 쓰지 않고, 앱은 문맥 해석이 이전 발언을 연결했을 때만 그 발언과 복원 질문을 라우팅에 넘기기 때문이다.

## 1단계: 의도 분류 → gpt-oss

나머지 라우팅 호출은 EXAONE으로 두고 의도 분류만 바꿨다. 보정 후 정답 수다.

| | 전체 365 | FAQ 210 | STORE 52 | BOTH 51 | UNKNOWN 52 | holdout 157 |
| --- | --- | --- | --- | --- | --- | --- |
| EXAONE | 335 | 208 | 50 | 47 | **30** | 145 |
| gpt-oss | 362 / 361 / 360 | 208 | 52 / 51 / 50 | 50 | **52** | 156 / 155 / 154 |

| 오류 (실행당 건수) | EXAONE | gpt-oss |
| --- | --- | --- |
| 무관한 질문에 답하려 함 (UNKNOWN→FAQ) | 22 | 0 |
| 통신 질문을 거절 (→UNKNOWN) | 1 | 2~3 |
| 복합 질문 한쪽 누락 (BOTH→FAQ, 응답 형식 오류 포함) | 4 | 1 |
| FAQ·매장 혼동 | 3 | 0~1 |

- EXAONE은 김치찌개, 파이썬, 노트북 추천, 주택담보대출 같은 질문을 FAQ로 보낸다. gpt-oss는 0건이다.
- gpt-oss가 거절한 통신 질문은 "제가 못 가서 동생을 보내려는데 제가 가는 것보다 오래 걸릴까요"(EXAONE 포함 네 실행 모두)와 "멤버십 VIP면 영화 할인도 받을 수 있나요?"(3회 중 2회)다.
- 맥락 문항 "로밍 신청 방법도 알려주고 거기 몇 시까지 하는지도요"(이전 발언 "인천공항 매장 위치 알려줘")는 네 실행 모두 FAQ로만 분류했다. 문장에 "로밍"이라는 대상이 있어 문맥 해석이 이전 발언을 연결하지 않고, 라우팅은 "거기"가 어디인지 모른 채 질문만 받는다. 모델이 아니라 앱 흐름의 빈틈이다.

## 2단계: 요청 개수 판정 → gpt-oss, 프롬프트 v4

의도 분류는 gpt-oss로 고정했다. 이 호출의 입력은 질문 하나뿐이다. 앱 흐름에서는 1단계가 나눈 문항에만 돌아서(gpt-oss 기준 365문항 중 51문항) 대부분 진짜 여러 요청이고, 합쳐야 하는 경우는 거의 시험되지 않는다. 그래서 FAQ 210문항 전체에 이 호출만 직접 보내 나누기와 합치기를 모두 시험했다. 프로브는 앱의 `requestInventory`를 그대로 불러 같은 응답 검증과 개수 보정(SINGLE·COMPARISON은 1)을 거친다.

### 요청 개수 기준 (팀 결정)

같은 업무를 이어 물어도 두 번째 질문의 답이 첫 질문의 답에 들어 있지 않은 다른 정보면 요청을 나눈다. 첫 질문 답의 일부를 다시 확인하는 것만 합친다.

- 합침: "개통 신분증은 어떤 걸 가져가나요? 면허증도 가능해요?" (프롬프트 예시)
- 나눔: "개통 취소 가능 기간이 언제까지예요? 그리고 취소하면 위약금 있어요?"

합치면 두 번째 질문에 맞는 FAQ가 없을 때 답에서 조용히 빠진다. 위 예에서 합친 문장으로 검색하면 철회 기간 FAQ만 나오고, "개통 취소 위약금"은 따로 검색해도 결과가 없다. 나누면 앱이 그 부분을 찾지 못했다고 알려 준다.

### 프롬프트 v4

`REQUEST_INVENTORY_PROMPT`에 규칙 문장만 넣었다(평가 문항과 닮은 예시는 넣지 않음). 버전은 `routing-request-inventory-v4`.

- 고객이 묻지 않았는데 답변에 필요한 설명 항목과, 고객이 직접 나열해 물은 정보(비용, 서류, 방법, 기간, 위약금, 가능 기종)를 구분한다. 후자는 정보마다 요청이다.
- '각각'이 붙어도 종류나 구성을 소개해 달라는 목록 설명은 한 요청이다.
- 같은 업무를 이어 물어도 다른 정보면 MULTIPLE이다. 첫 질문 답의 일부 확인만 SINGLE이다.
- 한 업무를 처리하는 방법이나 경로(온라인, 매장, 전화)를 함께 묻는 것은 한 요청이다.

### 결과 (FAQ 210문항, 여러 요청 57 / 한 요청 153)

| | 전체 | 한 요청 | 여러 요청 | 앱 흐름 대상 51 |
| --- | --- | --- | --- | --- |
| EXAONE v3 | 193 | 148 | 45 | 42 |
| gpt-oss v3 | 199 / 197 / 198 | 153 / 151 / 152 | 46 / 46 / 46 | 43 / 44 / 44 |
| EXAONE v4 | 200 | 145 | 55 | 51 |
| **gpt-oss v4** | **203 / 204 / 202** | **153 / 153 / 153** | 50 / 51 / 49 | 46 / 47 / 46 |

- 앱 흐름 대상은 1단계에서 gpt-oss가 FAQ 하위 질문을 2개 이상 만든 문항이다.
- gpt-oss v4는 한 요청을 잘못 나눈 경우가 3회 모두 0건이다. 남은 오류는 전부 여러 요청을 합친 것이다.
- EXAONE v4는 여러 요청은 잘 나누지만 한 요청을 잘못 나누는 경우가 7건으로 늘었다. gpt-oss로 바꾸므로 운영 영향은 없다.

### 과적합 확인 (새 21문항)

프롬프트를 고친 뒤, 결과를 보기 전에 정답을 정해 쓴 21문항이다(여러 요청 11, 한 요청 함정 10). 프롬프트 수정에는 쓰지 않았다.

| | v3 | v4 |
| --- | --- | --- |
| EXAONE | 12 | 17 |
| gpt-oss | 15 / 15 / 15 | 17 / 17 / 16 |

- 한 요청 함정 10문항에서 gpt-oss v4는 3회 모두 잘못 나누지 않았다.
- "각각" 없이 정보 3가지를 나열한 질문("인터넷 이전 설치 비용, 소요 기간, 신청 방법")은 v4에서도 gpt-oss가 대부분 합친다. 원래 틀린 문항에서 나열 유형이 다 맞은 것은 "각각"이 있었기 때문으로, 다른 나열에는 덜 일반화됐다.
- 같은 업무 이어 묻기는 문항마다 결과가 흔들린다. 모델이 확신하지 못하는 영역이다.

## 재현

```bash
# 평가셋 다시 만들기
python scripts/routing-call-comparison/build_eval_set.py
# 1단계: EXAONE 1회, gpt-oss 3회(동시)
bash scripts/routing-call-comparison/run_intent_step.sh
python scripts/routing-call-comparison/score_intent_step.py      # 검색 앱(0.69/0.80)이 8083에 떠 있어야 한다
# 2단계
bash scripts/routing-call-comparison/run_request_count_step.sh
python scripts/routing-call-comparison/score_request_count_step.py
python scripts/routing-call-comparison/score_request_count_step.py exaone-v3 gptoss-1-v3 gptoss-2-v3 gptoss-3-v3
```

준비물(Ollama EXAONE, Bedrock 라우터, 검색 앱 실행 방법)은 각 스크립트 머리말에 있다. 2단계 결과 파일의 `-v3` 키는 프롬프트를 고치기 전 결과다.

## 남은 일과 확인할 것

- 3단계 검색어 검증, 4단계 후속 분석, 마지막으로 실제 채팅 API로 전체 확인. 4단계는 TELME-174에서 gpt-oss가 되물은 뒤 처리(이미 답한 조건을 다시 묻기)에 약했던 부분이라 채팅 API로도 본다.
- 팀 확인
  - 판정 기준 "gpt-oss가 확실히 나쁘지 않으면 gpt-oss".
  - 새로 쓴 137문항의 라벨.
  - `review_note`가 있는 4문항: 아이폰 케이스·블루투스 이어폰 추천(U+ 매장이 액세서리를 다루면 FAQ), 상담원 채용·대리점 창업(회사 관련이지만 상담 범위 밖으로 UNKNOWN).
- 별도로 볼 것
  - 대상 업무가 있는 문장 안의 "거기" 같은 지시어는 문맥 해석이 연결하지 않아 라우팅에서 풀리지 않는다(1단계 맥락 문항).
  - 요청 개수 판정에서 "각각" 없는 정보 나열과 같은 업무 이어 묻기는 gpt-oss v4에서도 합치는 경우가 남는다.
