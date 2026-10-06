package com.telme.member.service;

import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.config.Oauth2Properties;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
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
import org.springframework.web.util.UriComponentsBuilder;

// OAuth2UserService는 공급자 원본 클레임과 공통 소셜 principal을 넘긴다 — 로그인인지 계정연결인지,
// 어떤 회원으로 귀결되는지는 세션에 접근 가능한 여기서 전부 판단한다
//
// 소셜 로그인 흐름 3가지 : 
// A-1(로그인 회원이 카카오·구글 연결 시작 — resolveLinkMode)
// B(소셜 로그인 중 이메일 일치 회원 발견 — resolveLoginMode에서 시작해 SocialAccountLinkService.confirmLink로 이어짐)
// A-2(소셜 전용 회원이 이메일 로그인을 추가 — 소셜 인증 자체가 없어 이 클래스를 거치지 않고 EmailLoginMethodService가 처리)
@Slf4j
@Component
@RequiredArgsConstructor
public class SocialLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String DEFAULT_FAILURE_REASON = "OAUTH2_LOGIN_FAILED";

    private final SocialMemberFinder socialMemberFinder;
    private final UserRepository userRepository;
    private final MemberStatusChecker memberStatusChecker;
    private final SocialLoginService socialLoginService;
    private final SocialLinkRequestStore socialLinkRequestStore;
    private final SocialEmailMatchStore socialEmailMatchStore;
    private final SecurityContextRepository securityContextRepository;
    private final Oauth2Properties oauth2Properties;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        try {
            SocialOAuth2Principal principal = (SocialOAuth2Principal) authentication.getPrincipal();
            User user = resolveUser(request, response, principal);
            if (user == null) {
                return;
            }
            finalizeSession(user, request, response);
        } catch (RuntimeException exception) {
            // 예상 밖 오류(DB 연결 오류, 동시 생성 재조회 실패 등)까지 여기서 막는다 — 원인은 내부 로그에만 남기고 프론트에는 고정 사유만 보낸다
            log.error("소셜 로그인 처리 중 예상하지 못한 오류", exception);
            redirectFailure(request, response, DEFAULT_FAILURE_REASON);
        }
    }

    private User resolveUser(
            HttpServletRequest request, HttpServletResponse response, SocialOAuth2Principal principal)
            throws IOException {
        socialEmailMatchStore.clear(request);

        SocialLinkRequest pending;
        try {
            pending = socialLinkRequestStore.consume(request);
        } catch (GeneralException exception) {
            redirectFailure(request, response, exception.getErrorCode().getCode());
            return null;
        }
        if (pending != null) {
            return resolveLinkMode(request, response, pending, principal);
        }
        return resolveLoginMode(request, response, principal);
    }

    private User resolveLinkMode(
            HttpServletRequest request, HttpServletResponse response, SocialLinkRequest pending,
            SocialOAuth2Principal principal)
            throws IOException {
        // pending만 믿지 않고 세션의 현재 로그인 상태와 대조 — 다른 탭에서 로그인 상태가 바뀌었을 수 있다
        // 프론트 호환을 위해 공급자와 관계없이 기존 사유값을 유지한다
        if (!pending.targetUserId().equals(readSessionUserId(request))) {
            redirectFailure(request, response, "KAKAO_LINK_SESSION_MISMATCH");
            return null;
        }
        // 인가 시작 시 registrationId를 대조했지만, 콜백에서 실제로 인증된 공급자도 한 번 더 확인한다
        if (pending.provider() != principal.getProvider()) {
            redirectFailure(request, response, MemberErrorCode.SOCIAL_LINK_SESSION_EXPIRED.getCode());
            return null;
        }
        try {
            User targetUser = userRepository.findById(pending.targetUserId())
                    .orElseThrow(() -> new GeneralException(MemberErrorCode.UNAUTHENTICATED));
            memberStatusChecker.checkActive(targetUser);
            return socialMemberFinder.linkExisting(
                    pending.provider(), principal.getProviderUserId(), principal.getEmail(), targetUser);
        } catch (GeneralException exception) {
            redirectFailure(request, response, exception.getErrorCode().getCode());
            return null;
        }
    }

    private User resolveLoginMode(
            HttpServletRequest request, HttpServletResponse response, SocialOAuth2Principal principal)
            throws IOException {
        try {
            return socialLoginService.findOrCreate(principal);
        } catch (SocialEmailAlreadyLinkedException exception) {
            socialEmailMatchStore.issue(
                    request, principal.getProvider(), principal.getProviderUserId(),
                    exception.getMatchedUserId(), exception.getMatchedEmail());
            redirectFailure(request, response, MemberErrorCode.EMAIL_LINK_REQUIRED.getCode());
            return null;
        } catch (GeneralException exception) {
            redirectFailure(request, response, exception.getErrorCode().getCode());
            return null;
        }
    }

    private void finalizeSession(User user, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!socialLoginService.completeLogin(user, request, response)) {
            logoutHandler.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
            redirectFailure(request, response, "GUEST_SUCCESSION_FAILED");
            return;
        }

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
                        existingUser -> {
                            memberStatusChecker.checkActive(existingUser);
                            context.setAuthentication(
                                    new UsernamePasswordAuthenticationToken(existingUser.getUserId(), null,
                                            List.of(new SimpleGrantedAuthority("ROLE_" + existingUser.getRole().name()))));
                        },
                        () -> removeSessionUserId(request));
            } catch (GeneralException exception) {
                removeSessionUserId(request);
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
