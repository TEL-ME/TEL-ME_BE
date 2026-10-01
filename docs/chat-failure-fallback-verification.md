# TELME-94: 모델 오류·타임아웃·검색 실패 폴백 검증 및 보완

검증일: 2026-10-01 KST. Jira: [TELME-94](https://telme-team.atlassian.net/browse/TELME-94).
브랜치: `fix/TELME-94-chat-failure-fallback`.
초기 개발 기준은 TELME-93 PR #83의 `3f8798a6247e831591582283aa7ed524325ab2fa`이며,
TELME-94 기능 보완 커밋은 `5fa882e63f892fad29b9e4ad2c3a2fe8575431d0`이다.
현재 검증 기준은 PR #84 HEAD `a19c26198bc5d22f70bf4ff4302fcf2bcd26506e`와
`develop`의 `c4f4458c5220d9e4cf1cb316cb9efce34edbbc8b`다.
이후의 리뷰 반영은 계약 주석·문서 정정이며 실행 코드·테스트·설정은 변경하지 않았다.

## 확인한 경로와 선행 상태

상담 연결 설정을 켠 실제 Spring 구성의 유일한 `ChatProcessingPort`는
`ConsultChatProcessingService`다. 이 처리기에서 FAQ 검색 어댑터, RAG 생성기·Guard,
호출 기록·재시도, 답변 저장, SSE를 연결해 확인했다.

2026-10-01 원격 조회 기준 상태와 병합 커밋은 다음과 같다.

| PR | 상태 | 병합 커밋 / 미병합 HEAD | 범위 |
| --- | --- | --- | --- |
| [#74](https://github.com/TEL-ME/TEL-ME_BE/pull/74) | MERGED | `650cc9b` | Answer Guard 잔여 오류 보완 |
| [#80](https://github.com/TEL-ME/TEL-ME_BE/pull/80) | MERGED | `c3eeee1` | 상담 처리기 기본 활성화·구형 처리기 삭제 |
| [#82](https://github.com/TEL-ME/TEL-ME_BE/pull/82) | MERGED | `bb17e7f` | Guard 검사·저장 후 최종 답변 전송 |
| [#83](https://github.com/TEL-ME/TEL-ME_BE/pull/83) | MERGED | `c4f4458` | 저장·기록 API 조회·재접속 검증 |
| [#75](https://github.com/TEL-ME/TEL-ME_BE/pull/75) | OPEN | HEAD `53087f3` | 복합 질문 처리. 현재 비교·CI에 포함하지 않음 |

PR #84의 대상은 `develop`이며 HEAD `a19c261`은 위 develop을 포함한다(behind 0).
`develop...a19c261` 비교는 TELME-94 기능 코드 5개·테스트 3개·이 문서 1개, 총 9개 파일이다.
이미 병합된 #74·#80·#82·#83의 변경은 현재 PR diff에 중복 포함되지 않는다.
#75 포함 호환성 검증은 이 기준의 검증과 구분한다.

### 초기 검증 설정과 현재 기본값

초기 기준 `3f8798a`의 `telme.consult` 기본값은 persistence/llm/chat-integration/rag-integration 모두
false였다. 당시 활성 처리기 연결 테스트는 persistence/chat-integration/rag-integration을 true로
명시했고, llm-enabled=false 및 검색·분석·외부 모델 대역을 사용했다. 과거 검증을 현재 기본값으로 소급하지 않는다.

#80 병합 후 현재 `a19c261`과 develop `c4f4458`의 `application.yml`에서는
`CONSULT_PERSISTENCE_ENABLED`, `CONSULT_LLM_ENABLED`, `CONSULT_CHAT_INTEGRATION_ENABLED`,
`CONSULT_RAG_INTEGRATION_ENABLED`가 모두 기본 true다. 실제 Chat 처리기 구성에는
persistence/chat-integration/rag-integration 세 설정이 필요하다.
운영 배포의 환경변수·빈 구성·실제 활성 경로와 브라우저 화면은 미확인이다.

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

`RetryingLlmClient.stream`의 catch는 모델 동기 예외뿐 아니라 `delegate.stream` 호출 중
하위 `onToken`/`onComplete`에서 전파된 RuntimeException도 보관한다. 기존 정책으로 재시도를
판단한 뒤 최종 실패는 `handler.onError`로 전달한다. 현재 `RagAnswerGenerator`는
`CollectingHandler.onError`에서 오류를 보관하고 `stream` 종료 뒤 `rethrowIfFailed`로 다시 던진다.
최종 오류·재시도 콜백과 대기 중 발생한 예외까지 모두 stream 밖으로 나가지 않는다는 계약은 아니다.
이번 리뷰에서는 이 계약을 주석으로 명시했으며 catch·재시도·Guard·취소 동작은 변경하지 않았다.

초기 기능 보완 당시 신규 연결 테스트에서 수정 전 8건의 실패를 확인했고, 보완 후 통과했다.
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

실제 소켓·브라우저 화면은 검증하지 않았다. HTTP API와 SSE 빌더·재구독 응답을 연결한 백엔드 테스트다.
현재 팀 프런트와 해당 백엔드를 연결한 환경에서 다음을 확인해야 한다.

1. 모델 오류·타임아웃·검색 오류에 맞는 안내가 표시되고 로딩·재접속 반복이 종료되는지
2. RETRYING 중 부분 문구가 없고, 성공 시 최종 답변 한 번 또는 실패 시 오류 안내 한 번인지
3. 검색 결과 없음은 근거 부족 안내, 검색 장애는 검색 실패 안내로 구분되는지
4. 실패 후 새로고침에서도 부분 답변이나 완료 답변이 생기지 않고 실패 상태가 유지되는지
5. 정상·Guard 수정·안전 안내 답변의 저장·조회·화면 표시가 기존대로 유지되는지

오류 객체의 message 추가와 검색 실패 분류는 공통 decisions.md 반영 대상이다.
관련 정책과 계약은 이 문서에 기록했다.

## 테스트 및 환경

### 최신 검증 기준과 결과

HEAD `a19c26198bc5d22f70bf4ff4302fcf2bcd26506e`, develop
`c4f4458c5220d9e4cf1cb316cb9efce34edbbc8b`에 대한
[GitHub CI 실행 36814562355](https://github.com/TEL-ME/TEL-ME_BE/actions/runs/36814562355)는 성공했다.
실행 메타데이터의 PR head/base SHA가 위 기준과 일치하며, 전체 build
(`./gradlew build --build-cache`), 기존 마이그레이션 수정·삭제 검사, 테스트 결과 게시가 모두 성공했다.
게시 로그 집계는 **총 1,309건, 통과 1,215건, 실패 0건, 스킵 94건**이다.

아래 표는 같은 CI의 테스트 결과 게시 로그에서 확인한 관련 테스트 부분 집계다.
별도의 선택 테스트 실행 결과가 아니며 전체 집계에 다시 합산하지 않는다.

| 최신 CI의 관련 테스트 | 통과 |
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
| AnswerGuardUserEvidenceTest | 67 |
| **위 항목 소계** | **213** |

과거 문서의 `AnswerGuardUserEvidenceTest` 42건은 초기 기준의 수치이며 최신 CI에서는
병합된 #74의 회귀를 포함한 67건이다. 나머지 위 항목 건수도 같은 최신 CI 로그와 대조했다.
94건 스킵은 환경변수로 활성화하는 로컬 DB 테스트와 평가 프로브다. 이번 주석·문서 리뷰 대응에서는
테스트·build·유료 모델/Judge를 새로 실행하지 않았고, 위 기존 CI 결과를 확인했다.
이 결과는 `a19c261` 실행의 근거이며 아직 커밋하지 않은 리뷰 정정본의 새 CI 실행 결과는 아니다.

### 재현 환경과 범위

JDK 21, pgvector PostgreSQL 16의 telme DB·vector 확장과 PostGIS 패키지가 필요하다.
CI는 `.github/workflows/ci.yml`에서 PostgreSQL 연결 환경을 지정하고 확장을 준비한다.
Spring 통합 테스트는 실제 DB·활성 처리기·Guard·저장·SSE·조회 API를 연결하되
검색·분석·외부 모델은 테스트 대역이다. 외부 유료 모델 호출 없이 재현하는 범위이며
모델·프롬프트·평가 기준·Guard 판정 규칙·Router·검색 튜닝을 변경하지 않았다.
환경변수로 활성화하는 로컬 DB 테스트·평가 프로브는 해당 조건을 충족해야 별도로 실행된다.
GitHub CI의 PR 대상은 develop/main이다.

### 과거 실행과 #75의 한계

초기 개발 기준의 로컬 전체 1,191건(통과 1,097·스킵 94·실패 0)과 선택 회귀 188건은
과거 실행 기록이며 최신 `a19c261` 검증 수치로 사용하지 않는다.
과거 #75 HEAD `53087f3`을 포함한 임시 조합의 관련 227건도 별도 실행으로,
현재 develop/PR #84의 호환성 검증 결과가 아니다. 서로 다른 실행의 건수는 합산하지 않는다.

#75의 빈 검색 결과 조기 반환과 생성 계층을 통과하는 현재 경로는 안내문·호출 기록 범위가
다를 수 있다. #75 병합 시 생성 호출 없음·NO_EVIDENCE·전송/저장/조회 일치와
검색 시스템 오류의 별도 실패 처리를 해당 조합에서 다시 확인해야 한다.
이번 리뷰 대응에서는 #75 조합을 새로 검증하거나 다른 담당자의 브랜치를 수정하지 않았다.
실제 소켓·브라우저·운영 활성 설정의 미검증 범위와 위 SSE 프런트 확인 5가지는 남아 있다.
