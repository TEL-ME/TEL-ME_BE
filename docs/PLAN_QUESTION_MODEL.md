# 요금제 질문 분류 모델 비교 (엑사원 vs gpt-oss)

TELME-182. 요금제·내 정보 질문 분류(`PlanQuestionClassifier`)를 엑사원 3.5 7.8B와 Bedrock gpt-oss-120b로 같은 입력에 돌려 비교했다. 결과에 따라 **gpt-oss로 정했다**. 설정은 `CONSULT_PLAN_QUESTION_BEDROCK`(`telme.consult.plan-question.bedrock`)이다.

## 왜 앞단을 결정 사항대로 두고 쟀나

분류기는 FAQ 경로로 가는 질문을 모두 먼저 받는다(`PlanAnswerProvider`). 요금제나 내 정보 질문으로 분류되면 요금제 데이터로 답하고 FAQ 검색, 근거 검증으로 가지 않는다. 분류기에 들어가는 문장은 고객 원문이 아니라 문맥 판정과 라우팅을 거친 문장(복합 질문의 하위 질문 원문, 풀어쓴 질문)이다. 그래서 앞단을 지금 결정대로 둔 앱에서 실제 입력을 모으고, 그 입력을 분류기에만 넣어 비교했다. 앱 전체를 모델별로 따로 돌리면 gpt-oss 라우팅이 실행마다 하위 질문 문장을 조금씩 바꿔, 분류기 차이와 입력 차이가 섞인다.

## 방법

| 항목 | 내용 |
|---|---|
| 대화 | TELME-180 Judge 평가셋 v2.2(286)와 흐름 v2(88) 중 질문에 요금제 낱말(분류기의 낱말 검사와 같은 정규식)이 든 대화 169개, 185턴. 로그인 회원 흐름 포함 |
| DB | 측정용 `telme-flow-postgres`(포트 5440)의 `telme_judge_eval`. FAQ 1,152건, 질문 벡터 1,152개(10-05 재임베딩)를 실행 전에 확인했다 |
| 1단계 입력 캡처 | 브랜치 앱(`5635840b`)을 위 DB로 띄워 실제 채팅 API로 1회 실행. 라우팅·문맥 판정·조건 뽑기 gpt-oss, 요청 개수 판정 끔, 회색 구간 판정 켬, FAQ 되묻기 켬, 근거 검증·분류기·답변 생성은 엑사원(로컬 Ollama). Bedrock은 `us-east-1`, 추론 강도 low |
| 분류 입력 | 실행 기록 `planQuestionClassifications`에 남은 230문장(중복 없음). 그중 낱말 검사를 통과해 모델을 부른 것이 185개 |
| 2단계 분류 비교 | 230문장을 앱의 분류기(보정 규칙, 규칙 대체 포함)에 넣고 gpt-oss 3회를 동시에, 엑사원 1회는 다른 실행이 없을 때 따로 돌렸다. gpt-oss는 엑사원 대체 없이 Bedrock만 불러 실패도 그대로 보이게 했다 |
| 출력 한도 | 512. 이전 30은 gpt-oss의 내부 추론이 한도를 써서 답이 비거나 잘린다(문맥 판정은 64에서 16% 실패). 엑사원은 스키마로 답만 내서 한도와 관계없다 |
| 채점 | 턴의 `answerSources` 라벨. `PLAN_TABLE`은 요금제(목록·추천)가 하나 이상, `MEMBER`는 내 정보(MY_*)가 하나 이상, `FAQ`+`PLAN_TABLE`은 내 정보가 없으면 정답, `FAQ`·`STORE`·빈 값은 모두 NONE이어야 정답. 턴 안의 분류 입력은 각각 분류하고 결과를 합쳤다 |

처음 잰 결과(`e5ad17aa`)는 앱 기본 포트 5432의 `telme-postgres`로 캡처했다. 이 DB는 09-28 벡터 그대로라 질문 벡터가 1,152개 중 2개뿐이어서 검색이 운영과 달랐다. 위 DB로 다시 캡처해 표와 결과 파일을 모두 바꿨다. 결론은 같다.

## 결과

