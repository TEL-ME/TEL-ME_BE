package com.telme.member.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.converter.MemberConverter;
import com.telme.member.dto.req.KakaoLinkConfirmRequest;
import com.telme.member.dto.res.LoginResponse;
import com.telme.member.entity.SocialAccount;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class KakaoAccountLinkService {

    private final KakaoEmailMatchStore kakaoEmailMatchStore;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberStatusChecker memberStatusChecker;
    private final SocialMemberFinder socialMemberFinder;
    private final GuestSuccessionService guestSuccessionService;
    private final LoginCompletionService loginCompletionService;
    private final GuestIdResolver guestIdResolver;
    private final MemberConverter memberConverter;
    private final TransactionTemplate transactionTemplate;

    public LoginResponse confirmLink(
            KakaoLinkConfirmRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        KakaoEmailMatch pending = kakaoEmailMatchStore.require(httpRequest);

        // pending 발급 시점에 이미 존재를 확인한 회원이라 등록 시점과 달리 존재 여부를 시간차로 감출 필요가 없다
        User matchedUser = userRepository.findById(pending.matchedUserId())
                .orElseThrow(() -> new GeneralException(MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED));

        if (!passwordEncoder.matches(request.password(), matchedUser.getPasswordHash())) {
            kakaoEmailMatchStore.registerFailedAttempt(httpRequest, pending);
            throw new GeneralException(MemberErrorCode.INVALID_CREDENTIALS);
        }

        memberStatusChecker.checkActive(matchedUser);

        // linkExisting은 UNIQUE 충돌 시 재조회로 복구하는 자체 트랜잭션을 가지고 있어, 게스트 승계와
        // 하나의 트랜잭션으로 묶지 않는다(참여 트랜잭션이 rollback-only로 표시되면 그 복구 자체가 무효화된다) —
        // login()과 동일하게 각각 독립된 트랜잭션으로 순차 처리하고, 둘 다 끝난 뒤에만 세션에 반영한다
        UUID guestId = guestIdResolver.resolve(httpRequest);
        User linkedUser = socialMemberFinder.linkExisting(
                SocialAccount.Provider.KAKAO, pending.providerUserId(), pending.matchedEmail(), matchedUser);
        if (guestId != null) {
            transactionTemplate.executeWithoutResult(status -> guestSuccessionService.succeedGuest(guestId, linkedUser));
        }

        kakaoEmailMatchStore.clear(httpRequest);
        loginCompletionService.completeLogin(linkedUser, guestId, httpRequest, httpResponse);
        return memberConverter.toLoginResponse(linkedUser);
    }
}
