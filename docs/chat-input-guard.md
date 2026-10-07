# 채팅 입력 검사

## 목적과 처리 경계

- 욕설 입력은 상담을 시작하기 전에 차단하고 경고·일시 제한을 적용한다.
- 주민등록번호·결제 카드번호의 지원 형식은 해당 부분만 마스킹하고 남은 문의를 처리한다.
- 감지 판단에 LLM을 사용하지 않는다. 정상 불만이나 단어 설명을 욕설로 일괄 판단하지 않는다.
- 관리자 조회·검토 화면, 감지 목록 편집 API, 프런트 입력 잠금은 별도 연결 범위다.

## 처리 흐름

`메시지 접수 → 좌표·세션 소유권·종료 상태 확인 → 개인정보 마스킹 → 욕설 검사 → 사용자 단위 잠금·집계 → 차단 기록 또는 정상 QUESTION 저장`

- 정상 접수한 내용만 기존 `ConsultChatProcessingService`에 전달한다.
- 검색·생성·Answer Guard·최종 답변 저장·SSE 계약은 기존 경로를 유지한다.
- 차단 입력은 실행을 생성하거나 상담 조건·대기 질문·상담 상태를 갱신하지 않는다.
- 제한 중 요청은 메시지·감지 이력을 추가하지 않는다. 제한 시작·종료 시각도 연장하지 않는다.
- 접수와 감지 이력은 같은 DB 트랜잭션이다. 저장 실패 시 둘 다 롤백하며 상담 작업은 DB 트랜잭션 확정 후에만 시작한다.

## 판정 기준

| 입력 | 처리 |
| --- | --- |
| 인사·일반 상담·서비스 불만 | 기존 상담 경로 유지 |
| 설정 목록의 명확한 욕설·초성·지원 변형 | 해당 입력 차단, 집계 기간 내 한 번 증가 |
| 여러 욕설이 섞인 한 입력 | 규칙은 여러 개 기록하되 횟수는 한 번 증가 |
| 지원하는 단어 뜻 질문·인용 피해 설명 | 해당 표현 구간만 예외, 다른 구간의 욕설은 계속 검사 |
| 개인정보와 정상 문의 | 번호만 마스킹하고 `QUESTION`으로 처리 |
| 개인정보와 욕설 | 마스킹한 내용으로 차단·경고 또는 제한 |
| 마스킹 후 번호와 단순 소개 문구만 남음 | 재입력 안내, 욕설 횟수 증가 없음 |
| 제한 중 정상 입력 또는 욕설 | 남은 시간 안내, 상담 처리·추가 집계 없음 |

### 욕설 목록과 예외

- 목록은 `src/main/resources/chat/input-guard-rules.json`에 있으며 정책 버전은 `chat-input-guard-v1`이다.
- 각 규칙은 `id`, `term`, `reason`을 가진다. 이유는 `PROFANITY` 또는 `INITIAL_PROFANITY`다.
- NFKC 정규화 후 표현 내부의 공백·점·별표·하이픈·숫자·지원하는 보이지 않는 구분자를 검사한다. 구분자는 문자 사이 최대 6개다.
- 초성은 끝·구분자·지원하는 욕설 어미 경계를 확인한다. 문장에 붙은 `이거ㅅㅂ`, `서비스ㅂㅅ같아`도 검사하지만 `ㅂㅅ역`처럼 정의하지 않은 단어 조합은 차단하지 않는다.
- `시발점`, `시발역`, `시발열차`는 해당 구간만 예외다.
- 단어 뜻 질문과 인용 피해 설명은 명시적인 문장 패턴만 지원한다. 임의 인용이나 모든 자연어 맥락을 판별하는 기능은 아니다.
- 규칙 변경은 설정 파일 수정·리뷰·배포와 정책 버전 갱신으로 반영한다.

### 개인정보 마스킹

