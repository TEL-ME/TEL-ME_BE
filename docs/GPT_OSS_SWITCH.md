# 답변 경로 LLM 호출의 gpt-oss 전환 현황

답변 경로의 LLM 호출을 EXAONE 3.5 7.8B에서 Bedrock gpt-oss-120b로 옮기는 작업(TELME-182)의 현황표다. 호출마다 근거가 있는 것만 바꾸고, 바꿀 때는 플래그로 켠다. Bedrock 호출이 실패하면 그 호출만 EXAONE으로 대체한다(`FallbackLlmClient`).

## 호출별 상태

| # | 호출 | 클래스 | 상태 | 플래그 (기본값) | 근거 |
| --- | --- | --- | --- | --- | --- |
| 1 | 문맥 해석 | `ChatQuestionResolver` | 미정 | 없음 | [CONTEXT_RESOLUTION.md](CONTEXT_RESOLUTION.md). gpt-oss로 바꾸면 `CHAT_QUESTION_RESOLUTION_STABILITY_GUARDS=true`를 함께 켠다 |
| 2 | 의도 분류 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (false) | [ROUTING_CALL_COMPARISON.md](ROUTING_CALL_COMPARISON.md) 1단계 |
| 3 | 요청 개수 판정 | `QueryRoutingService` | **gpt-oss 전환 시 끔** | `ROUTING_REQUEST_INVENTORY_ENABLED` (true) | 같은 문서 2단계. `ROUTING_BEDROCK=true`면 false로 둔다 |
| 4 | 검색어 검증 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (false) | 같은 문서 3단계 |
| 5 | 후속 분석 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (false) | 같은 문서 4단계 |
| 6 | 요금제 질문 분류 | `PlanQuestionClassifier` | 미측정 | 없음 (라우팅 클라이언트를 쓰지 않음) | answerSources 라벨로 잴 예정 |
| 7 | 조건 뽑기 | `ConditionExtractor` | **gpt-oss 확정**, 기본값 켜짐 | `CONSULT_FAQ_CLARIFICATION_BEDROCK` (true) | [CLARIFICATION_EXTRACTION_MODEL.md](CLARIFICATION_EXTRACTION_MODEL.md) |
| 8 | 되묻기 문장 | `DialogueService` | **호출 없앰**, 고정 문구 | — | 매장 위치 질문 하나에만 쓰였고 프롬프트가 대체 문구를 그대로 예시로 줬다. 지금은 "어느 지역의 매장을 찾으시나요? 역 이름이나 동네를 알려주세요."를 바로 쓴다 |
| 9 | 회색 구간 근거 판정 | `FaqEvidenceJudge` | 이미 Bedrock | — | [GRAY_ZONE_UPPER_BAND.md](GRAY_ZONE_UPPER_BAND.md) |
| 10, 11 | 비교·후보 근거 해석 | 근거 해석기 | 미정 | `CONSULT_EVIDENCE_RESOLVER_BEDROCK` (false) | [EVIDENCE_RESOLVER_COMPARISON.md](EVIDENCE_RESOLVER_COMPARISON.md) |
| 12 | 근거 적합성 판정 | `EvidenceRelevanceChecker` | 꺼짐, 삭제 또는 재측정 검토 | `RAG_EVIDENCE_CHECK_ENABLED` (false) | TELME-54에서 AWS 의존을 이유로 껐다. 9·10·11과 겹치는지 먼저 본다 |
| 13 | 답변 생성 | `RagAnswerGenerator` | 미정 | 없음 | #167 결과를 TELME-180 Judge로 채점할 예정 |

대화 요약, 세션 제목, 상담 종료 요약은 답변 경로 밖이고 두 모델이 같게 나왔다([BEDROCK_SWITCH_EVALUATION.md](BEDROCK_SWITCH_EVALUATION.md)).

## 라우팅 전환 켜기

```bash
ROUTING_BEDROCK=true
ROUTING_REQUEST_INVENTORY_ENABLED=false
```

- `QueryRoutingService`는 `routingLlmClient`(`RoutingLlmConfiguration`)를 이름으로 주입받는다. 다른 호출의 기본 모델은 그대로 EXAONE이다.
- `LLM_BEDROCK_ENABLED=false`로 Bedrock 빈이 없으면 플래그를 켜도 EXAONE을 쓴다.

## 남은 일

- 미정 호출(1, 10·11, 13) 결정, 6 측정, 12 정리.
- 전환을 모두 켠 구성으로 평가셋 v2.2와 대화 흐름 v2를 세 모델 Judge로 2~3회 잰다.
