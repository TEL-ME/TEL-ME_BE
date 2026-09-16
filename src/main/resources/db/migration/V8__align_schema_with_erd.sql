-- V1~V6가 팀 합의 ERD와 어긋난 부분을 바로잡는다 (PR #7 리뷰 반영).
-- 이미 적용된 V1~V6는 고치지 않고 여기서 ALTER로 맞춘다.

-- ── 1. 동일 도메인 내 FK에 ON DELETE CASCADE 추가 ──────────────
ALTER TABLE social_accounts DROP CONSTRAINT social_accounts_user_id_fkey;
ALTER TABLE social_accounts ADD CONSTRAINT social_accounts_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (user_id) ON DELETE CASCADE;

ALTER TABLE chat_messages DROP CONSTRAINT chat_messages_session_id_fkey;
ALTER TABLE chat_messages ADD CONSTRAINT chat_messages_session_id_fkey
    FOREIGN KEY (session_id) REFERENCES chat_sessions (session_id) ON DELETE CASCADE;

ALTER TABLE chat_executions DROP CONSTRAINT chat_executions_session_id_fkey;
ALTER TABLE chat_executions ADD CONSTRAINT chat_executions_session_id_fkey
    FOREIGN KEY (session_id) REFERENCES chat_sessions (session_id) ON DELETE CASCADE;

ALTER TABLE query_routings DROP CONSTRAINT query_routings_message_id_fkey;
ALTER TABLE query_routings ADD CONSTRAINT query_routings_message_id_fkey
    FOREIGN KEY (message_id) REFERENCES chat_messages (message_id) ON DELETE CASCADE;

ALTER TABLE consult_requests DROP CONSTRAINT consult_requests_session_id_fkey;
ALTER TABLE consult_requests ADD CONSTRAINT consult_requests_session_id_fkey
    FOREIGN KEY (session_id) REFERENCES chat_sessions (session_id) ON DELETE CASCADE;

ALTER TABLE consult_conditions DROP CONSTRAINT consult_conditions_consult_request_id_fkey;
ALTER TABLE consult_conditions ADD CONSTRAINT consult_conditions_consult_request_id_fkey
    FOREIGN KEY (consult_request_id) REFERENCES consult_requests (consult_request_id) ON DELETE CASCADE;

ALTER TABLE llm_generations DROP CONSTRAINT llm_generations_execution_id_fkey;
ALTER TABLE llm_generations ADD CONSTRAINT llm_generations_execution_id_fkey
    FOREIGN KEY (execution_id) REFERENCES chat_executions (execution_id) ON DELETE CASCADE;

ALTER TABLE message_sources DROP CONSTRAINT message_sources_message_id_fkey;
ALTER TABLE message_sources ADD CONSTRAINT message_sources_message_id_fkey
    FOREIGN KEY (message_id) REFERENCES chat_messages (message_id) ON DELETE CASCADE;

ALTER TABLE message_feedback DROP CONSTRAINT message_feedback_message_id_fkey;
ALTER TABLE message_feedback ADD CONSTRAINT message_feedback_message_id_fkey
    FOREIGN KEY (message_id) REFERENCES chat_messages (message_id) ON DELETE CASCADE;

ALTER TABLE store_services DROP CONSTRAINT store_services_store_id_fkey;
ALTER TABLE store_services ADD CONSTRAINT store_services_store_id_fkey
    FOREIGN KEY (store_id) REFERENCES stores (store_id) ON DELETE CASCADE;

ALTER TABLE store_hours DROP CONSTRAINT store_hours_store_id_fkey;
ALTER TABLE store_hours ADD CONSTRAINT store_hours_store_id_fkey
    FOREIGN KEY (store_id) REFERENCES stores (store_id) ON DELETE CASCADE;

-- ── 2. 임의로 뺐던 크로스 도메인 FK를 ERD대로 복원 ──────────────
ALTER TABLE faqs ADD CONSTRAINT faqs_created_by_fkey
    FOREIGN KEY (created_by) REFERENCES users (user_id);
ALTER TABLE faqs ADD CONSTRAINT faqs_updated_by_fkey
    FOREIGN KEY (updated_by) REFERENCES users (user_id);

ALTER TABLE chat_sessions ADD CONSTRAINT chat_sessions_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (user_id);
ALTER TABLE chat_sessions ADD CONSTRAINT chat_sessions_guest_id_fkey
    FOREIGN KEY (guest_id) REFERENCES guests (guest_id);

ALTER TABLE message_sources ADD CONSTRAINT message_sources_faq_id_fkey
    FOREIGN KEY (faq_id) REFERENCES faqs (faq_id);

ALTER TABLE message_feedback ADD CONSTRAINT message_feedback_user_id_fkey
    FOREIGN KEY (user_id) REFERENCES users (user_id);
ALTER TABLE message_feedback ADD CONSTRAINT message_feedback_guest_id_fkey
    FOREIGN KEY (guest_id) REFERENCES guests (guest_id);

-- ── 3. store_hours.day_of_week을 ERD 기준(1=월 ~ 7=일)으로 맞춤 ──
-- 기존 0~6(1~6=월~토는 값이 같고, 0=일요일만 다름)이라 0만 7로 옮기면 된다.
-- ERD엔 day_of_week 범위를 강제하는 CHECK가 없어서(주석으로만 "1(월)~7(일)" 기재), 옛 CHECK(0~6)는
-- 값만 옮기고 제거한다. 값 이관 자체는 CHECK 존재 여부와 무관하게 필요해서 그대로 남긴다.
ALTER TABLE store_hours DROP CONSTRAINT ck_store_hours_day;
UPDATE store_hours SET day_of_week = 7 WHERE day_of_week = 0;

-- ── 4. ERD에 정의된 인덱스로 맞춤 (누락분 추가, 약화된 인덱스는 재생성) ──
CREATE INDEX idx_guest_expires ON guests (expires_at) WHERE merged_user_id IS NULL;
CREATE INDEX idx_embed_sync ON faq_embeddings (sync_status) WHERE sync_status <> 'SYNCED';
CREATE INDEX idx_store_service ON store_services (service_type_id, store_id);

DROP INDEX idx_chat_sessions_user_id;
CREATE INDEX idx_session_user ON chat_sessions (user_id, last_active_at DESC);

DROP INDEX idx_chat_executions_session_id;
CREATE INDEX idx_execution_session ON chat_executions (session_id, started_at DESC);

DROP INDEX idx_consult_requests_session_id;
CREATE INDEX idx_request_pending ON consult_requests (session_id, status);

DROP INDEX idx_faqs_category;
CREATE INDEX idx_faq_category ON faqs (category) WHERE status = 'ACTIVE';

-- 컬럼 구성은 이미 같고 이름만 ERD와 다르던 인덱스들
DROP INDEX idx_chat_sessions_guest_id;
CREATE INDEX idx_session_guest ON chat_sessions (guest_id);

DROP INDEX idx_llm_generations_execution_id;
CREATE INDEX idx_generation_exec ON llm_generations (execution_id);

DROP INDEX idx_message_sources_message_id;
CREATE INDEX idx_source_message ON message_sources (message_id);

-- ── 5. content_hash를 ERD 타입(CHAR(64))으로 맞춤 ──────────────
ALTER TABLE faqs ALTER COLUMN content_hash TYPE CHAR(64);

-- ── 6. ERD "인덱스" 섹션에 없는 인덱스 제거 ──────────────
-- idx_faq_embeddings_embedding(HNSW)도 ERD에 없어서 포함해 제거하지만, 코사인 유사도 검색을
-- 인덱스 없이(순차 스캔으로) 돌리게 되는 영향이 커서 별도로 위험 메시지로 알린다.
DROP INDEX idx_stores_region_code;
DROP INDEX idx_chat_messages_reply_to_id;
DROP INDEX idx_consult_conditions_asked_message_id;
DROP INDEX idx_consult_conditions_answered_message_id;
DROP INDEX idx_message_sources_faq_id;
DROP INDEX idx_chat_executions_input_message_id;
DROP INDEX idx_chat_executions_output_message_id;
DROP INDEX idx_guests_merged_user_id;
DROP INDEX idx_social_accounts_user_id;
DROP INDEX idx_faq_embeddings_embedding;
DROP INDEX idx_consult_requests_origin_message_id;
DROP INDEX idx_message_feedback_message_id;

-- ── 7. ERD에 명시 안 된 CHECK 제약 제거 (ck_session_owner, ck_feedback_actor는 ERD에 있어 유지) ──
ALTER TABLE users DROP CONSTRAINT ck_user_role;
ALTER TABLE users DROP CONSTRAINT ck_user_status;
ALTER TABLE social_accounts DROP CONSTRAINT ck_social_provider;
ALTER TABLE faqs DROP CONSTRAINT ck_faq_status;
ALTER TABLE faq_embeddings DROP CONSTRAINT ck_faq_embedding_sync_status;
ALTER TABLE stores DROP CONSTRAINT ck_store_status;
ALTER TABLE store_service_types DROP CONSTRAINT ck_service_type_code;
ALTER TABLE chat_sessions DROP CONSTRAINT ck_session_status;
ALTER TABLE chat_messages DROP CONSTRAINT ck_message_role;
ALTER TABLE chat_messages DROP CONSTRAINT ck_message_type;
ALTER TABLE chat_messages DROP CONSTRAINT ck_message_status;
ALTER TABLE chat_messages DROP CONSTRAINT ck_message_answer_basis;
ALTER TABLE chat_executions DROP CONSTRAINT ck_execution_status;
ALTER TABLE consult_requests DROP CONSTRAINT ck_consult_request_intent;
ALTER TABLE consult_requests DROP CONSTRAINT ck_consult_request_status;
ALTER TABLE consult_conditions DROP CONSTRAINT ck_condition_source;
ALTER TABLE consult_conditions DROP CONSTRAINT ck_condition_status;
ALTER TABLE llm_generations DROP CONSTRAINT ck_generation_task_type;
ALTER TABLE llm_generations DROP CONSTRAINT ck_generation_status;
ALTER TABLE message_feedback DROP CONSTRAINT ck_feedback_rating;
ALTER TABLE message_feedback DROP CONSTRAINT ck_feedback_reason_code;
ALTER TABLE query_routings DROP CONSTRAINT ck_routing_intent;
ALTER TABLE query_routings DROP CONSTRAINT ck_routing_method;
