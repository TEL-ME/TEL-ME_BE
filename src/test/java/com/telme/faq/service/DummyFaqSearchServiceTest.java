package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 더미 구현체가 삭제될 때 이 테스트도 같이 지워야 함
class DummyFaqSearchServiceTest {

    private final DummyFaqSearchService service = new DummyFaqSearchService();

    @Test
    @DisplayName("요금제 키워드만 있으면 요금제 FAQ만 반환한다")
    void 요금제_키워드만_있으면_요금제_FAQ만_반환() {
        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("요금제 바꾸고 싶어요", 3));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).faqId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("관련 키워드가 없으면 빈 목록을 반환한다")
    void 관련_키워드_없으면_빈_목록() {
        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("오늘 날씨 어때", 3));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("topK보다 매칭 결과가 많으면 topK만큼만 반환한다")
    void topK_만큼만_반환() {
        List<FaqSearchResponse> result = service.search(new FaqSearchRequest("요금제 유심 다 궁금해요", 1));

        assertThat(result).hasSize(1);
    }
}
