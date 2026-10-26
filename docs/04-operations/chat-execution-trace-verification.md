# 질문·검색 근거·최종 답변 추적

## 목적과 처리 경로

- 목적: 한 실행의 질문·검색 근거·생성 시도·Guard 결과·최종 저장 답변을 연결해 실패 원인을 확인한다. 환각률·답변 품질 개선 측정은 아니다.
- 상담 연결 경로: `ChatProcessingDispatcher → ConsultChatProcessingService → FaqSearchAnswerProvider → RagSearchResultAnswerGenerator → RagAnswerGenerator → AnswerGuard → ConsultChatPersistenceService → 최종 SSE`.
- 이 경로는 `telme.consult.chat-integration-enabled`·`telme.consult.rag-integration-enabled`가 켜졌을 때 동작한다. 운영 배포의 실제 환경변수는 배포 환경에서 확인한다.
- 추적 조회 구조: Controller → Service → Repository로 SQL 접근을 분리하고, Converter가 `ChatExecutionTraceResponse` record DTO를 구성한다.
- 추적 기록은 모델·프롬프트·Guard 판정·Router·검색 설정과 기존 저장·전송 계약을 바꾸지 않는다.

## 기록 위치와 연결 식별자

- 사용자 입력: `chat_messages.content`, `chat_executions.input_message_id`. 이번 턴의 사용자 메시지와 분석기의 상담 원문은 구분한다.
- 기존 라우팅: `query_routings.message_id`로 intent·refined_query·confidence·method를 연결한다. 없는 신뢰도나 이전 턴의 라우팅을 현재 실행에 복사하지 않는다.
- 실행 메타데이터: `chat_executions.pipeline_trace`에 다음 단계를 기록한다.
  - `processing`·`analysis`: 처리기, purpose·action, 원문·정제 질문.
  - `searchRequests`: 실제 검색 질문·ORIGINAL/REFINED·topK·consultRequestId.
  - `searchResults`: EMPTY/FOUND/ERROR/CANCELLED, FAQ ID·slotId·순위·점수·matchedVariant·version·질문/답변 스냅샷.
  - `generationInputs`: 생성에 전달한 FAQ 후보·질문·promptVersion·consultRequestId·`guardEvidenceScope`(FAQ_ANSWERS_ONLY). Guard는 FAQ 답변만 근거로 보며, 생성 프롬프트에 FAQ 질문도 포함되는 것과 구분한다.
  - `guardResults`(배열)·`guard`(마지막 결과): KEPT/MODIFIED/REPLACED/NOT_RUN, 이유 범주·consultRequestId.
  - `finalTransmission`: outputMessageId, DISPATCH_RETURNED/DISPATCH_ERROR. 최종 답변 전송을 늦추지 않도록 전송 후에 기록한다.
- 모델 시도: 기존 `llm_generations.execution_id`·generation_id·task_type·attempt·status·model·prompt_version을 사용한다. `request_options`에는 provider·실제 요청 options/format·consultRequestId를 기록한다. 요청 옵션을 만들지 못하면 호출 기록 행은 남기고 `request_options`만 null로 둔다.
- 최종 답변: `chat_executions.output_message_id → chat_messages.content/status/answer_basis`. 답변을 별도 추적 열에 복사 저장하지 않는다. 실패한 출력 메시지의 content는 null이다.
- 여러 상담 요청과 생성 시도는 consultRequestId로 구분한다. generationInputs·guardResults는 배열이며, 단일 생성의 generationInput 별칭과 마지막 guard 요약만으로 여러 결과를 판단하지 않는다.
- JSON 갱신과 배열 추가는 실행 행에서 한 UPDATE로 원자적으로 수행한다. 재시도·동시 실행의 기록이 다른 단계와 섞이거나 덮어써지지 않는다.
- FAQ 스냅샷은 당시 검색 응답과 생성 후보를 재확인하는 자료다. 모델이 어느 문장을 실제 사용했는지 입증하는 인용이나 품질 판정은 아니다.

## 조회·권한·기록 실패 계약

- API: `GET /api/v1/chat/sessions/{sessionId}/executions/{executionId}/trace`.
- 기존 `CustomResponse.result` 안에 executionId·sessionId·inputMessageId·outputMessageId·originalUserMessage·status·errorCode·finalAnswer·answerStatus·answerBasis·steps·routing·modelAttempts·traceRecorded를 반환한다.
- SSE 구독과 같은 `ChatSessionService.getExecution` 소유권 검사를 먼저 적용한다. 다른 사용자·다른 게스트의 실행이나 sessionId 불일치는 404이며 기록을 반환하지 않는다. 별도 관리자 권한은 없다.
- pipeline_trace=null은 **미기록**이다. traceRecorded=false로 반환하며 소급 재구성하거나 현재 설정으로 추정하지 않는다. 모델 시도의 configuration=null도 당시 설정 미기록이다.
- 저장된 추적 JSON을 해석하지 못하면 내용을 노출하지 않고 공통 500 오류를 반환한다.
- 기록 저장은 best effort다. 기록 실패를 격리하고 사용자 값이나 DB 예외 본문을 추적 실패 로그에 출력하지 않는다. traceRecorded=true가 모든 단계의 완전성을 뜻하지는 않는다.
- Guard 전 원시 생성문·전체 프롬프트·제거된 문장·예외 본문을 추적 기록에 저장하지 않는다. Guard 세부 규칙 ID는 반환되지 않아 결과·이유 범주만 기록한다.
- 기록은 기존 대화 접근 권한과 삭제 생명주기를 따른다. 별도 보존기간·백필·관리 화면·검색 인덱스는 없다. FAQ 스냅샷의 보존 정책은 팀 결정 문서 반영 여부를 확인해야 한다.

