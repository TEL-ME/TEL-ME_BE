package com.telme.usage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.member.entity.User;
import com.telme.member.repository.UserRepository;
import com.telme.subscription.entity.Subscription;
import com.telme.subscription.repository.SubscriptionRepository;
import com.telme.usage.entity.SubscriptionUsage;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

// 월별 사용량 테이블(V33)의 저장·조회를 실제 DB로 확인한다. 트랜잭션 롤백으로 흔적을 남기지 않는다
@SpringBootTest
@Transactional
class SubscriptionUsageRepositoryTest {

    static final LocalDate OCT = LocalDate.of(2026, 10, 1);
    static final LocalDate NOV = LocalDate.of(2026, 11, 1);
    static final LocalDate DEC = LocalDate.of(2026, 12, 1);

    @Autowired UserRepository userRepository;
    @Autowired SubscriptionRepository subscriptionRepository;
    @Autowired SubscriptionUsageRepository usageRepository;

    Long subscriptionId;

    @BeforeEach
    void setUp() {
        Long userId = userRepository.save(User.builder().email("usage-repo@test.com").name("사용량").build()).getUserId();
        subscriptionId = subscriptionRepository.saveAndFlush(
                Subscription.subscribe(userId, Instant.parse("2026-10-05T03:00:00Z"))).getSubscriptionId();
    }

    @Test
    void 사용량을_넣으면_컬럼_값이_그대로_조회된다() {
        assertThat(usageRepository.insertIfAbsent(subscriptionId, OCT, 18_432L, 260, 120)).isEqualTo(1);

        SubscriptionUsage usage = usageRepository.findFromMonth(subscriptionId, OCT).get(0);
        assertThat(usage.getUsageMonth()).isEqualTo(OCT);
        assertThat(usage.getUsedDataMb()).isEqualTo(18_432L);
        assertThat(usage.getUsedVoiceMinutes()).isEqualTo(260);
        assertThat(usage.getUsedSmsCount()).isEqualTo(120);
        assertThat(usage.getCreatedAt()).isNotNull();
    }

    @Test
    void 같은_구독의_같은_달은_먼저_들어간_값을_유지하고_0을_돌려준다() {
        assertThat(usageRepository.insertIfAbsent(subscriptionId, OCT, 100L, 1, 1)).isEqualTo(1);

        assertThat(usageRepository.insertIfAbsent(subscriptionId, OCT, 999L, 9, 9)).isZero();

        List<SubscriptionUsage> usages = usageRepository.findFromMonth(subscriptionId, OCT);
        assertThat(usages).hasSize(1);
        assertThat(usages.get(0).getUsedDataMb()).isEqualTo(100L);
    }

    @Test
    void 지정한_달부터_오래된_순서로_조회한다() {
        usageRepository.insertIfAbsent(subscriptionId, DEC, 3L, 3, 3);
        usageRepository.insertIfAbsent(subscriptionId, OCT, 1L, 1, 1);
        usageRepository.insertIfAbsent(subscriptionId, NOV, 2L, 2, 2);

        assertThat(usageRepository.findFromMonth(subscriptionId, OCT))
                .extracting(SubscriptionUsage::getUsageMonth).containsExactly(OCT, NOV, DEC);
        assertThat(usageRepository.findFromMonth(subscriptionId, NOV))
                .extracting(SubscriptionUsage::getUsageMonth).containsExactly(NOV, DEC);
        assertThat(usageRepository.findFromMonth(subscriptionId, LocalDate.of(2027, 1, 1))).isEmpty();
    }

    @Test
    void 다른_구독의_사용량은_섞이지_않는다() {
        Long otherUser = userRepository.save(User.builder().email("usage-other@test.com").name("다른").build()).getUserId();
        Long otherSubscription = subscriptionRepository.saveAndFlush(
                Subscription.subscribe(otherUser, Instant.parse("2026-10-05T03:00:00Z"))).getSubscriptionId();
        usageRepository.insertIfAbsent(subscriptionId, OCT, 1L, 1, 1);
        usageRepository.insertIfAbsent(otherSubscription, OCT, 2L, 2, 2);

        assertThat(usageRepository.findFromMonth(subscriptionId, OCT)).hasSize(1);
        assertThat(usageRepository.findFromMonth(otherSubscription, OCT).get(0).getUsedDataMb()).isEqualTo(2L);
    }

    @Test
    void 월이_1일이_아니면_DB가_거부한다() {
        assertThatThrownBy(() -> usageRepository.insertIfAbsent(subscriptionId, LocalDate.of(2026, 10, 15), 1L, 1, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 음수_사용량은_DB가_거부한다() {
        assertThatThrownBy(() -> usageRepository.insertIfAbsent(subscriptionId, OCT, -1L, 1, 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
