# 피드백 로컬 작업 상태

별도 작업 폴더: TEL-ME_BE-feedback. 상담 TELME-17 PR에 포함하지 않음.

## 구현
- LIKE/DISLIKE 등록·조회·수정·취소.
- 현재 대화 소유권 확인. 회원 또는 게스트 중 한 신원 사용.
- 완료된 ASSISTANT/ANSWER만 평가 가능. 되묻기·STORE_RESULT는 현재 제외.
- ChatActorProvider로 확인한 신원을 재사용. 본문 userId/guestId로 작성자 지정 불가.
- 컨트롤러, 요청·응답 record DTO, Converter 분리.
- 도메인 에러 코드 및 공통 예외 처리 사용.
- Service 트랜잭션 및 저장소 잠금 처리.

## 현재 로컬 API
PUT/GET/DELETE /api/v1/chat/messages/{messageId}/feedback.
기본 비활성. telme.feedback.enabled=true로 활성화.
PUT은 등록·수정, GET 미작성은 result=null, DELETE는 취소.
사유 WRONG_INFO/NOT_RELATED/HARD_TO_READ, 의견 최대 1000자.

## 검증
최신 공통 코드 포함 전체 빌드 69건 통과, 실패 0건. 그중 피드백 테스트 28건.
DB 테스트도 Spring 트랜잭션 프록시를 거쳐 실행한다.
조회의 읽기 전용 트랜잭션과 FOR UPDATE 잠금 충돌을 재현하고 수정했다.
피드백 활성화 상태에서 Spring Boot 컨텍스트 시작은 앞선 로컬 테스트에서 확인.
실제 화면·로그인 전환·CSRF 포함 전체 사용자 흐름을 검증한 것은 아님.

## 공유 전 확인
게스트 평가 허용, 수정·취소, 최종 답변/매장 답변 평가 범위, v1 경로는 현재 로컬 구현 정책이며 팀 확인 필요.
TELME-20 브랜치로 별도 PR 리뷰 진행.
상담 TELME-17 코드와 분리해 공유.

로그인 후 회원·게스트 ID가 함께 남으면 회원 신원으로 정규화하도록 보완.
브랜치: feat/TELME-20-message-feedback (사용자 제공 이슈 키 기준).
