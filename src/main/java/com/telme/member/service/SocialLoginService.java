package com.telme.member.service;

import com.telme.member.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** 공급자와 무관한 소셜 회원 조회·생성, 게스트 승계, 로그인 세션 확정을 담당한다. */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final SocialMemberFinder socialMemberFinder;
    private final GuestSuccessionService guestSuccessionService;
    private final GuestIdResolver guestIdResolver;
    private final LoginCompletionService loginCompletionService;
    private final TransactionTemplate transactionTemplate;

    public User findOrCreate(SocialOAuth2Principal principal) {
        return socialMemberFinder.findOrCreate(
                principal.getProvider(),
                principal.getProviderUserId(),
                principal.getEmail(),
                principal.getDisplayName());
    }

    /** 게스트 승계와 세션 확정을 수행한다. 승계 실패 시 세션은 확정하지 않고 false를 반환한다. */
    public boolean completeLogin(
            User user, HttpServletRequest request, HttpServletResponse response) {
        UUID guestId = guestIdResolver.resolve(request);
        if (guestId != null) {
            try {
                transactionTemplate.executeWithoutResult(
                        status -> guestSuccessionService.succeedGuest(guestId, user));
            } catch (RuntimeException exception) {
                return false;
            }
        }
        loginCompletionService.completeLogin(user, guestId, request, response);
        return true;
    }
}
