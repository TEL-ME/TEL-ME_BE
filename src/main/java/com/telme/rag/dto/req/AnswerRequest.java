package com.telme.rag.dto.req;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;
import java.util.Map;

import lombok.Builder;

@Builder
public record AnswerRequest(
        // null이면 호출 기록을 남기지 않음
        Long executionId,
        // 정제된 질의가 아니라 사용자 원문
        String userQuery,
        // 되묻기로 확정된 조건
        Map<String, String> conditions,
        // 비어 있으면 근거 없음으로 처리
        List<FaqSearchResponse> searchResults
) {

    public AnswerRequest {
        if (conditions == null) {
            conditions = Map.of();
        }
        if (searchResults == null) {
            searchResults = List.of();
        }
    }
}
