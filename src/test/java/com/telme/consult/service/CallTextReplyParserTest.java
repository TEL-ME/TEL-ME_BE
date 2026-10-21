package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.consult.service.CallTextReplyParser.Kind;
import com.telme.consult.service.CallTextReplyParser.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CallTextReplyParserTest {

    @Test
    void 통화는_분_단위로_읽고_시간은_분으로_바꾼다() {
        assertThat(voice("300분")).isEqualTo(300);
        assertThat(voice("한 달에 200분쯤 써요")).isEqualTo(200);
        assertThat(voice("5시간")).isEqualTo(300);
        assertThat(voice("1시간 30분")).isEqualTo(90);
        assertThat(voice("1.5시간")).isEqualTo(90);
        assertThat(voice("200~300분")).isEqualTo(300);
        assertThat(voice("1,000분")).isEqualTo(1000);
    }

    @Test
    void 범위는_양쪽을_분으로_바꿔_큰_쪽으로_읽는다() {
        assertThat(voice("200분~300분")).isEqualTo(300);
        assertThat(voice("1시간~2시간")).isEqualTo(120);
        assertThat(voice("30분~1시간")).isEqualTo(60);
        assertThat(voice("30분에서 1시간")).isEqualTo(60);
        assertThat(voice("하루 10분~20분")).isEqualTo(600);
        assertThat(sms("30건~50건")).isEqualTo(50);
        assertThat(voice("통화는 200분~300분이고 문자는 50건이에요")).isEqualTo(300);
        assertThat(sms("통화는 200분~300분이고 문자는 50건이에요")).isEqualTo(50);
    }

    @Test
    void 문자는_건_단위로_읽는다() {
        assertThat(sms("50건")).isEqualTo(50);
        assertThat(sms("한 100개쯤 보내요")).isEqualTo(100);
        assertThat(sms("30통")).isEqualTo(30);
    }

    @Test
    void 하루_사용량은_한_달로_바꿔_읽는다() {
        assertThat(voice("하루 10분")).isEqualTo(300);
        assertThat(voice("매일 1시간")).isEqualTo(1800);
        assertThat(sms("하루에 3건")).isEqualTo(90);
    }

    @Test
    void 단위가_없으면_숫자만_말했을_때_묻는_단위로_읽는다() {
        assertThat(voice("300")).isEqualTo(300);
        assertThat(sms("50이요")).isEqualTo(50);
    }

    @Test
    void 다른_항목의_단위가_섞인_답은_묻는_항목의_값만_읽는다() {
        assertThat(voice("통화는 300분이고 문자는 50건이에요")).isEqualTo(300);
        assertThat(sms("통화는 300분이고 문자는 50건이에요")).isEqualTo(50);
        assertThat(CallTextReplyParser.parseSms("통화는 300분이에요").kind()).isEqualTo(Kind.UNREADABLE);
        assertThat(CallTextReplyParser.parseVoice("문자는 50건이에요").kind()).isEqualTo(Kind.UNREADABLE);
    }

    @Test
    void 거의_안_쓴다는_답은_없음으로_읽는다() {
        assertThat(CallTextReplyParser.parseVoice("거의 안 해요").kind()).isEqualTo(Kind.NONE);
        assertThat(CallTextReplyParser.parseSms("문자는 거의 안 써요").kind()).isEqualTo(Kind.NONE);
        assertThat(CallTextReplyParser.parseVoice("0분").kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void 무제한이라고_답하면_무제한으로_읽고_부정이면_읽지_않는다() {
        assertThat(CallTextReplyParser.parseVoice("무제한으로 써요").kind()).isEqualTo(Kind.UNLIMITED);
        assertThat(CallTextReplyParser.parseVoice("무제한은 필요 없어요").kind()).isEqualTo(Kind.UNREADABLE);
    }

    @Test
    void 모른다고_답하면_모름으로_읽고_값이_함께_있으면_값을_쓴다() {
        assertThat(CallTextReplyParser.parseVoice("잘 모르겠어요").kind()).isEqualTo(Kind.UNSURE);
        assertThat(voice("300분쯤인데 정확히는 몰라요")).isEqualTo(300);
    }

    @Test
    void 저장하는_글자를_다시_읽으면_같은_결과가_나온다() {
        for (Result result : new Result[] {CallTextReplyParser.parseVoice("5시간"), CallTextReplyParser.parseVoice("거의 안 해요"),
                CallTextReplyParser.parseVoice("무제한"), CallTextReplyParser.parseVoice("몰라요")}) {
            assertThat(CallTextReplyParser.parseVoice(result.canonical())).isEqualTo(result);
        }
        Result amount = CallTextReplyParser.parseSms("50건");
        assertThat(amount.canonical()).isEqualTo("50건");
        assertThat(CallTextReplyParser.parseSms(amount.canonical())).isEqualTo(amount);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "   ", "많이 써요", "100000000분", "200분 쓰다가 요즘 500분", "한 달에 100분 하루에 10분",
            "20대인데 300", "010-1234-5678", "200분~300분이고 500분", "100분~200분 하루에 10분"})
    void 분명하지_않은_답은_읽지_않는다(String reply) {
        assertThat(CallTextReplyParser.parseVoice(reply).kind()).isEqualTo(Kind.UNREADABLE);
    }

    @Test
    void 비어_있는_답은_읽지_않는다() {
        assertThat(CallTextReplyParser.parseVoice(null).readable()).isFalse();
        assertThat(CallTextReplyParser.parseSms(null).readable()).isFalse();
    }

    private static long voice(String reply) {
        Result result = CallTextReplyParser.parseVoice(reply);
        assertThat(result.kind()).as(reply).isEqualTo(Kind.AMOUNT);
        return result.value();
    }

    private static long sms(String reply) {
        Result result = CallTextReplyParser.parseSms(reply);
        assertThat(result.kind()).as(reply).isEqualTo(Kind.AMOUNT);
        return result.value();
    }
}
