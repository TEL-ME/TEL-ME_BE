# 답변 경로 LLM 호출의 gpt-oss 전환 현황

답변 경로의 LLM 호출을 EXAONE 3.5 7.8B에서 Bedrock gpt-oss-120b로 옮기는 작업(TELME-182)의 현황표다. 호출마다 근거가 있는 것만 바꾸고, 플래그로 켜고 끈다. **TELME-194부터 확정된 호출은 기본으로 gpt-oss(Bedrock)를 먼저 부른다.** Bedrock 호출이 실패하면 그 호출만 EXAONE으로 대체한다(`FallbackLlmClient`).

## 호출별 상태

| # | 호출 | 클래스 | 상태 | 플래그 (기본값) | 근거 |
| --- | --- | --- | --- | --- | --- |
| 1 | 문맥 해석 | `ChatQuestionResolver` | **gpt-oss 확정**, 설정 추가 | `CHAT_QUESTION_RESOLUTION_BEDROCK` (true) | [CONTEXT_RESOLUTION.md](CONTEXT_RESOLUTION.md). 켜면 흔들림 보완도 함께 켜진다. 비용 항목 목록의 `위약금` 누락은 프롬프트를 고치면 재측정이 필요해 아직 손대지 않았다 |
| 2 | 의도 분류 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (true) | [ROUTING_CALL_COMPARISON.md](ROUTING_CALL_COMPARISON.md) 1단계 |
| 3 | 요청 개수 판정 | `QueryRoutingService` | **라우팅이 gpt-oss면 하지 않음, 엑사원이면 함** | `ROUTING_REQUEST_INVENTORY_ENABLED` (비움 = 라우팅 모델을 따라감) | 같은 문서 2단계. 호출 자체를 하지 않는다(엑사원이 하는 것이 아니다). 시작할 때 라우팅이 엑사원이면(`ROUTING_BEDROCK=false`, `LLM_BEDROCK_ENABLED=false`) 자동으로 켠다. 값을 직접 줘서 라우팅 모델과 어긋나면 시작할 때 경고 로그를 남긴다. 키가 없는 경우(빈은 만들어져 Bedrock 실패 후 엑사원으로 대체되므로 판정은 꺼진 채 돈다)와 장애로 호출 단위로 엑사원이 되는 경우는 따라가지 못한다. 키가 없으면 시작할 때 경고 로그가 뜬다 |
| 4 | 검색어 검증 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (true) | 같은 문서 3단계 |
| 5 | 후속 분석 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (true) | 같은 문서 4단계 |
| 6 | 요금제 질문 분류 | `PlanQuestionClassifier` | **gpt-oss 확정**, 설정 추가 | `CONSULT_PLAN_QUESTION_BEDROCK` (true) | [PLAN_QUESTION_MODEL.md](PLAN_QUESTION_MODEL.md). 185턴 중 엑사원 154(3회 같음), gpt-oss 164~166. gpt-oss 호출당 p50 0.60초(엑사원 시간은 배포 환경에서 잰다). 출력 한도를 30에서 512로 올렸다 |
| 7 | 조건 뽑기 | `ConditionExtractor` | **gpt-oss 확정**, 설정 있음 | `CONSULT_FAQ_CLARIFICATION_BEDROCK` (true) | [CLARIFICATION_EXTRACTION_MODEL.md](CLARIFICATION_EXTRACTION_MODEL.md). 평가셋 32건 엑사원 22%, gpt-oss 72~81%(각 3회, TELME-185). TELME-185에서는 기본 꺼짐으로 두었다가 TELME-194에서 기본 켜짐으로 바꿨다 |
| 8 | 되묻기 문장 | `DialogueService` | **호출 없앰**, 고정 문구 | — | 매장 위치 질문 하나에만 쓰였고 프롬프트가 대체 문구를 그대로 예시로 줬다. 지금은 "어느 지역의 매장을 찾으시나요? 역 이름이나 동네를 알려주세요."를 바로 쓴다 |
| 9 | 회색 구간 근거 판정 | `FaqEvidenceJudge` | 이미 Bedrock | — | [GRAY_ZONE_UPPER_BAND.md](GRAY_ZONE_UPPER_BAND.md) |
| 10 | 비교 근거 검증 | `LlmComparisonEvidenceResolver` | **gpt-oss 확정**, 기본 켜짐(TELME-184) | `CONSULT_COMPARISON_EVIDENCE_BEDROCK` (주지 않으면 `CONSULT_EVIDENCE_RESOLVER_BEDROCK`, true) | [EVIDENCE_RESOLVER_COMPARISON.md](EVIDENCE_RESOLVER_COMPARISON.md) "비교 근거 검증 재확인". 블라인드 양쪽 답 있음 19개에서 맞는 답 엑사원 1, gpt-oss 5~6, 틀린 답 0 |
| 11 | 후보 근거 검증 | `LlmFaqCandidateEvidenceResolver` | **gpt-oss 확정**, TELME-184에서 수정(P7), 기본 켜짐 | `CONSULT_CANDIDATE_EVIDENCE_BEDROCK` (위와 같음, true), `CONSULT_CANDIDATE_EVIDENCE_JUDGE` (true) | 같은 문서 "복합 질문 입력 확인"(TELME-182, 수정 전 채택 1건)과 "후보 근거 검증 수정"(TELME-184). 수정 후 개발용 맞는 답 30 / 31 / 30, 블라인드 12 / 12 / 10. 문맥 해석이 후속 질문을 복원하지 못하면 틀린 답이 날 수 있다("후속 질문 확인") |
| 12 | 근거 적합성 판정 | `EvidenceRelevanceChecker` | **꺼진 채로 둠**(TELME-182 결정) | `RAG_EVIDENCE_CHECK_ENABLED` (false) | 회색 구간(Q_A 0.69~0.80, 질문 0.80~0.90)은 9가 v4로 이미 판정한다. 겹치지 않는 것은 판정 없이 통과하는 높은 점수 구간뿐이다. 켜면 기본 `LlmClient`(엑사원)로 판정하므로, 다시 쓰려면 Bedrock 연결과 재측정이 먼저다 |
| 13 | 답변 생성 | `RagAnswerGenerator` | **gpt-oss 확정**(TELME-180), 설정 추가 | `RAG_ANSWER_BEDROCK` (true) | [채점 결과](chat-judge/results/20261018-answer-generation/README.md). 정답 근거 고정(A, 217개) 통과율 엑사원 43%, gpt-oss 73~74%. 결정된 앞단을 통과한 입력(B, 131개)은 Judge가 갈린 답을 사람이 판정해 엑사원 67%, gpt-oss 76~77%. B 입력과 두 모델 답변은 아래 "답변 생성 입력과 답변"(TELME-182) 프롬프트는 TELME-194에서 금액 복사 규칙을 더해 v4.9가 됐다(아래 "답변 프롬프트 v4.9") |
| 14 | 세션 제목 | `ChatSessionTitleService` | **gpt-oss**, 설정 추가(TELME-194) | `CHAT_SESSION_TITLE_BEDROCK` (true) | 아래 "세션 제목". 출력 한도를 32에서 512로 올렸다(32에서는 제목이 비거나 잘렸다) |
| 15 | 대화 요약 | `ChatSummaryService` | **gpt-oss**, 설정 추가(TELME-194) | `CHAT_SUMMARY_BEDROCK` (true) | 대화가 16개 메시지 또는 2,048토큰을 넘을 때만 도는 백그라운드 호출. 60사례 두 모델 같게 나왔고(60/60, [BEDROCK_SWITCH_EVALUATION.md](BEDROCK_SWITCH_EVALUATION.md)) 그 측정도 출력 한도 512로 했다 |

