package com.telme.rag.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnswerContextConverterTest {

    private final AnswerContextConverter converter = new AnswerContextConverter();

    @Test
    @DisplayName("검색 결과를 번호가 붙은 컨텍스트로 만든다")
    void toContext_번호를_붙인다() {
        String context = converter.toContext(List.of(faq(1L, "질문1", "답변1", 1), faq(2L, "질문2", "답변2", 2)));

        assertThat(context).isEqualTo("""
                [1] Q: 질문1
                A: 답변1

                [2] Q: 질문2
                A: 답변2""");
    }

    @Test
    @DisplayName("검색 결과가 없으면 빈 컨텍스트를 만든다")
    void toContext_빈_목록() {
        assertThat(converter.toContext(List.of())).isEmpty();
    }

    @Test
    @DisplayName("검색 결과를 근거로 옮겨 담는다")
    void toSources_필드를_옮긴다() {
        List<AnswerSource> sources = converter.toSources(List.of(faq(7L, "질문", "답변", 3)));

        AnswerSource source = sources.getFirst();
        assertThat(source.faqId()).isEqualTo(7L);
        assertThat(source.titleSnapshot()).isEqualTo("질문");
        assertThat(source.faqVersion()).isEqualTo(1);
        assertThat(source.faqUpdatedAt()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(source.searchRank()).isEqualTo((short) 3);
    }

    @Test
    @DisplayName("제목이 200자를 넘으면 잘라서 담는다")
    void toSources_제목을_자른다() {
        String longQuestion = "가".repeat(250);

        AnswerSource source = converter.toSources(List.of(faq(1L, longQuestion, "답변", 1))).getFirst();

        assertThat(source.titleSnapshot()).hasSize(200);
    }

    @Test
    @DisplayName("검색 순위가 없으면 null로 담는다")
    void toSources_순위가_없으면_null() {
        AnswerSource source = converter.toSources(List.of(faq(1L, "질문", "답변", null))).getFirst();

        assertThat(source.searchRank()).isNull();
    }

    @Test
    @DisplayName("점수를 소수점 4자리로 맞춰 담는다")
    void toSources_점수_자릿수를_맞춘다() {
        FaqSearchResponse result = new FaqSearchResponse(
                1L, "BILLING", "질문", "답변", 0.912345, 1, LocalDate.of(2026, 9, 17), 1);

        AnswerSource source = converter.toSources(List.of(result)).getFirst();

        assertThat(source.score().scale()).isEqualTo(4);
        assertThat(source.score().toPlainString()).isEqualTo("0.9123");
    }

    private FaqSearchResponse faq(Long faqId, String question, String answer, Integer rank) {
        return new FaqSearchResponse(
                faqId, "BILLING", question, answer, 0.9, 1, LocalDate.of(2026, 9, 17), rank);
    }
}
