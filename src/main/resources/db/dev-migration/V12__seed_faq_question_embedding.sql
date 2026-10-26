-- V2 시드 FAQ 2건에만 질문 벡터를 채운다. 로컬에서 이중 벡터 검색을 켜고 확인하려면 두 컬럼이 다 있어야 한다.
-- 적재된 코퍼스는 건드리지 않는다. 재임베딩이 채워야 할 값이고, 여기서 embedding을 복사해 두면
-- 두 벡터가 같아져 합치기 규칙(중복 제거 → 질문만 우선 → 3개 컷)이 동작하는지 확인할 수 없다.
-- 시드는 policy_ref가 POLICY-로 시작하고 코퍼스에는 이 형식이 없다(docs/EVAL_SET_SUPPLEMENT.md 4.1절과 같은 기준).
--
-- V2와 같은 이유로 성분마다 값이 달라지는 패턴을 쓴다. 모든 성분이 같으면 코사인 유사도가 1.0으로 붙어
-- 랭킹이 구분되지 않는다. faq_id별로 다른 패턴을 줘서 두 시드가 서로 다른 방향을 보게 한다.
UPDATE faq_embeddings e
   SET embedding_question = (
           SELECT ('[' || string_agg(
                       (CASE e.faq_id
                            WHEN 1 THEN 0.003 + 0.0004 * (n % 11)
                            ELSE 0.004 + 0.0004 * (n % 19)
                        END)::text, ',' ORDER BY n) || ']')::vector
             FROM generate_series(1, 1024) AS n)
  FROM faqs f
 WHERE f.faq_id = e.faq_id
   AND f.policy_ref LIKE 'POLICY-%'
   AND e.embedding_question IS NULL;
