# 답변 생성 모델 비교 (엑사원 vs gpt-oss)

TELME-178. FAQ 근거로 답변을 쓰는 `RagAnswerGenerator`를 엑사원과 gpt-oss-120b로 같은 입력에 돌려 비교했다. 입력은 두 가지다. **A**는 평가셋이 가리키는 정답 FAQ를 근거로 고정한 입력(앞단과 검색을 거치지 않음), **B**는 실제 앞단(문맥 판정, 라우팅, 되묻기, 검색 + 회색 구간 판정기)을 전부 Bedrock으로 통과시킨 뒤 답변 생성 직전에 저장한 입력이다. **품질 채점은 하지 않았다.** 가드(`AnswerGuard`)를 거친 결과의 객관 지표만 센다. 앱의 모델 설정은 바꾸지 않았다. 결과는 모두 **가드를 고치기 전** 기준이다.

## 요약

- A와 B 모두에서 gpt-oss는 가드에 덜 걸리고(KEPT 73~92%, 엑사원 48~74%) 더 짧게 답했다. 잘림은 없었고 호출 실패도 없었다.
- gpt-oss의 "근거 없음"(A 43%, B 5%) 중 B에서는 대부분(23건 중 20건)이 모델의 거절이 아니라 가드가 답을 대체한 것이다. 확인한 6개 중 4개는 맞는 답이 가드에 막힌 오경고로 보인다. 가드가 엑사원이 쓰는 표현에 맞춰져 있을 가능성이 있다.
- 이 지표는 가드 기준이라 어느 쪽이 더 정확한지는 말해 주지 않는다. 우열은 품질 채점(TELME-180)으로 봐야 한다.

## 방법

| 항목 | 내용 |
|---|---|
| 모델 | 엑사원 `exaone3.5:7.8b`(Ollama, 1회), gpt-oss-120b(Bedrock, 추론 강도 low, 3회) |
| 리전·설정 | Bedrock **Tokyo**(`ap-northeast-1`), 출력 한도 1,000(앱 설정과 같음), 시간 제한은 평가용으로 60초 |
| 호출 경로 | 앱의 `RagAnswerGenerator`(프롬프트, 입력 예산, 가드)를 그대로 거친다. 가드는 이 이슈에서 보강한 버전이다. gpt-oss는 앱의 Bedrock 요청 변환을 쓰고 종료 사유와 토큰을 기록한다 |
| 측정 프로그램 | `AnswerGenerationProbe`: 입력을 읽어 모델별로 답을 만들고 가드 전 원본, 최종 답변, 호출별 한도·종료 사유·토큰을 저장한다. 집계는 `score_answers.py` |

## 입력

### A: 정답 근거 고정 (217개)

`build_inputs.py`가 평가셋의 정답 FAQ를 `faq_full_1150.json`에서 꺼내 근거로 붙인다(최대 3개). 조건과 대화 이력은 없다. 앞단이 완벽한 상황에서 답변 생성만 비교하려는 입력이다.

| 출처 | 개수 | 근거로 붙인 FAQ | 보는 것 |
|---|---|---|---|
| `eval_questions_180_multigold.json`의 답 있는 질문 | 108 | 정답 FAQ | 근거가 충분할 때 답하는가 |
| `eval_answer_quality_20.json` | 19 | 정답 FAQ | 근거에 없는 계산·조건을 지어내는가 |
| `eval_questions_holdout_90.json` | 90 | 관련 FAQ (`related_slot_id`) | 근거가 질문에 답하지 못할 때 거절하는가 |

뺀 것: 180의 무관 질문 70개(근거가 없어 모델을 부르지 않음), 비교 표현이 있는 질문 3개(실제 앱에서는 비교 근거 검증이 답을 만든다). 요금제 안내와 매장 결과는 모델이 쓰는 답변이 아니라 대상이 아니다.

### B: 실제 앞단 통과 (145개)

