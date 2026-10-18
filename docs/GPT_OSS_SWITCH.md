# 답변 경로 LLM 호출의 gpt-oss 전환 현황

답변 경로의 LLM 호출을 EXAONE 3.5 7.8B에서 Bedrock gpt-oss-120b로 옮기는 작업(TELME-182)의 현황표다. 호출마다 근거가 있는 것만 바꾸고, 바꿀 때는 플래그로 켠다. Bedrock 호출이 실패하면 그 호출만 EXAONE으로 대체한다(`FallbackLlmClient`).

## 호출별 상태

| # | 호출 | 클래스 | 상태 | 플래그 (기본값) | 근거 |
| --- | --- | --- | --- | --- | --- |
| 1 | 문맥 해석 | `ChatQuestionResolver` | **gpt-oss 확정**, 설정 추가 | `CHAT_QUESTION_RESOLUTION_BEDROCK` (false) | [CONTEXT_RESOLUTION.md](CONTEXT_RESOLUTION.md). 켜면 흔들림 보완도 함께 켜진다. 비용 항목 목록의 `위약금` 누락은 프롬프트를 고치면 재측정이 필요해 아직 손대지 않았다 |
| 2 | 의도 분류 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (false) | [ROUTING_CALL_COMPARISON.md](ROUTING_CALL_COMPARISON.md) 1단계 |
| 3 | 요청 개수 판정 | `QueryRoutingService` | **gpt-oss 전환 시 끔** | `ROUTING_REQUEST_INVENTORY_ENABLED` (true) | 같은 문서 2단계. `ROUTING_BEDROCK=true`면 false로 둔다 |
| 4 | 검색어 검증 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (false) | 같은 문서 3단계 |
| 5 | 후속 분석 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (false) | 같은 문서 4단계 |
| 6 | 요금제 질문 분류 | `PlanQuestionClassifier` | **gpt-oss 확정**, 설정 추가 | `CONSULT_PLAN_QUESTION_BEDROCK` (false) | [PLAN_QUESTION_MODEL.md](PLAN_QUESTION_MODEL.md). 185턴 중 엑사원 154(3회 같음), gpt-oss 164~166. gpt-oss 호출당 p50 0.60초(엑사원 시간은 배포 환경에서 잰다). 출력 한도를 30에서 512로 올렸다 |
| 7 | 조건 뽑기 | `ConditionExtractor` | **gpt-oss 확정**, 설정 있음 | `CONSULT_FAQ_CLARIFICATION_BEDROCK` (false) | [CLARIFICATION_EXTRACTION_MODEL.md](CLARIFICATION_EXTRACTION_MODEL.md). 평가셋 32건 엑사원 22%, gpt-oss 72~81%(각 3회, TELME-185). 기본값은 1~6번과 함께 아래 전환을 켤 때 켠다 |
| 8 | 되묻기 문장 | `DialogueService` | **호출 없앰**, 고정 문구 | — | 매장 위치 질문 하나에만 쓰였고 프롬프트가 대체 문구를 그대로 예시로 줬다. 지금은 "어느 지역의 매장을 찾으시나요? 역 이름이나 동네를 알려주세요."를 바로 쓴다 |
| 9 | 회색 구간 근거 판정 | `FaqEvidenceJudge` | 이미 Bedrock | — | [GRAY_ZONE_UPPER_BAND.md](GRAY_ZONE_UPPER_BAND.md) |
| 10 | 비교 근거 검증 | `LlmComparisonEvidenceResolver` | **gpt-oss 확정**, 기본 켜짐(TELME-184) | `CONSULT_COMPARISON_EVIDENCE_BEDROCK` (주지 않으면 `CONSULT_EVIDENCE_RESOLVER_BEDROCK`, true) | [EVIDENCE_RESOLVER_COMPARISON.md](EVIDENCE_RESOLVER_COMPARISON.md) "비교 근거 검증 재확인". 블라인드 양쪽 답 있음 19개에서 맞는 답 엑사원 1, gpt-oss 5~6, 틀린 답 0 |
| 11 | 후보 근거 검증 | `LlmFaqCandidateEvidenceResolver` | **gpt-oss 확정**, TELME-184에서 수정(P7), 기본 켜짐 | `CONSULT_CANDIDATE_EVIDENCE_BEDROCK` (위와 같음, true), `CONSULT_CANDIDATE_EVIDENCE_JUDGE` (true) | 같은 문서 "복합 질문 입력 확인"(TELME-182, 수정 전 채택 1건)과 "후보 근거 검증 수정"(TELME-184). 수정 후 개발용 맞는 답 30 / 31 / 30, 블라인드 12 / 12 / 10. 문맥 해석이 후속 질문을 복원하지 못하면 틀린 답이 날 수 있다("후속 질문 확인") |
| 12 | 근거 적합성 판정 | `EvidenceRelevanceChecker` | **꺼진 채로 둠**(TELME-182 결정) | `RAG_EVIDENCE_CHECK_ENABLED` (false) | 회색 구간(Q_A 0.69~0.80, 질문 0.80~0.90)은 9가 v4로 이미 판정한다. 겹치지 않는 것은 판정 없이 통과하는 높은 점수 구간뿐이다. 켜면 기본 `LlmClient`(엑사원)로 판정하므로, 다시 쓰려면 Bedrock 연결과 재측정이 먼저다 |
| 13 | 답변 생성 | `RagAnswerGenerator` | 미정 | 없음 | #167 결과를 TELME-180 Judge로 채점할 예정 |

