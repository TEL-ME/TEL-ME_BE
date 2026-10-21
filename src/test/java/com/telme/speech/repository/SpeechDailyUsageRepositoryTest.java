package com.telme.speech.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.speech.entity.SpeechDailyUsage;
import com.telme.speech.service.SpeechDailyUsageService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// 서비스가 REQUIRES_NEW로 커밋하고 여러 스레드가 동시에 차감해야 해서 롤백 트랜잭션을 쓰지 않는다.
// 실제 하루 사용량과 섞이지 않게 아주 먼 과거 날짜만 쓰고 끝나면 지운다
@SpringBootTest
class SpeechDailyUsageRepositoryTest {

    private static final LocalDate DAY = LocalDate.of(2000, 1, 1);
    private static final SpeechDailyUsage.Kind KIND = SpeechDailyUsage.Kind.TTS_CHARACTERS;

    @Autowired
    private SpeechDailyUsageService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from speech_daily_usages where usage_date = ?", DAY);
    }

    @Test
    @DisplayName("합이 한도 이하일 때만 차감하고, 한도를 넘는 요청은 값을 바꾸지 않는다")
    void 한도_이하일_때만_차감한다() {
        assertThat(service.tryCharge(DAY, KIND, 60, 100)).isTrue();
        assertThat(service.tryCharge(DAY, KIND, 41, 100)).isFalse();
        assertThat(service.tryCharge(DAY, KIND, 40, 100)).isTrue();

        assertThat(used()).isEqualTo(100);
    }

    @Test
    @DisplayName("그날 첫 요청이 한도보다 크면 행을 만들지 않고 막는다")
    void 첫_요청이_한도보다_크면_막는다() {
        assertThat(service.tryCharge(DAY, KIND, 101, 100)).isFalse();

        assertThat(rowCount()).isZero();
    }

    @Test
    @DisplayName("날짜와 종류가 다르면 따로 센다")
    void 날짜와_종류별로_센다() {
        assertThat(service.tryCharge(DAY, KIND, 100, 100)).isTrue();

        assertThat(service.tryCharge(DAY, SpeechDailyUsage.Kind.STT_SECONDS, 100, 100)).isTrue();
        assertThat(service.tryCharge(DAY.plusDays(1), KIND, 100, 100)).isTrue();
        jdbcTemplate.update("delete from speech_daily_usages where usage_date = ?", DAY.plusDays(1));
    }

    @Test
    @DisplayName("여러 스레드가 한도 근처에서 동시에 차감해도 합계가 한도를 넘지 않는다")
    void 동시에_차감해도_한도를_넘지_않는다() throws Exception {
        int threads = 16;
        int perRequest = 7;
        long limit = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.tryCharge(DAY, KIND, perRequest, limit);
                }));
            }
            ready.await();
            start.countDown();

            long accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }

            // 50 / 7 = 7건까지만 들어간다(49). 8번째부터는 56이라 넘는다
            assertThat(accepted).isEqualTo(7);
            assertThat(used()).isEqualTo(49);
        } finally {
            executor.shutdownNow();
        }
    }

    private long used() {
        return jdbcTemplate.queryForObject(
                "select used_amount from speech_daily_usages where usage_date = ? and kind = ?",
                Long.class, DAY, KIND.name());
    }

    private int rowCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from speech_daily_usages where usage_date = ?", Integer.class, DAY);
    }
}