**B-1 입력 저장.** 시나리오 236개(고객 발언 344개)를 실제 채팅 API(MockMvc)로 같은 세션에 순서대로 보내 앞단을 통과시키고, 답변 생성 직전의 `AnswerRequest`(질문, 해석된 질문, 확정 조건, 검색 근거, 대화 이력)를 턴마다 저장했다(`AnswerInputCaptureProbe`). 앱 코드는 바꾸지 않고 답변 생성기를 Spring 스파이로 감싸 입력만 가로챘다. 그 턴의 답변은 앱이 평소처럼 만들었다.

| 항목 | 내용 |
|---|---|
| 시나리오 | `build_scenarios_b.py`가 기존 평가셋에서 만든다(고정 시드). 복합 FAQ 53, FAQ+매장 51, 단일 FAQ 40, 되묻기 21, 대화 흐름 15, 멀티턴 56. 통합 라우팅 평가셋(`eval_routing_unified.json`), 되묻기 시나리오, 대화 흐름 22개(매장 제외), 문맥 판정 평가셋(일부)이 출처다. 비교 표현이 있는 질문은 뺐다 |
| 앞단 모델 | 측정용 중계기(`judge_router.py --chat bedrock`)로 앱의 모든 LLM 호출(문맥 판정, 라우팅, 되묻기 조건 추출, 답변 등)을 gpt-oss-120b로 보냈다. 앱 설정은 `CHAT_QUESTION_RESOLUTION_STABILITY_GUARDS=true`, `ROUTING_REQUEST_INVENTORY_ENABLED=false` |
| 판정기와 되묻기 | 테스트 기본 설정이 끄는 회색 구간 판정기(Bedrock `llm.bedrock`)와 FAQ 되묻기를 캡처 프로브가 다시 켰다. 판정기는 앱의 Bedrock 클라이언트로 gpt-oss를 직접 부른다 |
| DB | 개발 DB의 덤프 복제본(`telme_answer_b1`, FAQ 1,152건과 임베딩). 개발 DB는 건드리지 않았다 |
| 대화 이력의 어시스턴트 답변 | B-1이 모두 Bedrock 구성이라 앞 턴의 답변은 gpt-oss가 만든 것이다. B-2에서 두 모델이 같은 이력을 받는다 |

**B-1 결과(344턴).** 앱의 결과는 근거를 붙인 답변 104, 되묻기 153, 근거 없음 80, 범위 밖 4, 기타 3이다. 답변 생성 직전 입력은 235턴에서 276개가 만들어졌고, 그중 **검색 근거가 있는 145개**가 B 입력이다. 근거가 비어 모델을 부르지 않은 131개는 뺐다. 실제 앞단을 통과시키면 시나리오 236개 중 근거가 잡히는 입력이 절반 정도로 줄어든다.

| B 입력 | 개수 |
|---|---|
| 복합 FAQ | 38 |
| FAQ+매장 | 27 |
| 단일 FAQ | 13 |
| 되묻기 | 23 |
| 대화 흐름 | 14 |
| 멀티턴 | 30 |
| **합계** | **145** (근거 3개 94, 2개 23, 1개 28 / 앞 대화가 들어간 입력 13 / 확정 조건이 있는 입력 22 / 해석된 질문이 원문과 다른 입력 13) |

B-1 호출: 중계기를 거친 Bedrock 호출 1,252건(실패 0, 입력 2,380,289 / 출력 160,737 토큰, 약 $0.467)과 FAQ 판정기 호출 839건(Bedrock 직접, 토큰은 기록되지 않아 비용 미확인, 호출 크기로 보면 $0.1 안팎으로 추정). 합쳐서 약 $0.55~0.6이다. 처음 시작했다가 설정 문제(테스트 기본 설정이 판정기와 되묻기를 끔)로 멈춘 28턴은 버렸다.

## 결과

가드 처리는 가드가 최종 답변을 그대로 두었는지(KEPT), 일부 문장을 제거했는지(MODIFIED), 통째로 근거 없음 문구로 바꿨는지(REPLACED)다. 근거 없음은 최종 답변이 근거 없음으로 끝난 비율이다.

### A (엑사원 217건, gpt-oss 651건)

