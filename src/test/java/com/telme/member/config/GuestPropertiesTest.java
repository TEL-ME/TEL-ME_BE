package com.telme.member.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class GuestPropertiesTest {

    @Test
    void acceptsPositiveTtl() {
        GuestProperties properties = new GuestProperties(Duration.ofDays(30));

        assertThat(properties.ttl()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void rejectsNullTtl() {
        assertThatThrownBy(() -> new GuestProperties(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("member.guest.ttl 설정이 필요합니다.");
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new GuestProperties(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GuestProperties(Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
