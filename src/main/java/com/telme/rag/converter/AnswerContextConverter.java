package com.telme.rag.converter;

import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;

@Component
public class AnswerContextConverter {

    // message_sources.title_snapshot 길이 제한
    private static final int TITLE_MAX_LENGTH = 200;

    // message_sources.score 자릿수
    private static final int SCORE_SCALE = 4;

    public String toContext(List<FaqSearchResponse> searchResults) {
        return IntStream.range(0, searchResults.size())
                .mapToObj(index -> toContextEntry(index + 1, searchResults.get(index)))
                .collect(Collectors.joining("\n\n"));
    }

    public List<AnswerSource> toSources(List<FaqSearchResponse> searchResults) {
        return searchResults.stream()
                .map(this::toSource)
                .toList();
    }

    private String toContextEntry(int number, FaqSearchResponse result) {
        return """
                [%d] Q: %s
                A: %s""".formatted(number, result.question(), result.answer());
    }

    private AnswerSource toSource(FaqSearchResponse result) {
        return AnswerSource.builder()
                .faqId(result.faqId())
                .titleSnapshot(truncateTitle(result.question()))
                .faqVersion(result.version())
                .faqUpdatedAt(result.updatedAt())
                .searchRank(toRank(result.searchRank()))
                .score(toScore(result.score()))
                .build();
    }

    // varchar 길이는 코드포인트 기준이라 length()로 판단하면 이모지가 불필요하게 잘린다
    private String truncateTitle(String question) {
        if (question == null || question.codePointCount(0, question.length()) <= TITLE_MAX_LENGTH) {
            return question;
        }
        return question.substring(0, question.offsetByCodePoints(0, TITLE_MAX_LENGTH));
    }

    private Short toRank(Integer searchRank) {
        return searchRank == null ? null : searchRank.shortValue();
    }

    private BigDecimal toScore(double score) {
        return BigDecimal.valueOf(score).setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }
}
