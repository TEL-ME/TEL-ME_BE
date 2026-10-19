package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.consult.service.DataUsageReplyParser.Kind;
import com.telme.consult.service.DataUsageReplyParser.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DataUsageReplyParserTest {

    private static final long GB = 1024;

    @Test
    void 숫자와_기가_단위를_GB로_읽는다() {
        assertThat(mb("40기가")).isEqualTo(40 * GB);
        assertThat(mb("50GB")).isEqualTo(50 * GB);
        assertThat(mb("50 gb")).isEqualTo(50 * GB);
        assertThat(mb("2기가바이트")).isEqualTo(2 * GB);
        assertThat(mb("한 달에 40기가 정도 써요")).isEqualTo(40 * GB);
        assertThat(mb("1.5기가")).isEqualTo(1536);
        assertThat(mb("1,000기가")).isEqualTo(1000 * GB);
    }

    @Test
    void 메가_단위는_MB로_읽는다() {
        assertThat(mb("500MB")).isEqualTo(500);
        assertThat(mb("500메가")).isEqualTo(500);
    }

    @Test
    void 단위가_없으면_숫자만_말했을_때_GB로_읽는다() {
        assertThat(mb("200")).isEqualTo(200 * GB);
        assertThat(mb("50G")).isEqualTo(50 * GB);
        assertThat(mb("40이요")).isEqualTo(40 * GB);
        assertThat(mb("한 30쯤 써요")).isEqualTo(30 * GB);
    }

    @Test
    void 범위는_큰_쪽으로_읽는다() {
        assertThat(mb("30~40기가")).isEqualTo(40 * GB);
        assertThat(mb("30기가에서 40기가")).isEqualTo(40 * GB);
        assertThat(mb("30에서 40기가 사이")).isEqualTo(40 * GB);
        assertThat(mb("30-40")).isEqualTo(40 * GB);
    }

    @Test
    void 하루_사용량은_한_달로_바꿔_읽는다() {
        assertThat(mb("하루 2기가")).isEqualTo(60 * GB);
        assertThat(mb("매일 1.5기가쯤")).isEqualTo(45 * GB);
        assertThat(mb("하루에 500메가")).isEqualTo(15_000);
        assertThat(mb("1일 3기가쯤")).isEqualTo(90 * GB);
    }

    @Test
    void 한자어_수도_읽는다() {
        assertThat(mb("백기가")).isEqualTo(100 * GB);
        assertThat(mb("오십 기가")).isEqualTo(50 * GB);
        assertThat(mb("이백기가")).isEqualTo(200 * GB);
        assertThat(mb("백오십기가쯤")).isEqualTo(150 * GB);
    }

    @Test
    void 다른_숫자가_섞여도_단위가_붙은_숫자만_읽는다() {
        assertThat(mb("20대인데 40기가 써요")).isEqualTo(40 * GB);
        assertThat(mb("5G 요금제 쓰는데 40기가 써요")).isEqualTo(40 * GB);
    }

    @Test
    void 무제한이라고_답하면_무제한으로_읽는다() {
        assertThat(DataUsageReplyParser.parse("무제한").kind()).isEqualTo(Kind.UNLIMITED);
        assertThat(DataUsageReplyParser.parse("제한 없이 써요").kind()).isEqualTo(Kind.UNLIMITED);
        assertThat(DataUsageReplyParser.parse("무제한은 필요 없어요").kind()).isEqualTo(Kind.UNREADABLE);
    }

    @Test
    void 모른다고_답하면_모름으로_읽고_숫자가_함께_있으면_숫자를_쓴다() {
        assertThat(DataUsageReplyParser.parse("잘 모르겠어요").kind()).isEqualTo(Kind.UNSURE);
        assertThat(DataUsageReplyParser.parse("글쎄요 기억이 안 나요").kind()).isEqualTo(Kind.UNSURE);
        assertThat(mb("40기가쯤인데 정확히는 몰라요")).isEqualTo(40 * GB);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "많이 써요", "거의 안 써요",
            "5G 요금제요", "0기가", "100000기가", "99999999999999999999기가",
            "30기가 쓰다가 요즘 50기가", "한 달에 50기가 하루에 2기가",
            "010-1234-5678", "20대인데 40", "3개월 평균 40"})
    void 분명하지_않은_답은_읽지_않는다(String reply) {
        Result result = DataUsageReplyParser.parse(reply);

        assertThat(result.kind()).isEqualTo(Kind.UNREADABLE);
        assertThat(result.readable()).isFalse();
    }

    @Test
    void 비어_있는_답은_읽지_않는다() {
        assertThat(DataUsageReplyParser.parse(null).kind()).isEqualTo(Kind.UNREADABLE);
    }

    private static long mb(String reply) {
        Result result = DataUsageReplyParser.parse(reply);
        assertThat(result.kind()).as(reply).isEqualTo(Kind.AMOUNT);
        return result.dataMb();
    }
}
