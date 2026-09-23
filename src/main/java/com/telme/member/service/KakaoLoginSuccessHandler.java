package com.telme.member.service;

import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.config.Oauth2Properties;
import com.telme.member.entity.SocialAccount;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

// KakaoOAuth2UserService는 원본 카카오 클레임만 넘긴다 — 로그인인지 계정연결인지,
// 어떤 회원으로 귀결되는지는 세션에 접근 가능한 여기서 전부 판단한다
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String DEFAULT_FAILURE_REASON = "OAUTH2_LOGIN_FAILED";

    private final SocialMemberFinder socialMemberFinder;
    private final UserRepository userRepository;
    private final MemberStatusChecker memberStatusChecker;
    private final GuestSuccessionService guestSuccessionService;
    private final GuestIdResolver guestIdResolver;
    private final KakaoLinkPendingStore kakaoLinkPendingStore;
    private final PendingKakaoLinkStore pendingKakaoLinkStore;
    private final SecurityContextRepository securityContextRepository;
    private final Oauth2Properties oauth2Properties;
    private final TransactionTemplate transactionTemplate;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        try {
            KakaoOAuth2User kakaoPrincipal = (KakaoOAuth2User) authentication.getPrincipal();
            User user = resolveUser(request, response, kakaoPrincipal.getProviderUserId(), kakaoPrincipal.getEmail());
            if (user == null) {
                return;
            }
            finalizeSession(user, request, response);
        } catch (RuntimeException exception) {
            // 예상 밖 오류(DB 연결 오류, 동시 생성 재조회 실패 등)까지 여기서 막는다 — 원인은 내부 로그에만 남기고 프론트에는 고정 사유만 보낸다
            log.error("카카오 로그인 처리 중 예상하지 못한 오류", exception);
            redirectFailure(request, response, DEFAULT_FAILURE_REASON);
        }
    }

    private User resolveUser(HttpServletRequest request, HttpServletResponse response, String providerUserId, String email)
            throws IOException {
        pendingKakaoLinkStore.clear(request);

        KakaoLinkPending pending;
        try {
            pending = kakaoLinkPendingStore.consume(request);
        } catch (GeneralException exception) {
            redirectFailure(request, response, exception.getErrorCode().getCode());
            return null;
        }
        if (pending != null) {
            return resolveLinkMode(request, response, pending, providerUserId);
        }
        return resolveLoginMode(request, response, providerUserId, email);
    }

    private User resolveLinkMode(
            HttpServletRequest request, HttpServletResponse response, KakaoLinkPending pending, String providerUserId)
            throws IOException {
        // pending만 믿지 않고 세션의 현재 로그인 상태와 대조 — 다른 탭에서 로그인 상태가 바뀌었을 수 있다
        if (!pending.targetUserId().equals(readSessionUserId(request))) {
            redirectFailure(request, response, "KAKAO_LINK_SESSION_MISMATCH");
            return null;
        }
        try {
            User targetUser = userRepository.findById(pending.targetUserId())
                    .orElseThrow(() -> new GeneralException(MemberErrorCode.UNAUTHENTICATED));
            memberStatusChecker.checkActive(targetUser);
            return socialMemberFinder.linkExisting(SocialAccount.Provider.KAKAO, providerUserId, targetUser);
        } catch (GeneralException exception) {
            redirectFailure(request, response, exception.getErrorCode().getCode());
            return null;
        }
    }

    private User resolveLoginMode(HttpServletRequest request, HttpServletResponse response, String providerUserId, String email)
            throws IOException {
        try {
            return socialMemberFinder.findOrCreate(SocialAccount.Provider.KAKAO, providerUserId, email);
        } catch (SocialEmailAlreadyLinkedException exception) {
            pendingKakaoLinkStore.issue(request, providerUserId, exception.getMatchedUserId(), exception.getMatchedEmail());
            redirectFailure(request, response, MemberErrorCode.EMAIL_LINK_REQUIRED.getCode());
            return null;
        } catch (GeneralException exception) {
            redirectFailure(request, response, exception.getErrorCode().getCode());
            return null;
        }
    }

    private void finalizeSession(User user, HttpServletRequest request, HttpServletResponse response) throws IOException {
        UUID guestId = guestIdResolver.resolve(request);
        if (guestId != null) {
            try {
                transactionTemplate.executeWithoutResult(status -> guestSuccessionService.succeedGuest(guestId, user));
            } catch (RuntimeException exception) {
                logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
                redirectFailure(request, response, "GUEST_SUCCESSION_FAILED");
                return;
            }
        }

        HttpSession session = request.getSession();
        if (guestId != null) {
            session.removeAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE);
        }
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, user.getUserId());

        // 지금까지 SecurityContext엔 원본 카카오 클레임(KakaoOAuth2User)이 담겨 있다 — 실제로 해석된 회원 기준으로 교체
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUserId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        response.sendRedirect(redirectUri(true, null));
    }

    private Long readSessionUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE);
        return value instanceof Long userId ? userId : null;
    }

    private void redirectFailure(HttpServletRequest request, HttpServletResponse response, String reason) throws IOException {
        restoreOrClearAuthentication(request, response);
        response.sendRedirect(redirectUri(false, reason));
    }

    private void restoreOrClearAuthentication(HttpServletRequest request, HttpServletResponse response) {
        Long existingUserId = readSessionUserId(request);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (existingUserId != null) {
            try {
                userRepository.findById(existingUserId).ifPresentOrElse(
                        existingUser -> context.setAuthentication(
                                new UsernamePasswordAuthenticationToken(existingUser.getUserId(), null,
                                        List.of(new SimpleGrantedAuthority("ROLE_" + existingUser.getRole().name())))),
                        () -> removeSessionUserId(request));
            } catch (RuntimeException exception) {
                // 복원을 위한 재조회 자체가 실패하면(연결 실패와 같은 DB 장애 등) 복원을 포기하고 로그아웃 상태로 정리한다
                log.error("인증 복원을 위한 회원 재조회 실패 — 세션을 로그아웃 상태로 정리한다", exception);
                removeSessionUserId(request);
            }
        }
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    // 세션 userId만 남고 SecurityContext는 비어 어긋나지 않도록, 복원 실패(예외)와 회원 없음(Optional.empty) 둘 다 여기로 모은다
    private void removeSessionUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE);
        }
    }

    private String redirectUri(boolean success, String reason) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(oauth2Properties.frontendUri() + "/oauth/callback")
                .queryParam("success", success);
        if (reason != null) {
            builder.queryParam("reason", reason);
        }
        return builder.build().encode().toUriString();
    }
}
