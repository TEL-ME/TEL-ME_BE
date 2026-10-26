package com.telme.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class GuestTest {

    @Test
    void issue는_고정된_Clock_기준으로_expiresAt을_계산한다() {
        Instant now = Instant.parse("2026-09-21T00:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        Duration ttl = Duration.ofDays(30);

        Guest guest = Guest.issue(ttl, fixedClock);

        assertThat(guest.getGuestId()).isNotNull();
        assertThat(guest.getLastSeenAt()).isEqualTo(now);
        assertThat(guest.getExpiresAt()).isEqualTo(now.plus(ttl));
    }

    @Test
    void issue할_때마다_guestId가_다르다() {
        Clock clock = Clock.fixed(Instant.now(), ZoneOffset.UTC);

        Guest first = Guest.issue(Duration.ofDays(30), clock);
        Guest second = Guest.issue(Duration.ofDays(30), clock);

        assertThat(first.getGuestId()).isNotEqualTo(second.getGuestId());
    }
}
