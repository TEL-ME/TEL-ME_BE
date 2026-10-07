package com.telme.consult.service;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;

// 비교 질문의 양쪽 근거를 확인한 뒤 답변 생성에 전달할 FAQ만 고른다.
public interface ComparisonEvidenceResolver {
    boolean applies(String question);

    List<FaqSearchResponse> resolve(
            Long executionId, String question, List<FaqSearchResponse> originalResults,
            ComparisonSearcher searcher);

    // 검증된 FAQ 인용문을 그대로 표시할 수 있는 경우 답변까지 반환한다.
    default Resolution resolveDetailed(
            Long executionId, String question, List<FaqSearchResponse> originalResults,
            ComparisonSearcher searcher) {
        return new Resolution(resolve(executionId, question, originalResults, searcher), null);
    }

    default Resolution resolveDetailed(
            Long executionId, Long consultRequestId, String question,
            List<FaqSearchResponse> originalResults, ComparisonSearcher searcher) {
        return resolveDetailed(executionId, question, originalResults, searcher);
    }

    record Resolution(List<FaqSearchResponse> sources, String answer) {
        public Resolution {
            sources = List.copyOf(sources);
            if (sources.isEmpty() && answer != null) {
                throw new IllegalArgumentException("근거 없는 비교 답변은 허용되지 않습니다.");
            }
        }
    }

    @FunctionalInterface
    interface ComparisonSearcher {
        List<FaqSearchResponse> search(String query, String kind);
    }

    static ComparisonEvidenceResolver passthrough() {
        return new ComparisonEvidenceResolver() {
            @Override
            public boolean applies(String question) {
                return false;
            }

            @Override
            public List<FaqSearchResponse> resolve(
                    Long executionId, String question, List<FaqSearchResponse> originalResults,
                    ComparisonSearcher searcher) {
                return originalResults;
            }
        };
    }
}
