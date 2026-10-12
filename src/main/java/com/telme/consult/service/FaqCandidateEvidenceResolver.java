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

    /**
     * 복합 질문에서 라우터가 검색용으로 정규화한 문장과 사람이 읽는 원문이 다를 때 쓴다.
     * 모델이 후보 FAQ를 명시적으로 선택했고 용어 검증도 통과했으면, 인용문 형식 오류만으로
     * 이미 저장된 FAQ 답변을 버리지 않도록 허용한다.
     */
    default FaqSearchResponse resolve(Long executionId, Long consultRequestId, String question,
            List<FaqSearchResponse> candidates, boolean allowQuoteRepairFailure) {
        return resolve(executionId, consultRequestId, question, candidates);
    }

    static FaqCandidateEvidenceResolver disabled() {
        return (executionId, question, candidates) -> null;
    }
}
