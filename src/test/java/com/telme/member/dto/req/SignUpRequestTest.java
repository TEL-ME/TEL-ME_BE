package com.telme.member.dto.req;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SignUpRequestTest {

    private Locale originalDefaultLocale;

    @BeforeEach
    void saveDefaultLocale() {
        originalDefaultLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreDefaultLocale() {
        Locale.setDefault(originalDefaultLocale);
    }

    @Test
    @DisplayName("이메일 대문자는 소문자로 정규화된다")
    void 이메일이_소문자로_정규화된다() {
        var request = new SignUpRequest("User@Example.COM", "password123");

        assertThat(request.email()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("이메일이 null이면 정규화 없이 그대로 null이다")
    void 이메일이_null이면_그대로_null이다() {
        var request = new SignUpRequest(null, "password123");

        assertThat(request.email()).isNull();
    }

    @Test
    @DisplayName("서버 기본 Locale이 터키어여도 정규화 결과가 달라지지 않는다")
    void 터키어_Locale에서도_정규화_결과가_같다() {
        Locale.setDefault(Locale.of("tr", "TR"));

        var request = new SignUpRequest("ISTANBUL@Example.com", "password123");

        assertThat(request.email()).isEqualTo("istanbul@example.com");
    }
}
