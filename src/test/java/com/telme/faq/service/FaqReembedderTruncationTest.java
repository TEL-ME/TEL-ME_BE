package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.config.FaqEmbeddingTextProperties;
import com.telme.faq.config.FaqReembedProperties;
import com.telme.faq.entity.Faq;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.faq.repository.FaqRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.support.TransactionTemplate;

// answerTruncated 카운트만 보는 단위 테스트
class FaqReembedderTruncationTest {

    private static final int LIMIT = FaqEmbeddingTextVariant.ANSWER_HEAD_LIMIT;

    private FaqReembedder reembedder(FaqEmbeddingTextVariant variant, List<Faq> faqs) {
        FaqRepository faqRepository = mock(FaqRepository.class);
        when(faqRepository.count()).thenReturn((long) faqs.size());
        when(faqRepository.findAll(any(Pageable.class))).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            Page<Faq> page = pageable.getPageNumber() == 0
                    ? new PageImpl<>(faqs) : new PageImpl<>(List.of());
            return page;
        });

        FaqEmbeddingRepository embeddingRepository = mock(FaqEmbeddingRepository.class);
        when(embeddingRepository.findAllById(anyList())).thenReturn(List.of());

        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        doAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            List<float[]> vectors = new ArrayList<>();
            for (int i = 0; i < texts.size(); i++) {
                vectors.add(new float[1024]);
            }
            return vectors;
        }).when(embeddingClient).embedBatch(anyList());

        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Consumer.class).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        return new FaqReembedder(
                faqRepository,
                embeddingRepository,
                embeddingClient,
                new FaqEmbeddingTextAssembler(new FaqEmbeddingTextProperties(variant)),
                new EmbeddingProperties("bge-m3", 1024, null, null, null),
                new FaqReembedProperties(true, 50, false),
                transactionTemplate);
    }

    private static Faq faq(long id, int answerLength) {
        return Faq.builder().faqId(id).category("USIM").question("질문").answer("가".repeat(answerLength)).build();
    }

    @Test
    @DisplayName("Q_A_HEAD200이면 답변이 200자를 넘는 건수만 센다")
    void 잘린_답변_건수를_센다() {
        List<Faq> faqs = List.of(faq(1, LIMIT - 1), faq(2, LIMIT), faq(3, LIMIT + 1), faq(4, LIMIT + 500));

        FaqReembedder.ReembedResult result = reembedder(FaqEmbeddingTextVariant.Q_A_HEAD200, faqs).reembedAll(50);

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.answerTruncated()).isEqualTo(2);   // 201자, 700자만 잘림
    }

    @Test
    @DisplayName("자르지 않는 구성에서는 답변이 길어도 0으로 센다")
    void 다른_구성에서는_세지_않는다() {
        List<Faq> faqs = List.of(faq(1, LIMIT + 500), faq(2, LIMIT + 500));

        assertThat(reembedder(FaqEmbeddingTextVariant.Q_A, faqs).reembedAll(50).answerTruncated()).isZero();
        assertThat(reembedder(FaqEmbeddingTextVariant.CATEGORY_Q_A, faqs).reembedAll(50).answerTruncated()).isZero();
    }
}
