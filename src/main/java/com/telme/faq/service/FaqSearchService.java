package com.telme.faq.service;

import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;

public interface FaqSearchService {

    /**
     * 임계값 판정은 검색 쪽에서 처리함 — 최고 유사도가 임계값 미만이면 빈 리스트를 반환
     * 호출하는 쪽은 결과가 비었는지만 보면 되고, score로 직접 재판정할 필요 없음
     */
    List<FaqSearchResponse> search(FaqSearchRequest request);
}
