package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.entity.QueryRouting.Intent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleBasedRoutingFallbackTest {

    private RuleBasedRoutingFallback fallback;

    @BeforeEach
    void setUp() {
        fallback = new RuleBasedRoutingFallback();
    }

    @Test
    @DisplayName("FAQ 키워드(요금제 등)가 포함된 경우 FAQ 의도로 분류한다")
    void classify_faq_keyword() {
        LlmRoutingPayload result = fallback.classify("로밍 요금제 가입 방법 알려주세요");

        assertThat(result.intent()).isEqualTo(Intent.FAQ);
        assertThat(result.confidence().doubleValue()).isGreaterThanOrEqualTo(0.80);
        assertThat(result.subQueries()).hasSize(1);
        assertThat(result.subQueries().get(0).intent()).isEqualTo(Intent.FAQ);
    }

    @Test
    @DisplayName("매장 키워드(대리점, 위치 등)가 포함된 경우 STORE 의도로 분류한다")
    void classify_store_keyword() {
        LlmRoutingPayload result = fallback.classify("가장 가까운 직영점 위치 어디야?");

        assertThat(result.intent()).isEqualTo(Intent.STORE);
        assertThat(result.confidence().doubleValue()).isGreaterThanOrEqualTo(0.80);
        assertThat(result.subQueries()).hasSize(1);
        assertThat(result.subQueries().get(0).intent()).isEqualTo(Intent.STORE);
    }

    @Test
    @DisplayName("FAQ 키워드와 매장 키워드가 모두 포함된 경우 BOTH로 분류하고 서브질의 2개를 생성한다")
    void classify_both_keywords() {
        LlmRoutingPayload result = fallback.classify("로밍 요금제 변경하고 싶은데 강남역 대리점 찾아줘");

        assertThat(result.intent()).isEqualTo(Intent.BOTH);
        assertThat(result.confidence().doubleValue()).isEqualTo(0.70);
        assertThat(result.subQueries()).hasSize(2);
        assertThat(result.subQueries().get(0).intent()).isEqualTo(Intent.FAQ);
        assertThat(result.subQueries().get(1).intent()).isEqualTo(Intent.STORE);
    }

    @Test
    @DisplayName("통신 업무와 무관한 질문은 UNKNOWN 의도로 분류한다")
    void classify_unknown() {
        LlmRoutingPayload result = fallback.classify("오늘 저녁 메뉴 추천해줘");

        assertThat(result.intent()).isEqualTo(Intent.UNKNOWN);
        assertThat(result.confidence().doubleValue()).isEqualTo(0.50);
        assertThat(result.subQueries()).isEmpty();
    }

    @Test
    @DisplayName("유심 재발급 관련 키워드가 있으면 serviceType 조건으로 USIM_REISSUE를 추출한다")
    void extract_conditions_serviceType() {
        LlmRoutingPayload result = fallback.classify("유심 재발급 받으려면 대리점 어디로 가야 하나요?");

        assertThat(result.intent()).isEqualTo(Intent.BOTH);
        assertThat(result.extractedConditions()).containsEntry("serviceType", "USIM_REISSUE");
    }
}
