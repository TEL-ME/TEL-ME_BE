package com.telme.member.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class GuestRepositoryTest {

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void 게스트를_회원에게_승계한다() {
        Guest guest = createGuest();
        User user = createUser();
        // TIMESTAMPTZ는 마이크로초 정밀도라, DB 왕복 후 비교하려면 나노초 단위가 있는 Instant를 미리 잘라야 한다
        Instant mergedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);

        int updated = guestRepository.succeedGuest(guest.getGuestId(), user, mergedAt);

        assertThat(updated).isEqualTo(1);
        Guest succeeded = guestRepository.findById(guest.getGuestId()).orElseThrow();
        assertThat(succeeded.getMergedUser().getUserId()).isEqualTo(user.getUserId());
        assertThat(succeeded.getMergedAt()).isEqualTo(mergedAt);
    }

    @Test
    void 이미_승계된_게스트는_다시_갱신하지_않는다() {
        Guest guest = createGuest();
        User firstUser = createUser();
        User secondUser = createUser();
        Instant firstMergedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        guestRepository.succeedGuest(guest.getGuestId(), firstUser, firstMergedAt);

        int updated = guestRepository.succeedGuest(guest.getGuestId(), secondUser, Instant.now());

        assertThat(updated).isZero();
        Guest stillFirst = guestRepository.findById(guest.getGuestId()).orElseThrow();
        assertThat(stillFirst.getMergedUser().getUserId()).isEqualTo(firstUser.getUserId());
        assertThat(stillFirst.getMergedAt()).isEqualTo(firstMergedAt);
    }

    private Guest createGuest() {
        Guest guest = Guest.issue(Duration.ofDays(30), Clock.systemUTC());
        return guestRepository.save(guest);
    }

    private User createUser() {
        User user = User.builder()
                .email("guest-succeed-" + UUID.randomUUID() + "@example.com")
                .name("test")
                .build();
        return userRepository.save(user);
    }
}
