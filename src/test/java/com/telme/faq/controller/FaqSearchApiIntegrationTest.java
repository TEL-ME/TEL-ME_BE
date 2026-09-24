package com.telme.faq.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqEmbedding;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.faq.repository.FaqRepository;
import com.telme.faq.service.EmbeddingClient;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// HTTP~Postgres 전 구간 반복 호출 회귀 테스트
// Ollama는 EmbeddingClient 목킹으로 대체
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {"faq.search-test-api-enabled=true", "embedding.model=test-search-api-model"})
class FaqSearchApiIntegrationTest {

    private static final int REPEAT_COUNT = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private FaqEmbeddingRepository faqEmbeddingRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private EmbeddingClient embeddingClient;

    @Test
    @DisplayName("같은 요청을 여러 번 반복해도 매번 200으로 응답한다")
    void 반복_호출해도_항상_정상_응답한다() throws Exception {
        // 시드나 로컬에 적재된 다른 FAQ와 안 섞이도록 이 테스트 전용 모델(embedding.model)로 격리한 FAQ를 직접 만든다
        float[] queryVector = new float[1024];
        for (int i = 0; i < 1024; i++) {
            queryVector[i] = 0.001f + 0.0004f * (i % 13);
        }
        when(embeddingClient.embed(anyString())).thenReturn(queryVector);
        Faq faq = faqRepository.save(Faq.builder()
                .category("USIM")
                .question("유심 재발급 비용이 얼마인가요?")
                .answer("답변")
                .build());
        // created_at/updated_at은 DB가 채우는 값이라(insertable=false), save() 직후 메모리엔 null -> 새로 읽어와야 한다
        entityManager.flush();
        entityManager.refresh(faq);
        faqEmbeddingRepository.save(FaqEmbedding.builder()
                .faqId(faq.getFaqId())
                .faq(faq)
                .embedding(queryVector) // 쿼리와 완전히 일치 -> distance 0, threshold 무조건 통과
                .modelName("test-search-api-model")
                .faqVersion(faq.getVersion())
                .syncStatus(FaqEmbedding.SyncStatus.SYNCED)
                .build());

        for (int i = 0; i < REPEAT_COUNT; i++) {
            mockMvc.perform(get("/api/v1/faq/search").param("query", "유심 재발급 비용이 얼마인가요?"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.result[0].faqId").value(faq.getFaqId()));
        }
    }
}
