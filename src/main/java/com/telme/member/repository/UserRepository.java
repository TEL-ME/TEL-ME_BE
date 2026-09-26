package com.telme.member.repository;

import com.telme.member.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    // email이 비어있는 행만 원자적으로 갱신 — 같은 회원이 이 요청을 동시에 두 번 보내는 레이스 방지
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.email = :email, u.passwordHash = :passwordHash where u.userId = :userId and u.email is null")
    int addEmailLogin(@Param("userId") Long userId, @Param("email") String email, @Param("passwordHash") String passwordHash);
}
