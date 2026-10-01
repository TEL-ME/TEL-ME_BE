# TELME-92: Guard 검증·저장 이후 답변 전달

백엔드 검증 완료: 2026-10-01 KST. 브랜치 `fix/TELME-92-guard-safe-delivery`, 개발 기준 HEAD `d549643`.
Jira: TELME-92 `[Fix] Answer Guard 검증 전 답변 노출 방지`.
백엔드 구현과 관련 테스트는 완료했다. 배포와 실제 화면 검증은 후속 확인 대상이다.

## 확인한 경로와 PR 상태

- 기존 Guard 작업 폴더: `TEL-ME_BE-local`, 브랜치 `fix/TELME-88-answer-guard-residual-errors`, HEAD `61570d2`.
- PR #74 Guard 보완: OPEN, `61570d2`.
- PR #80 Chat 처리기 통합: OPEN, `4b03e7b`.
- PR #75 복합 질문: OPEN, `53087f3`.
- 개발 기준은 현재 코드와 Git이다. 공통 문서의 SSE 선노출 설명은 수정 전 코드에 해당한다.
- 실제 상담 연결 설정에서 쓰이는 `ConsultChatProcessingService` → FAQ 검색 어댑터 → `RagAnswerGenerator` → 상담 저장·SSE 경계를 수정했다.
- 테스트에서는 상담 연결을 켜고 `ChatProcessingPort`가 이 처리기 하나인지 확인했다. 기본 설정 활성화와 구형 처리기 삭제는 PR #80의 작업으로 유지했다. 운영 배포 전체의 적용은 해당 PR 병합·배포 후 확인해야 한다.

## 결정

사용자 답변은 **생성 완료 → Guard 검증 → 답변 저장 트랜잭션 완료 → 최종 token 한 번 → complete** 순서로 전달한다.

1. 모델의 원문 토큰은 생성기 내부에만 모은다. 완성되기 전 답변 내용은 외부로 보내지 않는다.
2. Guard가 제거·수정한 최종 문자열만 반환한다. Guard가 예외로 차단한 경우 기존 고정 안내 `안내드릴 수 있는 정보가 없습니다.`로 반환한다.
3. Guard 예외를 먼저 기록 클라이언트까지 전달하므로 LLM 호출 기록은 기존 `MODEL_ERROR`와 차단 이유를 유지한다. 이어서 상담 답변은 안전 안내와 `NO_EVIDENCE`로 저장·완료한다. Guard의 판정 규칙을 변경하지 않았다.
4. 상담 연결의 생성 중 handler는 검증된 최종 내용도 저장 전에 전송하지 않는다. 처리기가 저장에 사용한 동일 `ChatAnswer.content`를 저장 성공 후 보낸다. 저장 롤백 시 토큰을 보내지 않는다.
5. 생성 실패·타임아웃·취소에서는 기존 실패 상태·error 경로를 유지한다. 실패한 시도의 미검증 내용은 보내지 않는다.
6. 재시도 시 내부 버퍼를 비우고 기존 RETRYING 상태 이벤트만 전달한다. 기존 재시도 조건·횟수는 유지하며, 중간 모델 토큰 뒤 실패를 자동 재시도하도록 확대하지 않는다.
7. 내용이 없는 내부 `onProgress()` 콜백으로 토큰 수신 중 연결 이탈을 확인한다. 다음 모델 토큰 수신 시 취소를 감지하며, 프런트에 새 SSE 이벤트를 보내지 않는다.
8. 저장 완료 후 전송 실패는 완료된 DB 답변을 실패 상태로 되돌리지 않는다. 저장 답변은 이력 조회로 다시 읽을 수 있다.

이 전달 순서와 Guard 차단 시 안전 안내 완료 방식은 공통 `decisions.md` 반영 대상이다. 기존 작업 폴더의 미커밋 문서는 보존하고 이 문서에 결정과 증거를 기록했다.

## 변경 파일

| 파일 | 목적 |
| --- | --- |
| `RagAnswerGenerator.java` | 원문 버퍼링, 최종 검증 답변·안전 안내, 재시도 버퍼 폐기, 중복 완료 방지 |
| `LlmStreamHandler.java` | 내용을 전달하지 않는 내부 진행 콜백 추가 |
| `RagSearchResultAnswerGenerator.java` | 상담 경계에서 진행 콜백 전달 |
| `ConsultRagAnswerConfiguration.java` | 생성 중 내용 전송 억제, 취소 확인·재시도 상태 유지 |
| `ChatEmitterConsultEvents.java` | 내용 없이 SSE 연결 이탈 확인 |
| `ConsultChatProcessingService.java` | 저장 이후 최종 내용 한 번 전송, 저장 이후 전송 실패 분리 |
| `RagAnswerGeneratorTest.java`, `RagAnswerDeliveryTest.java` | 최종 전달·차단·오류·취소·재시도·중복 회귀 |
| `ChatEmitterConsultEventsTest.java`, `ConsultGuardedDeliveryConfigurationTest.java` | 전송 억제와 진행 중 연결 확인 |
| `ConsultChatPersistenceIntegrationTest.java` | 기존 생성 중 전달 기대값을 저장 후 전달로 변경 |
| `ConsultGuardedAnswerDeliveryIntegrationTest.java` | 실제 처리기·Guard·DB·SSE·이력 조회 연결 검증 |
| 이 문서 | 계약·결정·검증·화면 확인 사항 |

