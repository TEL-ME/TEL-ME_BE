package com.telme.member.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.SocialAccount;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
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
    private static final String USER_PROVIDER_UNIQUE_CONSTRAINT = "uk_social_user_provider";
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

    // 이미 연결된 회원(matchedUserId)에 새 소셜 계정을 붙인다 — email 충돌로 findOrCreate가 중단시킨 뒤,
    // 본인확인(비밀번호 등)을 통과한 호출자만 이 메서드로 실제 연결을 완료한다
    public User linkExisting(SocialAccount.Provider provider, String providerUserId, User existingUser) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    socialAccountRepository.saveAndFlush(SocialAccount.builder()
                            .user(existingUser)
                            .provider(provider)
                            .providerUserId(providerUserId)
                            .email(existingUser.getEmail())
                            .build()));
            return existingUser;
        } catch (DataIntegrityViolationException exception) {
            return resolveLinkConflict(exception, provider, providerUserId, existingUser);
        }
    }

    private User resolveLinkConflict(
            DataIntegrityViolationException exception, SocialAccount.Provider provider, String providerUserId, User existingUser) {
        if (isUserProviderUniqueViolation(exception)) {
            // 이 회원은 이미 해당 provider가 연결돼 있음 — 회원당 provider 1개 정책 위반
            throw new GeneralException(MemberErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
        }
        if (isSocialUniqueViolation(exception)) {
            // 동시 중복 클릭 등 — 이미 이 소셜 계정이 연결돼 있다면 대상이 같은 회원일 때만 그대로 성공 처리(멱등)
            SocialAccount winner = socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
                    .orElseThrow(() -> exception);
            if (!winner.getUser().getUserId().equals(existingUser.getUserId())) {
                throw new GeneralException(MemberErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
            }
            return existingUser;
        }
        throw exception;
    }

    private User createNew(SocialAccount.Provider provider, String providerUserId, String email) {
        if (email != null) {
            Optional<User> matched = userRepository.findByEmail(email);
            if (matched.isPresent()) {
                // 자동으로 연결하지 않는다 — 호출자가 본인확인(비밀번호) 후 linkExisting()으로 넘어가야 한다
                throw new SocialEmailAlreadyLinkedException(matched.get().getUserId(), email);
            }
        }
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

    private boolean isUserProviderUniqueViolation(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException constraintViolation
                && USER_PROVIDER_UNIQUE_CONSTRAINT.equals(constraintViolation.getConstraintName());
    }
}
