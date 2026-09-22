package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.member.config.GuestProperties;
import com.telme.member.entity.Guest;
import com.telme.member.repository.GuestRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GuestIdentityServiceTest {

    @Autowired
    private GuestIdentityService guestIdentityService;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private GuestProperties guestProperties;

    @Test
    @DisplayName("발급한 guestId가 DB에 그대로 저장된다")
    void issueGuest는_DB에_저장된_guestId를_반환한다() {
        UUID guestId = guestIdentityService.issueGuest();

        Optional<Guest> saved = guestRepository.findById(guestId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getGuestId()).isEqualTo(guestId);
        assertThat(saved.get().getExpiresAt()).isAfter(saved.get().getLastSeenAt());
    }

    @Test
    @DisplayName("만료일이 설정값(member.guest.ttl)을 기준으로 계산된다")
    void expiresAt은_설정된_ttl만큼_뒤이다() {
        Instant before = Instant.now();

        UUID guestId = guestIdentityService.issueGuest();

        Guest saved = guestRepository.findById(guestId).orElseThrow();
        Duration actualTtl = Duration.between(saved.getLastSeenAt(), saved.getExpiresAt());
        assertThat(actualTtl).isEqualTo(guestProperties.ttl());
        // application.yml 기본값(30d)이 실제로 적용됐는지도 함께 확인
        assertThat(saved.getExpiresAt()).isAfter(before.plus(guestProperties.ttl()).minusSeconds(5));
    }
}
