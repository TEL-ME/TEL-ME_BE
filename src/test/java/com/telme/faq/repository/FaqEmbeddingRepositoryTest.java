package com.telme.faq.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqEmbedding;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// V2 시드 데이터(faqId 1, 2, model_name='bge-m3', faq_version=1) 기준.
// 각 테스트는 트랜잭션 롤백으로 시드에 영향 없음
@SpringBootTest
@Transactional
class FaqEmbeddingRepositoryTest {

    private static final String MODEL = "bge-m3";

    @Autowired
    private FaqEmbeddingRepository repository;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("코사인 거리가 가까운 순으로 정렬해서 반환한다")
    void 거리순으로_정렬해서_반환한다() {
        // 시드나 로컬에 적재된 다른 FAQ와 절대 안 섞이도록 이 테스트 전용 modelName으로 격리
        String testModel = "test-order-model";
        float[] queryVector = new float[1024];
        float[] closeVector = new float[1024]; // queryVector와 완전 일치 → distance 0
        float[] farVector = new float[1024]; // queryVector와 직교 → distance 1
        for (int i = 0; i < 1024; i++) {
            queryVector[i] = closeVector[i] = (i % 2 == 0) ? 1f : 0f;
            farVector[i] = (i % 2 == 0) ? 0f : 1f;
        }
        FaqEmbedding close = saveEmbedding("가까운 FAQ", closeVector, testModel);
        FaqEmbedding far = saveEmbedding("먼 FAQ", farVector, testModel);

        List<FaqNearestMatch> result = repository.findNearest(queryVector, 2, testModel);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).embedding().getFaqId()).isEqualTo(close.getFaqId());
        assertThat(result.get(0).embedding().getFaq().getQuestion()).isEqualTo("가까운 FAQ"); // JOIN FETCH로 연관 로딩됨
        assertThat(result.get(0).distance()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(result.get(1).embedding().getFaqId()).isEqualTo(far.getFaqId());
        assertThat(result.get(0).distance()).isLessThan(result.get(1).distance()); // 반환 순서와 distance 오름차순이 같다
    }

    private FaqEmbedding saveEmbedding(String question, float[] vector, String modelName) {
        return saveEmbedding(question, vector, null, modelName);
    }

    private FaqEmbedding saveEmbedding(String question, float[] vector, float[] questionVector, String modelName) {
        Faq faq = faqRepository.save(Faq.builder()
                .category("BILLING")
                .question(question)
                .answer("답변")
                .build());
        return repository.save(FaqEmbedding.builder()
                .faqId(faq.getFaqId())
                .faq(faq)
                .embedding(vector)
                .embeddingQuestion(questionVector)
                .modelName(modelName)
                .faqVersion(faq.getVersion())
                .syncStatus(FaqEmbedding.SyncStatus.SYNCED)
                .build());
    }

    // embedding_question은 nullable이라 값이 없는 행과 있는 행이 섞인다.
    // vector 매핑이 두 번째 컬럼에서도 동작하는지(차원 보존, null 허용) 확인한다
    @Test
    @DisplayName("질문 벡터를 저장하면 1024차원 그대로 읽히고, 안 넣으면 null이다")
    void 질문_벡터를_저장하고_다시_읽는다() {
        String testModel = "test-question-vector-model";
        float[] docVector = new float[1024];
        float[] questionVector = new float[1024];
        for (int i = 0; i < 1024; i++) {
            docVector[i] = (i % 2 == 0) ? 1f : 0f;
            questionVector[i] = i / 1024f;
        }
        Long withQuestion = saveEmbedding("질문 벡터 있음", docVector, questionVector, testModel).getFaqId();
        Long withoutQuestion = saveEmbedding("질문 벡터 없음", docVector, testModel).getFaqId();
        // 영속성 컨텍스트 캐시가 아니라 DB에서 다시 읽도록 flush + clear
        entityManager.flush();
        entityManager.clear();

        FaqEmbedding saved = repository.findById(withQuestion).orElseThrow();
        FaqEmbedding empty = repository.findById(withoutQuestion).orElseThrow();

        assertThat(saved.getEmbeddingQuestion()).hasSize(1024).containsExactly(questionVector);
        assertThat(saved.getEmbedding()).containsExactly(docVector); // 기존 컬럼은 그대로
        assertThat(empty.getEmbeddingQuestion()).isNull();
    }

    @Test
    @DisplayName("topK 개수만큼만 반환한다")
    void topK_개수만큼만_반환한다() {
        float[] queryVector = new float[1024];

        List<FaqNearestMatch> result = repository.findNearest(queryVector, 1, MODEL);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("status가 ACTIVE가 아닌 FAQ는 제외한다")
    void 비활성_faq는_제외한다() {
        float[] queryVector = new float[1024];
        for (int i = 0; i < 1024; i++) {
            queryVector[i] = 0.001f + 0.0004f * (i % 13);
        }
        jdbcTemplate.update("UPDATE faqs SET status = 'HIDDEN' WHERE faq_id = 1");

        List<FaqNearestMatch> result = repository.findNearest(queryVector, 2, MODEL);

        assertThat(result).extracting(m -> m.embedding().getFaqId()).doesNotContain(1L);
    }

    @Test
    @DisplayName("syncStatus가 SYNCED가 아닌 임베딩은 제외한다")
    void sync_status가_synced가_아니면_제외한다() {
        float[] queryVector = new float[1024];
        for (int i = 0; i < 1024; i++) {
            queryVector[i] = 0.001f + 0.0004f * (i % 13);
        }

        // 시드 faqId=1의 embedding을 PENDING으로 바꿔서 findNearest 결과에서 빠지는지 확인
        jdbcTemplate.update("UPDATE faq_embeddings SET sync_status = 'PENDING' WHERE faq_id = 1");

        List<FaqNearestMatch> result = repository.findNearest(queryVector, 2, MODEL);

        assertThat(result).extracting(m -> m.embedding().getFaqId()).doesNotContain(1L);
    }

    @Test
    @DisplayName("faqVersion이 faqs.version과 다른(재임베딩 미완료) 임베딩은 제외한다")
    void 버전이_다르면_제외한다() {
        float[] queryVector = new float[1024];
        for (int i = 0; i < 1024; i++) {
            queryVector[i] = 0.001f + 0.0004f * (i % 13);
        }
        // FAQ 내용은 수정됐는데(version=2) 재임베딩은 아직 안 끝난 상황을 재현
        jdbcTemplate.update("UPDATE faqs SET version = 2 WHERE faq_id = 1");

        List<FaqNearestMatch> result = repository.findNearest(queryVector, 2, MODEL);

        assertThat(result).extracting(m -> m.embedding().getFaqId()).doesNotContain(1L);
    }

    @Test
    @DisplayName("요청한 modelName과 다른 모델로 만든 임베딩은 제외한다")
    void 모델이_다르면_제외한다() {
        float[] queryVector = new float[1024];
        for (int i = 0; i < 1024; i++) {
            queryVector[i] = 0.001f + 0.0004f * (i % 13);
        }

        List<FaqNearestMatch> result = repository.findNearest(queryVector, 2, "other-model");

        assertThat(result).isEmpty();
    }
}
