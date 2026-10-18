# 답변 경로 LLM 호출의 gpt-oss 전환 현황

답변 경로의 LLM 호출을 EXAONE 3.5 7.8B에서 Bedrock gpt-oss-120b로 옮기는 작업(TELME-182)의 현황표다. 호출마다 근거가 있는 것만 바꾸고, 바꿀 때는 플래그로 켠다. Bedrock 호출이 실패하면 그 호출만 EXAONE으로 대체한다(`FallbackLlmClient`).

## 호출별 상태

| # | 호출 | 클래스 | 상태 | 플래그 (기본값) | 근거 |
| --- | --- | --- | --- | --- | --- |
| 1 | 문맥 해석 | `ChatQuestionResolver` | **gpt-oss 확정**, 설정 추가 | `CHAT_QUESTION_RESOLUTION_BEDROCK` (false) | [CONTEXT_RESOLUTION.md](CONTEXT_RESOLUTION.md). 켜면 흔들림 보완도 함께 켜진다. 비용 항목 목록의 `위약금` 누락은 프롬프트를 고치면 재측정이 필요해 아직 손대지 않았다 |
| 2 | 의도 분류 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (false) | [ROUTING_CALL_COMPARISON.md](ROUTING_CALL_COMPARISON.md) 1단계 |
| 3 | 요청 개수 판정 | `QueryRoutingService` | **gpt-oss 전환 시 끔** | `ROUTING_REQUEST_INVENTORY_ENABLED` (true) | 같은 문서 2단계. `ROUTING_BEDROCK=true`면 false로 둔다. 켜 둔 채 Bedrock으로 라우팅하면 시작할 때 경고 로그를 남긴다 |
| 4 | 검색어 검증 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (false) | 같은 문서 3단계 |
| 5 | 후속 분석 | `QueryRoutingService` | **gpt-oss 확정**, 설정 추가 | `ROUTING_BEDROCK` (false) | 같은 문서 4단계 |
| 6 | 요금제 질문 분류 | `PlanQuestionClassifier` | **gpt-oss 확정**, 설정 추가 | `CONSULT_PLAN_QUESTION_BEDROCK` (false) | [PLAN_QUESTION_MODEL.md](PLAN_QUESTION_MODEL.md). 185턴 중 엑사원 154(3회 같음), gpt-oss 164~166. gpt-oss 호출당 p50 0.60초(엑사원 시간은 배포 환경에서 잰다). 출력 한도를 30에서 512로 올렸다 |
| 7 | 조건 뽑기 | `ConditionExtractor` | **gpt-oss 확정**, 설정 있음 | `CONSULT_FAQ_CLARIFICATION_BEDROCK` (false) | [CLARIFICATION_EXTRACTION_MODEL.md](CLARIFICATION_EXTRACTION_MODEL.md). 평가셋 32건 엑사원 22%, gpt-oss 72~81%(각 3회, TELME-185). 기본값은 1~6번과 함께 아래 전환을 켤 때 켠다 |
| 8 | 되묻기 문장 | `DialogueService` | **호출 없앰**, 고정 문구 | — | 매장 위치 질문 하나에만 쓰였고 프롬프트가 대체 문구를 그대로 예시로 줬다. 지금은 "어느 지역의 매장을 찾으시나요? 역 이름이나 동네를 알려주세요."를 바로 쓴다 |
| 9 | 회색 구간 근거 판정 | `FaqEvidenceJudge` | 이미 Bedrock | — | [GRAY_ZONE_UPPER_BAND.md](GRAY_ZONE_UPPER_BAND.md) |
| 10 | 비교 근거 검증 | `LlmComparisonEvidenceResolver` | **gpt-oss 확정**, 기본 켜짐(TELME-184) | `CONSULT_COMPARISON_EVIDENCE_BEDROCK` (주지 않으면 `CONSULT_EVIDENCE_RESOLVER_BEDROCK`, true) | [EVIDENCE_RESOLVER_COMPARISON.md](EVIDENCE_RESOLVER_COMPARISON.md) "비교 근거 검증 재확인". 블라인드 양쪽 답 있음 19개에서 맞는 답 엑사원 1, gpt-oss 5~6, 틀린 답 0 |
| 11 | 후보 근거 검증 | `LlmFaqCandidateEvidenceResolver` | **gpt-oss 확정**, TELME-184에서 수정(P7), 기본 켜짐 | `CONSULT_CANDIDATE_EVIDENCE_BEDROCK` (위와 같음, true), `CONSULT_CANDIDATE_EVIDENCE_JUDGE` (true) | 같은 문서 "복합 질문 입력 확인"(TELME-182, 수정 전 채택 1건)과 "후보 근거 검증 수정"(TELME-184). 수정 후 개발용 맞는 답 30 / 31 / 30, 블라인드 12 / 12 / 10. 문맥 해석이 후속 질문을 복원하지 못하면 틀린 답이 날 수 있다("후속 질문 확인") |
| 12 | 근거 적합성 판정 | `EvidenceRelevanceChecker` | **꺼진 채로 둠**(TELME-182 결정) | `RAG_EVIDENCE_CHECK_ENABLED` (false) | 회색 구간(Q_A 0.69~0.80, 질문 0.80~0.90)은 9가 v4로 이미 판정한다. 겹치지 않는 것은 판정 없이 통과하는 높은 점수 구간뿐이다. 켜면 기본 `LlmClient`(엑사원)로 판정하므로, 다시 쓰려면 Bedrock 연결과 재측정이 먼저다 |
| 13 | 답변 생성 | `RagAnswerGenerator` | **gpt-oss 확정**(TELME-180), 설정 추가 | `RAG_ANSWER_BEDROCK` (false) | [채점 결과](chat-judge/results/20261018-answer-generation/README.md). 정답 근거 고정(A, 217개) 통과율 엑사원 43%, gpt-oss 73~74%. 결정된 앞단을 통과한 입력(B, 131개)은 Judge가 갈린 답을 사람이 판정해 엑사원 67%, gpt-oss 76~77%. B 입력과 두 모델 답변은 아래 "답변 생성 입력과 답변"(TELME-182) |

