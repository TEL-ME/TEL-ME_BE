package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConversationInputTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "고마워 바라야 😊|고마워 바라야", "안녕 👨‍👩‍👧‍👦 바라야|안녕  바라야",
            "👍🏽 고마워요|고마워요", "🇰🇷 로밍|로밍", "1️⃣GB .5GB ₩39,000 + 10%|1GB .5GB ₩39,000 + 10%",
            "[문의] [질문] 무러바라님|무러바라님", "[5G 프리미어] 요금제|[5G 프리미어] 요금제",
            "[상품명] 요금제|[상품명] 요금제", "하이😊패스|하이패스"
    })
    void preservesMeaningWhileRemovingDecorations(String raw, String expected) {
        assertThat(ConversationInput.normalize(raw)).isEqualTo(expected);
        assertThat(ConversationInput.normalize(expected)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"😊", "👨‍👩‍👧‍👦", "👍🏽", "?!", "[문의] 😊"})
    void decorationsAloneAreNotConsultationAnswers(String raw) {
        assertThat(ConversationInput.hasSemanticContent(raw)).isFalse();
    }
}
