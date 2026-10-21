package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StatedRecommendationAnswersTest {

    private static final String AGE = "age_group";
    private static final String DATA = "monthly_data_usage";
    private static final String PRICE = "price_request";

    @Test
    void 단위를_붙여_쓴다고_말한_사용량을_읽는다() {
        assertThat(StatedRecommendationAnswers.of("50GB 쓰는데 요금제 추천해줘")).containsOnly(Map.entry(DATA, "50GB"));
        assertThat(StatedRecommendationAnswers.of("한 달에 100기가 정도 써요 요금제 추천해줘")).containsOnly(Map.entry(DATA, "100GB"));
        assertThat(StatedRecommendationAnswers.of("500메가 쓰는데 요금제 추천해줘")).containsOnly(Map.entry(DATA, "500MB"));
        assertThat(StatedRecommendationAnswers.of("하루에 2기가 쓰는데 요금제 추천해줘")).containsOnly(Map.entry(DATA, "60GB"));
        assertThat(StatedRecommendationAnswers.of("매달 30GB 쓰는데 요금제 추천해줘")).containsOnly(Map.entry(DATA, "30GB"));
        assertThat(StatedRecommendationAnswers.of("아이폰 쓰는데 한 달에 50GB 정도 써요 요금제 추천해줘")).containsOnly(Map.entry(DATA, "50GB"));
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

    @Test
    void 더_싼_요금제를_찾는_말은_추천_사용량_기준으로_읽는다() {
        for (String question : new String[] {"더 싼 요금제 없어?", "더 저렴한 요금제 알려줘", "좀 더 싸게 쓸 수 없을까", "조금 싼 걸로 추천해줘"}) {
            assertThat(StatedRecommendationAnswers.of(question)).containsOnly(Map.entry(PRICE, "cheaper"));
        }
    }

    @Test
    void 사용량과_상관없이_싼_요금제를_찾는_말은_싼_순서로_읽는다() {
        assertThat(StatedRecommendationAnswers.of("얼마나 쓰는지에 상관없이 더 싼 요금제 없어?"))
                .containsOnly(Map.entry(PRICE, "cheapest"));
        assertThat(StatedRecommendationAnswers.of("사용량 말고 더 저렴한 거 알려줘")).containsOnly(Map.entry(PRICE, "cheapest"));
    }

    @Test
    void 최소_사용량을_말하는_적어도는_사용량과_상관없다는_말로_읽지_않는다() {
        assertThat(StatedRecommendationAnswers.of("적어도 10기가는 써야 하는데 더 싼 거 없어요?"))
                .containsOnly(Map.entry(PRICE, "cheaper"), Map.entry(DATA, "10GB"));
    }

    @Test
    void 가장_싼_요금제를_묻거나_싸다는_말이_없으면_읽지_않는다() {
        assertThat(StatedRecommendationAnswers.of("가장 저렴한 요금제 추천해줘")).isEmpty();
        assertThat(StatedRecommendationAnswers.of("요금제 추천해줘")).isEmpty();
        assertThat(StatedRecommendationAnswers.of("그 요금제로 가입하고 싶어")).isEmpty();
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
            "10대인데 요금제 추천해줘", "60대인데 요금제 추천해줘", "휴대폰 2대 쓰는데 요금제 추천해줘", "2세대 폰이라 5G 요금제 추천해줘",
            // 현재 요금제 크기를 말한 것이지 사용량이 아니다
            "지금 10GB 요금제 쓰고 있는데 추천해줘", "10GB 쓰는 요금제인데 추천해줘", "10GB짜리 쓰는데 추천해줘",
            // 한 달이 아닌 기간이나 지난 때
            "일주일에 5GB 써요 요금제 추천해줘", "3일 동안 10GB 썼어요 요금제 추천해줘", "지난달에 50GB 사용했어요 요금제 추천해줘",
            // 정확한 양이 아니다(다 써서 모자람, 안 씀, 이상·이하)
            "10GB 다 써서 모자라요 요금제 추천해줘", "10GB도 안 써요 요금제 추천해줘", "50GB 이상 쓰는 사람한테 맞는 요금제 추천해줘",
            "1GB 이하 쓰는데 가장 싼 요금제 추천해줘",
            // 본인 이야기가 아니다
            "친구는 50GB 쓴대 요금제 추천해줘", "친구는 50GB 써요 요금제 추천해줘", "어머니가 70세인데 추천해줘", "저희 아이 17살이에요 요금제 추천해줘",
            // 나이 표현이지만 본인의 현재 나이가 아니다(부정, 비교, 지난 때, 자격 질문, 나이를 알 수 없는 학생 구분)
            "청소년 요금제 말고 일반 요금제 추천해줘", "청소년 요금제랑 성인 요금제 비교해줘", "65세 이상 요금제 있어요?",
            "고등학생 때 쓰던 번호인데 요금제 추천해줘", "중학생이에요 요금제 추천해줘", "17살 가입 가능한 요금제 추천해줘"})
    void 분명하지_않은_표현은_읽지_않는다(String question) {
        assertThat(StatedRecommendationAnswers.of(question)).isEmpty();
    }

    @Test
    void 비어_있으면_읽지_않는다() {
        assertThat(StatedRecommendationAnswers.of(null)).isEmpty();
        assertThat(StatedRecommendationAnswers.of("  ")).isEmpty();
    }
}
