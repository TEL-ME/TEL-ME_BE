package com.telme.faq.service;

import com.telme.faq.config.FaqEmbeddingTextProperties;
import com.telme.faq.entity.Faq;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// FAQ 한 건을 임베딩할 텍스트로 조립
// 방식은 faq.embedding-text.variant 설정이 정한다
// (배치 적재·재임베딩·upsert가 모두 이 클래스를 거침)
@Component
@RequiredArgsConstructor
public class FaqEmbeddingTextAssembler {

    private final FaqEmbeddingTextProperties properties;

    public String assemble(Faq faq) {
        return assemble(faq.getCategory(), faq.getQuestion(), faq.getAnswer());
    }

    public String assemble(String category, String question, String answer) {
        return properties.variant().assemble(category, question, answer);
    }

    public FaqEmbeddingTextVariant variant() {
        return properties.variant();
    }
}
