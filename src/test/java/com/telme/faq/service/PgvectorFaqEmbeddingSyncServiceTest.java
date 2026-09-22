package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.telme.faq.entity.FaqEmbedding;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.global.common.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

// dev-migration V2 시드의 faqId=1(version=1, faq_embeddings 있음) 기준. 트랜잭션 롤백으로 시드에 영향 없음.
// 애너테이션은 다른 FAQ 통합 테스트와 같게 맞춰 Spring 컨텍스트를 재사용한다
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "faq.search-test-api-enabled=true")
@Transactional
class PgvectorFaqEmbeddingSyncServiceTest {

    private static final long SEED_FAQ_ID = 1L;

    @Autowired
    private PgvectorFaqEmbeddingSyncService service;
    @Autowired
    private FaqEmbeddingRepository embeddingRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private EmbeddingClient embeddingClient;

    private static float[] vector(float fill) {
        float[] v = new float[1024];
        java.util.Arrays.fill(v, fill);
        return v;
    }

    @Test
    @DisplayName("upsert는 기존 임베딩 행을 새 벡터로 덮어쓴다")
    void upsert는_기존_행을_갱신한다() {
        when(embeddingClient.embed(anyString())).thenReturn(vector(0.5f));

        service.upsert(SEED_FAQ_ID);
        embeddingRepository.flush();

        FaqEmbedding saved = embeddingRepository.findById(SEED_FAQ_ID).orElseThrow();
        assertThat(saved.getEmbedding()[0]).isEqualTo(0.5f);
        assertThat(saved.getModelName()).isEqualTo("bge-m3");
        assertThat(saved.getSyncStatus()).isEqualTo(FaqEmbedding.SyncStatus.SYNCED);
        assertThat(saved.getFaqVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("FAQ version이 올라간 뒤 upsert하면 faq_version이 따라 올라간다")
    void faq_version이_faqs_version을_따라간다() {
        when(embeddingClient.embed(anyString())).thenReturn(vector(0.1f));
        jdbcTemplate.update("UPDATE faqs SET version = 2, answer = '수정된 답변' WHERE faq_id = ?", SEED_FAQ_ID);

        service.upsert(SEED_FAQ_ID);
        embeddingRepository.flush();

        Integer faqVersion = jdbcTemplate.queryForObject(
                "SELECT faq_version FROM faq_embeddings WHERE faq_id = ?", Integer.class, SEED_FAQ_ID);
        assertThat(faqVersion).isEqualTo(2);
    }

    @Test
    @DisplayName("임베딩이 없던 FAQ에 upsert하면 새 행이 생긴다")
    void 임베딩이_없으면_새로_만든다() {
        when(embeddingClient.embed(anyString())).thenReturn(vector(0.2f));
        jdbcTemplate.update("DELETE FROM faq_embeddings WHERE faq_id = ?", SEED_FAQ_ID);

        service.upsert(SEED_FAQ_ID);
        embeddingRepository.flush();

        assertThat(embeddingRepository.findById(SEED_FAQ_ID)).isPresent();
    }

    @Test
    @DisplayName("없는 faqId면 FAQ_NOT_FOUND")
    void 없는_faq면_예외() {
        assertThatThrownBy(() -> service.upsert(999_999L))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(FaqErrorCode.FAQ_NOT_FOUND);
    }

    @Test
    @DisplayName("임베딩 호출이 실패하면 기존 행이 그대로 남는다")
    void 임베딩_실패면_기존_행_유지() {
        when(embeddingClient.embed(anyString()))
                .thenThrow(new GeneralException(FaqErrorCode.EMBEDDING_REQUEST_FAILED));
        FaqEmbedding before = embeddingRepository.findById(SEED_FAQ_ID).orElseThrow();
        float first = before.getEmbedding()[0];

        assertThatThrownBy(() -> service.upsert(SEED_FAQ_ID)).isInstanceOf(GeneralException.class);

        FaqEmbedding after = embeddingRepository.findById(SEED_FAQ_ID).orElseThrow();
        assertThat(after.getEmbedding()[0]).isEqualTo(first);
    }

    @Test
    @DisplayName("delete는 행을 지우고, 다시 호출해도 예외가 없다")
    void delete는_멱등() {
        service.delete(SEED_FAQ_ID);
        embeddingRepository.flush();
        assertThat(embeddingRepository.findById(SEED_FAQ_ID)).isEmpty();

        service.delete(SEED_FAQ_ID);
        assertThat(embeddingRepository.findById(SEED_FAQ_ID)).isEmpty();
    }
}
