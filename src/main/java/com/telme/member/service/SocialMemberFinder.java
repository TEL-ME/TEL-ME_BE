package com.telme.member.service;

import com.telme.member.entity.SocialAccount;
import com.telme.member.entity.User;
import com.telme.member.repository.SocialAccountRepository;
import com.telme.member.repository.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class SocialMemberFinder {

    private static final String SOCIAL_UNIQUE_CONSTRAINT = "uk_social_provider";
    private static final int RETRY_COUNT = 3;
    private static final long RETRY_DELAY_MS = 100;

    private final SocialAccountRepository socialAccountRepository;
    private final UserRepository userRepository;
    private final MemberStatusChecker memberStatusChecker;
    private final TransactionTemplate transactionTemplate;

    public User findOrCreate(SocialAccount.Provider provider, String providerUserId, String email) {
        User user = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(SocialAccount::getUser)
                .orElseGet(() -> createNew(provider, providerUserId, email));
        memberStatusChecker.checkActive(user);
        return user;
    }

    private User createNew(SocialAccount.Provider provider, String providerUserId, String email) {
        try {
            return transactionTemplate.execute(status -> {
                User user = userRepository.save(User.builder().build());
                socialAccountRepository.saveAndFlush(SocialAccount.builder()
                        .user(user)
                        .provider(provider)
                        .providerUserId(providerUserId)
                        .email(email)
                        .build());
                return user;
            });
        } catch (DataIntegrityViolationException exception) {
            if (!isSocialUniqueViolation(exception)) {
                throw exception;
            }
            // 동시 최초 로그인 레이스의 패자 — 승자의 커밋을 기다렸다가(짧게 재시도) 그 계정으로 로그인
            return awaitWinner(provider, providerUserId);
        }
    }

    private User awaitWinner(SocialAccount.Provider provider, String providerUserId) {
        for (int attempt = 1; attempt <= RETRY_COUNT; attempt++) {
            Optional<SocialAccount> winner = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
            if (winner.isPresent()) {
                return winner.get().getUser();
            }
            sleep();
        }
        throw new IllegalStateException("동시 최초 로그인 처리 중 상대 트랜잭션을 찾지 못함: " + provider + "/" + providerUserId);
    }

    private void sleep() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private boolean isSocialUniqueViolation(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException constraintViolation
                && SOCIAL_UNIQUE_CONSTRAINT.equals(constraintViolation.getConstraintName());
    }
}
