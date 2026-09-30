# 질문·검색 근거·최종 답변 추적 검증

확인일: 2026-10-01. 로컬 브랜치: `feat/chat-execution-trace`.
개발 시작점: PR #84의 `69a379c`. 로컬 코드·검증 결과를 먼저 공유한 뒤 사용자 푸시 요청을 받았다.
새 작업의 Jira 번호는 확인되지 않았으므로 번호를 지정하지 않는다.
이 작업은 실패 원인 조사에 필요한 연결 보완이며 환각률·답변 품질 개선 측정이 아니다.

## 실제 경로와 선행 상태

상담 기능을 활성화한 설정의 경로:
`ChatProcessingDispatcher → ConsultChatProcessingService → FaqSearchAnswerProvider
→ RagSearchResultAnswerGenerator → RagAnswerGenerator → AnswerGuard
→ ConsultChatPersistenceService → 최종 SSE`.

- #82 Guard 선검증, #83 최종 답변 저장·조회, #84 오류·검색 실패 폴백은 확인 당시 OPEN이며 develop 대상 CI가 통과했다.
- 현재 개발 기준 설정의 상담 연결 기본값은 false다. #80은 기본 활성화·기존 처리기 제거 담당 PR이다.
  배포 환경의 실제 활성 설정은 확인하지 않았다.
- 임시 코드에 #74 `61570d2`, #75 `53087f3`, #80 `4b03e7b` 및 선행 기능을 포함해 관련 회귀를 검증했다.
  다른 담당자의 실제 브랜치나 PR은 병합·수정하지 않았다.
- 기존 미커밋 문서의 “Guard 전에 SSE 토큰이 나간다”는 설명은 #82를 포함한 이번 코드와 다르다.
  원래 문서를 덮어쓰지 않고 이번 검증 기준을 여기에 기록한다.

## 기존 기록과 보완

| 항목 | 기존 위치·연결 | 이번 보완·확인 방법 |
|---|---|---|
| 사용자 입력 | chat_messages.content, chat_executions.input_message_id | 기존 내용 그대로 조회. 분석기의 원문과 사용자가 이번 턴에 보낸 메시지는 구분 |
| 라우팅·재작성·신뢰도 | query_routings.message_id, intent/refined_query/confidence/method | 입력 메시지 ID로 기존 행 연결. 새 Router 판단·신뢰도 추정 없음 |
| 처리 경로·상담용 질문 | 상담 어댑터의 메모리 입력 | pipeline_trace.processing/analysis에 처리기·purpose·action·원문·정제 질문 기록 |
| 실제 검색 요청 | 메모리상의 FaqSearchRequest | searchRequests에 실제 질문·ORIGINAL/REFINED·topK·consultRequestId 기록 |
| 검색 결과·오류 | 성공 시 message_sources 일부 정보, 오류 시 execution.error_code | searchResults에 EMPTY/FOUND/ERROR/CANCELLED 및 FAQ 목록 스냅샷 기록 |
| FAQ 식별자·순위·점수 | message_sources의 faq_id/search_rank/score | 검색 DTO의 FAQ ID·slotId·순위·원래 정밀도 점수·matchedVariant·version 보존 |
| 생성에 넘긴 근거 | 메모리 컨텍스트, 성공 시 제목·버전만 저장 | generationInputs에 전달된 FAQ Q/A 목록과 질문·promptVersion·consultRequestId 기록 |
| 실제 Guard 근거 범위 | FAQ 답변만 사용 | guardEvidenceScope=FAQ_ANSWERS_ONLY. 생성 프롬프트에는 FAQ 질문도 포함됨을 구분 |
| 모델 시도·결과 | llm_generations.execution_id/generation_id/task_type/attempt/status | 기존 행 그대로 조회. request_options에 요청 설정과 consultRequestId 추가 |
| 모델·버전 | llm_generations.model/prompt_version | 요청한 모델 태그·프롬프트 버전 유지. Ollama 변환기의 실제 기본값으로 options/format 스냅샷 |
| Guard 결과 | 최종 answer_basis만으로는 수정·보류 원인 구분 불가 | guardResults에 KEPT/MODIFIED/REPLACED/NOT_RUN·이유 범주·consultRequestId 기록 |
| 최종 답변 | chat_executions.output_message_id → chat_messages.content/status/answer_basis | 복사 저장 없이 기존 완료·실패 행 조회. 실패 내용은 null 유지 |
| 최종 전송 단계 | SSE 메모리·서버 로그 | finalTransmission에 동일 outputMessageId와 DISPATCH_ATTEMPTED/RETURNED/ERROR 기록 |

기존 평가 산출물도 읽기 전용으로 확인했다. `.measure/postfix2-20260930/` 생성 JSON은
eval_id·질문·답변·sources·모델/설정을, frozen-baseline Judge JSON은 eval_id·문장·verdict·context를 보관한다.
운영 executionId/messageId가 없어 이번 API와 과거 산출물을 자동 연결할 수 없다.
평가 ID를 실행 ID로 간주하거나 백필하지 않았고 데이터셋·Judge 파일도 변경하지 않았다.

