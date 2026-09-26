package com.telme.member.service;

import static com.telme.member.entity.SocialAccount.Provider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;

import com.telme.member.entity.User;
import java.util.List;
import java.util.UUID;
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

// 실제 스레드 2개로 같은 카카오 계정 최초 로그인을 동시에 태워, DB UNIQUE 충돌이 실제로 나는지와
// 둘 다 같은 회원으로 정상 완료되는지 확인한다.
@SpringBootTest
class SocialMemberFinderConcurrencyIntegrationTest {

    @Autowired
    private SocialMemberFinder socialMemberFinder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String providerUserId;

    @AfterEach
    void cleanUp() {
        if (providerUserId != null) {
            jdbcTemplate.update("delete from users where user_id in "
                    + "(select user_id from social_accounts where provider_user_id = ?)", providerUserId);
        }
    }

    @Test
    @DisplayName("같은 카카오 계정으로 동시에 최초 로그인해도 회원은 하나만 생기고 둘 다 그 회원으로 완료된다")
    void 동시_최초_로그인은_하나의_회원으로_수렴한다() throws Exception {
        providerUserId = "concurrent-kakao-" + UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<User> first = executor.submit(() -> findOrCreate(ready, start));
            Future<User> second = executor.submit(() -> findOrCreate(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Long> userIds = List.of(
                    first.get(10, TimeUnit.SECONDS).getUserId(),
                    second.get(10, TimeUnit.SECONDS).getUserId());

            assertThat(userIds.get(0)).isEqualTo(userIds.get(1));
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from social_accounts where provider_user_id = ?", Integer.class, providerUserId))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private User findOrCreate(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return socialMemberFinder.findOrCreate(KAKAO, providerUserId, null);
    }
}
