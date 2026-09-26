package com.telme.faq.repository;

import com.telme.faq.entity.FaqEmbedding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqEmbeddingRepository extends JpaRepository<FaqEmbedding, Long> {

    // 코사인 거리 기반 topK 조회. faqVersion=버전 일치(stale 벡터 제외), modelName=현재 모델만 포함
    // distance를 같이 반환해 rank와 score가 같은 값에서 나오게 한다(재계산 금지)
    @Query("""
            SELECT new com.telme.faq.repository.FaqNearestMatch(e, cosine_distance(e.embedding, cast(:queryVector as vector)))
            FROM FaqEmbedding e
            JOIN FETCH e.faq f
            WHERE f.status = 'ACTIVE' AND e.syncStatus = 'SYNCED'
                  AND e.faqVersion = f.version AND e.modelName = :modelName
            ORDER BY cosine_distance(e.embedding, cast(:queryVector as vector))
            LIMIT :topK
            """)
    List<FaqNearestMatch> findNearest(
            @Param("queryVector") float[] queryVector,
            @Param("topK") int topK,
            @Param("modelName") String modelName);
}