## 마이그레이션

- V19: `chat_executions.pipeline_trace`, `llm_generations.request_options`에 nullable jsonb 열 2개를 추가한다. 기존 행의 값은 null이며 기존 ID·상태·내용을 변경하지 않는다.
- 신규 앱 배포 전에 적용해야 한다. 배포 직전에 버전 중복과 적용 순서, ALTER TABLE의 잠금 영향을 확인한다.

## 검증 범위

- 연결 테스트(`ConsultGuardedAnswerDeliveryIntegrationTest`): 분석·검색·모델은 대역이고, 상담 처리기·Guard·DB·HTTP 조회·SSE 이벤트 경계는 실제 구현이다. 실제 모델 품질이나 브라우저 표시의 증거가 아니다.
- 확인 사례: 정상 답변, Guard 부분 수정·안전 안내 교체·예외, 검색 없음·검색 오류·재작성 검색, 모델 오류·타임아웃·취소·재시도, 최종 저장/조회/전달 일치, 타인 접근 차단, 동시 요청 분리.
- 응답 계약(`ChatExecutionTraceConverterTest`): 직렬화 JSON의 전체 필드·점수/신뢰도·단일 생성 별칭, 여러 생성의 분리, 미기록/null 유지, 저장 JSON 해석 실패의 공통 500 오류.
- 스냅샷·기록: FAQ 수정 후 당시 내용·버전·점수 유지, 과거 미기록 행, 기존 라우팅 연결, 병렬 기록 보존, 여러 생성의 상담 요청 연결, 기록 실패 격리·로그 보호(`ChatExecutionTraceServiceTest`), 요청 옵션 생성 실패 시 호출 기록 유지(`LlmGenerationRecorderTest`).

## 실패 추적 예시

- 사례: `exhaustedModelFailureTerminatesOnceWithReadableGuidance(TIMEOUT)` 연결 테스트.
- 입력: “유심 재발급 비용과 배송비를 알려주세요”. 분석 대역은 GENERAL_FAQ/PROCEED이며 신뢰도 행을 임의로 만들지 않는다.
- 같은 executionId·consultRequestId에서 원문 검색 FOUND → 비용 7,700원·배송 2~3 영업일 FAQ 스냅샷 → generationInputs → RAG_ANSWER 시도 1·2 TIMEOUT을 확인한다.
- 모델 기록은 대역의 `guard-delivery-test`, temperature=0.0·num_predict=1024·num_ctx=8192 요청 설정이다. 실제 외부 모델 실행 기록은 아니다.
- 결과: Guard NOT_RUN/GENERATION_FAILED, 실행 FAILED/LLM504-0, 출력 메시지 FAILED/content=null, finalTransmission 없음. 오류 안내만 전달하며 답변 token·정상 complete·부분 답변 저장은 없다.

## 한계와 남은 확인

- follow-up에는 기존 query_routings 행이 없을 수 있다. 현재 실행의 purpose·실제 검색·consultRequestId를 기준으로 확인한다.
- 복합 질문 처리 경로에서 하위 요청과 최종 전송 단계의 기록은 별도 검증이 필요하다. 생성 전에 직접 반환한 근거 부족 안내에는 generationInputs·모델 시도가 없을 수 있다.
- 요청 모델 태그·옵션은 기록하지만 실제 가중치 digest·Ollama 엔진 버전·서버 빌드 SHA는 기록하지 않는다. `request_options.provider`는 현재 `ollama`로 고정 기록한다.
- Guard 결과 REPLACED는 Guard 결과에 근거 부족 안내 문구가 포함됐는지로 판정한다.
- 평가 파일은 운영 executionId/messageId가 없어 API 기록과 자동 연결하지 않는다. eval ID를 실행 ID로 간주하지 않는다.
- 검색·생성 스냅샷에는 내용 중복이 있다. 후보 수·복합 질문 범위 확대 시 저장 크기와 보존 정책을 재검토한다.
- DISPATCH_RETURNED는 서버의 이벤트 전달 함수 반환을 뜻하며 실제 구독자 수신·브라우저 표시는 입증하지 않는다.
- 실제 화면·소켓·새로고침 복원·운영 활성 설정은 미검증이다. 화면의 최종 답변과 저장/조회 결과, 실패 시 로딩 종료, 재접속 시 중복 표시 여부는 별도 확인해야 한다.
