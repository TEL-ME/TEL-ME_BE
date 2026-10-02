# 질문·검색 근거·최종 답변 추적 검증

## 검증 범위와 처리 경로

- 목적: 한 실행의 질문·검색 근거·생성 시도·Guard 결과·최종 저장 답변을 연결해 실패 원인을 확인한다. 환각률·답변 품질 개선 측정은 아니다.
- 검증 기준: PR #85 HEAD `55b3839463854e453dd112519381fcd7021652d6`와 develop `98d16817584156da860b26aabe56f2c24460f7bb`을 결합하고 미커밋 추적 컨벤션 보완을 적용한 검증용 사본. 실제 PR 브랜치를 rebase하거나 원격 HEAD를 변경한 결과가 아니다.
- 선행 상태: #80·#82·#83·#84는 병합됐으며, #89의 V18도 develop에 병합됐다. #81의 V20은 미병합이며 이 PR의 V19 이후 적용해야 한다.
- 기본 설정: `consult.chat-integration-enabled=true`, `consult.rag-integration-enabled=true`. 운영 배포의 환경변수와 실제 활성 경로는 미확인이다.
- 상담 연결 경로: `ChatProcessingDispatcher → ConsultChatProcessingService → FaqSearchAnswerProvider → RagSearchResultAnswerGenerator → RagAnswerGenerator → AnswerGuard → ConsultChatPersistenceService → 최종 SSE`.
- 추적 조회 구조: Controller → Service → Repository로 SQL 접근을 분리하고, Converter가 `ChatExecutionTraceResponse` record DTO를 구성한다. API의 JSON 필드·null·배열 의미는 유지한다.
- 모델·프롬프트·Guard 판정·Router·검색 설정과 기존 저장·전송 계약은 변경하지 않는다.

## 기록 위치와 연결 식별자

- 사용자 입력: `chat_messages.content`, `chat_executions.input_message_id`. 이번 턴의 사용자 메시지와 분석기의 상담 원문은 구분한다.
- 기존 라우팅: `query_routings.message_id`로 intent·refined_query·confidence·method를 연결한다. 없는 신뢰도나 이전 턴의 라우팅을 현재 실행에 복사하지 않는다.
- 실행 메타데이터: `chat_executions.pipeline_trace`에 다음 단계를 기록한다.
  - `processing`·`analysis`: 처리기, purpose·action, 원문·정제 질문.
  - `searchRequests`: 실제 검색 질문·ORIGINAL/REFINED·topK·consultRequestId.
  - `searchResults`: EMPTY/FOUND/ERROR/CANCELLED, FAQ ID·slotId·순위·점수·matchedVariant·version·질문/답변 스냅샷.
  - `generationInputs`: 생성에 전달한 FAQ 후보·질문·promptVersion·consultRequestId.
  - `guardEvidenceScope`: FAQ_ANSWERS_ONLY. 생성 프롬프트에 FAQ 질문도 포함되는 것과 구분한다.
  - `guardResults`: KEPT/MODIFIED/REPLACED/NOT_RUN, 이유 범주·consultRequestId.
  - `finalTransmission`: outputMessageId, DISPATCH_ATTEMPTED/RETURNED/ERROR.
- 모델 시도: 기존 `llm_generations.execution_id`·generation_id·task_type·attempt·status·model·prompt_version을 사용한다. `request_options`에는 provider·실제 요청 options/format·consultRequestId를 기록한다.
- 최종 답변: `chat_executions.output_message_id → chat_messages.content/status/answer_basis`. 답변을 별도 추적 열에 복사 저장하지 않는다. 실패한 출력 메시지의 content는 null이다.
- 여러 상담 요청과 생성 시도는 consultRequestId로 구분한다. generationInputs·guardResults는 배열이며, 단일 생성의 generationInput 별칭과 마지막 guard 요약만으로 여러 결과를 판단하지 않는다.
- JSON 갱신과 배열 추가는 실행 행에서 원자적으로 수행한다. 재시도·동시 실행의 기록과 다른 단계가 섞이거나 덮어써지지 않는지 회귀로 확인한다.
- FAQ 스냅샷은 당시 검색 응답과 생성 후보를 재확인하는 자료다. 모델이 어느 문장을 실제 사용했는지 입증하는 인용이나 품질 판정은 아니다.

## 조회·권한·기록 실패 계약

