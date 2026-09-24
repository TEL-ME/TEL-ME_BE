# 피드백 API

피드백 기능은 기본 비활성입니다. 애플리케이션 실행 환경에 `TELME_FEEDBACK_ENABLED=true`를 설정하면 `telme.feedback.enabled=true`로 인식되어 API와 대화 이력의 피드백 조회가 활성화됩니다. `bootRun`과 IDE 실행은 `.env` 파일을 자동으로 읽지 않습니다.

## 평가 대상과 요청

회원과 게스트는 자신이 소유한 채팅 세션의 `ASSISTANT` 메시지 중 `status=COMPLETED`인 `ANSWER` 또는 `STORE_RESULT`만 평가할 수 있습니다. 질문, 되묻기, 오류 메시지, 생성 중이거나 실패한 답변은 평가할 수 없습니다. 사용자 신원은 서버 세션에서 확인하며 요청 본문에서 받지 않습니다.

`PUT /api/v1/chat/messages/{messageId}/feedback`은 평가를 등록하거나 기존 평가를 수정합니다. 같은 사용자가 같은 메시지에 남길 수 있는 평가는 한 건입니다.

```json
{"rating":"LIKE"}
```

```json
{"rating":"DISLIKE","reason":"WRONG_INFO","comment":"안내된 요금이 달라요"}
```

| 필드 | 규칙 |
| --- | --- |
| `rating` | 필수. `LIKE` 또는 `DISLIKE` |
| `reason` | `DISLIKE`일 때 필수, `LIKE`일 때 사용 불가 |
| `comment` | `DISLIKE`일 때만 사용 가능. 앞뒤 공백을 제거하고 최대 1000자 |

`reason`의 값은 `WRONG_INFO`(정보 오류), `NOT_RELATED`(질문과 관련 없음), `HARD_TO_READ`(읽기 어려움)입니다.

## 조회와 취소

- `GET /api/v1/chat/messages/{messageId}/feedback`: 내 평가를 조회합니다. 작성하지 않았으면 성공 응답의 `result`가 `null`입니다.
- `DELETE /api/v1/chat/messages/{messageId}/feedback`: 내 평가를 취소합니다. 이미 평가가 없어도 성공합니다.
- `GET /api/v1/chat/sessions/{sessionId}/messages`: 각 메시지의 `ratable`과 `myFeedback`을 반환합니다. 평가를 작성하지 않았거나 기능이 꺼져 있으면 `myFeedback`은 `null`입니다. 기능이 꺼져 있으면 `ratable`도 `false`입니다.

게스트가 로그인하거나 회원가입하면 기존 게스트 세션과 피드백이 회원 계정으로 승계됩니다. 이후 회원 세션에서 기존 평가를 조회, 수정, 취소할 수 있으며 이전 게스트 신원으로는 접근할 수 없습니다.

## 오류 응답

오류도 공통 응답 형식(`isSuccess`, `code`, `message`, `result`)으로 반환됩니다.

| HTTP | 코드 | 조건 |
| --- | --- | --- |
| 400 | `FEEDBACK400-0` | `messageId`가 0 이하이거나 평가와 사유의 조합이 잘못됨 |
| 400 | `COMMON400-0` | JSON 형식 또는 enum 값이 잘못됨 |
| 400 | `COMMON400-1` | 필수값 누락, 의견 길이 초과, 경로 변수 형식 오류 |
| 401 | `CHAT401-0` | 사용할 수 있는 회원 또는 게스트 신원이 없음 |
| 404 | `FEEDBACK404-0` | 메시지가 없거나 내 세션에 속하지 않음 |
| 409 | `FEEDBACK409-0` | 등록 또는 수정 대상이 완료된 답변이나 매장 추천이 아님 |

채팅 경로에 처음 접근하면 게스트 세션이 자동 발급됩니다. 따라서 쿠키가 없는 사용자가 다른 사람의 메시지에 접근하면 일반적으로 401 대신 404가 반환됩니다. 409는 등록 또는 수정 요청에만 적용됩니다.

## 확인 범위

2026-09-23 로컬 실행에서 API 활성화, 게스트 평가 등록, 대화 이력 반영, 로그인 후 조회와 수정, 취소까지 HTTP 요청으로 확인했습니다. 완료된 답변 메시지는 이 확인을 위해 DB에 직접 준비했으므로 실제 AI 답변 생성부터 평가까지의 전체 파이프라인은 별도 통합 검증 대상입니다.
