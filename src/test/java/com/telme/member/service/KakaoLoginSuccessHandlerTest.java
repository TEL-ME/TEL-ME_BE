package com.telme.member.service;

import static com.telme.chat.service.HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE;
import static com.telme.chat.service.HttpSessionChatActorProvider.USER_ID_ATTRIBUTE;
import static com.telme.member.entity.SocialAccount.Provider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.config.Oauth2Properties;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class KakaoLoginSuccessHandlerTest {

    private final SocialMemberFinder socialMemberFinder = mock(SocialMemberFinder.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MemberStatusChecker memberStatusChecker = new MemberStatusChecker();
    private final GuestSuccessionService guestSuccessionService = mock(GuestSuccessionService.class);
    private final GuestIdResolver guestIdResolver = new GuestIdResolver();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);
    private final KakaoLinkRequestStore kakaoLinkRequestStore = new KakaoLinkRequestStore(clock);
    private final KakaoEmailMatchStore kakaoEmailMatchStore = new KakaoEmailMatchStore(clock);
    private final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
    private final Oauth2Properties oauth2Properties = new Oauth2Properties("http://localhost:3000");
    private final TransactionTemplate transactionTemplate = new TransactionTemplate() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final KakaoLoginSuccessHandler handler = new KakaoLoginSuccessHandler(
            socialMemberFinder, userRepository, memberStatusChecker, guestSuccessionService, guestIdResolver,
            kakaoLinkRequestStore, kakaoEmailMatchStore, securityContextRepository, oauth2Properties, transactionTemplate);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("일반 로그인 - guestId가 있고 승계에 성공하면 세션 반영 후 성공 URL로 리다이렉트한다")
    void 일반_로그인_승계_성공시_세션_반영_후_리다이렉트() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID guestId = UUID.randomUUID();
        request.getSession().setAttribute(GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = User.builder().userId(5L).build();
        when(socialMemberFinder.findOrCreate(KAKAO, "kakao-1", null)).thenReturn(user);

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-1", null));

        verify(guestSuccessionService).succeedGuest(guestId, user);
        assertThat(request.getSession().getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(5L);
        assertThat(request.getSession().getAttribute(GUEST_ID_ATTRIBUTE)).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback?success=true");
    }

    @Test
    @DisplayName("일반 로그인 - guestId가 없으면 승계를 호출하지 않고 세션에 userId만 반영한다")
    void 일반_로그인_guestId_없으면_승계_생략() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = User.builder().userId(9L).build();
        when(socialMemberFinder.findOrCreate(KAKAO, "kakao-2", null)).thenReturn(user);

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-2", null));

        verify(guestSuccessionService, never()).succeedGuest(any(), any());
        assertThat(request.getSession().getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(9L);
    }

    @Test
    @DisplayName("일반 로그인 - 승계가 실패하면 인증·세션을 정리하고 실패 URL로 리다이렉트한다")
    void 일반_로그인_승계_실패시_인증과_세션을_정리한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        UUID guestId = UUID.randomUUID();
        request.getSession().setAttribute(GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = User.builder().userId(5L).build();
        when(socialMemberFinder.findOrCreate(KAKAO, "kakao-3", null)).thenReturn(user);
        doThrow(new RuntimeException("강제 실패")).when(guestSuccessionService).succeedGuest(any(), any());
        Authentication authentication = authenticationOf("kakao-3", null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            handler.onAuthenticationSuccess(request, response, authentication);

            assertThat(request.getSession(false)).isNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            assertThat(response.getRedirectedUrl())
                    .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=GUEST_SUCCESSION_FAILED");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("일반 로그인 - 이메일이 기존 회원과 겹치면 pending 연결 정보를 저장하고 실패 URL로 리다이렉트한다")
    void 이메일_충돌시_pending_저장_후_리다이렉트() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(socialMemberFinder.findOrCreate(KAKAO, "kakao-4", "match@example.com"))
                .thenThrow(new SocialEmailAlreadyLinkedException(20L, "match@example.com"));

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-4", "match@example.com"));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=MEMBER409-1");
        // KakaoEmailMatchStore(5-2)에 저장됐는지 require()로 확인
        KakaoEmailMatch pending = kakaoEmailMatchStore.require(request);
        assertThat(pending.providerUserId()).isEqualTo("kakao-4");
        assertThat(pending.matchedUserId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("일반 로그인 - 회원 상태 오류면 실패 URL로 리다이렉트한다")
    void 회원_상태_오류면_리다이렉트() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(socialMemberFinder.findOrCreate(KAKAO, "kakao-5", null))
                .thenThrow(new GeneralException(MemberErrorCode.ACCOUNT_SUSPENDED));

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-5", null));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=MEMBER403-0");
    }

    @Test
    @DisplayName("일반 로그인 - 예상 밖 런타임 오류가 나도 예외를 던지지 않고 고정 사유로 리다이렉트하며 SecurityContext를 비운다")
    void 예상밖_오류는_고정_사유로_리다이렉트() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(socialMemberFinder.findOrCreate(KAKAO, "kakao-14", null))
                .thenThrow(new IllegalStateException("동시 생성 재조회 실패 시뮬레이션"));

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-14", null));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=OAUTH2_LOGIN_FAILED");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("연결 모드 - pending과 세션 userId가 일치하면 연결하고 세션을 유지한다")
    void 연결_모드_정상_연결() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(USER_ID_ATTRIBUTE, 30L);
        kakaoLinkRequestStore.issue(request, 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        User targetUser = User.builder().userId(30L).build();
        when(userRepository.findById(30L)).thenReturn(Optional.of(targetUser));
        when(socialMemberFinder.linkExisting(KAKAO, "kakao-6", "kakao@example.com", targetUser))
                .thenReturn(targetUser);

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-6", "kakao@example.com"));

        verify(socialMemberFinder).linkExisting(KAKAO, "kakao-6", "kakao@example.com", targetUser);
        assertThat(request.getSession().getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(30L);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://localhost:3000/oauth/callback?success=true");
    }

    @Test
    @DisplayName("연결 모드 - pending의 targetUserId와 세션 userId가 다르면 연결하지 않는다")
    void 연결_모드_세션_불일치시_거부() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(USER_ID_ATTRIBUTE, 99L);
        kakaoLinkRequestStore.issue(request, 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-7", null));

        verify(socialMemberFinder, never()).linkExisting(any(), any(), any(), any());
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=KAKAO_LINK_SESSION_MISMATCH");
    }

    @Test
    @DisplayName("연결 모드 - 대상 회원이 정지 상태면 연결하지 않는다")
    void 연결_모드_정지_회원은_거부() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(USER_ID_ATTRIBUTE, 30L);
        kakaoLinkRequestStore.issue(request, 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        User targetUser = User.builder().userId(30L).status(User.Status.SUSPENDED).build();
        when(userRepository.findById(30L)).thenReturn(Optional.of(targetUser));

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-8", null));

        verify(socialMemberFinder, never()).linkExisting(any(), any(), any(), any());
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=MEMBER403-0");
    }

    @Test
    @DisplayName("연결 모드 - 이미 다른 회원에 연결된 카카오 계정이면 거부한다")
    void 연결_모드_이미_연결된_계정이면_거부() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(USER_ID_ATTRIBUTE, 30L);
        kakaoLinkRequestStore.issue(request, 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        User targetUser = User.builder().userId(30L).build();
        when(userRepository.findById(30L)).thenReturn(Optional.of(targetUser));
        when(socialMemberFinder.linkExisting(KAKAO, "kakao-9", null, targetUser))
                .thenThrow(new GeneralException(MemberErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED));

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-9", null));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=MEMBER409-2");
    }

    @Test
    @DisplayName("연결 모드 - 실패해도 세션을 무효화하지 않고 원래(연결 시도 전) 회원 인증으로 복원한다")
    void 연결_모드_실패시_원래_회원_인증으로_복원() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(USER_ID_ATTRIBUTE, 30L);
        kakaoLinkRequestStore.issue(request, 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        User targetUser = User.builder().userId(30L).role(User.Role.ADMIN).build();
        when(userRepository.findById(30L)).thenReturn(Optional.of(targetUser));
        when(socialMemberFinder.linkExisting(KAKAO, "kakao-10", null, targetUser))
                .thenThrow(new GeneralException(MemberErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED));

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-10", null));

        // 세션이 살아있어야 B pending 등 다른 상태가 보존된다(로그아웃/무효화 아님)
        assertThat(request.getSession(false)).isNotNull();
        assertThat(request.getSession(false).getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(30L);
        Authentication restored = SecurityContextHolder.getContext().getAuthentication();
        assertThat(restored.getPrincipal()).isEqualTo(30L);
        assertThat(restored.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        verify(securityContextRepository).saveContext(any(), any(), any());
    }

    @Test
    @DisplayName("연결 모드 - 예상 밖 오류가 나도 원래 회원 인증으로 복원한다")
    void 연결_모드_예상밖_오류도_원래_회원_인증으로_복원() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(USER_ID_ATTRIBUTE, 30L);
        kakaoLinkRequestStore.issue(request, 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        User targetUser = User.builder().userId(30L).build();
        when(userRepository.findById(30L)).thenReturn(Optional.of(targetUser));
        when(socialMemberFinder.linkExisting(KAKAO, "kakao-15", null, targetUser))
                .thenThrow(new IllegalStateException("예상 밖 오류 시뮬레이션"));

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-15", null));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=OAUTH2_LOGIN_FAILED");
        Authentication restored = SecurityContextHolder.getContext().getAuthentication();
        assertThat(restored.getPrincipal()).isEqualTo(30L);
    }

    @Test
    @DisplayName("연결 모드 - 인증 복원을 위한 재조회마저 실패하면 예외를 던지지 않고 로그아웃 상태로 정리한다")
    void 연결_모드_복원용_재조회도_실패하면_로그아웃_상태로_정리() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(USER_ID_ATTRIBUTE, 30L);
        kakaoLinkRequestStore.issue(request, 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        User targetUser = User.builder().userId(30L).build();
        when(userRepository.findById(30L))
                .thenReturn(Optional.of(targetUser))
                .thenThrow(new RuntimeException("DB 장애 시뮬레이션 — 복원용 재조회"));
        when(socialMemberFinder.linkExisting(KAKAO, "kakao-16", null, targetUser))
                .thenThrow(new IllegalStateException("DB 장애로 인한 연결 실패 시뮬레이션"));

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-16", null));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=OAUTH2_LOGIN_FAILED");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false).getAttribute(USER_ID_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("연결 모드 - 복원 대상 회원이 그 사이 삭제됐으면(Optional.empty) 세션 userId도 함께 지운다")
    void 연결_모드_복원_대상_회원이_없으면_세션_userId도_지운다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(USER_ID_ATTRIBUTE, 30L);
        kakaoLinkRequestStore.issue(request, 30L);
        MockHttpServletResponse response = new MockHttpServletResponse();
        User targetUser = User.builder().userId(30L).build();
        when(userRepository.findById(30L))
                .thenReturn(Optional.of(targetUser))
                .thenReturn(Optional.empty());
        when(socialMemberFinder.linkExisting(KAKAO, "kakao-17", null, targetUser))
                .thenThrow(new GeneralException(MemberErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED));

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-17", null));

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=MEMBER409-2");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getSession(false).getAttribute(USER_ID_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("일반 로그인 - 실패하면 임시 카카오 인증을 남기지 않고 SecurityContext를 비운다")
    void 일반_로그인_실패시_SecurityContext를_비운다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(socialMemberFinder.findOrCreate(KAKAO, "kakao-11", null))
                .thenThrow(new GeneralException(MemberErrorCode.ACCOUNT_SUSPENDED));

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-11", null));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("이전에 남아있던 B pending은 이번 로그인 결과와 섞이지 않고 지워진다")
    void 이전_B_pending은_섞이지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        kakaoEmailMatchStore.issue(request, "old-kakao-id", 999L, "old@example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();
        User user = User.builder().userId(5L).build();
        when(socialMemberFinder.findOrCreate(KAKAO, "kakao-12", null)).thenReturn(user);

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-12", null));

        assertThat(request.getSession(false).getAttribute(KakaoEmailMatchStore.SESSION_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("연결 pending이 만료됐으면 일반 로그인으로 새지 않고 만료 실패로 리다이렉트한다")
    void 연결_pending_만료시_일반_로그인으로_새지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        Instant longAgo = clock.instant().minusSeconds(600);
        request.getSession().setAttribute(KakaoLinkRequestStore.SESSION_ATTRIBUTE, new KakaoLinkRequest(30L, longAgo));
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authenticationOf("kakao-13", null));

        verify(socialMemberFinder, never()).findOrCreate(any(), any(), any());
        verify(socialMemberFinder, never()).linkExisting(any(), any(), any(), any());
        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:3000/oauth/callback?success=false&reason=MEMBER400-0");
    }

    private Authentication authenticationOf(String providerUserId, String email) {
        OAuth2User principal = new KakaoOAuth2User(providerUserId, email, Map.of("id", providerUserId));
        return new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "kakao");
    }
}