| 묶음 | 엑사원 | gpt-oss 1 / 2 / 3 |
|---|---|---|
| 요금제 표로 답할 질문 (15) | 13 | 13 / 14 / 14 |
| 내 정보로 답할 질문 (28) | 15 | 15 / 15 / 15 |
| FAQ·요금제 둘 다 정답 (10) | 10 | 10 / 10 / 10 |
| 요금제 경로로 가면 안 되는 질문 (132) | 116 | 126 / 126 / 127 |
| **전체 (185)** | **154 (83%)** | **164 / 165 / 166 (89%)** |

- **차이는 대부분 요금제 경로로 가면 안 되는 질문에서 나왔다.** 엑사원은 제도 문의를 내 위약금 조회(`MY_PENALTY`)로 보내는 경우가 gpt-oss보다 7건 많았다. 이렇게 분류되면 FAQ 답 대신 회원 위약금 안내가 나간다.
  - V2-SINGLE-A010 "이번 달 요금 깜빡하고 못 냈는데 얼마나 지나면 폰 끊겨요?"
  - V2-SINGLE-A012 "이번 달 중간에 요금제 바꾸면 요금은 어떻게 나와요?"
  - 명의 변경 때 약정 승계(V2-NOANSWER-U016, V2-PARTIAL-P006), 연체(V2-PARTIAL-P002), 복합 질문의 하위 질문(V2-COMPOUND-C011, C019)
- gpt-oss 호출 555회 중 실패, 빈 응답, 읽지 못한 응답은 0건이다. 같은 입력의 3회 결과는 230개 중 228개가 같았다.
- 회원 추천 질문 2건("제 사용량이면 어떤 게 맞을까요?" 등)은 라벨이 `MEMBER`라 추천(`RECOMMENDATION`)을 오답으로 셌다. 추천도 정답으로 보면 엑사원 156, gpt-oss 165 / 166 / 167이고 결론은 같다.

## 응답 시간

모델을 부르는 분류 입력 50개를 골라, 다른 측정이 없을 때 한 줄로(동시 실행 없이) 돌린 gpt-oss 값이다. 앞 표의 비교는 여러 실행을 동시에 돌려 시간에 쓰지 않는다. 엑사원 시간은 배포 환경(vLLM)에서 따로 잰다.

| 모델 | p50 | p95 | 최대 |
|---|---|---|---|
| gpt-oss (Bedrock `us-east-1`) | 0.60초 | 0.99초 | 2.4초 (첫 호출) |

## 두 모델이 같이 틀리는 것 (모델과 별개 과제)

- **내 정보 질문 28개 중 9개는 두 모델이 네 번 모두 NONE으로 분류했다.** "이번 달 전화 몇 분 했어요?", "제 폰 약정 걸려 있어요?", "이번 달 데이터 아직 얼마 남았는지 알 수 있나요?", "그럼 끝나는 건 언제쯤이에요?" 등이다. 프롬프트의 "상황을 설명하며 묻는 질문은 제도 문의" 기준이나 앱 보정 규칙(본인 표현 확인, 절차 질문 제외)과 관련 있어 보인다. 고치면 이 비교를 다시 한다.
- "문자는요?", "이번 달 폰 요금 대충 얼마 나올 것 같아?"처럼 분류기에 들어가는 문장에 요금제 낱말이 없으면 낱말 검사에서 걸러져 분류기까지 오지 않는다.
- FLOW-CF04 "약정 걸려 있는데 지금 해지하면 위약금 많이 나오나요?"는 라벨이 FAQ지만 두 모델 모두 `MY_PENALTY`로 분류했다. 라벨 검토 대상이다.

## 한계

- 대화는 원래 질문의 낱말로 골랐다. 라우팅이 바꾼 문장에만 요금제 낱말이 생기는 경우는 빠졌을 수 있다.
- 라벨은 에이전트가 쓴 것이고 사람 검토가 남아 있다(TELME-180).
- 고정 평가셋이라 실제 사용자 분포와 다르다. 엑사원은 1회만 쟀다.

## 비용

처음 측정(5432)을 포함해 Bedrock 약 2,800회(캡처 3번 약 1,620회, 분류 비교 약 1,080회, 응답 시간 50회), 약 $0.8로 추정(실제 청구액 미확인).

