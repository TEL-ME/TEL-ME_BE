package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.feedback.repository.FeedbackStore;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.converter.MemberConverter;
import com.telme.member.dto.req.LoginRequest;
import com.telme.member.dto.req.SignUpRequest;
import com.telme.member.dto.res.LoginResponse;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.GuestRepository;
import com.telme.member.repository.UserRepository;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class MemberAuthServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final GuestRepository guestRepository = mock(GuestRepository.class);
    private final ChatSessionRepository chatSessionRepository = mock(ChatSessionRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final MemberConverter memberConverter = mock(MemberConverter.class);
    private final SecurityContextRepository securityContextRepository = mock(SecurityContextRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-22T00:00:00Z"), ZoneOffset.UTC);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<FeedbackStore> feedbackStoreProvider = mock(ObjectProvider.class);
    // 콜백을 즉시 실행하는 가짜 트랜잭션 — 대부분의 테스트는 커밋이 항상 성공한다고 가정한다
    private final TransactionTemplate transactionTemplate = new TransactionTemplate() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final MemberAuthService memberAuthService = new MemberAuthService(
            userRepository, guestRepository, chatSessionRepository, passwordEncoder,
            memberConverter, securityContextRepository, clock, feedbackStoreProvider, transactionTemplate);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("신규 이메일이면 비밀번호를 해싱해 저장하고 자동 로그인 처리된다")
    void 정상_가입() {
        SignUpRequest request = new SignUpRequest("new@example.com", "password123");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        User saved = User.builder().userId(1L).email("new@example.com").passwordHash("HASHED").build();
        when(userRepository.save(any(User.class))).thenReturn(saved);
        SignUpResponse expected = new SignUpResponse(1L, "new@example.com");
        when(memberConverter.toSignUpResponse(saved)).thenReturn(expected);

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        MockHttpServletResponse httpResponse = new MockHttpServletResponse();

        SignUpResponse response = memberAuthService.signUp(request, httpRequest, httpResponse);

        assertThat(response).isEqualTo(expected);
        assertThat(httpRequest.getSession(false).getAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE))
                .isEqualTo(1L);
        verify(securityContextRepository).saveContext(any(), any(), any());
        verify(guestRepository, never()).succeedGuest(any(), any(), any());
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 예외를 던지고 로그인 처리를 하지 않는다")
    void 이메일_중복이면_예외() {
        SignUpRequest request = new SignUpRequest("dup@example.com", "password123");
        when(userRepository.findByEmail("dup@example.com"))
                .thenReturn(Optional.of(User.builder().userId(1L).email("dup@example.com").build()));

        assertThatThrownBy(() -> memberAuthService.signUp(
                request, new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.EMAIL_ALREADY_EXISTS);
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
    }

    @Test
    @DisplayName("동시 가입으로 findByEmail 통과 후 DB UNIQUE 제약에 걸려도 동일한 예외로 처리한다")
    void 동시_가입은_UNIQUE_제약으로_처리된다() {
        SignUpRequest request = new SignUpRequest("race@example.com", "password123");
        when(userRepository.findByEmail("race@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        // Spring Data JPA는 UNIQUE 위반을 DuplicateKeyException이 아니라 DataIntegrityViolationException으로 번역하고,
        // 그 원인(cause)에 Hibernate의 ConstraintViolationException을 담는다 — 실제 로컬 Postgres로 재현해 확인한 모양 그대로 흉내낸다
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "could not execute statement", new SQLException("duplicate key", "23505"), "users_email_key");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("insert failed", constraintViolation));

        assertThatThrownBy(() -> memberAuthService.signUp(
                request, new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("UNIQUE 위반이 아닌 데이터 무결성 오류는 이메일 중복으로 바뀌지 않는다")
    void UNIQUE_위반이_아니면_그대로_전파된다() {
        SignUpRequest request = new SignUpRequest("toolong@example.com", "password123");
        when(userRepository.findByEmail("toolong@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("value too long for type character varying(255)"));

        assertThatThrownBy(() -> memberAuthService.signUp(
                request, new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("이메일이 아닌 다른 UNIQUE 제약 위반은 이메일 중복으로 바뀌지 않는다")
    void 다른_UNIQUE_제약_위반은_이메일_중복으로_바뀌지_않는다() {
        SignUpRequest request = new SignUpRequest("other-constraint@example.com", "password123");
        when(userRepository.findByEmail("other-constraint@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        ConstraintViolationException otherConstraint = new ConstraintViolationException(
                "could not execute statement", new SQLException("unique violation", "23505"), "some_other_key");
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("insert failed", otherConstraint));

        assertThatThrownBy(() -> memberAuthService.signUp(
                request, new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("정상 로그인이면 세션에 userId가 저장된다")
    void 정상_로그인() {
        User user = User.builder().userId(1L).email("login@example.com").passwordHash("HASHED").build();
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);
        when(memberConverter.toLoginResponse(user)).thenReturn(new LoginResponse(1L, "login@example.com"));

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        LoginResponse response = memberAuthService.login(
                new LoginRequest("login@example.com", "password123"), httpRequest, new MockHttpServletResponse());

        assertThat(response).isEqualTo(new LoginResponse(1L, "login@example.com"));
        assertThat(httpRequest.getSession(false).getAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 이메일이면 로그인 실패 처리한다")
    void 이메일이_없으면_로그인_실패() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberAuthService.login(
                new LoginRequest("nobody@example.com", "password123"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("비밀번호가 틀리면 이메일이 없을 때와 동일한 에러로 로그인 실패 처리한다")
    void 비밀번호가_틀리면_로그인_실패() {
        User user = User.builder().userId(1L).email("login@example.com").passwordHash("HASHED").build();
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "HASHED")).thenReturn(false);

        assertThatThrownBy(() -> memberAuthService.login(
                new LoginRequest("login@example.com", "wrong"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("정지된 계정은 비밀번호가 맞아도 로그인할 수 없다")
    void 정지된_계정은_로그인_거부() {
        User user = User.builder().userId(1L).email("login@example.com").passwordHash("HASHED")
                .status(User.Status.SUSPENDED).build();
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);

        assertThatThrownBy(() -> memberAuthService.login(
                new LoginRequest("login@example.com", "password123"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.ACCOUNT_SUSPENDED);
        verify(guestRepository, never()).succeedGuest(any(), any(), any());
    }

    @Test
    @DisplayName("탈퇴한 계정은 비밀번호가 맞아도 로그인할 수 없다")
    void 탈퇴한_계정은_로그인_거부() {
        User user = User.builder().userId(1L).email("login@example.com").passwordHash("HASHED")
                .status(User.Status.WITHDRAWN).build();
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);

        assertThatThrownBy(() -> memberAuthService.login(
                new LoginRequest("login@example.com", "password123"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.ACCOUNT_WITHDRAWN);
        verify(guestRepository, never()).succeedGuest(any(), any(), any());
    }

    @Test
    @DisplayName("세션에 guestId가 있으면 게스트를 승계하고 세션 ID를 재발급한다")
    void 로그인시_게스트를_승계하고_세션ID를_재발급한다() {
        User user = User.builder().userId(1L).email("login@example.com").passwordHash("HASHED").build();
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);
        when(memberConverter.toLoginResponse(user)).thenReturn(new LoginResponse(1L, "login@example.com"));

        UUID guestId = UUID.randomUUID();
        when(guestRepository.succeedGuest(eq(guestId), eq(user), any())).thenReturn(1);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(session);
        String sessionIdBefore = session.getId();

        memberAuthService.login(
                new LoginRequest("login@example.com", "password123"), httpRequest, new MockHttpServletResponse());

        verify(guestRepository).succeedGuest(guestId, user, clock.instant());
        verify(chatSessionRepository).succeedGuestSessions(guestId, 1L);
        assertThat(httpRequest.getSession(false).getId()).isNotEqualTo(sessionIdBefore);
        assertThat(httpRequest.getSession(false).getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE))
                .isNull();
    }

    @Test
    @DisplayName("FeedbackStore가 등록돼 있으면 게스트 피드백도 함께 승계한다")
    void FeedbackStore가_있으면_피드백도_승계한다() {
        User user = User.builder().userId(1L).email("login@example.com").passwordHash("HASHED").build();
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);
        when(memberConverter.toLoginResponse(user)).thenReturn(new LoginResponse(1L, "login@example.com"));

        UUID guestId = UUID.randomUUID();
        when(guestRepository.succeedGuest(eq(guestId), eq(user), any())).thenReturn(1);

        FeedbackStore feedbackStore = mock(FeedbackStore.class);
        doAnswer(invocation -> {
            Consumer<FeedbackStore> consumer = invocation.getArgument(0);
            consumer.accept(feedbackStore);
            return null;
        }).when(feedbackStoreProvider).ifAvailable(any());

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(session);

        memberAuthService.login(
                new LoginRequest("login@example.com", "password123"), httpRequest, new MockHttpServletResponse());

        verify(feedbackStore).succeedGuestFeedback(guestId, 1L);
    }

    @Test
    @DisplayName("FeedbackStore가 없어도(기능 비활성화) 로그인은 정상 동작한다")
    void FeedbackStore가_없어도_로그인된다() {
        User user = User.builder().userId(1L).email("login@example.com").passwordHash("HASHED").build();
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);
        when(memberConverter.toLoginResponse(user)).thenReturn(new LoginResponse(1L, "login@example.com"));

        UUID guestId = UUID.randomUUID();
        when(guestRepository.succeedGuest(eq(guestId), eq(user), any())).thenReturn(1);
        // feedbackStoreProvider.ifAvailable()는 스텁하지 않는다 — 빈 없음(비활성화)을 흉내낸다

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(session);

        LoginResponse response = memberAuthService.login(
                new LoginRequest("login@example.com", "password123"), httpRequest, new MockHttpServletResponse());

        assertThat(response).isEqualTo(new LoginResponse(1L, "login@example.com"));
        verify(chatSessionRepository).succeedGuestSessions(guestId, 1L);
    }

    @Test
    @DisplayName("DB 작업은 전부 성공했지만 트랜잭션 커밋이 실패하면 세션은 로그인 상태로 바뀌지 않는다")
    void 커밋_실패시_세션에_로그인_흔적이_남지_않는다() {
        User user = User.builder().userId(1L).email("login@example.com").passwordHash("HASHED").build();
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);

        UUID guestId = UUID.randomUUID();
        when(guestRepository.succeedGuest(eq(guestId), eq(user), any())).thenReturn(1);

        // 콜백(DB 작업)은 그대로 실행해 전부 성공시키되, 그 이후 커밋 자체가 실패하는 상황을 흉내낸다
        TransactionTemplate committingThenFailingTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                action.doInTransaction(null);
                throw new RuntimeException("commit 실패 시뮬레이션");
            }
        };
        MemberAuthService service = new MemberAuthService(
                userRepository, guestRepository, chatSessionRepository, passwordEncoder,
                memberConverter, securityContextRepository, clock, feedbackStoreProvider,
                committingThenFailingTemplate);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(session);
        String sessionIdBefore = session.getId();

        assertThatThrownBy(() -> service.login(
                new LoginRequest("login@example.com", "password123"), httpRequest, new MockHttpServletResponse()))
                .isInstanceOf(RuntimeException.class);

        assertThat(session.getId()).isEqualTo(sessionIdBefore);
        assertThat(session.getAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE)).isEqualTo(guestId);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(securityContextRepository, never()).saveContext(any(), any(), any());
    }

    @Test
    @DisplayName("이미 다른 회원에게 승계된 게스트면 재승계하지 않는다")
    void 이미_승계된_게스트는_재승계하지_않는다() {
        User secondUser = User.builder().userId(2L).email("second@example.com").passwordHash("HASHED").build();
        when(userRepository.findByEmail("second@example.com")).thenReturn(Optional.of(secondUser));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);
        when(memberConverter.toLoginResponse(secondUser)).thenReturn(new LoginResponse(2L, "second@example.com"));

        UUID guestId = UUID.randomUUID();
        // merged_user_id가 이미 채워져 있어 원자적 UPDATE가 0건을 갱신한 상황(동시 승계 레이스의 패자 포함)을 흉내낸다
        when(guestRepository.succeedGuest(eq(guestId), eq(secondUser), any())).thenReturn(0);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(session);

        memberAuthService.login(
                new LoginRequest("second@example.com", "password123"), httpRequest, new MockHttpServletResponse());

        verify(chatSessionRepository, never()).succeedGuestSessions(any(), any());
    }

    @Test
    @DisplayName("게스트 이력이 없어도 로그인은 정상 동작한다")
    void 게스트_이력_없이도_로그인된다() {
        User user = User.builder().userId(1L).email("login@example.com").passwordHash("HASHED").build();
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);
        when(memberConverter.toLoginResponse(user)).thenReturn(new LoginResponse(1L, "login@example.com"));

        memberAuthService.login(
                new LoginRequest("login@example.com", "password123"),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        verify(guestRepository, never()).succeedGuest(any(), any(), any());
        verify(chatSessionRepository, never()).succeedGuestSessions(any(), any());
    }

    @Test
    @DisplayName("로그아웃하면 세션이 무효화되고 SecurityContext가 비워진다")
    void 로그아웃하면_세션이_무효화된다() {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(session);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(1L, null, List.of()));
        SecurityContextHolder.setContext(context);

        memberAuthService.logout(httpRequest, new MockHttpServletResponse());

        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("세션 없이 로그아웃을 호출해도 예외 없이 처리된다")
    void 세션_없이_로그아웃해도_예외_없다() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();

        memberAuthService.logout(httpRequest, new MockHttpServletResponse());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("로그인하면 SecurityContext에 userId를 principal로 하는 Authentication이 저장된다")
    void SecurityContext에_Authentication이_저장된다() {
        User user = User.builder().userId(1L).email("login@example.com").passwordHash("HASHED").build();
        when(userRepository.findByEmail("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "HASHED")).thenReturn(true);
        when(memberConverter.toLoginResponse(user)).thenReturn(new LoginResponse(1L, "login@example.com"));

        memberAuthService.login(
                new LoginRequest("login@example.com", "password123"),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        ArgumentCaptor<SecurityContext> captor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(securityContextRepository).saveContext(captor.capture(), any(), any());
        Authentication authentication = captor.getValue().getAuthentication();
        assertThat(authentication.getPrincipal()).isEqualTo(1L);
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_USER");
    }
}
