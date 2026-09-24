package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

import com.telme.faq.entity.FaqEmbedding;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.faq.repository.FaqRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// 로컬 DB에는 실제 FAQ가 적재돼 있어 재임베딩이 전량을 덮어쓴다 — @Transactional로 롤백시켜 원래 벡터를 지킨다.
// 청크 단위 커밋 자체는 같은 TransactionTemplate를 쓰는 FaqBatchLoaderTest가 검증한다.
// 애너테이션은 다른 FAQ 통합 테스트와 같게 맞춰 Spring 컨텍스트를 재사용한다
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "faq.search-test-api-enabled=true")
@Transactional
class FaqReembedderTest {

    private static final long SEED_FAQ_ID = 1L;

    @Autowired
    private FaqReembedder reembedder;
    @Autowired
    private FaqRepository faqRepository;
    @Autowired
    private FaqEmbeddingRepository faqEmbeddingRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private EmbeddingClient embeddingClient;

    @BeforeEach
    void stubEmbedding() {
        doAnswer(inv -> {
            List<String> texts = inv.getArgument(0);
            List<float[]> out = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                float[] vector = new float[1024];
                vector[0] = 0.42f;
                out.add(vector);
            }
            return out;
        }).when(embeddingClient).embedBatch(anyList());
    }

    @Test
    @DisplayName("faqs 전체를 새 벡터로 덮어쓴다")
    void 전량을_갱신한다() {
        long total = faqRepository.count();

        FaqReembedder.ReembedResult result = reembedder.reembedAll(50);
        faqEmbeddingRepository.flush();

        assertThat(result.total()).isEqualTo((int) total);
        assertThat(result.updated() + result.created()).isEqualTo((int) total);
        assertThat(faqEmbeddingRepository.findById(SEED_FAQ_ID).orElseThrow().getEmbedding()[0]).isEqualTo(0.42f);
    }

    @Test
    @DisplayName("임베딩이 없던 FAQ는 새 행으로 만든다")
    void 임베딩이_없으면_새로_만든다() {
        jdbcTemplate.update("DELETE FROM faq_embeddings WHERE faq_id = ?", SEED_FAQ_ID);

        FaqReembedder.ReembedResult result = reembedder.reembedAll(50);
        faqEmbeddingRepository.flush();

        assertThat(result.created()).isEqualTo(1);
        assertThat(faqEmbeddingRepository.findById(SEED_FAQ_ID)).isPresent();
    }

    @Test
    @DisplayName("faq_version을 faqs.version에 맞춘다")
    void faq_version이_faqs_version을_따라간다() {
        jdbcTemplate.update("UPDATE faqs SET version = 7 WHERE faq_id = ?", SEED_FAQ_ID);

        reembedder.reembedAll(50);
        faqEmbeddingRepository.flush();

        assertThat(faqEmbeddingRepository.findById(SEED_FAQ_ID).orElseThrow().getFaqVersion()).isEqualTo(7);
    }

    @Test
    @DisplayName("sync_status가 FAILED로 남아 있던 행도 SYNCED로 되돌린다")
    void 실패_상태를_정상으로_되돌린다() {
        jdbcTemplate.update("UPDATE faq_embeddings SET sync_status = 'FAILED' WHERE faq_id = ?", SEED_FAQ_ID);

        reembedder.reembedAll(50);
        faqEmbeddingRepository.flush();

        FaqEmbedding embedding = faqEmbeddingRepository.findById(SEED_FAQ_ID).orElseThrow();
        assertThat(embedding.getSyncStatus()).isEqualTo(FaqEmbedding.SyncStatus.SYNCED);
    }

    @Test
    @DisplayName("batchSize가 1~50을 벗어나면 거부한다")
    void 잘못된_batchSize는_거부한다() {
        assertThatThrownBy(() -> reembedder.reembedAll(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reembedder.reembedAll(51)).isInstanceOf(IllegalArgumentException.class);
    }
}