상담 종료 요약은 기능을 없앴다. 세션 제목과 대화 요약은 TELME-194에서 gpt-oss로 바꿨다(14, 15번).

## 기본 구성과 끄는 방법

TELME-194부터 1·2·4·5·6·7·10·11·13·14·15번은 기본으로 gpt-oss(Bedrock)를 먼저 부르고, 3번(요청 개수 판정)은 하지 않는다. 호출을 엑사원으로 돌리려면 아래 플래그를 false로 준다.

### 전체 플래그

| 호출 | 환경 변수 | 설정 키 | 기본 | 바꾸면 |
| --- | --- | --- | --- | --- |
| 문맥 해석 | `CHAT_QUESTION_RESOLUTION_BEDROCK` | `chat.question-resolution.bedrock` | true | false면 엑사원. 흔들림 보완도 함께 꺼진다 |
| 문맥 흔들림 보완 | `CHAT_QUESTION_RESOLUTION_STABILITY_GUARDS` | `chat.question-resolution.stability-guards` | false | 위 bedrock이 true면 이 값과 관계없이 켜진다 |
| 라우팅(의도 분류, 검색어 검증, 후속 분석, 켠 경우 요청 개수 판정) | `ROUTING_BEDROCK` | `telme.routing.bedrock` | true | false면 모두 엑사원 |
| 요청 개수 판정 | `ROUTING_REQUEST_INVENTORY_ENABLED` | `telme.routing.request-inventory-enabled` | 비움 | 비우면 라우팅이 gpt-oss일 때 끄고 엑사원일 때 켠다. true/false를 주면 그 값을 쓴다 |
| 요금제 질문 분류 | `CONSULT_PLAN_QUESTION_BEDROCK` | `telme.consult.plan-question.bedrock` | true | false면 엑사원 |
| 조건 뽑기 | `CONSULT_FAQ_CLARIFICATION_BEDROCK` | `telme.consult.faq-clarification.bedrock` | true | false면 엑사원 |
| 비교 근거 검증 | `CONSULT_COMPARISON_EVIDENCE_BEDROCK`(없으면 `CONSULT_EVIDENCE_RESOLVER_BEDROCK`) | `telme.consult.evidence-resolver.comparison-bedrock` | true | false면 엑사원 |
| 후보 근거 검증 | `CONSULT_CANDIDATE_EVIDENCE_BEDROCK`(없으면 `CONSULT_EVIDENCE_RESOLVER_BEDROCK`) | `telme.consult.evidence-resolver.candidate-bedrock` | true | false면 엑사원 |
| 후보 판정 방식 | `CONSULT_CANDIDATE_EVIDENCE_JUDGE` | `telme.consult.evidence-resolver.candidate-judge` | true | false면 단어 검사로 막는다 |
| 답변 생성 | `RAG_ANSWER_BEDROCK` | `rag.answer.bedrock` | true | false면 엑사원 |
| 세션 제목 | `CHAT_SESSION_TITLE_BEDROCK` | `chat.session-title.bedrock` | true | false면 엑사원. 출력 한도(`chat.session-title.max-output-tokens`)는 512 |
| 대화 요약 | `CHAT_SUMMARY_BEDROCK` | `chat.summary.bedrock` | true | false면 엑사원 |
| 회색 구간 판정(Bedrock 전용) | `SEARCH_GRAY_ZONE_ENABLED` | `search.gray-zone.enabled` | true | false면 판정을 하지 않는다 |
| Bedrock 연결 | `LLM_BEDROCK_ENABLED` | `llm.bedrock.enabled` | true | false면 Bedrock 호출이 없고, 전환 플래그가 켜져 있어도 엑사원으로 동작한다 |
| 근거 적합성 판정 | `RAG_EVIDENCE_CHECK_ENABLED` | `rag.evidence-check.enabled` | false | true면 엑사원으로 판정한다(위 12번) |

