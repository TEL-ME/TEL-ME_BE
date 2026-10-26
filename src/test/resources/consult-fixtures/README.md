# 스키마 출처

init-schema.sql은 PR #7의 31fb0586c6fb824e2c508f86ffecccc31439bf41 커밋 src/main/resources/db/migration/V1__init_schema.sql을 그대로 복사한 테스트 fixture다.

운영 마이그레이션이 아니며 Flyway 실행 자체를 테스트하지 않는다. 매 테스트마다 무작위 로컬 스키마에 전체 V1 DDL을 적용한다. vector 확장은 public에 유지하고 임시 테스트 스키마만 삭제한다. 개발용 V2 시드 데이터는 적용하지 않고 테스트가 자신의 사용자·게스트를 생성한다.
