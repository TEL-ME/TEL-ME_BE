package com.telme.member.repository;

import com.telme.member.entity.SocialAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

    @Query("select sa from SocialAccount sa join fetch sa.user where sa.provider = :provider and sa.providerUserId = :providerUserId")
    Optional<SocialAccount> findByProviderAndProviderUserId(
            @Param("provider") SocialAccount.Provider provider, @Param("providerUserId") String providerUserId);
}
