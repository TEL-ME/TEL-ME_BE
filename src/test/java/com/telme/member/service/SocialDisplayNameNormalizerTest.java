package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialDisplayNameNormalizerTest {

    @Test
    @DisplayName("앞뒤 공백은 전각 공백까지 제거한다")
    void 전각_공백까지_제거() {
        assertThat(SocialDisplayNameNormalizer.normalize("　 회원 　")).isEqualTo("회원");
    }

    @Test
    @DisplayName("null이거나 공백뿐이면 null을 반환한다")
    void 비어_있으면_null() {
        assertThat(SocialDisplayNameNormalizer.normalize(null)).isNull();
        assertThat(SocialDisplayNameNormalizer.normalize(" 　 ")).isNull();
    }

    @Test
    @DisplayName("50자를 넘으면 자르고, 경계에 걸린 서로게이트 쌍은 쪼개지 않는다")
    void 길이_제한과_서로게이트_보호() {
        assertThat(SocialDisplayNameNormalizer.normalize("가".repeat(60))).isEqualTo("가".repeat(50));
        String boundaryEmoji = "가".repeat(49) + "😀" + "가";

        String result = SocialDisplayNameNormalizer.normalize(boundaryEmoji);

        assertThat(result).isEqualTo("가".repeat(49));
        assertThat(Character.isHighSurrogate(result.charAt(result.length() - 1))).isFalse();
    }
}
