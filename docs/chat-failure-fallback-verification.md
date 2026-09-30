# TELME-94: 모델 오류·타임아웃·검색 실패 폴백 검증 및 보완

검증일: 2026-10-01 KST. Jira: [TELME-94](https://telme-team.atlassian.net/browse/TELME-94).
브랜치: `fix/TELME-94-chat-failure-fallback`. 기준: TELME-93 PR #83의 `3f8798a`.
시작 시 작업 트리는 깨끗했다. TELME-92·93 변경을 보존하고 이번 변경만 분리했다.

## 확인한 경로와 선행 상태

상담 연결 설정을 켠 실제 Spring 구성의 유일한 `ChatProcessingPort`는
`ConsultChatProcessingService`다. 이 처리기에서 FAQ 검색 어댑터, RAG 생성기·Guard,
호출 기록·재시도, 답변 저장, SSE를 연결해 확인했다.

- PR #82 / TELME-92: OPEN, `f94bf7b`, Guard 검사·저장 후 최종 답변 전송
- PR #83 / TELME-93: OPEN, `3f8798a`, 저장·기록 API 조회·재접속 검증
- PR #80 / TELME-90: OPEN, `4b03e7b`, 상담 처리기 기본 활성화·구형 처리기 삭제
- 관련 PR #74: `61570d2`, #75: `53087f3`, 확인 시 OPEN

이 PR의 병합 대상은 GitHub CI가 실행되는 develop이다. #82·#83이 미병합이므로
전체 비교에는 두 선행 변경도 포함된다. TELME-94 자체 기능 변경은 `5fa882e` 커밋이다.
병합 순서는 #82 → #83 → #84이며 선행 PR 병합 후 남은 diff·CI·운영 활성 설정을 재확인한다.
기준 코드의 상담 연결 기본값은 false이고, 운영 환경변수·실제 화면은 확인하지 않았다.

## 발견한 문제와 보완

1. 오류 이벤트에는 status/errorCode만 있고 사람이 읽을 안내문이 없었다.
   `ChatFailure` 직렬화에 고정 `message`를 추가했다. 최초 실패와 종료 후 재구독에서
   같은 안내를 제공하며 예외 원문이나 DB·연결 상세를 포함하지 않는다.
2. 검색 오류도 기존에 실행 실패로 종료됐지만, DB 호출·잘못된 검색 응답은 일반 처리 오류로,
   검색 임베딩 오류는 별도 FAQ 오류로 기록돼 검색 시스템 실패를 일관되게 구분하기 어려웠다.
   상담의 FAQ 검색 경계에서 `FaqAnswerSearchException`으로 원인을 보존하고,
   실행 errorCode를 `FAQ_SEARCH_FAILED`로 기록한다. 실제 검색 구현·튜닝은 변경하지 않았다.
3. 재시도 계층은 onError 콜백만 관찰해 토큰 이전의 동기 예외가 재시도를 우회했다.
   동기 RuntimeException도 같은 오류 경로로 전달해 기존 재시도 정책을 적용한다.
   연결 실패·타임아웃만 재시도하고, 횟수·대기 시간·원문 토큰 이후 재시도 금지 정책은 유지한다.

신규 연결 테스트에서 보완 전 8건이 실패하는 것을 확인하고, 수정 후 통과했다.
이는 오류 안내·분류·재시도 처리 문제 재현이며 환경 연결 실패로 판정하지 않았다.

## 기존에 정상으로 확인한 부분

| 상황 | 최종 동작 |
| --- | --- |
| 정상 생성 | Guard 처리·DB 저장 후 같은 최종 답변 한 번 전송, COMPLETED/GROUNDED |
| 검색 결과 없음 | 생성 모델 호출 없이 기존 근거 부족 안내, COMPLETED/NO_EVIDENCE |
| 검색 시스템 오류·null 응답 | 생성 모델 호출 없음, FAILED/FAQ_SEARCH_FAILED, 불완전 답변 저장 없음 |
| 모델 오류 | 재시도 없이 FAILED/LLM502-0, 오류 안내, 답변 token/complete 없음 |
| 모델 타임아웃 | 첫 토큰 전 최대 기존 횟수 재시도, 최종 실패는 FAILED/LLM504-0 |
| 모델 연결 실패 | 첫 토큰 전 재시도, 최종 실패는 FAILED/LLM503-0 |
| 재시도 후 성공 | 시도별 실패·성공 기록 유지, 최종 답변 한 번 저장·전송 |
| 재시도 최종 실패 중 부분 출력 | 부분 내용은 보관하지 않고 실패 메시지 행 하나, 오류 이벤트 한 번 |
| 생성 중 연결 이탈 | 기존 CANCELLED/USER_CANCELLED 유지, 미검증 내용 전송 없음 |
| Guard 수정·차단 | 선행 작업의 수정 답변·안전 안내 저장 및 원문 노출 차단 유지 |

검색 결과 없음과 시스템 오류를 같은 빈 근거로 처리하지 않는다.
검색 실패의 원인 예외는 서버 로그에 보존하며 SSE에는 고정 문구만 담는다.
실패한 메시지의 content는 null이며 성공 답변으로 덮거나 안내문을 GROUNDED 답변으로 저장하지 않는다.
모델 타임아웃의 실행·메시지 상태는 기존 FAILED이며 실행 타임아웃 스케줄러의 TIMEOUT과 구분한다.

## SSE 계약과 화면 후속 확인

기존 이벤트 이름과 status/errorCode 타입을 유지하고 error 객체에 message를 추가했다.

```json
{
  "status": "FAILED",
  "errorCode": "FAQ_SEARCH_FAILED",
  "message": "참고 정보를 검색하지 못했습니다. 잠시 후 다시 질문해 주세요."
}
```

프런트 오류 처리기는 message가 있으면 안내로 표시하고, 없으면 기존 오류 코드 안내를 사용할 수 있다.
생성 실패 시 token이나 complete를 보내지 않으며, error를 받으면 로딩을 끝내고
EventSource를 닫아야 한다. 실패 후 새로고침에서는 기록 API의 실패 상태를 확인하고,
필요하면 종료된 실행을 재구독해 같은 errorCode/message를 받는다.
현재 기록 API의 메시지 DTO에는 오류 코드·안내문을 새로 추가하지 않았다.

실제 화면은 검증하지 않았다. HTTP API와 SSE 빌더·재구독 응답을 연결한 백엔드 테스트다.
현재 팀 프런트와 해당 백엔드를 연결한 환경에서 다음을 확인해야 한다.

1. 모델 오류·타임아웃·검색 오류에 맞는 안내가 표시되고 로딩·재접속 반복이 종료되는지
2. RETRYING 중 부분 문구가 없고, 성공 시 최종 답변 한 번 또는 실패 시 오류 안내 한 번인지
3. 검색 결과 없음은 근거 부족 안내, 검색 장애는 검색 실패 안내로 구분되는지
4. 실패 후 새로고침에서도 부분 답변이나 완료 답변이 생기지 않고 실패 상태가 유지되는지
5. 정상·Guard 수정·안전 안내 답변의 저장·조회·화면 표시가 기존대로 유지되는지

오류 객체의 message 추가와 검색 실패 분류는 공통 decisions.md 반영 대상이다.
기존 작업 폴더의 미커밋 문서는 보존하고 이번 정책과 계약을 여기에 기록했다.

## 테스트 및 환경

독립 pgvector PostgreSQL 16에 CI와 같은 telme DB·vector 확장을 준비했다.
검색·분석·외부 모델은 테스트 대역이며 유료·외부 모델 API를 호출하지 않았다.
모델·프롬프트·평가 기준·Guard 판정 규칙·Router·검색 튜닝은 변경하지 않았다.

| 선택 회귀 테스트 | 통과 |
| --- | ---: |
| ConsultGuardedAnswerDeliveryIntegrationTest | 26 |
| FaqSearchAnswerProviderTest | 7 |
| RetryingLlmClientTest | 15 |
| RecordingLlmClientTest | 9 |
| RagAnswerDeliveryTest | 16 |
| RagAnswerGeneratorTest | 26 |
| ChatExecutionCommandTest | 5 |
| ChatExecutionControllerIntegrationTest | 4 |
| ChatExecutionServiceIntegrationTest | 17 |
| ConsultChatPersistenceIntegrationTest | 21 |
| AnswerGuardUserEvidenceTest | 42 |
| **합계** | **188** |

- 선택 회귀: 188건 통과, 실패·오류·스킵 0건
- 최종 전체 build: 1,191건 중 1,097건 통과, 94건 스킵, 실패·오류 0건
- PR #74·#75·#80 및 선행 #82·#83과 이번 변경의 임시 통합 코드: 관련 227건 통과, 실패·오류·스킵 0건

서로 다른 실행이므로 합산하지 않는다. 전체 스킵은 기존 조건부 로컬 DB 테스트·평가 프로브이며
실행 조건을 바꾸지 않았다. GitHub CI는 develop/main 대상 PR에만 실행된다.
이 PR은 develop 대상으로 변경하고 문서 정정 커밋을 푸시해 CI를 실행한다.
아래 로컬 검증과 GitHub CI 결과는 구분해 확인한다.
전체 로컬 build를 별도로 실행했다.

임시 통합의 최초 실패 1건은 PR #75가 이미 도입한 별도의 빈 검색 결과 안내를
테스트가 기존 RAG 안내문과 같다고 가정했기 때문이다. 안내·프롬프트·평가 기준은 변경하지 않고,
생성 호출 없음·NO_EVIDENCE·전송/저장/조회 일치라는 공통 계약으로 검증했다.
현재 브랜치에서는 NO_EVIDENCE 호출 기록이 남고, #75의 빈 검색 결과 조기 반환에서는
생성 계층을 거치지 않아 호출 기록이 없을 수 있다. 시스템 오류는 별도의 실행 실패 코드로 남는다.
이번 실행에서 DB·외부 모델 연결 환경 문제는 발생하지 않았다.

실제 PR·다른 담당자의 브랜치를 병합하거나 수정하지 않았다.
