package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PlanCatalogAnswererTest {

    @Autowired PlanCatalogAnswerer answerer;
    @Autowired JdbcTemplate jdbc;

    @Test
    void 요금제_이름이_있으면_그_요금제의_월정액과_데이터를_알려준다() {
        assertThat(answerer.answer("5G 스탠다드 얼마예요"))
                .contains("5G 스탠다드", "월정액 65,000원", "데이터 50GB")
                .doesNotContain("LTE", "알뜰");
        assertThat(answerer.answer("알뜰 미니가 얼마예요")).contains("월정액 15,000원", "데이터 1.5GB");
        assertThat(answerer.answer("5G 언리미티드 요금 알려줘")).contains("데이터 무제한");
    }

    @Test
    void 망_이름을_뗀_상품_이름과_여러_요금제_이름도_알아본다() {
        assertThat(answerer.answer("스탠다드 요금 알려줘")).contains("5G 스탠다드", "65,000원");
        assertThat(answerer.answer("5G 스탠다드랑 LTE 플러스 월 요금 알려줘"))
                .contains("5G 스탠다드", "65,000원", "LTE 플러스", "49,000원");
    }

    @Test
    void 청소년과_시니어는_이름이_겹쳐_상품_이름으로_알아보지_않는다() {
        assertThat(answerer.answer("청소년 요금제 있나요"))
                .contains("요금제 안내예요", "[5G]", "5G 청소년", "LTE 청소년", "알뜰 청소년")
                .doesNotContain("시니어", "5G 스탠다드");
        assertThat(answerer.answer("LTE 시니어 요금제 월 요금 알려줘"))
                .contains("LTE 시니어", "29,000원")
                .doesNotContain("5G 시니어", "알뜰 시니어");
    }

    @Test
    void 망으로_거르면_그_망의_요금제만_망_이름으로_묶어_보여준다() {
        String answer = answerer.answer("LTE 요금제 종류 알려줘");

        assertThat(answer).contains("[LTE]", "LTE 맥스", "LTE 플러스", "LTE 베이직", "LTE 청소년", "LTE 시니어")
                .doesNotContain("[5G]", "[알뜰]", "5G 스탠다드");
    }

    @Test
    void 청소년이나_시니어_요금제가_들어가면_가입_나이를_함께_알려준다() {
        assertThat(answerer.answer("청소년 요금제 있나요")).contains("청소년 요금제는 만 14~18세만 가입할 수 있어요");
        assertThat(answerer.answer("시니어 요금제 가격 알려주세요")).contains("시니어 요금제는 만 65세 이상만 가입할 수 있어요");
        assertThat(answerer.answer("요금제 뭐가 있어요"))
                .contains("청소년 요금제는 만 14~18세, 시니어 요금제는 만 65세 이상만 가입할 수 있어요");
        assertThat(answerer.answer("5G 스탠다드 얼마예요")).doesNotContain("가입할 수 있어요");
    }

    @Test
    void 가장_싼_요금제와_가장_비싼_요금제는_일반_요금제_중에서_고른다() {
        assertThat(answerer.answer("LTE 제일 싼 요금제가 얼마예요"))
                .contains("일반 요금제 중 가장 저렴한", "LTE 베이직", "33,000원")
                .doesNotContain("LTE 시니어");
        assertThat(answerer.answer("가장 비싼 요금제는 뭐예요")).contains("가장 비싼", "5G 언리미티드", "105,000원");
        assertThat(answerer.answer("시니어 요금제 중 제일 저렴한 건 뭐예요"))
                .contains("알뜰 시니어", "18,000원").doesNotContain("일반 요금제 중");
    }

    @Test
    void 요금제_개수를_물으면_판매_중인_전체_종류를_알려준다() {
        assertThat(answerer.answer("요금제가 다 해서 몇 가지예요")).contains("총 15종", "[5G]", "[LTE]", "[알뜰]");
    }

    @Test
    void 가격대와_가격_이하_조건으로_거른다() {
        String band = answerer.answer("3만 원대로 쓸 수 있는 요금제가 있나요");
        assertThat(band).contains("LTE 베이직", "33,000원", "LTE 청소년", "39,000원").doesNotContain("LTE 시니어", "5G 스탠다드");

        String under = answerer.answer("5만 원 안쪽 요금제 있나요");
        assertThat(under).contains("5G 라이트", "알뜰 미니").doesNotContain("5G 스탠다드", "LTE 맥스", "5G 프리미엄");
        assertThat(answerer.answer("2만 원 미만 요금제")).contains("알뜰 미니", "알뜰 시니어").doesNotContain("알뜰 청소년");
    }

    @Test
    void 데이터_무제한_요금제만_거른다() {
        assertThat(answerer.answer("데이터 무제한 요금제도 있나요")).contains("5G 언리미티드").doesNotContain("5G 프리미엄");
    }

    @Test
    void 조건에_맞는_요금제가_없으면_없다고_알리고_전체_요금제를_보여준다() {
        assertThat(answerer.answer("100만 원대 요금제"))
                .contains("조건에 맞는 요금제를 찾지 못했어요", "5G 언리미티드", "알뜰 시니어");
    }

    @Test
    void 조건을_말하지_않은_요금제_질문은_전체_요금제를_보여준다() {
        assertThat(answerer.answer("우리 요금제들 요금표 좀 보여주세요")).contains("요금제 안내", "5G 언리미티드", "알뜰 시니어");
    }

    @Test
    void 판매_종료된_요금제는_답변에서_빠지고_판매_중인_요금제가_없으면_답하지_않는다() {
        jdbc.update("UPDATE plans SET status = 'DISCONTINUED' WHERE code = 'LTE_BASIC'");

        assertThat(answerer.answer("LTE 요금제 종류 알려줘")).doesNotContain("LTE 베이직").contains("LTE 맥스");
        assertThat(answerer.answer("LTE 베이직 얼마예요")).doesNotContain("LTE 베이직");

        jdbc.update("UPDATE plans SET status = 'DISCONTINUED'");
        assertThat(answerer.answer("요금제 뭐가 있어요")).isNull();
    }
}
