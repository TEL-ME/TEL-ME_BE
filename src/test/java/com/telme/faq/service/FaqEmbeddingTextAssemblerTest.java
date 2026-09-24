package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.config.FaqEmbeddingTextProperties;
import com.telme.faq.entity.Faq;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FaqEmbeddingTextAssemblerTest {

    private static final String CATEGORY = "USIM";
    private static final String QUESTION = "유심 얼마예요?";
    private static final String ANSWER = "7,700원입니다.";

    private static FaqEmbeddingTextAssembler assembler(FaqEmbeddingTextVariant variant) {
        return new FaqEmbeddingTextAssembler(new FaqEmbeddingTextProperties(variant));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', value = {
            "QUESTION_ONLY | 유심 얼마예요?",
            "Q_A           | 유심 얼마예요? 7,700원입니다.",
            "CATEGORY_Q_A  | [USIM] 유심 얼마예요? 7,700원입니다.",
            "Q_A_HEAD200   | 유심 얼마예요? 7,700원입니다."
    })
    @DisplayName("4안이 각각 정해진 형식으로 조립한다")
    void 안마다_형식이_다르다(FaqEmbeddingTextVariant variant, String expected) {
        assertThat(assembler(variant).assemble(CATEGORY, QUESTION, ANSWER)).isEqualTo(expected);
    }

    @Test
    @DisplayName("엔티티를 넘겨도 같은 문자열")
    void 엔티티와_문자열_오버로드가_같은_결과를_낸다() {
        FaqEmbeddingTextAssembler assembler = assembler(FaqEmbeddingTextVariant.CATEGORY_Q_A);
        Faq faq = Faq.builder().category(CATEGORY).question(QUESTION).answer(ANSWER).build();

        assertThat(assembler.assemble(faq)).isEqualTo(assembler.assemble(CATEGORY, QUESTION, ANSWER));
    }

    @ParameterizedTest(name = "답변 {0}자 → 조립 후 {1}자")
    @CsvSource({"199, 199", "200, 200", "201, 200"})
    @DisplayName("Q_A_HEAD200은 200자까지만 남긴다")
    void 답변을_200자에서_자른다(int answerLength, int expectedAnswerLength) {
        String answer = "가".repeat(answerLength);
        String assembled = assembler(FaqEmbeddingTextVariant.Q_A_HEAD200).assemble(CATEGORY, QUESTION, answer);

        assertThat(assembled).isEqualTo(QUESTION + " " + "가".repeat(expectedAnswerLength));
    }

    @Test
    @DisplayName("Q_A_HEAD200은 서로게이트 쌍을 쪼개지 않는다")
    void 이모지_경계에서_깨지지_않는다() {
        String answer = "😀".repeat(201); // char 402개, 코드포인트 201개
        String assembled = assembler(FaqEmbeddingTextVariant.Q_A_HEAD200).assemble(CATEGORY, QUESTION, answer);
        String assembledAnswer = assembled.substring(QUESTION.length() + 1);

        assertThat(assembledAnswer.codePointCount(0, assembledAnswer.length())).isEqualTo(200);
        assertThat(assembledAnswer).isEqualTo("😀".repeat(200));
    }
}
