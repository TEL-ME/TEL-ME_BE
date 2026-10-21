package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.plan.entity.Plan.TargetGroup;
import org.junit.jupiter.api.Test;

class AgeReplyParserTest {

    @Test
    void 나이를_대상_구분으로_읽는다() {
        assertThat(AgeReplyParser.parse("17살이에요")).isEqualTo(TargetGroup.YOUTH);
        assertThat(AgeReplyParser.parse("34세")).isEqualTo(TargetGroup.GENERAL);
        assertThat(AgeReplyParser.parse("만 34 세")).isEqualTo(TargetGroup.GENERAL);
        assertThat(AgeReplyParser.parse("70세")).isEqualTo(TargetGroup.SENIOR);
        assertThat(AgeReplyParser.parse("5살")).isNull();
    }

    @Test
    void 나이대는_한_구분_안에_들어갈_때만_읽는다() {
        assertThat(AgeReplyParser.parse("30대")).isEqualTo(TargetGroup.GENERAL);
        assertThat(AgeReplyParser.parse("70대")).isEqualTo(TargetGroup.SENIOR);
        assertThat(AgeReplyParser.parse("10대")).isNull();
        assertThat(AgeReplyParser.parse("60대예요")).isNull();
    }

    @Test
    void 말로_적은_구분도_읽는다() {
        assertThat(AgeReplyParser.parse("청소년")).isEqualTo(TargetGroup.YOUTH);
        assertThat(AgeReplyParser.parse("시니어")).isEqualTo(TargetGroup.SENIOR);
        assertThat(AgeReplyParser.parse("직장인이에요")).isEqualTo(TargetGroup.GENERAL);
        assertThat(AgeReplyParser.parse("모르겠어요")).isNull();
        assertThat(AgeReplyParser.parse(" ")).isNull();
    }

    @Test
    void 서로_다른_구분의_단어가_함께_나오면_읽지_않는다() {
        assertThat(AgeReplyParser.parse("청소년이고 성인이에요")).isNull();
        assertThat(AgeReplyParser.parse("시니어랑 청소년 요금제")).isNull();
        assertThat(AgeReplyParser.parse("고등학생이에요")).isEqualTo(TargetGroup.YOUTH);
        assertThat(AgeReplyParser.parse("청소년 청소년")).isEqualTo(TargetGroup.YOUTH);
    }

    @Test
    void 범위가_한_구분_안이면_읽는다() {
        assertThat(AgeReplyParser.parse("30~40세")).isEqualTo(TargetGroup.GENERAL);
        assertThat(AgeReplyParser.parse("만 14~18세")).isEqualTo(TargetGroup.YOUTH);
        assertThat(AgeReplyParser.parse("65세 이상")).isEqualTo(TargetGroup.SENIOR);
    }

    @Test
    void 미만_이하_초과가_붙어_여러_구분에_걸치면_읽지_않는다() {
        assertThat(AgeReplyParser.parse("65세 미만이요")).isNull();
        assertThat(AgeReplyParser.parse("19세 미만")).isNull();
        assertThat(AgeReplyParser.parse("64세 이하")).isNull();
        assertThat(AgeReplyParser.parse("14세 이상")).isNull();
        assertThat(AgeReplyParser.parse("18세 초과")).isNull();
        assertThat(AgeReplyParser.parse("60~70세")).isNull();
    }

    @Test
    void 나이가_둘_이상이면_모두_같은_구분일_때만_읽는다() {
        assertThat(AgeReplyParser.parse("15살이랑 45살")).isNull();
        assertThat(AgeReplyParser.parse("30세와 70세")).isNull();
        assertThat(AgeReplyParser.parse("30세랑 40세")).isEqualTo(TargetGroup.GENERAL);
        assertThat(AgeReplyParser.parse("20대~30대")).isEqualTo(TargetGroup.GENERAL);
        assertThat(AgeReplyParser.parse("50대~60대")).isNull();
    }
}
