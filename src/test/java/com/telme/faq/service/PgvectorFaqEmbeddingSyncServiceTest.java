package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.faq.entity.FaqEmbedding;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.global.common.exception.GeneralException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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

    // 텍스트마다 다른 벡터를 돌려준다.
    // 전부 같은 값이면 본문 벡터와 질문 벡터가 실제로 다른 텍스트에서 나왔는지를 단언할 수 없다
    private static float[] vectorOf(String text) {
        float[] v = new float[1024];
        int seed = text.hashCode();
        for (int i = 0; i < v.length; i++) {
            v[i] = Math.floorMod(seed + i * 31, 97) / 97f;
        }
        return v;
    }

    @Test
    @DisplayName("upsert는 기존 임베딩 행을 새 벡터로 덮어쓴다")
    void upsert는_기존_행을_갱신한다() {
        when(embeddingClient.embed(anyString())).thenReturn(vector(0.5f));
        // 기존 값을 다른 모델명으로 바꿔둔다
        // 시드와 기대값이 같으면 모델명 갱신이 빠져도 통과한다
        jdbcTemplate.update("UPDATE faq_embeddings SET model_name = 'stale-model' WHERE faq_id = ?", SEED_FAQ_ID);

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
    @DisplayName("upsert는 본문, 질문 두 벡터를 함께 저장하고 둘은 서로 다르다")
    void 두_벡터를_함께_저장한다() {
        when(embeddingClient.embed(anyString())).thenAnswer(inv -> vectorOf(inv.getArgument(0)));

        service.upsert(SEED_FAQ_ID);
        embeddingRepository.flush();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT (embedding IS NOT NULL) AS qa, (embedding_question IS NOT NULL) AS qo, "
                        + "(embedding = embedding_question) AS same FROM faq_embeddings WHERE faq_id = ?",
                SEED_FAQ_ID);

        assertThat(row.get("qa")).isEqualTo(true);
        assertThat(row.get("qo")).isEqualTo(true);
        assertThat(row.get("same")).isEqualTo(false); // 질문만 구성이라 본문 벡터와 달라야 한다
        verify(embeddingClient, times(2)).embed(anyString());
    }

    @Test
    @DisplayName("질문 벡터 임베딩만 실패해도 한 트랜잭션으로 되돌아가 기존 행이 남는다")
    void 질문_임베딩_실패면_기존_행_유지() {
        // 두 번째 호출 = 질문 벡터.
        // 여기서 터뜨려 본문 벡터만 갱신되는 일이 없는지 본다
        AtomicInteger calls = new AtomicInteger();
        when(embeddingClient.embed(anyString())).thenAnswer(inv -> {
            if (calls.incrementAndGet() == 2) {
                throw new GeneralException(FaqErrorCode.EMBEDDING_REQUEST_FAILED);
            }
            return vectorOf(inv.getArgument(0));
        });
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
