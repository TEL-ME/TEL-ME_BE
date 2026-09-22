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
}