키 이름은 호출마다 정해진 대로 두었다. 모델 전환 플래그는 대부분 `bedrock`이고 근거 검증만 호출이 둘이라 `comparison-bedrock`, `candidate-bedrock`으로 나눈다. 기본값은 `application.yml`과 각 `*Properties`의 `@DefaultValue`가 같다(`BedrockFlagDefaultsTest`가 확인한다).

### 라우팅 호출과 두 스위치

| 라우팅 호출 | `ROUTING_BEDROCK` (어느 모델로 부르나) | `ROUTING_REQUEST_INVENTORY_ENABLED` (이 호출을 하나) |
| --- | --- | --- |
| 의도 분류 | 적용 | 해당 없음 |
| 요청 개수 판정 | 적용 | **이 호출만 켜고 끔**(기본 끔) |
| 검색어 검증 | 적용 | 해당 없음 |
| 후속 분석 | 적용 | 해당 없음 |

- 라우팅 호출 4개는 같은 클라이언트를 쓰므로 `ROUTING_BEDROCK`으로 한꺼번에 바꾸고, 호출별로 나눌 수 없다.
- 요청 개수 판정은 의도 분류가 FAQ 하위 질문을 2개 이상으로 나눴을 때 "정말 요청이 여러 개인가"를 한 번 더 확인해 잘못 나눈 것을 합치는 호출이다. EXAONE의 과잉 분해를 막으려고 넣었다. gpt-oss는 정확하게 나눠서 이 판정이 맞게 나눈 질문을 약 10% 다시 합치므로 하지 않는다(TELME-182 결정).
- 하지 않으면 의도 분류가 나눈 개수를 그대로 쓰고, 하위 질문마다 서로 다른 원문 근거가 있는지만 코드 규칙(`IndependentQuestionPolicy`)으로 확인한다. 단독 비교 질문 보정 규칙과 검색어 검증은 그대로 돈다.
- 라우팅을 엑사원으로 되돌리면(`ROUTING_BEDROCK=false` 등) 요청 개수 판정은 자동으로 켜진다. 값을 직접 줘서 라우팅 모델과 어긋나면(gpt-oss인데 켬, 엑사원인데 끔) 시작할 때 경고 로그를 남긴다.

### 동작 방식

