package com.telme.member.service;

import com.telme.member.config.GuestProperties;
import com.telme.member.entity.Guest;
import com.telme.member.repository.GuestRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestIdentityService {

    private final GuestRepository guestRepository;
    private final GuestProperties properties;
    private final Clock clock;

    @Transactional
    public UUID issueGuest() {
        Guest guest = Guest.issue(properties.ttl(), clock);
        return guestRepository.save(guest).getGuestId();
    }
}
