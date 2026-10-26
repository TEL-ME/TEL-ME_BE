-- 회원 1명당 provider(카카오/구글 등)는 하나만 연결 허용 — 일반적인 소셜 로그인 연결 정책과 동일
ALTER TABLE social_accounts
    ADD CONSTRAINT uk_social_user_provider UNIQUE (user_id, provider);