라우팅·복합 질문 처리·검색·모델 설정·SSE 이벤트 명칭/데이터 DTO·프롬프트·Guard 판정 규칙·평가셋·Judge를 변경하지 않았다. 상담 처리기 파일은 이번 작업에 필요한 단일 답변 전달 위치만 바꿨다.

## 백엔드 검증 결과

아래 표는 PR #82 최초 작성 시점의 검증 결과다. 후속 저장·조회 API 검증은
[`rag-answer-persistence-verification.md`](rag-answer-persistence-verification.md)에 별도로 기록한다.

| 테스트 | 개발 기준 수정본 | PR #74·#75·#80 임시 통합본 |
| --- | ---: | ---: |
| AnswerGuardTest | 38 | 38 |
| AnswerGuardUserEvidenceTest | 42 | 67 |
| RagAnswerGeneratorTest | 26 | 26 |
| RagAnswerDeliveryTest | 16 | 16 |
| MessageSourceDispatcherTest | 5 | 5 |
| FakeLlmClientTest / OllamaClientTest / RecordingLlmClientTest / RetryingLlmClientTest | 5 / 10 / 9 / 11 | 5 / 10 / 9 / 11 |
| ConsultGuardedDeliveryConfigurationTest | 3 | 3 |
| ChatEmitterConsultEventsTest | 4 | 4 |
| ConsultChatPipelineConfigurationTest / ConsultChatProcessingApplicationIntegrationTest | 2 / 1 | 2 / 1 |
| RagSearchResultAnswerGeneratorTest | 1 | 4 |
| ConsultChatPersistenceIntegrationTest | 21 | 24 |
| ConsultGuardedAnswerDeliveryIntegrationTest | 11 | 11 |
| ConsultChatApiIntegrationTest | 4 | 4 |
| ConsultMultiFaqProcessingTest | 해당 기준에 클래스 없음 | 5 |
| ChatEmitterRegistryTest / ChatExecutionControllerIntegrationTest | 11 / 4 | 11 / 4 |
| **합계** | **224** | **260** |

두 실행 모두 실패·오류·스킵 0건이다. 서로 다른 실행이므로 합산하지 않는다.
전체 애플리케이션 테스트를 모두 실행한 수치가 아니라 관련 테스트를 선택해 실행한 수치다.

새 저장·SSE 연결 테스트 11건에서는 정상 답변, 부분 제거, 전체 제거, 예외 차단의 안전 안내, 검색 결과 없음, 모델 오류, 타임아웃, 실제 재시도 체인, 연결 이탈, 저장 롤백, 저장 후 전송 실패를 확인했다. 실제 SSE 이벤트 빌더의 이름과 payload를 캡처했고, token 전송 시 별도 DB 조회로 COMPLETED 상태와 동일 내용을 확인했다. 실제 소켓·브라우저 동작을 검증한 것은 아니다.

검색과 외부 모델 응답은 테스트 대역으로 고정했다. 실제 Spring 처리기·Guard·LLM 기록/재시도 체인·저장 트랜잭션·SSE 레지스트리·이력 재조회는 연결했다. 일회용 pgvector PostgreSQL 16 DB를 사용했고 외부 모델 API는 호출하지 않았다. 실제 모델의 출력 품질이나 새 환각률을 측정하지 않았다.

PR #74와 #80은 충돌 없이 임시 병합했고, #75와 이번 상담 처리기 변경은 공통 기준에서 3방향 통합했다. 현재 확인한 head끼리 내용 충돌 없이 관련 회귀가 통과했다. 실제 PR이나 원래 브랜치를 병합·수정한 것은 아니다. 이후 PR 변경 시 재확인이 필요하다.

## 프런트 계약과 남은 화면 검증

- 이벤트 이름: 기존 `start`, 재시도 시 `status`/`RETRYING`, `token`, `complete`, 실패 시 `error` 유지.
- `token`은 기존 문자열 형식이며 이제 저장된 최종 답변 전체가 한 번 전달된다. `complete`의 기존 실행/메시지 ID DTO도 유지한다.
- 필수 프런트 API·DTO 변경은 발견하지 않았다. `onProgress()`는 서버 내부 콜백이다.
- 생성·검사·저장 동안 글자가 나타나지 않으므로 기존 시작 이벤트 이후 로딩 상태가 유지되는지 확인한다.
- 최종 token과 complete 처리에서 답변이 중복 표시되지 않는지 확인한다.
- 새로고침·재접속 후 저장된 동일 답변을 이력 조회로 복원하는지 확인한다.
- 취소·연결 이탈·오류 뒤에도 중간 답변이 남지 않는지 확인한다.
- 토큰 전체 전달의 줄바꿈 표시와 안전 안내문 표시를 확인한다.
- PR #80 병합·배포 이후 실제 활성 처리 경로와 배포 설정을 재확인한다.

백엔드 코드·관련 검증 완료와 실제 화면 검증 완료를 구분한다. 현재 실제 화면 검증은 미완료다.