FAQ 스냅샷은 해당 검색 응답 객체의 질문·답변 내용이다. 모델이 실제로 어느 문장을 참고했는지
입증하는 인용이나 품질 판정은 아니다. 생성에 전달한 후보 목록과 Guard 근거 범위를 구분한다.
검색·임베딩·리랭커 설정과 프롬프트·Guard 규칙은 변경하지 않았다.

## 저장·조회 계약

새 테이블 대신 V15에서 기존 두 테이블에 nullable jsonb 열을 추가한다.

- chat_executions.pipeline_trace: 실행별 단계 메타데이터·검색/근거 스냅샷.
- llm_generations.request_options: 모델 시도와 함께 저장한 provider/options/format/consultRequestId.
- 과거 행은 null을 유지하며 소급 재구성하지 않는다. API의 traceRecorded=false는 미기록이다.
- 기존 시도 행의 configuration=null도 당시 설정 미기록이며 현재 설정을 소급 적용하지 않는다.
- JSON 갱신과 배열 추가는 실행 행에서 원자적으로 수행한다. 다른 단계와 동시 기록이 덮어써지지 않는다.
- generationInputs/guardResults는 배열로 보존한다. 상담 요청 ID가 여러 개인 실행도 검색·시도와 연결한다.
  단일 생성만 있으면 API가 generationInput 별칭을 제공한다. guard 단일 필드는 마지막 요약이고
  여러 생성의 결과는 guardResults를 기준으로 확인한다.
- 신규 열 추가는 기존 행·식별자·상태를 변경하지 않는다. 기존 앱도 열을 사용하지 않고 동작할 수 있다.
  새 앱보다 마이그레이션을 먼저 적용해야 한다. ALTER TABLE의 짧은 테이블 잠금과 다른 PR의
  V15 번호 중복을 병합 직전에 재확인한다. 최신 develop의 마지막 스키마 번호는 확인 당시 V14였다.

조회:

`GET /api/v1/chat/sessions/{sessionId}/executions/{executionId}/trace`

기존 CustomResponse.result 안에 executionId/sessionId/inputMessageId/outputMessageId,
원문·실행 상태·오류 코드·최종 답변·answerBasis·steps·routing·modelAttempts를 반환한다.
회원·게스트 소유권 검사는 SSE 조회와 같은 ChatSessionService.getExecution을 사용한다.
다른 사용자나 잘못된 sessionId는 404다. 익명 요청은 기존 필터가 게스트를 만들 수 있지만
다른 대화를 소유하지 않으므로 404이며 정보는 반환하지 않는다. 별도 관리자 접근 권한은 추가하지 않는다.
기존 기록 조회·SSE 형식과 권한은 변경하지 않는다.

## 실패 사례 추적 예시

실제 연결 테스트 `exhaustedModelFailureTerminatesOnceWithReadableGuidance(TIMEOUT)` 기준이다.
검색·분석·모델은 대역이며 실행·Guard·DB·HTTP API·SSE 경계는 실제 구현이다.
실행 ID·입력/출력 메시지 ID는 DB 생성 값을 조회하고 다른 실행의 ID를 혼합하지 않는다.

1. originalUserMessage: “유심 재발급 비용과 배송비를 알려주세요”.
2. 분석 결과: GENERAL_FAQ/PROCEED, 원문·정제 질문 동일. 이 테스트의 분석기는 대역이므로 routing 행이 없고
   신뢰도를 만들어 넣지 않는다. 별도 테스트에서 실제 저장된 FAQ/LLM/0.875 라우팅 행 연결을 확인했다.
3. searchRequests: 원문 질문, ORIGINAL, topK=3, 같은 consultRequestId.
4. searchResults: FOUND. 비용 7,700원과 택배 2~3 영업일이 기재된 FAQ 스냅샷·순위·점수.
5. generationInputs: 동일 스냅샷과 원문, 프롬프트 버전. Guard는 FAQ 답변만 근거로 사용.
6. modelAttempts: RAG_ANSWER, attempt=1/2, TIMEOUT/TIMEOUT.
   모델명은 guard-delivery-test, 설정은 temperature=0.0, num_predict=1024, num_ctx=8192.
   이는 외부 모델 실행 기록이 아니라 대역 호출과 실제 기록 계층의 연결 검증이다.
7. guard: NOT_RUN/GENERATION_FAILED. 모델이 완료하지 않아 Guard 품질 판정으로 해석하지 않는다.
8. 실행 FAILED/LLM504-0, 출력 메시지 FAILED/content=null, finalTransmission 없음.
   오류 안내만 SSE로 전달하고 답변 token·정상 complete·부분 답변 저장은 없다.

## 검증·환경

