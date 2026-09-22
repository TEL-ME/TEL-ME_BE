package com.telme.member.repository;

import com.telme.member.entity.Guest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, UUID> {
}