- 주민등록번호: 유효한 생년월일과 지원하는 구분 코드가 있는 6+7자리 패턴을 검사한다. 실제 번호의 발급 여부를 검증하지 않는다.
- 카드번호: 13~19자리, 지원하는 시작 숫자와 Luhn 검증을 사용한다. 공백·하이픈 구분 및 전각 숫자도 지원한다. 2로 시작하는 16자리 Mastercard의 BIN 222100~272099도 포함한다.
- Mastercard 2-series 범위는 [공식 BIN 안내](https://cam.mastercard.com/content/dam/mccom/en-us/documents/issuer-2-series-BIN-impact-checklist-aug-2016.pdf)를 따른다.
- 감지 부분은 `[주민등록번호]`, `[카드번호]`로 대체한다.
- 감지 결과에는 유형·규칙 ID만 남긴다. 매칭된 번호와 원문 번호의 해시는 저장하지 않는다.
- 마스킹된 내용만 채팅 메시지·감지 이력·실행 추적·상담 입력에 전달한다. 신규 검사 코드는 원문·매칭 값을 로그에 출력하지 않는다.
- 전화번호·계좌번호·이메일·자유 형식 개인정보와 모든 우회 표기를 포괄하는 검사는 현재 지원하지 않는다.
- 기존 대화·요약에 저장된 과거 내용을 소급 검사하거나 수정하지 않는다.

## 경고와 일시 제한

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| `chat.input-guard.observation-window` | `PT10M` | 현재 시각 이전 10분의 유효 감지 입력 집계 |
| `chat.input-guard.threshold` | `3` | 제한을 시작하는 누적 횟수 |
| `chat.input-guard.restriction-duration` | `PT1M` | 일시 제한 시간 |
| `chat.input-guard.retention` | `P30D` | 감지 이력 보존 기간 |
| `chat.input-guard.cleanup-enabled` | `true` | 감지 이력 정리 실행 여부 |
| `chat.input-guard.cleanup-cron` | `0 0 3 * * *` | 매일 03:00 UTC에 이력 정리 |

- 회원은 `user_id`, 게스트는 서버가 확인한 `guest_id` 기준으로 집계한다. 새 채팅 세션에서도 유지된다.
- 10분 전 시각과 정확히 일치하는 기록은 제외한다. 정상 입력은 기간 내 기록을 초기화하지 않는다.
- 1·2회에는 해당 입력을 차단하고 경고한다. 3회에는 60초 제한을 시작한다.
- 제한 종료 시각부터 새 집계 주기를 시작한다. 이전 감지 이력은 보존한다.
- `guests.merged_user_id`의 기존 승계 관계를 읽어 게스트 감지 이력·유효 제한을 회원에게 적용한다. 인증·승계 절차 자체는 변경하지 않는다.
- 자동 상담 종료·영구 차단·오탐 표시를 통한 조기 제한 해제는 하지 않는다.
- 게스트 식별자가 새로 발급되는 상황까지 동일인을 판별하지 않는다.

## 접수·조회·SSE 계약

- 대상: `POST /api/v1/chat/sessions/{sessionId}/messages`.
- 기존 `content`, 선택 좌표를 유지하며 선택 UUID 필드 `requestId`를 추가한다.
- 정상 접수는 기존 `201`, `executionId`, `executionStatus` 계약을 유지한다.
- 개인정보를 마스킹한 정상 접수는 `201`이며 `inputGuard.action=MASKED` 안내가 추가된다.
- 정책 차단·제한·재입력 안내는 `200`이며 `inputGuard`를 반환한다. 생성 오류가 아니며 `executionId`·`executionStatus`는 없다.
- `inputGuard` 필드: `action`, `message`, `violationCount`, `retryAfterSeconds`, `restrictionStartedAt`, `restrictionUntil`, `detections`.
- 조치: `WARNED`, `RESTRICTED`, `REWRITE_REQUIRED`, `MASKED`.
- 남은 제한 시간이 있으면 HTTP `Retry-After` 헤더도 초 단위로 반환한다.
- 최초 경고·제한 유발 입력·재입력 대상은 `USER/BLOCKED/COMPLETED`로 저장한다. 제한 중 추가 입력에는 메시지 ID·순번이 없다.
- 개인정보 마스킹 후 정상 상담은 `USER/QUESTION`으로 저장한다.
- 기록 조회는 `BLOCKED` 유형과 안전한 내용을 반환한다. 정책 안내문은 접수 응답에 제공하며 별도 ASSISTANT 답변이나 SSE 실행으로 저장하지 않는다.
- 차단 메시지는 질문 통계의 `USER/QUESTION` 조건에 포함되지 않는다. 문맥·요약 조회에서는 `ERROR`와 `BLOCKED`를 페이지 제한 적용 전에 함께 제외한다.

### 프런트 연결 요구사항

- 경고·제한·재입력 안내와 입력 잠금은 프런트에서 별도로 연결해야 한다. 백엔드의 정책 응답 제공과 실제 화면 표시를 구분한다.
- `executionId`가 있는 정상 접수만 기존 SSE를 구독해야 한다. 실행이 없는 정책 안내는 생성 실패나 자동 재시도로 처리하지 않아야 한다.
- `BLOCKED` 메시지는 정상 질문·답변 평가와 구분해야 한다.
- 입력 잠금·남은 시간 표시에는 `restrictionUntil`과 `retryAfterSeconds`를 사용한다. 제한 종료 후에는 기존 상담에서 새 요청을 보낼 수 있어야 한다.
- 새로고침 후 서버의 현재 제한 상태 조회가 필요하면 조회 계약을 추가로 맞춘다. 대화 문구를 분석해 제한 상태를 재구성하지 않는다.

### 중복 처리

- 감지가 있는 요청에 `requestId`가 있으면 동일 주체의 같은 논리 요청은 저장된 응답을 다시 반환한다. 횟수·메시지·실행을 추가하지 않는다.
- 요청 ID를 다른 세션·안전한 내용·좌표에 재사용하면 `409/CHAT409-4`를 반환한다.
- 비교용 해시는 세션·마스킹된 내용·좌표로 만든다. 서로 다른 실제 번호가 같은 대체 문구로 바뀐 경우 번호 차이를 재현하거나 비교하지 않는다.
- 과거 제한 응답의 재전송은 당시 조치·횟수를 유지하고 남은 시간만 현재 시각으로 계산한다. 만료된 동일 요청을 정상 질문으로 다시 접수하지 않는다.
- 정상 무감지 요청에는 이력 영수증을 만들지 않는다. 일반 메시지 API 전체의 중복 방지 계약으로 확대하지 않는다.
- `requestId` 없는 HTTP 재전송과 30일 정리 후 요청 재전송은 중복 방지를 보장하지 않는다.
- 기존 실행 재구독은 메시지 접수 경로를 호출하지 않으므로 감지 횟수를 늘리지 않는다.

## DB 저장 계약

### `chat_input_guard_states`

- `guard_state_id`: BIGINT PK.
- `user_id`: BIGINT 또는 `guest_id`: UUID. 두 필드 중 정확히 하나만 사용한다.
- `counting_from_at`: TIMESTAMPTZ. 현재 집계 주기의 시작 기준.
- `restriction_started_at`, `restriction_until`: nullable TIMESTAMPTZ. 제한이 없으면 둘 다 null.
- `updated_at`: TIMESTAMPTZ.
- 사용자·게스트별 부분 고유 인덱스와 행 잠금으로 다른 세션의 동시 입력도 직렬화한다.

### `chat_input_guard_events`

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| `guard_event_id` | BIGINT PK | 감지 이력 ID |
| `guard_state_id` | BIGINT NOT NULL | 집계 주체와 연결 |
| `detected_at` | TIMESTAMPTZ | 감지 시각 |
| `user_id`, `guest_id` | nullable BIGINT / UUID | 감지 당시 사용자·게스트 |
| `session_id`, `message_id` | nullable BIGINT | 세션·차단 또는 마스킹 메시지 연결 |
| `request_id`, `request_fingerprint` | nullable UUID / VARCHAR(64) | 논리 요청 및 안전한 값의 비교 해시 |
| `sanitized_content` | TEXT | 지원 개인정보가 제거된 입력 |
| `detections` | JSONB | `{reason, ruleId}` 목록 |
| `actions` | JSONB | 마스킹·경고·제한·재입력 조치 목록 |
| `counted_violation` | BOOLEAN | 욕설 횟수 집계 대상 여부 |
| `violation_count` | INTEGER | 당시 최근 집계 횟수 |
| `restriction_started_at`, `restriction_until` | nullable TIMESTAMPTZ | 당시 제한 시각 |
| `policy_version` | VARCHAR(50) | 감지 정책 버전 |
| `response_snapshot` | JSONB | 재전송에 반환할 안전한 접수 응답 |
| `review_status` | VARCHAR(20) | `UNREVIEWED` / `CONFIRMED` / `FALSE_POSITIVE` |
| `reviewed_by`, `reviewed_at` | nullable BIGINT / TIMESTAMPTZ | 검토자·검토 시각 |

- 감지 이유: `PROFANITY`, `INITIAL_PROFANITY`, `SENSITIVE_INFORMATION`.
- 오탐 표시는 규칙 개선용 검토 결과다. 당시 조치·횟수나 현재 제재를 변경하지 않는다.
- 정렬 기준은 `detected_at DESC, guard_event_id DESC`다. 감지 목록 조회·검토 갱신은 관리자 권한에서 구현한다.
- 정리 작업은 감지 이력만 삭제한다. `chat_messages`, 상담, 실행, 제한 상태는 기간 정리에 의해 삭제되지 않는다.
- 계정·게스트의 물리 삭제에는 해당 제한 상태와 이력이 함께 삭제된다. 세션·메시지가 삭제되면 이력의 해당 연결은 null이 된다.
- 새 테이블은 `V25__add_chat_input_guard.sql`로 생성한다. `BLOCKED`는 기존 `message_type` 컬럼에 저장하므로 해당 컬럼 변경은 없다. 기존 마이그레이션 내용은 변경하지 않는다.
- V25 적용 전에 V24를 포함한 선행 마이그레이션을 먼저 적용한다. 기존 DB는 실제 Flyway 적용 이력을 확인하며, 낮은 버전의 미적용 파일을 `outOfOrder`나 `repair`로 우회하지 않는다.

## 검증 방법과 한계

- DB 접속 환경변수와 실행 준비는 [프로젝트 README](../README.md)를 따른다. 검증에는 운영·공유 DB 대신 독립 개발 DB를 사용한다.
- 입력 검사 단위·통합 테스트를 선택해 실행할 수 있다.

```bash
LLM_PROVIDER=fake TELME_DB_TESTS=false \
TELME_PROBE=false TELME_PROBE_ALLOW_PAID=false \
TELME_ROUTING_EVAL=false TELME_PROMPT_COMPARE=false \
FAQ_REEMBED_ENABLED=false FAQ_BATCH_LOAD_ENABLED=false EMBEDDING_WARMUP_ENABLED=false \
./gradlew test \
  --tests 'com.telme.chat.guard.ChatInputInspectorTest' \
  --tests 'com.telme.chat.service.ChatInputGuardIntegrationTest'
```


- Java 21, 독립 PostgreSQL 및 기존 확장·마이그레이션을 준비한다. 유료 모델 없이 규칙 단위 테스트와 채팅 처리기 통합 테스트를 실행할 수 있다.
- 단위 테스트는 지원 욕설·예외·번호 형식·마스킹 경계를 검증한다.
- 통합 테스트는 실제 접수·처리·저장·조회에서 사용자 경계, 동시 집계, 요청 중복, 제한 만료, 되묻기 유지, 저장 실패 롤백, 문맥·요약 제외를 검증한다.
- 테스트의 시각은 주입된 `Clock`으로 제어한다. 제한 만료 검증에 실제 대기를 사용하지 않는다.
- 분석·답변 대역을 사용한 통합 검증은 실제 모델 대화·브라우저 입력 잠금·경고 표시를 검증한 것이 아니다.
- 화면 확인 시 경고·제한 안내, 남은 시간, 만료 후 재개, 마스킹 표시, 새로고침 후 BLOCKED 유형 처리, 정책 안내의 오류·재시도 방지 여부를 확인한다.
