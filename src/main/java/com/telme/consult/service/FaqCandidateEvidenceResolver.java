package com.telme.consult.service;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;

// 임계값 검색에서 빠진 FAQ를 후보 중에서 근거 확인 후 복구한다.
public interface FaqCandidateEvidenceResolver {
    FaqSearchResponse resolve(Long executionId, String question, List<FaqSearchResponse> candidates);

    default FaqSearchResponse resolve(Long executionId, Long consultRequestId, String question,
            List<FaqSearchResponse> candidates) {
        return resolve(executionId, question, candidates);
    }

    static FaqCandidateEvidenceResolver disabled() {
        return (executionId, question, candidates) -> null;
    }
}
