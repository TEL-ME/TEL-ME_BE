package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.entity.Faq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaqEmbeddingTextAssemblerTest {

    private final FaqEmbeddingTextAssembler assembler = new FaqEmbeddingTextAssembler();

    @Test
    @DisplayName("기준안 3 - [카테고리] 질문 답변")
    void 카테고리_질문_답변_순으로_조립한다() {
        assertThat(assembler.assemble("USIM", "유심 얼마예요?", "7,700원입니다."))
                .isEqualTo("[USIM] 유심 얼마예요? 7,700원입니다.");
    }

    @Test
    @DisplayName("엔티티를 넘겨도 같은 문자열")
    void 엔티티와_문자열_오버로드가_같은_결과를_낸다() {
        Faq faq = Faq.builder().category("USIM").question("유심 얼마예요?").answer("7,700원입니다.").build();
        assertThat(assembler.assemble(faq)).isEqualTo(assembler.assemble("USIM", "유심 얼마예요?", "7,700원입니다."));
    }
}