- `QueryRoutingService`는 `routingLlmClient`(`RoutingLlmConfiguration`)를, `ChatQuestionResolver`는 `contextResolutionLlmClient`(`ChatQuestionResolutionLlmConfiguration`)를, `PlanQuestionClassifier`는 `planQuestionLlmClient`(`PlanQuestionConfiguration`)를, `RagAnswerGenerator`는 `ragAnswerLlmClient`(`RagAnswerLlmConfiguration`)를 이름으로 주입받는다. 이름 붙은 클라이언트만 바꾸므로 다른 호출의 기본 모델은 그대로 EXAONE이다.
- Bedrock 빈이 없으면(`LLM_BEDROCK_ENABLED=false`) 플래그가 켜져 있어도 EXAONE을 쓴다.
- **대체는 호출마다 따로 판단하고 실패를 기억하지 않는다.** 라우팅이 엑사원으로 대체되어도 다음 호출(조건 뽑기, 답변 생성 등)은 다시 Bedrock부터 시도한다. Bedrock 전체가 응답하지 않으면 호출마다 먼저 Bedrock을 시도한 뒤 엑사원으로 가며, 응답이 오지 않는 시간 초과는 호출마다 최대 8초(`LLM_BEDROCK_TIMEOUT`)를 기다린다. 한 턴에 이 지연이 쌓여 40~85초가 걸렸다(아래 "Bedrock 장애 시 동작 확인").
- **키가 없으면 시작할 때 경고한다.** `AWS_BEARER_TOKEN_BEDROCK`이 없으면 모든 Bedrock 호출이 실패해 조용히 엑사원으로 도므로 앱이 뜬 뒤 경고 로그를 한 번 남긴다(`BedrockCredentialsCheck`). 키 값이 맞는지는 호출해 봐야 알 수 있어 확인하지 않는다(키가 틀리면 호출마다 약 0.3초 만에 실패하고 ERROR 로그가 남는다).
- 라우팅이 엑사원으로 대체되는 동안에는 요청 개수 판정이 꺼져 있어 엑사원의 과잉 분해를 막는 모델 검사가 없다(하위 질문 원문 근거 확인 규칙은 동작한다). 한 요청 질문 9개 중 판정을 끄면 7개가 잘못 나뉘거나 거절됐고 켜면 2개였다(아래 C).
- Bedrock 키(`AWS_BEARER_TOKEN_BEDROCK`)가 없으면 Bedrock 호출이 실패해 엑사원으로 대체된다. 로컬에서 키 없이 쓰려면 `SEARCH_GRAY_ZONE_ENABLED=false`와 `LLM_BEDROCK_ENABLED=false`를 함께 준다. 하나만 끄면 앱이 뜨지 않거나 판정이 실패한다.
- 답변 생성은 스트리밍이다. Bedrock 클라이언트는 답을 다 받은 뒤 한 번에 보내므로(TELME-182 B 입력에서 gpt-oss 호출 중앙값 0.44초) 글자가 나눠서 나오지 않는다. 첫 글자를 보내기 전에 Bedrock이 실패하면 같은 요청을 엑사원 스트리밍으로 다시 부른다(`FallbackLlmClient`). 글자가 나간 뒤에는 대체하지 않는다.
- 호출 수가 늘어난다. TELME-182 캡처에서 고객 발화 344개에 앞단 gpt-oss 호출이 1,815건(발화당 약 5건)이었고, 답변 생성이 발화마다 1건 더 든다.

## Bedrock 장애 시 동작 확인 (TELME-194)

모든 전환 플래그를 기본(켜짐)으로 둔 앱을 로컬 엑사원(Ollama)과 측정용 DB 복제본으로 띄우고, Bedrock을 두 가지로 실패시켜 대표 대화를 돌렸다. 실패한 호출은 과금되지 않는다.

| 구성 | 방법 |
| --- | --- |
| A. 바로 실패 | 틀린 키(`AWS_BEARER_TOKEN_BEDROCK`)로 보내 403이 즉시 돌아오게 했다 |
| B. 응답 없음 | `AWS_ENDPOINT_URL_BEDROCK_RUNTIME`을 연결만 받고 답하지 않는 로컬 서버로 지정해 호출이 시간 제한(8초)까지 걸리게 했다 |

대화는 단일 FAQ, 복합 질문, 비교 질문, 앞 대화를 이어받는 후속 질문, 매장 되묻기를 포함한 12턴(A)과 4턴(B)이다.

### A. 바로 실패 (12턴)

- 12턴 모두 오류 없이 끝났다. 앱 로그에 `[LLM 대체]`가 32번 남았다.
- 호출마다 Bedrock 실패와 엑사원 성공이 쌍으로 기록됐다(`llm_generations`).

| 호출 | Bedrock 실패 | 엑사원 성공 |
| --- | ---: | ---: |
| 라우팅 | 16 | 16 |
| 문맥 해석 | 6 | 6 |
| 조건 뽑기 | 5 | 5 |
| 답변 생성(스트리밍) | 5 | 5 |

- 실패한 Bedrock 호출은 평균 약 0.3초(최대 1.1초)였다.
- 회색 구간 판정(`FaqEvidenceJudge`)은 Bedrock 전용이라 엑사원으로 대체하지 않는다. 엑사원은 이 판정에서 엉뚱한 근거를 못 막아 쓰지 않는다([GRAY_ZONE_UPPER_BAND.md](GRAY_ZONE_UPPER_BAND.md) 6절). 24건이 모두 실패했고, 실패하면 판정이 없던 때와 같게 처리한다(점수가 아래쪽인 후보는 버리고 위쪽은 통과). 그래서 장애 때는 판정으로 얻던 개선만 사라지고 판정 도입 전의 동작으로 돌아간다. "eSIM이랑 일반 유심 차이점이 뭐예요?"는 근거 없음 안내로 끝났지만, 이 질문이 Bedrock이 정상일 때 어떻게 끝나는지는 같은 조건으로 비교하지 않아 원인을 이 판정 실패로 단정하지는 못한다.
- 403 오류는 호출마다 `ERROR` 로그(`[Bedrock] 설정·권한 오류`)를 남겨 12턴에 56줄이 쌓였다.

### B. 응답 없음 (4턴)

