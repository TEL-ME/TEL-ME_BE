package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StoreHoursQuestionTest {

    // 2026-10-21은 수요일
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 10, 21);

    @Test
    void 요일을_말하면_그_요일을_읽는다() {
        var request = StoreHoursQuestion.parse("거기 토요일에도 열어?", WEDNESDAY).orElseThrow();

        assertThat(request.days()).containsExactly(DayOfWeek.SATURDAY);
        assertThat(request.now()).isFalse();
        assertThat(request.label()).isEqualTo("토요일");
    }

    @Test
    void 주말과_평일은_여러_요일로_읽는다() {
        assertThat(StoreHoursQuestion.parse("주말에도 영업해요?", WEDNESDAY).orElseThrow().days())
                .containsExactly(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        assertThat(StoreHoursQuestion.parse("평일 영업시간 알려줘", WEDNESDAY).orElseThrow().days()).hasSize(5);
        assertThat(StoreHoursQuestion.parse("토요일이랑 일요일 영업시간이 어떻게 돼요", WEDNESDAY).orElseThrow().label()).isEqualTo("주말");
    }

    @Test
    void 요일이_없으면_오늘로_읽고_내일_모레도_읽는다() {
        assertThat(StoreHoursQuestion.parse("몇 시까지 해요?", WEDNESDAY).orElseThrow().days()).containsExactly(DayOfWeek.WEDNESDAY);
        assertThat(StoreHoursQuestion.parse("내일 영업시간 알려줘", WEDNESDAY).orElseThrow().days()).containsExactly(DayOfWeek.THURSDAY);
        assertThat(StoreHoursQuestion.parse("모레 열어요?", WEDNESDAY).orElseThrow().days()).containsExactly(DayOfWeek.FRIDAY);
    }

    @Test
    void 지금_열었는지는_시각으로_비교하는_요청이다() {
        var request = StoreHoursQuestion.parse("지금 열었어요?", WEDNESDAY).orElseThrow();

        assertThat(request.now()).isTrue();
        assertThat(request.days()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"매장 찾아줘", "근처 매장 알려줘", "번호이동 가능한 매장 찾아줘", "영업점 찾아줘", "운영하는 매장 알려줘",
            "강남역 근처 매장 어디예요"})
    void 영업_여부를_묻는_말이_아니면_읽지_않는다(String question) {
        assertThat(StoreHoursQuestion.parse(question, WEDNESDAY)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"홍대입구역 근처 매장 몇시까지해요?", "토요일에도 열어요?", "일요일 휴무예요?", "영업시간이 어떻게 돼요", "언제까지 해요"})
    void 영업_여부를_묻는_말은_읽는다(String question) {
        assertThat(StoreHoursQuestion.parse(question, WEDNESDAY)).isPresent();
    }

    @Test
    void 날짜가_없으면_빈_결과를_돌려주지_않고_널도_안전하다() {
        assertThat(StoreHoursQuestion.parse(null, WEDNESDAY)).isEmpty();
        assertThat(StoreHoursQuestion.parse("", WEDNESDAY)).isEmpty();
        assertThat(List.of(DayOfWeek.values())).hasSize(7);
    }
}
