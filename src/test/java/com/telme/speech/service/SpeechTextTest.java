package com.telme.speech.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpeechTextTest {

    @Test
    @DisplayName("링크 주소와 목록 기호, 마크다운 기호는 읽지 않고 줄바꿈은 공백 하나로 합친다")
    void 읽지_않을_부분을_뺀다() {
        String content = """
                요금제 변경은 아래에서 할 수 있어요.
                - **마이페이지** 접속
                • 요금제 변경 선택
                자세한 내용: https://www.example.com/plan?id=1
                """;

        assertThat(SpeechText.toSpeakable(content, 3000))
                .isEqualTo("요금제 변경은 아래에서 할 수 있어요. 마이페이지 접속 요금제 변경 선택 자세한 내용:");
    }

    @Test
    @DisplayName("한도를 넘으면 한도 안의 마지막 문장 끝에서 자른다")
    void 문장_끝에서_자른다() {
        String content = "첫 문장입니다. 두 번째 문장입니다. 세 번째 문장은 아주 깁니다";

        assertThat(SpeechText.toSpeakable(content, 25)).isEqualTo("첫 문장입니다. 두 번째 문장입니다.");
    }

    @Test
    @DisplayName("한도의 앞쪽 절반 안에 문장 끝이 없으면 한도에서 자른다")
    void 문장_끝이_없으면_한도에서_자른다() {
        String content = "가.나다라마바사아자차카타파하";

        assertThat(SpeechText.toSpeakable(content, 8)).isEqualTo("가.나다라마바사");
    }

    @Test
    @DisplayName("내용이 없거나 링크뿐이면 빈 문자열이 된다")
    void 읽을_내용이_없으면_빈_문자열() {
        assertThat(SpeechText.toSpeakable(null, 3000)).isEmpty();
        assertThat(SpeechText.toSpeakable("  https://www.example.com \n ", 3000)).isEmpty();
    }
}