| 질문 | 시간 초과 호출 | 걸린 시간 | A의 같은 질문 |
| --- | ---: | ---: | ---: |
| 단일 FAQ | 5 | 45초, 42초 | 12~33초 |
| 복합 질문(2개) | 14 | 83초 | 23초 |
| 후속 질문 | 8 | 58초 | 17초 |

- 4턴 모두 오류 없이 엑사원으로 끝났고 답 내용도 A와 거의 같았다.
- 호출마다 8초를 기다린 뒤 대체해서 턴당 40~85초가 걸렸다. 일부 호출이 겹쳐 호출 수 × 8초보다는 짧다.
- 시간 초과는 호출마다 따로 일어난다. 앞 호출이 시간 초과였어도 다음 호출은 다시 Bedrock부터 시도한다.

### C. 엑사원 라우팅에서 요청 개수 판정이 없을 때

Bedrock이 계속 실패해 라우팅이 엑사원이 되면 요청 개수 판정이 꺼져 있어 엑사원의 과잉 분해를 막는 모델 검사가 없다. 이 영향을 보려고 같은 질문 12개를 엑사원 라우팅으로 판정을 끈 구성(기본)과 켠 구성에서 새 대화마다 한 번씩 돌렸다(Bedrock과 회색 구간 판정은 끔, 요청 수는 `consult_requests` 행 수).

| 질문 종류 | 판정 끔 | 판정 켬 |
| --- | --- | --- |
| 한 요청 질문 9개 | 한 요청으로 처리 2, **잘못 나눔 4**(2~3요청), 거절 3 | 한 요청으로 처리 **7**, 거절 2 |
| 원래 두 요청인 질문 2개 | 둘 다 2요청 | 둘 다 2요청 |
| 범위 밖 입력 "20건" | 범위 밖 안내 | 범위 밖 안내 |

- 한 요청 질문은 과잉 분해가 알려진 유형을 골랐다("저가형, 무제한형으로 나눠서 각각 소개해줘", "온라인이나 매장에서 하는 절차", "청구서 확인부터 요금 납부까지", "5G, LTE, 알뜰 요금제 구성을 순서대로", "정지가 나아요, 해지가 나아요?" 등). 표본이 작고 쉬운 질문은 넣지 않았다.
- 거절은 "현재 질문을 안전하게 나누어 처리하기 어려워요" 안내이고, 쪼갠 결과를 하위 질문 원문 근거 확인 규칙이 막은 것이다. 판정을 끄면 이 규칙 하나만 남아 잘못 나눈 것을 다 막지는 못하고 답이 둘로 나뉘어 나온다.
- 1요청이라고 해서 답이 맞은 것은 아니다. 두 구성 모두 일부는 근거 없음이나 되묻기로 끝났다.
- 영향은 라우팅이 엑사원이 되는 호출에만 있다. 대체는 호출마다 따로 일어나므로 가끔 실패할 때는 그 턴만 영향을 받고, Bedrock이 계속 멈춰 있을 때 모든 턴에 해당한다.

### D. 정상 Bedrock으로 확인 (기본 설정)

기본 설정(전환 플래그 모두 켜짐, 요청 개수 판정 끔, 리전 `us-east-1`)으로 앱을 띄우고 실제 Bedrock 키로 25턴을 돌렸다(새 대화 20개). 질문은 위 C의 12개와 단일 FAQ, 비교, 후속 질문, 매장 되묻기, 요금제 추천(이 브랜치는 TELME-191 이전 동작) 등이다.

- Bedrock 호출 135건이 모두 응답을 받았고(가드가 답을 막은 1건 포함) 엑사원 대체는 한 번도 일어나지 않았다(`[LLM 대체]` 로그 0건, 엑사원 호출 0건). 25턴이 합계 157초, 턴당 평균 6.3초였다.
- 호출당 평균 시간은 라우팅 1.2초(41건), 문맥 해석 1.1초(7건), 조건 뽑기 1.8초(16건), 근거 판정 1.5초(61건), 답변 생성 1.1초(10건)였다.
- 비용은 호출 수에 TELME-182의 호출당 비용(약 $0.0002)을 곱해 $0.03 안팎으로 추정한다(토큰은 기록하지 않았고 실제 청구액은 확인하지 않았다).
- C의 한 요청 질문 9개를 gpt-oss 라우팅(판정 끔)으로 돌리면 한 요청 8개, 잘못 나눔 1개("청구서 확인부터 요금 납부까지"), 거절 0개였다. 엑사원 라우팅은 판정을 끄면 정상 2, 켜도 정상 7이었다.
- 답변 생성 10건 중 1건은 가드가 막았다(`근거에 없는 금액: [7800]`). "유심 재발급 비용이랑 로밍 신청 방법 알려줘"의 첫 하위 질문 "유심 재발급 비용"에서 gpt-oss가 근거(7,700원)와 다른 금액을 써서 "지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요"로 끝났다. 같은 질문이 엑사원 대체 실행(A)에서는 7,700원으로 답했다. 아래 "답변 프롬프트 v4.9"의 7,800원 문제와 같은 현상이고, 이 확인은 v4.9 적용 전 프롬프트(v4.8)로 했다. 한 문장으로 묻는 "유심 재발급 비용 알려줘"는 7,700원으로 맞게 답했다.

