-- V2 시드의 admin@example.com은 password_hash가 형식만 맞춘 더미라 어떤 비밀번호로도 로그인되지 않았다.
-- 관리자 API를 확인하려면 실제로 로그인되는 ADMIN 계정이 필요해 해시만 교체한다.
-- 적용된 V2를 직접 고치면 Flyway 체크섬이 어긋나 팀원 전원이 DB를 지워야 하므로 새 파일로 덮어쓴다.
-- 로컬 전용 계정이므로 dev-migration에 둔다 (prod 프로파일에서는 제외).
-- 비밀번호: admin1234 (BCrypt, strength 10)
UPDATE users
SET password_hash = '$2a$10$gml.HwK/Z/CbAYo2F5bIE.qaevdLG7caTCzQPZicqvufImuq4tmMy'
WHERE email = 'admin@example.com';
