# TELME-93: Guard 최종 답변 저장·조회 일치 및 새로고침 유지 검증

검증일: 2026-10-01 KST. 브랜치: `test/TELME-93-guard-answer-persistence`.
Jira: TELME-93 `[Test] Guard 최종 답변 저장·조회 일치 및 새로고침 유지 검증`.
개발 기준: TELME-92의 PR #82 커밋 `f94bf7b`. 문서·테스트 검토 기준: `fbc61fc`.
이번 변경은 기존 연결 테스트의 보강과 검증 문서뿐이며, 기능 코드는 변경하지 않았다.

## 확인한 경로

`ChatProcessingDispatcher` → `ConsultChatProcessingService` → FAQ 답변 어댑터 →
`RagAnswerGenerator`/Guard → `ConsultChatPersistenceService.persistFinalAnswer` →
`ChatExecutionService.completeAnswer` → 저장 커밋 → 최종 SSE `token`/`complete`.

기록 조회는 `GET /api/v1/chat/sessions/{sessionId}/messages` →
`ChatSessionService.getMessages` → 메시지 저장소 → `ChatMessageConverter` 경로다.
생성기·저장 경로와 별개인 HTTP 조회 요청으로 저장된 최종 내용을 읽었다.

상담 연결을 활성화한 실제 Spring 설정에서 `ChatProcessingPort`가 하나이며
`ConsultChatProcessingService`인지 확인했다. 분석·검색·외부 모델만 테스트 대역으로 사용했다.
검증 당시 `fbc61fc`의 기본값과 테스트에서 명시한 활성화 조건은 다음과 같다.
현재 develop 설정을 과거 검증에 소급 적용하지 않는다.

| 설정 | 검증 커밋 기본값 | 검증 테스트 설정 | 현재 develop 기본값 |
| --- | --- | --- | --- |
| `telme.consult.persistence-enabled` | false | true | true |
| `telme.consult.llm-enabled` | false | false | true |
| `telme.consult.chat-integration-enabled` | false | true | true |
| `telme.consult.rag-integration-enabled` | false | true | true |