### 한계와 판단

- 엑사원 시간은 로컬 Ollama 값이고, A의 첫 턴(33초)은 모델을 처음 올리는 시간이 들어 있다. 대화 표본이 작다.
- B는 모든 호출이 8초까지 걸리는 최악의 경우다. 실제 장애는 일부 호출만 느릴 수 있다.
- Bedrock이 완전히 멈추면 응답이 끝까지 오지만 느려진다. 실패를 기억해 일정 시간 Bedrock을 건너뛰는 장치나 시간 제한 조정은 이번 범위에 넣지 않았다.
- 요금제 추천 흐름은 이 브랜치에 TELME-191이 들어 있지 않아 예전 동작이다.

## 답변 생성 입력과 답변 (13번)

13번 모델은 품질 채점으로 정해야 해서 TELME-180에서 정했다(gpt-oss). TELME-182에서는 위 결정으로 앞단을 통과한 입력을 저장하고, 두 모델로 답을 만들었다. 답변 생성 B 입력은 [ANSWER_GENERATION_COMPARISON.md](ANSWER_GENERATION_COMPARISON.md)(TELME-178)에 처음 만든 것이 있고, 그 입력으로 엑사원·gpt-oss를 각 3회 돌린 결과는 [ANSWER_GENERATION_EXAONE_REPEAT.md](ANSWER_GENERATION_EXAONE_REPEAT.md)(TELME-186)에 있다. 그 입력은 앞단 전체를 측정용 중계기로 gpt-oss에 보내 만든 것이라, 이번에는 앱 설정으로 결정된 구성만 켜고 다시 캡처했다.

| 항목 | 내용 |
|---|---|
| 앞단 | 1·2·4·5·6·7·10·11번 gpt-oss(앱 Bedrock 클라이언트), 3번 끔. 캡처 프로브가 회색 구간 판정과 되묻기를 켠다. 측정 당시 답변 생성(13번)은 엑사원이 기본이라(설정 추가 전), 대화 이력에 들어가는 앞 턴 응답은 엑사원이 만든 것이다 |
| DB | `telme-flow-postgres`(5440)에서 `telme_judge_eval`을 템플릿으로 만든 `telme_answer_b1_182`, 질문 벡터 1,152 |
| 시나리오 | `scenarios-b.json` 236개, 고객 발화 344개를 같은 세션에 순서대로 보냈다. 앱 응답은 답변 181, 되묻기 163이고 오류는 없었다 |
| 입력 | 답변 생성 요청 256개 중 검색 근거가 있는 131개(복합 FAQ 31, FAQ+매장 27, 멀티턴 25, 되묻기 20, 단일 FAQ 15, 대화 흐름 13). 대화 이력이 있는 입력 12, 확정 조건이 있는 입력 19, 해석된 질문이 원문과 다른 입력 12 |
| 앞단 호출 | gpt-oss 1,815건(근거 검증 895, 라우팅 592, 조건 뽑기 207, 문맥 해석 121). 조건 뽑기 1건이 엑사원으로 대체됐다 |
| 답변 | 엑사원 3회(하나씩), gpt-oss 3회(Tokyo, 동시). 프롬프트 `rag-answer-v4.8` |

| 가드 처리 (393건씩) | KEPT / MODIFIED / REPLACED | 최종 답변 길이 중앙값 |
|---|---|---|
| 엑사원 | 77% / 23% / 0% | 85자 |
| gpt-oss | 90% / 2% / 8% | 53자 |

- 엑사원은 세 회차 답이 모두 같다. gpt-oss는 근거 여부가 129/131에서 같고, 최종 답변 문자열까지 같은 것은 49/131이다.
- gpt-oss 호출은 393건, 실패 0, 종료 사유 모두 `end_turn`이다. 입력 335,541 / 출력 25,028 토큰, 약 $0.067(위 문서와 같은 단가)이다. 출력 토큰 중앙값 62, 최대 155(한도 1,000), 응답 시간 중앙값 0.44초, 최대 1.62초다.
- 가드가 gpt-oss 답을 대체한 31건 중 26건은 근거에 없는 숫자였고, 거의 모두 `유심 재발급 비용` 질문에 `7,800원`(근거는 `7,700원`)이라고 쓴 것이다. 위 문서의 "가드 관련 발견"과 같은 현상이다.
- 가드 지표는 품질이 아니다. 어느 쪽이 나은지는 TELME-180 채점으로 본다.

