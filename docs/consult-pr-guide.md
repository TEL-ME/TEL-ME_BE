# TELME-17 상담 코드 사용·검증

## 구현 범위
지역 누락 되묻기, 후속 조건·정정·거절 반영, 상담 재개 판단.
복합 요청별 판단 및 한 번에 하나의 되묻기 선택, 조건·질문 메시지 연결, 버전 충돌 방지.
최종 답변 생성·Chat HTTP 파이프라인·SSE 통합은 이번 PR의 완료 범위에 포함하지 않음.

## 사용 순서
Chat에서 소유권과 저장된 상담 ID 확인 → 메시지 저장 트랜잭션 종료 → prepareTurn 호출.
- waitingForReply=true, prepared=null: 기존 질문 대기. 다시 생성·저장하지 않음.
- waitingForReply=true, prepared 있음: persistWaitingChanges로 정정 조건 저장. 새 질문 없음.
- prepared.decision().action()=ASK: Chat의 잠금·순번 발급 방식으로 질문 저장 후 persist와 같은 트랜잭션 처리.
- PROCEED: 검색과 최종 답변 생성 단계로 전달. 최종 답변 저장 후 상담 complete 처리.

## 설정
상담 저장 Bean: telme.consult.persistence-enabled=true.
모델 질문: telme.consult.llm-enabled=true. 실제 모델은 공통 Ollama 구현 및 llm.provider=ollama 필요.
기본 질문 생성은 고정 문장. Fake 공통 클라이언트도 고정 질문으로 처리.
현재 실제 좌표 입력을 지원하지 않으며 location 조건 문자열을 사용.

## 검증
JDK 21 및 팀 PostgreSQL 환경에서 ./gradlew build.
상담 DB 테스트는 TELME_DB_TESTS=true와 POSTGRES_PORT 등 테스트 DB 설정을 추가해야 실행.
테스트는 무작위 전용 스키마에서 실행하며 공유 public 상담 데이터는 수정하지 않음.
현재 기본 CI에는 TELME_DB_TESTS가 없으므로 상담 DB 회귀 테스트는 건너뜀. 로컬 검증 결과를 PR에 첨부.
실제 LLM 실험은 별도 실행. 일반 CI에 모델 다운로드·유료 API 호출 없음.
기존 V1 마이그레이션 변경 없음. fixture는 현재 V1 사본.

## 제한
이 모듈의 sessionId 검사는 대화 소유권 인증을 대체하지 않음.
동시 저장 중복은 방지하지만 동시 모델 호출을 단 한 번으로 제한하는 구현은 아님.
FAQ 조건 확인은 일반 FAQ 진행, 매장 조건 확인은 지역 범위로 구현. 정책별 조건 확장은 후속 작업.