대화 요약, 세션 제목, 상담 종료 요약은 답변 경로 밖이고 두 모델이 같게 나왔다([BEDROCK_SWITCH_EVALUATION.md](BEDROCK_SWITCH_EVALUATION.md)).

## 전환 켜기

```bash
ROUTING_BEDROCK=true
ROUTING_REQUEST_INVENTORY_ENABLED=false
CHAT_QUESTION_RESOLUTION_BEDROCK=true
CONSULT_PLAN_QUESTION_BEDROCK=true
CONSULT_FAQ_CLARIFICATION_BEDROCK=true
```

- `QueryRoutingService`는 `routingLlmClient`(`RoutingLlmConfiguration`)를, `ChatQuestionResolver`는 `contextResolutionLlmClient`(`ChatQuestionResolutionLlmConfiguration`)를, `PlanQuestionClassifier`는 `planQuestionLlmClient`(`PlanQuestionConfiguration`)를 이름으로 주입받는다. 다른 호출의 기본 모델은 그대로 EXAONE이다.
- `LLM_BEDROCK_ENABLED=false`로 Bedrock 빈이 없으면 플래그를 켜도 EXAONE을 쓴다.
- 1~7번은 아직 기본 꺼짐이다. 아래 마지막 측정 뒤에 한 번에 켠다. 10·11번(근거 검증)은 TELME-184에서 기본 켜짐으로 들어갔다.

## 남은 일

- 미정 호출(13) 결정.
- 문맥 해석의 앞단 규칙(`REGEX_GATED`)이 대상이 빠진 후속 질문("그 기간에도 요금이 나와요?", "그동안 문자는 받을 수 있어요?")을 모델 없이 혼자 완결된 질문으로 넘긴다. 후속 질문 블라인드 30개 중 24개가 그랬다([EVIDENCE_RESOLVER_COMPARISON.md](EVIDENCE_RESOLVER_COMPARISON.md) "후속 질문 확인").
- 요금제 질문 분류에서 두 모델이 같이 놓치는 내 정보 질문(28개 중 9개, [PLAN_QUESTION_MODEL.md](PLAN_QUESTION_MODEL.md)).
- 전환을 모두 켠 구성으로 평가셋 v2.2와 대화 흐름 v2를 세 모델 Judge로 2~3회 잰다.