```bash
# 입력 캡처(유료, 측정 DB 복제본에서만). 실행 전 질문 벡터가 1,152인지 확인한다
CAPTURE_PROBE=true CAPTURE_PROBE_OUT=build/capture-b182.jsonl \
POSTGRES_PORT=5440 POSTGRES_DB=telme_answer_b1_182 LLM_BEDROCK_ENABLED=true \
ROUTING_BEDROCK=true ROUTING_REQUEST_INVENTORY_ENABLED=false \
CHAT_QUESTION_RESOLUTION_BEDROCK=true CONSULT_PLAN_QUESTION_BEDROCK=true CONSULT_FAQ_CLARIFICATION_BEDROCK=true \
CONSULT_EVIDENCE_RESOLVER_BEDROCK=true CONSULT_CANDIDATE_EVIDENCE_JUDGE=true \
  ./gradlew test --tests 'com.telme.probe.AnswerInputCaptureProbe' --rerun-tasks
python3 -I scripts/answer-generation-evaluation/build_inputs_b.py build/capture-b182.jsonl -o build/inputs-b182.json

# 답변 생성. gpt-oss 3회 뒤 엑사원 3회(엑사원은 다른 실행과 겹치지 않게)
ANSWER_PROBE=true ANSWER_PROBE_INPUT=build/inputs-b182.json ANSWER_PROBE_OUT=build/answers-b182-gptoss.jsonl \
ANSWER_PROBE_MODELS=gptoss ANSWER_PROBE_GPTOSS_REPEATS=3 ./gradlew test --tests 'com.telme.probe.AnswerGenerationProbe' --rerun-tasks
ANSWER_PROBE=true ANSWER_PROBE_INPUT=build/inputs-b182.json ANSWER_PROBE_OUT=build/answers-b182-exaone.jsonl \
ANSWER_PROBE_MODELS=exaone ANSWER_PROBE_EXAONE_REPEATS=3 ./gradlew test --tests 'com.telme.probe.AnswerGenerationProbe' --rerun-tasks
python3 -I scripts/answer-generation-evaluation/score_answers.py build/answers-b182-exaone.jsonl build/answers-b182-gptoss.jsonl
```

결과 파일은 `scripts/answer-generation-evaluation/results/TELME-182/`의 `capture-b182.jsonl.gz`(캡처), `inputs-b182.json`(입력), `answers-b182-exaone.jsonl.gz`, `answers-b182-gptoss.jsonl.gz`(두 모델 3회 답변)다.

## 세션 제목 (TELME-194)

세션 제목은 첫 질문으로 짧은 제목을 만드는 호출이다. TELME-174에서 두 모델이 같게 나와(20/20) 바꾸지 않았지만, 그 측정은 평가용 라우터를 거쳤고 앱의 Bedrock 클라이언트와 앱의 출력 한도(32토큰)로 잰 것이 아니었다. 앱 설정 그대로 실제 Bedrock(`us-east-1`, 추론 강도 low)을 불러 보니 한도가 문제였다.

| 출력 한도 | 성공 | 실패(빈 응답) | 비고 |
| ---: | ---: | ---: | --- |
| 32 (이전 기본) | 13 / 20 | **7** | 성공한 것 중에도 "소액결", "휴대폰 분", "5G"처럼 잘린 제목이 있었다 |
| 128 | 20 / 20 | 0 | 제목 30자 초과 0 |
| **512 (현재 기본)** | 20 / 20 | 0 | 제목 30자 초과 0 |

- gpt-oss는 답 앞의 내부 추론도 출력 한도를 써서 32토큰으로는 제목이 비거나 중간에서 끊긴다. 그래서 켜기만 하고 한도를 두면 실패 35%가 엑사원으로 대체되고 잘린 제목은 그대로 저장된다. 한도를 512로 올렸다. 엑사원은 제목이 짧아 한도를 올려도 같고, 30자를 넘으면 코드가 잘라 낸다.
- 실제 앱(기본 설정)에서 새 대화 5개를 시작했을 때 제목 5개 모두 gpt-oss가 만들었고 성공했다(호출 0.6~0.9초). "유심 재발급 비용 안내", "소액결제 한도 상향 요청", "휴대폰 분실 및 정지 요청", "약정 종료 후 요금 변동 여부", "근처 매장 위치 안내"였다.
- 호출은 한도 확인 60건과 앱 확인 5건, 모두 응답을 받았다(약 $0.01 추정).
- Bedrock 호출이 실패하면 엑사원으로 대체하는 것은 다른 호출과 같고(`FallbackLlmClient`), 이름 붙은 클라이언트 연결과 기본값은 `BedrockSwitchWiringTest`, `BedrockFlagDefaultsTest`가 확인한다.

## 답변 프롬프트 v4.9: 금액 복사 규칙 (TELME-194)

gpt-oss가 짧은 `유심 재발급 비용` 질문에 근거(7,700원)와 다른 `7,800원`을 써서 가드가 거절로 바꾸는 문제(TELME-182 B 입력에서 가드가 대체한 31건 중 26건)를 줄이려고 답변 프롬프트 규칙 4번 뒤에 한 줄을 더했다. 프롬프트는 엑사원 대체 때도 같이 쓰이므로 엑사원도 확인했다.

> 금액과 기간은 근거에 적힌 글자를 그대로 복사해 쓰고, 다시 계산하거나 비슷한 숫자로 바꿔 쓰지 마십시오.