- API: `GET /api/v1/chat/sessions/{sessionId}/executions/{executionId}/trace`.
- 기존 `CustomResponse.result` 안에 executionId·sessionId·inputMessageId·outputMessageId·originalUserMessage·status·errorCode·finalAnswer·answerStatus·answerBasis·steps·routing·modelAttempts·traceRecorded를 반환한다.
- SSE 구독과 같은 `ChatSessionService.getExecution` 소유권 검사를 먼저 적용한다. 다른 사용자·다른 게스트의 실행이나 sessionId 불일치는 404이며 기록을 반환하지 않는다. 별도 관리자 권한은 추가하지 않는다.
- 과거 pipeline_trace=null은 **미기록**이다. traceRecorded=false로 반환하며 소급 재구성하거나 현재 설정으로 추정하지 않는다. 모델 시도의 configuration=null도 당시 설정 미기록이다.
- 기록 저장은 best effort다. 기록 실패를 격리하고 사용자 값이나 DB 예외 본문을 추적 실패 로그에 출력하지 않는다. traceRecorded=true가 모든 단계의 완전성을 뜻하지는 않는다.
- Guard 전 원시 생성문·전체 프롬프트·제거된 문장·예외 본문을 새 추적 기록에 저장하지 않는다. Guard 세부 규칙 ID는 현재 반환되지 않아 안전한 결과·이유 범주만 기록한다.
- 기록은 기존 대화 접근 권한과 삭제 생명주기를 따른다. 별도 보존기간·백필·관리 화면·검색 인덱스는 추가하지 않는다. FAQ 스냅샷의 보존 정책은 팀 결정 문서 반영 여부를 확인해야 한다.

## 마이그레이션과 배포 순서