대화 요약, 세션 제목, 상담 종료 요약은 답변 경로 밖이고 두 모델이 같게 나왔다([BEDROCK_SWITCH_EVALUATION.md](BEDROCK_SWITCH_EVALUATION.md)).

## 전환 켜기

```bash
ROUTING_BEDROCK=true
ROUTING_REQUEST_INVENTORY_ENABLED=false
CHAT_QUESTION_RESOLUTION_BEDROCK=true
CONSULT_PLAN_QUESTION_BEDROCK=true
CONSULT_FAQ_CLARIFICATION_BEDROCK=true
RAG_ANSWER_BEDROCK=true
```

- `QueryRoutingService`는 `routingLlmClient`(`RoutingLlmConfiguration`)를, `ChatQuestionResolver`는 `contextResolutionLlmClient`(`ChatQuestionResolutionLlmConfiguration`)를, `PlanQuestionClassifier`는 `planQuestionLlmClient`(`PlanQuestionConfiguration`)를, `RagAnswerGenerator`는 `ragAnswerLlmClient`(`RagAnswerLlmConfiguration`)를 이름으로 주입받는다. 다른 호출의 기본 모델은 그대로 EXAONE이다.
- `LLM_BEDROCK_ENABLED=false`로 Bedrock 빈이 없으면 플래그를 켜도 EXAONE을 쓴다.
- 답변 생성은 스트리밍이다. Bedrock 클라이언트는 답을 다 받은 뒤 한 번에 보내므로(TELME-182 B 입력에서 gpt-oss 호출 중앙값 0.44초) 글자가 나눠서 나오지 않는다. 첫 글자를 보내기 전에 Bedrock이 실패하면 같은 요청을 엑사원 스트리밍으로 다시 부른다(`FallbackLlmClient`). 글자가 나간 뒤에는 대체하지 않는다.
- 1~7번과 13번은 아직 기본 꺼짐이다. 아래 마지막 측정 뒤에 한 번에 켠다. 10·11번(근거 검증)은 TELME-184에서 기본 켜짐으로 들어갔다.

## 답변 생성 입력과 답변 (13번)

13번 모델은 품질 채점으로 정해야 해서 TELME-180에서 정했다(gpt-oss). TELME-182에서는 위 결정으로 앞단을 통과한 입력을 저장하고, 두 모델로 답을 만들었다. 답변 생성 B 입력은 [ANSWER_GENERATION_COMPARISON.md](ANSWER_GENERATION_COMPARISON.md)(TELME-178)에 처음 만든 것이 있고, 그 입력으로 엑사원·gpt-oss를 각 3회 돌린 결과는 [ANSWER_GENERATION_EXAONE_REPEAT.md](ANSWER_GENERATION_EXAONE_REPEAT.md)(TELME-186)에 있다. 그 입력은 앞단 전체를 측정용 중계기로 gpt-oss에 보내 만든 것이라, 이번에는 앱 설정으로 결정된 구성만 켜고 다시 캡처했다.

| 항목 | 내용 |
|---|---|
| 앞단 | 1·2·4·5·6·7·10·11번 gpt-oss(앱 Bedrock 클라이언트), 3번 끔. 캡처 프로브가 회색 구간 판정과 되묻기를 켠다. 답변 생성(13번)은 앱 기본값(엑사원)이라, 대화 이력에 들어가는 앞 턴 응답은 엑사원이 만든 것이다 |
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

## 남은 일

- 13번(답변 생성): 짧은 `유심 재발급 비용` 질문에 gpt-oss가 근거(7,700원)와 다른 `7,800원`을 써서 가드가 거절로 바꾸는 문제(TELME-182 B 입력에서 가드가 대체한 31건 중 26건).
- 문맥 해석의 앞단 규칙(`REGEX_GATED`)이 대상이 빠진 후속 질문("그 기간에도 요금이 나와요?", "그동안 문자는 받을 수 있어요?")을 모델 없이 혼자 완결된 질문으로 넘긴다. 후속 질문 블라인드 30개 중 24개가 그랬다([EVIDENCE_RESOLVER_COMPARISON.md](EVIDENCE_RESOLVER_COMPARISON.md) "후속 질문 확인").
- 요금제 질문 분류에서 두 모델이 같이 놓치는 내 정보 질문(28개 중 9개, [PLAN_QUESTION_MODEL.md](PLAN_QUESTION_MODEL.md)).
- 전환을 모두 켠 구성으로 평가셋 v2.2와 대화 흐름 v2를 세 모델 Judge로 2~3회 잰다.
