package com.telme.speech.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.speech.service.SpeechTestSupport.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpeechAudioCacheTest {

    private MutableClock clock;
    private SpeechAudioCache cache;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-12T00:00:00Z"));
        cache = new SpeechAudioCache(clock, SpeechTestSupport.properties(10, 1000, 10));
    }

    @Test
    @DisplayName("저장한 음성은 1시간 동안 돌려주고 지나면 버린다")
    void 유효_기간() {
        cache.put(1L, new byte[] {1, 2, 3});

        clock.advance(Duration.ofMinutes(59));
        assertThat(cache.get(1L)).hasValueSatisfying(audio -> assertThat(audio).containsExactly(1, 2, 3));

        clock.advance(Duration.ofMinutes(1));
        assertThat(cache.get(1L)).isEmpty();
    }

    @Test
    @DisplayName("전체 크기가 한도를 넘으면 가장 오래 안 쓴 음성부터 버린다")
    void 크기_한도() {
        cache.put(1L, new byte[4]);
        cache.put(2L, new byte[4]);
        cache.get(1L);

        cache.put(3L, new byte[4]);

        assertThat(cache.get(1L)).isPresent();
        assertThat(cache.get(2L)).isEmpty();
        assertThat(cache.get(3L)).isPresent();
    }

    @Test
    @DisplayName("한도보다 큰 음성 하나는 저장하지 않고 기존 음성도 지우지 않는다")
    void 한도보다_큰_음성() {
        cache.put(1L, new byte[4]);

        cache.put(2L, new byte[11]);

        assertThat(cache.get(1L)).isPresent();
        assertThat(cache.get(2L)).isEmpty();
    }
}