| 평가셋 | 지표 | 엑사원 | gpt-oss |
|---|---|---|---|
| 전체 | 가드 KEPT / MODIFIED / REPLACED | 48% / 30% / 21% | 73% / 3% / 24% |
| | 근거 없음으로 끝남 | 22% | 43% |
| | 최종 답변 길이 중앙값 | 70자 | 35자 |
| eval180 (정답 FAQ를 줌) | 근거 없음 (낮을수록 좋음) | 5% | 1% |
| | 가드 MODIFIED | 27% | 4% |
| holdout90 (근거가 질문에 못 답함) | 근거 없음 (높을수록 좋음) | 41% | 92% |
| | 가드 KEPT | 28% | 48% |
| answer_quality_20 (지어낼 위험) | 근거 없음 | 26% | 44% |
| | 가드 MODIFIED | 47% | 7% |

### B (엑사원 145건, gpt-oss 435건)

| | 엑사원 | gpt-oss |
|---|---|---|
| 가드 KEPT / MODIFIED / REPLACED | 74% / 24% / 1% | 92% / 3% / 5% |
| 근거 없음으로 끝남 | 1% | 5% |
| 최종 답변 길이 중앙값 | 80자 | 54자 |
| 잘림 의심 | 0 | 0 |

| 질문 종류 | 입력 수 | 가드 MODIFIED (엑사원 / gpt-oss) | 근거 없음 (엑사원 / gpt-oss) |
|---|---|---|---|
| 복합 FAQ | 38 | 26% / 5% | 3% / 6% |
| FAQ+매장 | 27 | 26% / 4% | 0% / 2% |
| 단일 FAQ | 13 | 46% / 0% | 0% / 8% |
| 되묻기 후 | 23 | 4% / 0% | 4% / 0% |
| 대화 흐름 | 14 | 0% / 0% | 0% / 0% |
| 멀티턴 | 30 | 37% / 3% | 0% / 12% |
| 앞 대화가 입력에 있는 것 | 13 | 23% / 8% | 0% / 28% |
| 확정 조건이 있는 것 | 22 | 5% / 0% | 5% / 0% |

### gpt-oss 호출과 일관성

| | A | B |
|---|---|---|
| 호출 | 651건, 실패 0, 종료 사유 전부 정상 | 435건, 실패 0, 종료 사유 전부 정상 |
| 토큰 | 입력 543,840 / 출력 36,171 | 입력 371,217 / 출력 27,474 |
| 비용 | 약 $0.106 | 약 $0.074 |
| 출력 토큰 | 중앙값 47, 최대 180 (한도 1,000) | 중앙값 60, 최대 165 |
| 응답 시간 | 중앙값 0.42초, 최대 8.03초 (앱의 8초 제한 초과 1건) | 중앙값 0.43초, 최대 7.69초 (초과 0건) |
| 같은 입력 3회 | 근거 여부 일치 212/217, 문자열까지 일치 144/217 (66%) | 근거 여부 일치 139/145, 문자열까지 일치 52/145 (36%) |

비용은 입력 $0.1545, 출력 $0.618 / 100만 토큰(AWS 요금표 Sydney 표기값) 기준이고 실제 청구액은 확인하지 않았다. 엑사원은 로컬이라 비용이 없고 호출 단위 시간과 토큰은 기록하지 않았다(스트리밍 경로).

## 가드 관련 발견

B에서 gpt-oss의 근거 없음 23건(435건 중)을 열어 봤다. **모델이 스스로 거절한 것은 3건(고유 입력 1개)뿐이고 20건(고유 입력 10개)은 가드가 모델 답변을 근거 없음 문구로 대체한 것**이다. 고유 입력 10개 중 6개를 확인했다.

| gpt-oss 원본 답변 | 가드가 막은 이유(추정) | 판단 |
|---|---|---|
| `유심 재발급 비용은 7,800원입니다` (근거는 7,700원) | 근거에 없는 금액 | 가드가 맞음 |
| `...별도의 추가 수수료는 없습니다` (근거에 없는 주장) | 근거에 없는 정책 속성 | 가드가 맞음 |
| `재발급 비용은 7,800원이 아니라 7,700원입니다` 2건 (대화에서 고객이 말한 7,800원을 바로잡음) | 고객이 말한 금액을 되받음 | 오경고로 보임 |
| `4년 전 기록은 발급 범위에 포함되지 않습니다` (근거: 최근 3년분까지만) | 근거에 없는 수치 `4년` | 오경고로 보임 |
| `고객센터 전화, 홈페이지, 또는 매장 방문 중 편하신 방법으로…` (근거에 세 채널이 모두 있음) | `편하신` 같은 비교 표현이 근거에 없음 | 오경고로 보임 |

