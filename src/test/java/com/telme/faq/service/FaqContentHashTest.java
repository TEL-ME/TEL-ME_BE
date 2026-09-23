package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaqContentHashTest {

    // scripts/data/faq_sample_30.json의 BILLING-S01. 기대값은 Python hashlib으로 계산한 것
    private static final String QUESTION = "요금제 바꾸는 거 한 달에 몇 번까지 되나요?";
    private static final String ANSWER = "요금제 변경은 한 달에 1회만 가능합니다. 변경을 신청하시면 다음 날 00:00부터 새 요금제가 적용되고, "
            + "그 달 요금은 일할 계산됩니다. 가입한 달에는 변경할 수 없고 다음 달부터 가능합니다.";
    private static final String PYTHON_HASH = "763d0fa11d9fc199d3da41d1cb5999444beba393d45b66fbf0453cb1c1eb3087";

    @Test
    @DisplayName("Python check_eval_questions.content_hash()와 같은 값을 낸다")
    void 파이썬_구현과_같은_해시를_낸다() {
        assertThat(FaqContentHash.of(QUESTION, ANSWER)).isEqualTo(PYTHON_HASH);
    }

    @Test
    @DisplayName("64자 소문자 hex")
    void 형식은_64자_소문자_hex다() {
        assertThat(FaqContentHash.of("q", "a")).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("구분자 없이 이어 붙인다 — 경계가 이동한 조합은 같은 해시")
    void 구분자_없이_이어_붙인다() {
        assertThat(FaqContentHash.of("ab", "c")).isEqualTo(FaqContentHash.of("a", "bc"));
    }
}
