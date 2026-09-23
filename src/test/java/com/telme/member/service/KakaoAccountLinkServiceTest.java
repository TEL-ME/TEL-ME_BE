package com.telme.member.service;

import static com.telme.chat.service.HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE;
import static com.telme.member.entity.SocialAccount.Provider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.converter.MemberConverter;
import com.telme.member.dto.req.KakaoLinkConfirmRequest;
import com.telme.member.dto.res.LoginResponse;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class KakaoAccountLinkServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final MemberStatusChecker memberStatusChecker = new MemberStatusChecker();
    private final SocialMemberFinder socialMemberFinder = mock(SocialMemberFinder.class);
    private final GuestSuccessionService guestSuccessionService = mock(GuestSuccessionService.class);
    private final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
    private final LoginCompletionService loginCompletionService = new LoginCompletionService(securityContextRepository);
    private final GuestIdResolver guestIdResolver = new GuestIdResolver();
    private final MemberConverter memberConverter = new MemberConverter();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-23T00:00:00Z"), ZoneOffset.UTC);
    private final PendingKakaoLinkStore pendingKakaoLinkStore = new PendingKakaoLinkStore(clock);
    private final TransactionTemplate transactionTemplate = new TransactionTemplate() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final KakaoAccountLinkService service = new KakaoAccountLinkService(
            pendingKakaoLinkStore, userRepository, passwordEncoder, memberStatusChecker, socialMemberFinder,
            guestSuccessionService, loginCompletionService, guestIdResolver, memberConverter, transactionTemplate);

    @Test
    @DisplayName("비밀번호가 맞으면 연결하고 로그인 처리한 뒤 pending 정보를 지운다")
    void 정상_연결() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        pendingKakaoLinkStore.issue(request, "kakao-1", 10L, "match@example.com");
        User matchedUser = User.builder().userId(10L).email("match@example.com").passwordHash("HASHED").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(matchedUser));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);
        when(socialMemberFinder.linkExisting(KAKAO, "kakao-1", "match@example.com", matchedUser)).thenReturn(matchedUser);

        LoginResponse response = service.confirmLink(
                new KakaoLinkConfirmRequest("password123"), request, new MockHttpServletResponse());

        assertThat(response).isEqualTo(new LoginResponse(10L, "match@example.com"));
        verify(socialMemberFinder).linkExisting(KAKAO, "kakao-1", "match@example.com", matchedUser);
        assertThatThrownBy(() -> pendingKakaoLinkStore.require(request)).isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 실패 시도를 기록하고 연결하지 않는다")
    void 비밀번호_틀리면_실패_기록() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        pendingKakaoLinkStore.issue(request, "kakao-1", 10L, "match@example.com");
        User matchedUser = User.builder().userId(10L).email("match@example.com").passwordHash("HASHED").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(matchedUser));
        when(passwordEncoder.matches("wrong", "HASHED")).thenReturn(false);

        assertThatThrownBy(() -> service.confirmLink(
                new KakaoLinkConfirmRequest("wrong"), request, new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);

        verify(socialMemberFinder, never()).linkExisting(any(), any(), any(), any());
        PendingKakaoLink pending = pendingKakaoLinkStore.require(request);
        assertThat(pending.failedAttempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("연결 대상 회원이 정지 상태면 거부한다")
    void 정지된_회원은_거부() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        pendingKakaoLinkStore.issue(request, "kakao-1", 10L, "match@example.com");
        User matchedUser = User.builder().userId(10L).email("match@example.com").passwordHash("HASHED")
                .status(User.Status.SUSPENDED).build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(matchedUser));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);

        assertThatThrownBy(() -> service.confirmLink(
                new KakaoLinkConfirmRequest("password123"), request, new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.ACCOUNT_SUSPENDED);
        verify(socialMemberFinder, never()).linkExisting(any(), any(), any(), any());
    }

    @Test
    @DisplayName("pending 정보가 없으면 만료 예외를 던진다")
    void pending_없으면_예외() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> service.confirmLink(
                new KakaoLinkConfirmRequest("password123"), request, new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.KAKAO_LINK_SESSION_EXPIRED);
    }

    @Test
    @DisplayName("guestId가 있으면 연결 완료 후 게스트를 승계한다")
    void guestId_있으면_승계() {
        MockHttpSession session = new MockHttpSession();
        UUID guestId = UUID.randomUUID();
        session.setAttribute(GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        pendingKakaoLinkStore.issue(request, "kakao-1", 10L, "match@example.com");
        User matchedUser = User.builder().userId(10L).email("match@example.com").passwordHash("HASHED").build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(matchedUser));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);
        when(socialMemberFinder.linkExisting(KAKAO, "kakao-1", "match@example.com", matchedUser)).thenReturn(matchedUser);

        service.confirmLink(new KakaoLinkConfirmRequest("password123"), request, new MockHttpServletResponse());

        verify(guestSuccessionService).succeedGuest(eq(guestId), eq(matchedUser));
    }
}
