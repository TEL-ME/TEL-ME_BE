package com.telme.faq.service;

import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqGrayZoneSearchResponse;
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

    // 회색 구간(search.gray-zone)으로 나눈 벡터별 결과. 판정과 합치기는 호출하는 쪽이 한다.
    // 회색 구간을 지원하지 않는 구현은 기존 검색 결과를 모두 통과로 돌려준다
    default FaqGrayZoneSearchResponse searchGrayZone(FaqSearchRequest request) {
        return FaqGrayZoneSearchResponse.allPassed(search(request));
    }

    // 회색 구간 판정이 끝난 뒤 searchGrayZone이 남긴 점수 기록에 판정 여부와 최종 근거 여부를 채운다.
    // 기록 번호가 없거나 점수를 기록하지 않는 구현은 아무것도 하지 않는다
    default void recordGrayZoneOutcome(Long searchScoreId, boolean judged, boolean finalPassed) {
    }
}