최종 `./gradlew build --build-cache` 성공: 총 1,201건 중 **1,107건 통과, 94건 스킵, 실패·오류 0건**.
기존 조건부 로컬 DB 테스트·평가 프로브의 실행 조건은 변경하지 않았다.
연결 추적 테스트는 기존 26건을 보강하고 9건 추가해 35건, 기록 실패·로그 보호 단위 테스트는 1건 통과했다.
임시 #74·#75·#80 및 선행 기능 조합의 관련 테스트는 **84건 통과, 실패·오류·스킵 0건**이다.
두 실행의 테스트는 중복되므로 합산하지 않는다.

- 정상·부분 수정·안전 안내 교체·Guard 예외·검색 없음·검색 오류·모델 오류·타임아웃·취소:
  기존 연결 사례에 추적 API와 단계 결과 검증을 추가했다.
- 원문 검색 없음 → 정제 검색 성공, FAQ 수정 이후 당시 내용·버전·정밀도 점수 유지.
- 기존 라우팅 행의 메시지 연결, 미기록 과거 실행 구분.
- 회원·게스트 타인 대화 차단, 경로 sessionId 불일치 차단.
- 두 질문을 실제 처리기에서 동시에 처리하고 원문·FAQ·시도 ID·최종 답변 분리.
- 한 실행에서 병렬 메타데이터 추가 시 16개 항목과 다른 단계 보존.
- 한 실행의 두 생성은 generationInputs·guardResults·modelAttempts 설정에서 각각의 상담 요청 ID 유지.
- 실제 V15 SQL을 기존 행이 있는 별도 스키마에 적용해 상태·ID·null 호환성 확인.
- 추적 저장 실패가 호출자에게 예외를 던지지 않고 사용자 값·DB 예외 텍스트를 로그에 남기지 않음.
- 모델이 이미 안전 안내를 생성한 경우 Guard KEPT와 Guard REPLACED를 구분.

전용 pgvector PostgreSQL 16/telme DB를 사용하고 유료·외부 모델 API는 호출하지 않았다.
첫 설정 테스트 실패는 새 기록 의존성이 없는 테스트 구성, 일부 연결 실패는 검증 위치/익명 게스트의
기존 404 응답을 401로 가정한 테스트 문제였다. 기존 권한 정책은 변경하지 않았다.
임시 선행 조합을 현재 DB에 붙인 첫 실행은 해당 코드에 V13/V14가 없어서 Flyway 검증에 실패했다.
운영 코드 오류로 분류하지 않고 별도 빈 DB로 분리해 검증했다. 기존 마이그레이션을 수정하지 않았다.

## 한계·후속 확인

- 기록 저장은 best effort다. DB 기록 장애가 발생하면 답변은 유지하되 단계가 누락될 수 있다.
  traceRecorded=true 하나로 모든 단계가 완전하다고 판단하지 말고 해당 배열/단계 유무를 확인한다.
- follow-up은 기존 query_routings 행이 없을 수 있다. 현재 실행의 purpose와 실제 검색/상담 요청 ID를 사용하며
  이전 라우팅을 현재 턴의 신뢰도로 복사하지 않는다.
- 복합 질문 어댑터는 분석 요약이 ADAPTER_BRANCH일 수 있다. 하위 검색/생성/Guard/시도는 consultRequestId로 연결한다.
  어댑터가 직접 반환한 근거 부족 안내에는 모델 시도나 generationInputs가 없을 수 있다.
  선행 복합 처리기 자체의 별도 최종 전송 함수는 이번 브랜치에 없으며 finalTransmission이 없을 수 있다.
  최종 답변은 outputMessageId와 저장 행으로 확인하고, 실제 통합 뒤 해당 전송 단계 계측은 후속 확인한다.
- Guard의 세부 제거 문장·규칙 ID는 현재 공개 반환값에 없다. 안전한 범주만 기록하며
  전체 생성문이나 예외의 수치/문장을 새 추적 정보에 저장하지 않는다.
- 모델 태그와 요청 옵션을 기록했으며 실제 서버 가중치 digest·Ollama 엔진 버전·빌드 SHA는 확인하지 못한다.
- 기록은 기존 대화 삭제 생명주기를 따른다. 별도 보존기간·백필·관리 화면·검색용 새 인덱스는 추가하지 않는다.
  FAQ 스냅샷 보존과 접근 범위 결정은 팀 decisions.md 반영이 필요하다.
- 검색 스냅샷과 생성에 전달한 후보 목록을 각각 보존해 일부 내용이 중복된다. 현재 Top-3 경계이며
  후보 수·복합 질문 범위 확대 시 저장 크기와 보존정책을 재검토한다.
- DISPATCH_RETURNED는 서버 이벤트 전달 함수가 반환했다는 뜻이다. 구독자 유무나 브라우저 수신·표시는 입증하지 않는다.
  실제 화면 연결, 실패 상태 표시, 새로고침 복원 및 응답/기록 일치 확인은 미완료다.
- 이번 작업의 PR은 사용자 코드 확인 뒤 develop 대상으로 생성해야 CI가 실행된다.
  선행 #82 → #83 → #84 이후의 변경이며 선행 미병합 변경의 diff 포함 범위를 본문에 명시해야 한다.
  게시 후 최신 PR 커밋의 GitHub CI 결과를 별도로 확인한다.