- 가드의 일부 규칙은 엑사원 답변에서 나온 실패 사례를 보고 만들어졌다. 그래서 엑사원은 자주 걸리고(MODIFIED 24~30%), gpt-oss의 다른 표현은 오경고로 걸릴 수 있다. 반대로 gpt-oss의 실수 유형을 가드가 모르면 진짜 오류를 놓칠 수도 있다.
- 가드가 제거한 문장이 모두 해로운 것도 아니다. 엑사원 답변에서 제거된 문장 중에는 `체류 기간에 따라 가장 적합한 요금제를 선택하시면…` 같은 일반 조언이 있었다.
- 그래서 가드 처리 비율과 근거 없음 비율은 **모델의 우열이 아니라 가드와 모델 표현의 궁합**을 포함한다. 앞 대화가 있는 입력에서 gpt-oss의 근거 없음이 28%로 높은 것도 오경고 때문일 가능성이 있다(표본 13개).
- 확인한 것은 10개 중 6개이고 나머지 4개는 보지 않았다.

## 읽을 때 주의할 점

- **채점이 아니다.** 가드가 건드렸는지, 근거 없음으로 끝났는지만 센 것이다. 답이 맞는지, 근거를 빠뜨렸는지는 TELME-180의 판정 기준과 채점 모델로 따로 봐야 한다.
- **근거 없음 비율은 평가셋마다 좋고 나쁨이 다르다.** 정답 FAQ를 준 입력에서는 낮을수록, 근거가 질문에 답하지 못하는 A의 holdout90에서는 높을수록 맞다. B는 실제 검색 근거라 대부분 질문에 맞아서 근거 없음이 낮게 나온다.
- **표본이 다르다.** 엑사원은 1회, gpt-oss는 3회다. gpt-oss는 같은 입력에도 표현이 달라진다. 이력이나 근거가 여러 개인 B에서 문자열 일치율이 더 낮다(36%).
- **B는 입력이 145개이고 묶음에 따라 적다.** 단일 FAQ 13개, 앞 대화가 있는 입력 13개처럼 작은 묶음의 비율은 흔들린다.
- Tokyo에서 측정했고 앱 기본 리전은 `us-east-1`이다. 응답 시간은 비교하지 않았다.
- 가드는 이 이슈에서 보강한 버전이고 두 모델에 같은 가드를 썼다. 가드를 바꾸면 수치가 달라진다(저장된 원본 답변에 가드를 다시 적용하면 호출 없이 비교할 수 있다).

## 판단

- 객관 지표로는 gpt-oss가 근거 안에서 짧게 답하고 가드 개입이 적다. 호출은 빠르고 싸며 잘림이 없었다.
- gpt-oss의 근거 없음 비율이 높게 보이는 부분은 대부분 가드의 오경고이므로, 가드를 gpt-oss 표현에 맞게 보정하면 줄 가능성이 크다. 다만 보정으로 진짜 오류(7,800원 같은)를 놓치지 않는지 확인해야 한다.
- 모델 결정은 TELME-180의 채점 후에 한다. 이 문서의 수치는 채점의 입력 자료와 가드 보정의 근거다.

## 재현

