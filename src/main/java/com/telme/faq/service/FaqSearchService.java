package com.telme.faq.service;

import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;

public interface FaqSearchService {

    /**
     * 각 검색 결과에 임계값을 적용하며, 임계값 이상인 결과만 반환한다.
     * 호출하는 쪽은 score로 직접 재판정할 필요가 없다.
     */
    List<FaqSearchResponse> search(FaqSearchRequest request);

    // 일반 답변 임계값을 적용하지 않고 가까운 FAQ 후보를 반환한다.
    // 호출자는 후보가 질문의 근거인지 별도로 확인한 뒤 답변 생성에 사용해야 한다.
    default List<FaqSearchResponse> searchCandidates(FaqSearchRequest request) {
        return search(request);
    }
}