- 순서: develop의 V18(#89) → V19(#85) → V20(#81). 신규 앱 배포 전에 필요한 마이그레이션을 적용한다.
- V19: `chat_executions.pipeline_trace`, `llm_generations.request_options`에 nullable jsonb 열 2개를 추가한다. 기존 행의 값은 null이며 기존 ID·상태·내용을 변경하지 않는다.
- V20: 업무 종류 4개를 코드 중복 없이 추가하고 `stores.lock_version`을 NOT NULL·기본값 0으로 추가한다. #81 원본 SQL을 변경하지 않고 호환성만 확인한다.
- 이전 추적 SQL을 이미 적용한 DB는 배포 전에 실제 파일·checksum·Flyway 적용 이력을 확인해야 한다. 파일 교체만으로 전환이 완료됐다고 가정하지 않는다.
- 운영·공유 DB의 이력은 이번 검증 대상이 아니다. 기존 마이그레이션을 수정하거나 outOfOrder·repair로 적용 순서를 우회하지 않는다.
- 병합·배포 직전에 버전 중복과 적용 순서를 다시 확인한다. ALTER TABLE의 잠금 영향과 운영 확장 설치 권한도 별도로 확인한다.

## 검증 결과

- 실행일: 2026-10-02. 위 HEAD와 develop을 결합한 사본의 전체 `build --no-daemon --rerun-tasks` 성공.
- 환경: JDK 21, 전용 로컬 PostgreSQL 16·pgvector·PostGIS·intarray, 새 독립 DB, `TELME_DB_TESTS=true`.
- 직접 실행 결과: **총 1,396건 / 통과 1,394건 / 실패 0건 / 오류 0건 / 스킵 2건**.
- 스킵: 조건부 `RoutingEvaluationProbe`, `AnswerQualityBaselineProbe` 각 1건. 유료·외부 모델/Judge 호출과 실제 브라우저 검증은 실행하지 않았다.
- 아래는 같은 전체 실행에 포함된 관련 테스트다. 별도 실행처럼 합산하지 않는다.

| 테스트 | 통과 | 실패·오류·스킵 |
|---|---:|---:|
| ConsultGuardedAnswerDeliveryIntegrationTest | 35 | 0 |
| ChatExecutionTraceServiceTest | 2 | 0 |
| ChatExecutionTraceConverterTest | 5 | 0 |
| RagAnswerGeneratorTest | 26 | 0 |
| RagAnswerDeliveryTest | 16 | 0 |
| AnswerGuardUserEvidenceTest | 67 | 0 |
| AnswerGuardTest | 38 | 0 |
| RetryingLlmClientTest | 15 | 0 |
| ConsultGuardedDeliveryConfigurationTest | 3 | 0 |
| ConsultChatPipelineConfigurationTest | 2 | 0 |
| AdminUnansweredControllerTest / AdminUnansweredQueryServiceTest | 6 / 10 | 0 |

- 연결 테스트 대역: 분석·검색·모델은 대역, 활성 상담 처리기·Guard·DB·HTTP 조회·SSE 이벤트 경계는 실제 구현이다. 실제 모델 품질이나 브라우저 표시의 증거로 해석하지 않는다.
- 확인 사례: 정상 답변, Guard 부분 수정·안전 안내 교체·예외, 검색 없음·검색 오류·재작성 검색, 모델 오류·타임아웃·취소·재시도, 최종 저장/조회/전달 일치, 타인 접근 차단, 동시 요청 분리.
- 응답 계약 검증: 실제 직렬화 JSON의 전체 필드·점수/신뢰도·단일 생성 별칭, 여러 생성의 분리, 미기록/null 유지, 저장 JSON 해석 실패의 기존 공통 500 오류를 확인한다.
- 스냅샷·기록 검증: FAQ 수정 후 당시 내용·버전·점수 유지, 과거 미기록 행, 기존 라우팅 연결, 병렬 기록 보존, 여러 생성의 상담 요청 연결, 기록 실패 격리·로그 보호.
- 별도 SQL 호환성 검증: 업무 코드가 이미 있는 경우와 비어 있는 경우의 독립 DB 2개에서 Flyway target을 18→19→20으로 변경해 migrate·validate 성공. 각 DB에서 V18까지 14개 기본 마이그레이션, V19·V20 각 1개를 순서대로 적용했다. 개발용 시드 마이그레이션은 이 SQL 검증에 포함하지 않았다.
- SQL 결과: V18 인덱스 존재, V19 열 2개·기존 행 null 및 내용 보존, V20 업무 코드 4개·기존 코드/명칭 보존·기존 매장 lock_version=0·추적 값과 기존 업무 데이터 보존. V19/V20 파일 내용과 번호는 원본과 동일하다.
- SQL 호환성 결과를 #81 기능 전체 검증으로 표현하지 않는다. 과거 실험·다른 검증자의 실행 수치는 위 직접 실행 결과에 합산하지 않는다.

## 실패 추적 예시

- 사례: `exhaustedModelFailureTerminatesOnceWithReadableGuidance(TIMEOUT)` 연결 테스트.
- 입력: “유심 재발급 비용과 배송비를 알려주세요”. 분석 대역은 GENERAL_FAQ/PROCEED이며 신뢰도 행을 임의로 만들지 않는다.
- 같은 executionId·consultRequestId에서 원문 검색 FOUND → 비용 7,700원·배송 2~3 영업일 FAQ 스냅샷 → generationInputs → RAG_ANSWER 시도 1·2 TIMEOUT을 확인한다.
- 모델 기록은 대역의 `guard-delivery-test`, temperature=0.0·num_predict=1024·num_ctx=8192 요청 설정이다. 실제 외부 모델 실행 기록은 아니다.
- 결과: Guard NOT_RUN/GENERATION_FAILED, 실행 FAILED/LLM504-0, 출력 메시지 FAILED/content=null, finalTransmission 없음. 오류 안내만 전달하며 답변 token·정상 complete·부분 답변 저장은 없다.

## 한계와 남은 확인

- follow-up에는 기존 query_routings 행이 없을 수 있다. 현재 실행의 purpose·실제 검색·consultRequestId를 기준으로 확인한다.
- #75 복합 질문은 미병합이다. 실제 결합 후 하위 요청과 최종 전송 단계 계측을 별도로 검증해야 한다. 직접 반환한 근거 부족 안내에는 generationInputs·모델 시도가 없을 수 있다.
- 요청 모델 태그·옵션은 기록하지만 실제 가중치 digest·Ollama 엔진 버전·서버 빌드 SHA는 확인하지 못한다.
- 과거 평가 파일은 운영 executionId/messageId가 없어 API 기록과 자동 연결하지 않는다. eval ID를 실행 ID로 간주하거나 소급 추정하지 않는다.
- 검색·생성 스냅샷에는 내용 중복이 있다. 후보 수·복합 질문 범위 확대 시 저장 크기와 보존 정책을 재검토한다.
- DISPATCH_RETURNED는 서버의 이벤트 전달 함수 반환을 뜻하며 실제 구독자 수신·브라우저 표시는 입증하지 않는다.
- 실제 화면·소켓·새로고침 복원·운영 활성 설정은 미검증이다. 화면의 최종 답변과 저장/조회 결과, 실패 시 로딩 종료, 재접속 시 중복 표시 여부는 별도 확인해야 한다.
