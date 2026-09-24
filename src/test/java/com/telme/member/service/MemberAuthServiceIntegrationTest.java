package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.dto.req.LoginRequest;
import com.telme.member.dto.req.SignUpRequest;
import com.telme.member.dto.res.LoginResponse;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.GuestRepository;
import com.telme.member.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MemberAuthServiceIntegrationTest {

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("가입한 이메일·해싱된 비밀번호가 DB에 그대로 저장된다")
    void 회원가입은_DB에_저장된다() {
        String email = "member-" + UUID.randomUUID() + "@example.com";
        SignUpRequest request = new SignUpRequest(email, "password123");

        SignUpResponse response = memberAuthService.signUp(
                request, new MockHttpServletRequest(), new MockHttpServletResponse());

        User saved = userRepository.findById(response.userId()).orElseThrow();
        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getPasswordHash()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("UTF-8 기준 72바이트 비밀번호는 실제 BCrypt 해싱까지 정상적으로 끝난다")
    void 비밀번호가_72바이트면_실제_해싱까지_성공한다() {
        String email = "bcrypt-boundary-" + UUID.randomUUID() + "@example.com";
        String password72Bytes = "a".repeat(72);
        SignUpRequest request = new SignUpRequest(email, password72Bytes);

        SignUpResponse response = memberAuthService.signUp(
                request, new MockHttpServletRequest(), new MockHttpServletResponse());

        User saved = userRepository.findById(response.userId()).orElseThrow();
        assertThat(passwordEncoder.matches(password72Bytes, saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("이미 가입된 이메일로 다시 가입하면 예외가 발생하고 두 번째 계정은 생기지 않는다")
    void 중복_이메일_가입은_거절된다() {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        memberAuthService.signUp(
                new SignUpRequest(email, "password123"), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThatThrownBy(() -> memberAuthService.signUp(
                new SignUpRequest(email, "different-password"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.EMAIL_ALREADY_EXISTS);

        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    @Test
    @DisplayName("정상 로그인 후 다음 요청에서 세션 userId로 회원임을 알 수 있다")
    void 정상_로그인() {
        String email = "login-" + UUID.randomUUID() + "@example.com";
        memberAuthService.signUp(
                new SignUpRequest(email, "password123"), new MockHttpServletRequest(), new MockHttpServletResponse());

        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        LoginResponse response = memberAuthService.login(
                new LoginRequest(email, "password123"), httpRequest, new MockHttpServletResponse());

        assertThat(response.email()).isEqualTo(email);
        assertThat(httpRequest.getSession(false).getAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE))
                .isEqualTo(response.userId());
    }

    @Test
    @DisplayName("존재하지 않는 이메일과 틀린 비밀번호 모두 같은 에러로 실패한다")
    void 로그인_실패는_원인을_구분하지_않는다() {
        String email = "exists-" + UUID.randomUUID() + "@example.com";
        memberAuthService.signUp(
                new SignUpRequest(email, "password123"), new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThatThrownBy(() -> memberAuthService.login(
                new LoginRequest("nobody-" + UUID.randomUUID() + "@example.com", "password123"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);

        assertThatThrownBy(() -> memberAuthService.login(
                new LoginRequest(email, "wrong-password"),
                new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("게스트로 만든 채팅이 로그인 후 회원 채팅으로 승계된다")
    void 로그인시_게스트_채팅이_승계된다() {
        Guest guest = Guest.issue(Duration.ofDays(30), clock);
        guestRepository.save(guest);
        ChatSession guestSession = ChatSession.builder().guestId(guest.getGuestId()).build();
        entityManager.persist(guestSession);
        entityManager.flush();

        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guest.getGuestId());
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(httpSession);

        String email = "succeed-" + UUID.randomUUID() + "@example.com";
        User user = User.builder().email(email).passwordHash(passwordEncoder.encode("password123")).build();
        userRepository.save(user);

        memberAuthService.login(new LoginRequest(email, "password123"), httpRequest, new MockHttpServletResponse());

        entityManager.clear();
        ChatSession succeeded = entityManager.find(ChatSession.class, guestSession.getSessionId());
        assertThat(succeeded.getUserId()).isEqualTo(user.getUserId());
        assertThat(succeeded.getGuestId()).isEqualTo(guest.getGuestId());

        Guest mergedGuest = guestRepository.findById(guest.getGuestId()).orElseThrow();
        assertThat(mergedGuest.getMergedUser().getUserId()).isEqualTo(user.getUserId());
        assertThat(mergedGuest.getMergedAt()).isNotNull();
    }

    @Test
    @DisplayName("게스트로 만든 채팅이 회원가입 후 회원 채팅으로 승계된다")
    void 회원가입시_게스트_채팅이_승계된다() {
        Guest guest = Guest.issue(Duration.ofDays(30), clock);
        guestRepository.save(guest);
        ChatSession guestSession = ChatSession.builder().guestId(guest.getGuestId()).build();
        entityManager.persist(guestSession);
        entityManager.flush();

        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guest.getGuestId());
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(httpSession);

        String email = "signup-succeed-" + UUID.randomUUID() + "@example.com";
        SignUpResponse response = memberAuthService.signUp(
                new SignUpRequest(email, "password123"), httpRequest, new MockHttpServletResponse());

        entityManager.clear();
        ChatSession succeeded = entityManager.find(ChatSession.class, guestSession.getSessionId());
        assertThat(succeeded.getUserId()).isEqualTo(response.userId());
        assertThat(succeeded.getGuestId()).isEqualTo(guest.getGuestId());

        Guest mergedGuest = guestRepository.findById(guest.getGuestId()).orElseThrow();
        assertThat(mergedGuest.getMergedUser().getUserId()).isEqualTo(response.userId());
        assertThat(mergedGuest.getMergedAt()).isNotNull();
    }
}
