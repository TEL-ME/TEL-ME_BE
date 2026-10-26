-- 검색이 질문+답변(Q_A) 벡터 하나만 봐서, 사용자가 말을 바꿔 물으면 임계값에서 잘린다.
-- 같은 FAQ를 질문만(QUESTION_ONLY)으로 한 번 더 임베딩해 두고 두 벡터를 각자의 임계값으로 조회하면
-- Recall@3이 0.375 → 0.463으로 오르고 무관 질문 거부율은 그대로다. 근거는 docs/EVAL_SET_SUPPLEMENT.md 6절.
ALTER TABLE faq_embeddings
    ADD COLUMN embedding_question vector(1024);   -- QUESTION_ONLY 구성. embedding과 같은 트랜잭션에서 갱신된다

-- embedding 인덱스(V1)와 같은 이유로 HNSW + vector_cosine_ops. 파라미터는 pgvector 기본값을 쓴다.
-- 값이 NULL인 행은 인덱싱되지 않으므로 백필 전까지 이 인덱스는 비어 있다.
CREATE INDEX idx_faq_embeddings_embedding_question ON faq_embeddings
    USING hnsw (embedding_question vector_cosine_ops);