## 재현

캡처 실행기(`ChatJudgeCaptureProbe`, `scripts/chat_judge`)와 평가셋은 TELME-180 브랜치에 있다. 그 브랜치의 파일을 이 브랜치 작업본에 함께 둔 측정용 워크트리에서 실행했다. 실행기의 설정 기록에 `telme.routing`을 넣으려면 `ChatJudgeCaptureProbe.CONFIG_PREFIXES`에 추가한다. 검색이 끼는 캡처는 반드시 측정 DB(5440)로 돌린다.

```bash
# 측정 DB 확인: 1152가 아니면 멈춘다
docker exec telme-flow-postgres psql -U telme -d telme_judge_eval -Atc "select count(embedding_question) from faq_embeddings"
# 대화 고르기 (TELME-180 평가셋)
python -I scripts/plan-question-evaluation/plan_replay.py select \
  scripts/chat_judge/data/chat_pipeline_quality_v2_2.json scripts/chat_judge/data/chat_pipeline_flows_v2.json fixture.json
# 1단계 입력 캡처. 테스트 설정이 끄는 항목은 속성 이름 그대로의 환경 변수로 켠다
POSTGRES_PORT=5440 AWS_PROFILE=telme-bedrock-eval LLM_BEDROCK_ENABLED=true SEARCH_GRAY_ZONE_ENABLED=true \
TELME_CONSULT_FAQCLARIFICATION_ENABLED=true ROUTING_BEDROCK=true ROUTING_REQUEST_INVENTORY_ENABLED=false \
CHAT_QUESTION_RESOLUTION_BEDROCK=true CONSULT_FAQ_CLARIFICATION_BEDROCK=true \
  python -m scripts.chat_judge.run_chat_pipeline_eval --fixture fixture.json --out <새 폴더> --capture-only \
  --postgres-container telme-flow-postgres
# 분류 입력 꺼내기
python -I scripts/plan-question-evaluation/plan_replay.py extract <캡처>/capture.json replay-inputs.json
# 2단계: gpt-oss 3회는 동시에, 엑사원 1회는 따로 (PLAN_REPLAY_MODELS=모델:회차)
POSTGRES_PORT=5440 PLAN_REPLAY_PROBE=true PLAN_REPLAY_INPUT=<절대 경로>/replay-inputs.json PLAN_REPLAY_OUT=<절대 경로>/replay.jsonl \
PLAN_REPLAY_MODELS=gptoss:3 LLM_PROVIDER=ollama LLM_BEDROCK_ENABLED=true AWS_PROFILE=telme-bedrock-eval \
  ./gradlew test --tests 'com.telme.probe.PlanQuestionReplayProbe' --rerun-tasks
# 채점 (저장된 결과로 바로 다시 계산할 수 있다)
python -I scripts/plan-question-evaluation/plan_replay.py score \
  scripts/plan-question-evaluation/results/capture.json.gz scripts/plan-question-evaluation/results/replay.jsonl.gz score.md
```

응답 시간은 모델을 부른 입력 50개만 골라 `PLAN_REPLAY_MODELS=gptoss:1`로 돌린 결과(`latency.jsonl.gz`, 로컬 엑사원 값도 들어 있지만 쓰지 않는다)다.

## 파일

| 파일 | 내용 |
|---|---|
| `scripts/plan-question-evaluation/plan_replay.py` | 대화 고르기, 분류 입력 꺼내기, 채점 |
| `src/test/java/com/telme/probe/PlanQuestionReplayProbe.java` | 같은 입력을 모델·회차별로 분류 |
| `scripts/plan-question-evaluation/results/fixture.json.gz` | 고른 대화 169개 |
| `scripts/plan-question-evaluation/results/capture.json.gz` | 1단계 캡처(답변, 실행 기록, 앱 설정) |
| `scripts/plan-question-evaluation/results/replay-inputs.json` | 분류 입력 230문장 |
| `scripts/plan-question-evaluation/results/replay.jsonl.gz` | 모델별 분류 결과와 모델 원문 응답 |
| `scripts/plan-question-evaluation/results/latency.jsonl.gz` | 응답 시간 측정(입력 50개, 모델별 1회) |
