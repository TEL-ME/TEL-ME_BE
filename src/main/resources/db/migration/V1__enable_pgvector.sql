-- AWS RDS/Aurora PostgreSQL은 docker-entrypoint-initdb.d 같은 컨테이너 초기화 스크립트를 실행하지
-- 않으므로, 로컬/CI/RDS 모두에서 동일하게 적용되도록 확장 생성을 Flyway가 직접 관리한다.
-- RDS/Aurora에서는 마스터 유저가 별도 권한 없이 허용 목록에 있는 확장을 생성할 수 있다.
CREATE EXTENSION IF NOT EXISTS vector;
