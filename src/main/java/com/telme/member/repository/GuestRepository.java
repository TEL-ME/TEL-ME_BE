package com.telme.member.repository;

import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

    // 동시 로그인 레이스 방지 — merged_user_id가 비어있는 행만 원자적으로 갱신한 요청만 채팅·피드백 승계로 이어간다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Guest guest
            set guest.mergedUser = :user, guest.mergedAt = :mergedAt
            where guest.guestId = :guestId
              and guest.mergedUser is null
            """)
    int succeedGuest(@Param("guestId") UUID guestId, @Param("user") User user, @Param("mergedAt") Instant mergedAt);
}