```bash
# A 입력 / B 시나리오 (호출 없음)
python3 -I scripts/answer-generation-evaluation/build_inputs.py
python3 -I scripts/answer-generation-evaluation/build_scenarios_b.py

# B-1: 입력 저장 (복제 DB telme_answer_b1과 측정용 중계기 필요, 유료)
# python3 scripts/judge-serving-evaluation/judge_router.py --chat bedrock --judge bedrock --log router.jsonl
CAPTURE_PROBE=true POSTGRES_DB=telme_answer_b1 CAPTURE_PROBE_OUT=build/capture-b1.jsonl \
  OLLAMA_URL=http://127.0.0.1:11435 CHAT_QUESTION_RESOLUTION_STABILITY_GUARDS=true \
  ROUTING_REQUEST_INVENTORY_ENABLED=false LLM_BEDROCK_REGION=ap-northeast-1 \
  ./gradlew test --tests '*AnswerInputCaptureProbe'
python3 -I scripts/answer-generation-evaluation/build_inputs_b.py build/capture-b1.jsonl

# 두 모델 답변 생성 (INPUT은 inputs-a.json 또는 inputs-b.json)
ANSWER_PROBE=true ANSWER_PROBE_MODELS=exaone ANSWER_PROBE_OUT=build/answers-exaone.jsonl \
  ANSWER_PROBE_INPUT=scripts/answer-generation-evaluation/inputs-b.json \
  ./gradlew test --tests '*AnswerGenerationProbe'
ANSWER_PROBE=true ANSWER_PROBE_MODELS=gptoss ANSWER_PROBE_OUT=build/answers-gptoss.jsonl \
  ANSWER_PROBE_INPUT=scripts/answer-generation-evaluation/inputs-b.json \
  ./gradlew test --tests '*AnswerGenerationProbe'   # Bedrock API 키를 AWS_BEARER_TOKEN_BEDROCK로 지정

# 집계 (A와 B는 따로)
python3 -I scripts/answer-generation-evaluation/score_answers.py \
  scripts/answer-generation-evaluation/results/answers-exaone.jsonl.gz \
  scripts/answer-generation-evaluation/results/answers-gptoss.jsonl.gz
python3 -I scripts/answer-generation-evaluation/score_answers.py \
  scripts/answer-generation-evaluation/results/answers-b-exaone.jsonl.gz \
  scripts/answer-generation-evaluation/results/answers-b-gptoss.jsonl.gz
```

`ANSWER_PROBE_MODELS=fake`는 호출 없이 입력과 저장 형식만 확인하는 점검용이다. B-1은 상담 기록이 쌓이므로 개발 DB가 아닌 복제 DB에서만 실행한다. 환경 변수 전체는 `AnswerGenerationProbe`, `AnswerInputCaptureProbe`, `ProbeModelClients`의 주석에 있다.

## 함께 고친 것 (같은 이슈)

- `5g 요금제 알려줘`처럼 소문자로 쓰면 5G 필터가 적용되지 않아 전체 요금제가 나오던 문제(`PlanCatalogAnswerer`)를 고쳤다.
- `AnswerGuard`에 세 가지를 보강했다: 근거에 금액이 있는데 `무료`라고 한 문장 제거, `오후 7시 30분` 같은 한글 시각 표기를 `19:30`과 같은 값으로 비교, 시각 조건의 가능 여부를 근거와 반대로 말한 문장 제거. 고객이 말한 시각을 근거의 시각과 견주는 문장은 허용한다.

## 파일

| 파일 | 내용 |
|---|---|
| `scripts/answer-generation-evaluation/build_inputs.py`, `inputs-a.json` | A 입력 변환과 결과(217개) |
| `scripts/answer-generation-evaluation/build_scenarios_b.py`, `scenarios-b.json` | B 시나리오(236개) |
| `scripts/answer-generation-evaluation/build_inputs_b.py`, `inputs-b.json` | B-1 캡처를 답변 생성 입력으로 변환(145개) |
| `scripts/answer-generation-evaluation/score_answers.py` | 결과 집계 |
| `scripts/answer-generation-evaluation/results/answers-*.jsonl.gz` | A의 두 모델 원시 결과(약 97KB) |
| `scripts/answer-generation-evaluation/results/capture-b1.jsonl.gz`, `answers-b-*.jsonl.gz` | B-1 캡처와 B의 두 모델 원시 결과(약 90KB). 가드 전 원본, 최종 답변, 호출 기록이 들어 있다 |
| `src/test/java/com/telme/probe/AnswerGenerationProbe.java`, `AnswerInputCaptureProbe.java`, `ProbeModelClients.java` | 측정 프로그램 |