버전은 `rag-answer-v4.8`에서 `rag-answer-v4.9`로 올렸다. 입력, 모델 설정(Tokyo, 추론 강도 low, 출력 한도 1,000)은 TELME-182·186 측정과 같고, 이전 결과는 그때 저장한 파일(B: TELME-182, A: TELME-186)이다. 결과 파일은 `scripts/answer-generation-evaluation/results/TELME-194-20261019/`에 있다.

| 확인 | 입력 | 이전 | v4.9 |
| --- | --- | --- | --- |
| `유심 재발급 비용` 입력 (gpt-oss 3회) | 7개, 21건 | 가드가 막음 20, 7,700원 1 | **막음 0, 7,700원 21** |
| B (gpt-oss 3회) | 131개, 393건 | 정상 90% / 수정 2% / 막음 8%, 근거 없음 7.9% | **정상 95% / 수정 3% / 막음 3%, 근거 없음 2.5%** |
| A (gpt-oss 3회) | 217개, 651건 | 정상 75% / 수정 2% / 막음 23%, 근거 없음 43% | **정상 85% / 수정 2% / 막음 13%, 근거 없음 44%** |
| B (엑사원 1회) | 131개 | 정상 77% / 수정 23% / 막음 0% | 정상 74% / 수정 26% / 막음 0% |

- **B**: 막음이 늘어난 입력은 0개, 줄어든 입력은 9개, 같은 입력은 122개다. 이전에 매번 정상이던 답이 근거 없음으로 바뀐 입력은 없었다. 이전에 3회 모두 정상이던 "유심 재발급 방법" 입력 2개는 가드가 일부 문장을 손봤지만(수정) 답은 나왔다.
- **A**: eval180은 정상 97%로 그대로이고, 답이 없는 질문인 holdout90은 막음 49%에서 25%로, 근거 없음은 92%에서 95%로 바뀌었다. 모델이 지어내다 가드에 막히던 것이 스스로 모른다고 답하는 쪽으로 옮겨 간 것이다. 이전에 3회 모두 근거 없음이던 91개 입력 중 근거 없음이 아닌 답이 나온 것은 1개(HALLU-004)이고, 그 답은 근거에 없는 "3배"를 계산하지 않고 근거의 두 금액만 말한 것이다. 이전 A 결과는 통신사 이름이 `LG U+`인 프롬프트(v4.7)로 만든 것이라 이름 차이가 섞여 있다.
- **엑사원**: 같은 입력에 같은 답이 나오는 모델인데 프롬프트가 바뀌어 55개 입력의 최종 문장이 달라졌다. 가드 기준으로는 정상 7개가 수정으로, 수정 3개가 정상으로 바뀌는 정도이고 막음과 근거 없음은 0이다.
- **남은 7,800원**: B에서 막힌 10건 중 7건이 여전히 7,800원이다. "그럼 비용은 얼마나 드나요?", "그럼비용은?" 같은 짧은 후속 질문(입력 3개)과 `유심 재발급 비용` 입력 1개(3회 중 1회)다. 한 번은 "7,800원이 아니라 7,700원입니다"라고 썼다. 줄었지만 없어지지는 않았다.
- **한계**: gpt-oss는 같은 입력에도 흔들려 3회 범위로 본다. 가드 처리 비율로만 봤고 품질 채점(Judge)은 다시 하지 않았다. 변경은 한 줄이라 이 결과를 이 한 줄의 효과로 읽었지만, 입력 일부는 회차 간 흔들림 안에서 바뀐 것이다(예: EVAL-019 1회 근거 없음, EVAL-060 1회 막음).
- **호출과 비용**: gpt-oss 1,065건(21 + 393 + 651, 모두 성공)과 엑사원 131건이다. 호출당 약 $0.0002로 곱해 $0.2 안팎으로 추정한다(실제 청구액은 확인하지 않았다).

## 남은 일

- 13번(답변 생성): gpt-oss가 근거(7,700원)와 다른 `7,800원`을 쓰는 문제는 v4.9로 크게 줄었지만(B 가드 막음 31건에서 10건) 짧은 후속 질문("그럼 비용은?")에서 남아 있다.
- 문맥 해석의 앞단 규칙(`REGEX_GATED`)이 대상이 빠진 후속 질문("그 기간에도 요금이 나와요?", "그동안 문자는 받을 수 있어요?")을 모델 없이 혼자 완결된 질문으로 넘긴다. 후속 질문 블라인드 30개 중 24개가 그랬다([EVIDENCE_RESOLVER_COMPARISON.md](EVIDENCE_RESOLVER_COMPARISON.md) "후속 질문 확인").
- 요금제 질문 분류에서 두 모델이 같이 놓치는 내 정보 질문(28개 중 9개, [PLAN_QUESTION_MODEL.md](PLAN_QUESTION_MODEL.md)).
- 전환을 모두 켠 구성으로 평가셋 v2.2와 대화 흐름 v2를 세 모델 Judge로 2~3회 잰다.
