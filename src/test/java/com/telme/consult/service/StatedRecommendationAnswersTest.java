package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatedRecommendationAnswersTest {

    private static final String AGE = "age_group";
    private static final String DATA = "monthly_data_usage";

    @Test
    void 단위를_붙여_쓴다고_말한_사용량을_읽는다() {
        assertThat(StatedRecommendationAnswers.of("50GB 쓰는데 요금제 추천해줘")).containsOnly(Map.entry(DATA, "50GB"));
        assertThat(StatedRecommendationAnswers.of("한 달에 100기가 정도 써요 요금제 추천해줘")).containsOnly(Map.entry(DATA, "100GB"));
        assertThat(StatedRecommendationAnswers.of("500메가 쓰는데 요금제 추천해줘")).containsOnly(Map.entry(DATA, "500MB"));
        assertThat(StatedRecommendationAnswers.of("하루에 2기가 쓰는데 요금제 추천해줘")).containsOnly(Map.entry(DATA, "60GB"));
        assertThat(StatedRecommendationAnswers.of("50GB 이상 쓰는 사람한테 맞는 요금제 추천해줘")).containsOnly(Map.entry(DATA, "50GB"));
    }

    @Test
    void 나이와_나이대와_구분을_말하면_나이대_선택지_글자로_읽는다() {
        assertThat(StatedRecommendationAnswers.of("저 17살인데 요금제 추천해줘")).containsOnly(Map.entry(AGE, "만 14~18세"));
        assertThat(StatedRecommendationAnswers.of("30대 직장인인데 요금제 추천해줘")).containsOnly(Map.entry(AGE, "만 19~64세"));
        assertThat(StatedRecommendationAnswers.of("만 30세 요금제 추천해줘")).containsOnly(Map.entry(AGE, "만 19~64세"));
        assertThat(StatedRecommendationAnswers.of("시니어 요금제 추천해줘")).containsOnly(Map.entry(AGE, "만 65세 이상"));
    }

    @Test
    void 나이와_사용량을_함께_말하면_둘_다_읽는다() {
        assertThat(StatedRecommendationAnswers.of("저는 17살이고 한 달에 50GB 써요. 요금제 추천해줘"))
                .containsOnly(Map.entry(AGE, "만 14~18세"), Map.entry(DATA, "50GB"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "요금제 추천해줘", "저한테 맞는 요금제가 뭐예요?",
            // 사용량이 아닌 숫자: 금액, 개수, 기간, 사람 수, 망 이름
            "5만원 이하 요금제 추천해줘", "3만원대 요금제 추천해줘", "5G 요금제 추천해줘", "가족 3명이 쓸 요금제 추천해줘",
            "1년 쓸 건데 요금제 추천해줘", "6개월 쓸 요금제 추천해줘", "10명이 쓰는 회사용 요금제 추천해줘", "50 정도 쓰는데 요금제 추천해줘",
            // 요금제 크기나 종류를 말한 것이지 사용량이 아니다
            "50GB 요금제 추천해줘", "데이터 무제한 요금제 추천해줘",
            // 값이 둘이거나 하루와 한 달이 섞여 어느 쪽인지 알 수 없다
            "10GB 쓰다가 요즘 30GB 쓰는데 요금제 추천해줘", "하루 2GB 쓰고 한 달에 50GB 써요 요금제 추천해줘",
            // 나이가 한 구분에 들어가지 않거나 나이 표현이 아니다
            "10대인데 요금제 추천해줘", "60대인데 요금제 추천해줘", "휴대폰 2대 쓰는데 요금제 추천해줘", "2세대 폰이라 5G 요금제 추천해줘"})
    void 분명하지_않은_표현은_읽지_않는다(String question) {
        assertThat(StatedRecommendationAnswers.of(question)).isEmpty();
    }

    @Test
    void 비어_있으면_읽지_않는다() {
        assertThat(StatedRecommendationAnswers.of(null)).isEmpty();
        assertThat(StatedRecommendationAnswers.of("  ")).isEmpty();
    }
}
