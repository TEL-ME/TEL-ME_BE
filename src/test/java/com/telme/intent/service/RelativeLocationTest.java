package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class RelativeLocationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "현재 위치", "현재위치", "내 위치", "지금 위치", "현위치", "여기", "근처", "주변", "이 근처",
            "현재 위치 근처", "현재 위치에서", "가까운 곳", "우리 동네", "위치로", "GPS", "거기", "집 근처"})
    @DisplayName("기준점만 가리키는 말은 지역명이 아니다")
    void 기준점만_가리키는_말(String value) {
        assertThat(RelativeLocation.isOnlyRelative(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"강남역", "강남역 근처", "서초동", "테헤란로", "을지로", "성남시", "판교", "신촌 근처"})
    @NullSource
    @DisplayName("실제 지명이 들어 있으면 지역명으로 둔다")
    void 실제_지명(String value) {
        assertThat(RelativeLocation.isOnlyRelative(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"현재 위치에서 가까운 매장을 찾아줘", "근처 매장", "여기서 가까운 곳", "내위치 기준으로"})
    @DisplayName("문장 안에 기준점 표현이 있는지 찾는다")
    void 기준점_표현_포함(String text) {
        assertThat(RelativeLocation.mentions(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"강남역이요", "신촌", "잠깐만요", "유심 바꾸려고요"})
    @NullSource
    @DisplayName("기준점 표현이 없는 문장은 해당하지 않는다")
    void 기준점_표현_없음(String text) {
        assertThat(RelativeLocation.mentions(text)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "판교 근처요, 판교",
            "신촌 주변요, 신촌",
            "신촌근처, 신촌",
            "신촌에서 가까운 곳, 신촌",
            "판교 쪽이요, 판교"})
    @DisplayName("기준점 표현 앞에 접미사 없는 실제 지명이 있으면 그 지명을 뽑는다")
    void 기준점_앞의_지명(String text, String place) {
        assertThat(RelativeLocation.placeBefore(text)).isEqualTo(place);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "현재 위치에서 가까운 매장을 찾아줘", "근처 매장", "여기 근처요", "내 위치 근처", "이 근처",
            "그 근처요", "거기 근처요", "우리 동네 근처", "집 근처요"})
    @NullSource
    @DisplayName("기준점 앞이 지명이 아니면 지명을 뽑지 않는다")
    void 기준점_앞이_지명이_아님(String text) {
        assertThat(RelativeLocation.placeBefore(text)).isNull();
    }
}