기본 활성화·구형 처리기 제거를 맡은 [PR #80](https://github.com/TEL-ME/TEL-ME_BE/pull/80)은
2026-10-01 KST에 병합됐다(`c3eeee1`). 현재 설정은 같은 날 확인한 develop `bb17e7f` 기준이다.
근거는 [검증 커밋 설정](https://github.com/TEL-ME/TEL-ME_BE/blob/fbc61fc50ba12a2ab02c5fcccb519eafcdd53c1d/src/main/resources/application.yml#L141-L147),
[검증 테스트의 명시 설정](https://github.com/TEL-ME/TEL-ME_BE/blob/fbc61fc50ba12a2ab02c5fcccb519eafcdd53c1d/src/test/java/com/telme/consult/service/ConsultGuardedAnswerDeliveryIntegrationTest.java#L61-L66),
[develop 설정](https://github.com/TEL-ME/TEL-ME_BE/blob/bb17e7f92ed072245456258a5b605a6402240f76/src/main/resources/application.yml#L141-L148)이다.
환경변수로 기본값이 덮일 수 있으므로 운영 배포의 활성 경로·환경변수는 미확인으로 남긴다.

## 확인 결과

| 사례 | 전송·저장·조회 결과 |
| --- | --- |
| 정상 답변 | 동일 최종 문자열, `COMPLETED`/`GROUNDED`, 동일 메시지 ID |
| Guard 부분 수정 | 제거된 배송비 포함 주장이 전송·메시지 저장·기록 API에 없음 |
| Guard 전체 제거 | 기존 안전 안내문 저장·전송·조회, `COMPLETED`/`NO_EVIDENCE` |
| Guard 예외 차단 | 안전 안내문 저장·전송·조회. 호출 기록은 `MODEL_ERROR`로 유지 |
| 생성 중 조회 | 답변 행은 `GENERATING`, 내용은 null, `runningExecutionId` 있음. 원문 없음 |
| 모델 오류·부분 출력 후 타임아웃 | 답변 내용 null, 실행·메시지 `FAILED`, 완료 답변 전송 없음 |
| 연결 이탈 취소 | 답변 내용 null, 실행·메시지 `CANCELLED`, 미검증 토큰 전송 없음 |
| 실제 재시도 | 첫 토큰 전 실패 후 재시도. 최종 답변 하나만 저장·전송·조회 |
| 부분 출력 후 실패 | 기존 정책대로 자동 재시도하지 않음. 버린 원문이 저장·조회되지 않음 |
| 중복 완료 콜백 | 답변 행·최종 토큰·완료 이벤트 하나. 중복 저장 없음 |
| 저장 충돌·롤백 | 최종 문자열 저장·전송 없음. 실패 메시지 하나로 남음 |
| 저장 후 전송 실패 | 완료된 저장 내용 유지. 기록 API로 동일 답변 복원 가능 |
| 이력 재조회·페이지 조회 | SSE 종료 후 새 HTTP 요청에서도 동일 ID·내용·상태. 페이지 경계에서 질문과 답변 구분 |
| 종료 후 SSE 재구독 | 완료 시 기존 메시지 ID의 `complete`, 실패 시 기존 오류 코드의 `error`. 새 답변 저장 없음 |

생성 시작 시 내용이 없는 답변 행을 하나 만들고, 완료 또는 실패 시 같은 행을 갱신한다.
실패 이력의 행이 남는 것은 정상 동작이다. 불완전한 내용을 정상 답변으로 저장하지 않았고,
성공·실패 모두 SQL로 ASSISTANT 행이 정확히 하나인지 확인했다.

모델 타임아웃은 현재 상담 처리기의 기존 정책상 `FAILED`와 오류 코드 `LLM504-0`으로 기록된다.
실행 타임아웃 스케줄러의 `TIMEOUT` 상태와 구분한다. 이번에 상태 매핑은 변경하지 않았다.
기록 API는 메시지 상태를 반환하고, 오류 코드는 실행 상태 및 재구독 `error` 이벤트에서 확인했다.

재구독 `complete`는 기존 메시지 ID·상태를 반환하며 답변 본문을 포함하지 않는다.
새로고침 시 화면은 동일 채팅방의 기록 API로 본문을 복원해야 한다. 새 API 구현은 필요하지 않았다.

## 실행한 테스트

독립 pgvector PostgreSQL 16 DB에서 선택한 관련 테스트를 실행했다.
재현 시 DB 이름은 `telme`로 준비하고 `vector` 확장을 활성화해야 한다.
기록 API 검증에는 인증된 사용자와 해당 사용자의 채팅방을 사용한다.

| 테스트 | 통과 |
| --- | ---: |
| ConsultGuardedAnswerDeliveryIntegrationTest | 17 |
| ConsultChatPersistenceIntegrationTest | 21 |
| ConsultChatApiIntegrationTest | 4 |
| ChatSessionApiIntegrationTest | 14 |
| ChatExecutionControllerIntegrationTest | 4 |
| ChatExecutionServiceIntegrationTest | 17 |
| ChatSessionServiceTest | 4 |
| ChatEmitterRegistryTest | 11 |
| RagAnswerGeneratorTest | 26 |
| RagAnswerDeliveryTest | 16 |
| AnswerGuardUserEvidenceTest | 42 |
| RetryingLlmClientTest | 11 |
| RecordingLlmClientTest | 9 |
| **합계** | **196** |

실패·오류·스킵 0건. 현재 브랜치의 후속 검증 수치이며 이전 224/260건과 합산하지 않는다.
위 196건은 선택 실행 결과다. 관련 PR의 임시 통합본을 이번에 다시 실행한 것은 아니다.
기존 연결 테스트 11건에 조회 API 검증을 보강하고, 생성 중 조회·부분 실패·중복 완료·
페이지 조회·완료 재접속·실패 재접속 6건을 추가했다.

검증 커밋 기준 전체 `./gradlew build --build-cache`도 성공했다. 전체 1,176건 중 1,082건 통과,
94건 스킵, 실패·오류 0건이다. 스킵에는 기존 조건부 로컬 DB 테스트와 평가 프로브가 포함되며
이번 범위에서 실행 조건을 변경하지 않았다. 선택 실행 196건과 중복되므로 합산하지 않는다.

모델·프롬프트·평가 기준·Guard 판정 규칙은 변경하지 않았고 외부 모델 API를 호출하지 않았다.

## 남은 실제 화면 검증

실제 브라우저에서 새로고침을 수행하지 않았다. MockMvc의 HTTP/SSE 응답 검증과
서비스 재조회는 실제 화면 검증이 아니다.

아래 6가지는 SSE 프런트를 해당 백엔드에 연결할 때 필요한 계약 검증이다.
팀 프런트의 기록 API 폴링·SSE 사용 여부는 이 문서에서 구현 사실로 단정하지 않는다.

1. 정상·부분 수정·안전 안내 답변을 각각 받아 표시된 문구, `token` 내용,
   기록 API의 `content`와 `messageId`를 대조한다. 제거된 원문은 표시되지 않아야 한다.
2. 완료 후 같은 채팅방에서 브라우저를 새로고침하고 다시 연다.
   인증·채팅방 ID를 복원하고 기록 API를 호출해 같은 ID·내용·상태를 한 번만 표시해야 한다.
3. 생성 중 새로고침한다. 기록의 `runningExecutionId`가 있으면 해당 실행을 재구독하고,
   종료 이벤트 수신 후 기록을 다시 조회한다. 미검증 답변이나 빈 행을 완료 답변처럼 표시하면 안 된다.
4. 오류·타임아웃·취소 후 새로고침한다. 실패 상태가 유지되고 이전 부분 답변은 없어야 한다.
   복원된 상태에 맞게 실패·취소 안내를 표시하고 완료 답변으로 표시하지 않아야 한다.
5. 저장 직후 연결을 끊고 재접속한다. 최종 토큰을 받지 못했어도 기록에서 완료 답변을 복원해야 한다.
6. 최종 token과 complete, 기록 재조회가 모두 도착해도 같은 메시지 ID를 기준으로
   중복 표시하지 않아야 한다. complete/error 이후에는 EventSource를 닫아 재접속 반복을 막아야 한다.

결론: 확인한 백엔드 저장·조회 경로는 정상이며 불필요한 기능을 추가하지 않았다.
실제 화면의 복원·표시·재구독 동작과 운영 활성 설정은 후속 확인 대상이다.
