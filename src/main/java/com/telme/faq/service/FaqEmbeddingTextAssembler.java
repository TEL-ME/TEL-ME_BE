package com.telme.faq.service;

import com.telme.faq.entity.Faq;
import org.springframework.stereotype.Component;

// FAQ 한 건을 임베딩할 텍스트로 조립. 현재 기준안 "[카테고리] 질문 답변"
// 배치 적재와 upsert가 모두 이 클래스를 거친다
@Component
public class FaqEmbeddingTextAssembler {

    public String assemble(Faq faq) {
        return assemble(faq.getCategory(), faq.getQuestion(), faq.getAnswer());
    }

    public String assemble(String category, String question, String answer) {
        return "[" + category + "] " + question + " " + answer;
    }
}
