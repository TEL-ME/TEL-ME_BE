package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.dto.req.EmailLoginMethodRequest;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class EmailLoginMethodServiceTest {

    private final CurrentMemberResolver currentMemberResolver = mock(CurrentMemberResolver.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final EmailUniqueConstraintChecker emailUniqueConstraintChecker = new EmailUniqueConstraintChecker();
    private final TransactionTemplate transactionTemplate = new TransactionTemplate() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final EmailLoginMethodService service = new EmailLoginMethodService(
            currentMemberResolver, userRepository, passwordEncoder, emailUniqueConstraintChecker, transactionTemplate);

    @Test
    @DisplayName("이메일이 없는 회원이면 이메일·비밀번호를 등록한다")
    void 정상_등록() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        User currentUser = User.builder().userId(1L).build();
        when(currentMemberResolver.resolve(httpRequest)).thenReturn(currentUser);
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.addEmailLogin(1L, "new@example.com", "HASHED")).thenReturn(1);

        SignUpResponse response = service.addEmailLogin(new EmailLoginMethodRequest("new@example.com", "password123"), httpRequest);

        assertThat(response).isEqualTo(new SignUpResponse(1L, "new@example.com"));
    }

    @Test
    @DisplayName("이미 이메일이 있는 회원이면 거부한다")
    void 이미_이메일이_있으면_거부() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        User currentUser = User.builder().userId(1L).email("old@example.com").build();
        when(currentMemberResolver.resolve(httpRequest)).thenReturn(currentUser);

        assertThatThrownBy(() -> service.addEmailLogin(
                new EmailLoginMethodRequest("new@example.com", "password123"), httpRequest))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.EMAIL_LOGIN_ALREADY_SET);
    }

    @Test
    @DisplayName("새 이메일이 다른 회원에게 이미 있으면 거부한다")
    void 이메일이_이미_존재하면_거부() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        User currentUser = User.builder().userId(1L).build();
        when(currentMemberResolver.resolve(httpRequest)).thenReturn(currentUser);
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.findByEmail("dup@example.com"))
                .thenReturn(Optional.of(User.builder().userId(2L).email("dup@example.com").build()));

        assertThatThrownBy(() -> service.addEmailLogin(
                new EmailLoginMethodRequest("dup@example.com", "password123"), httpRequest))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("동시 등록으로 findByEmail 통과 후 UNIQUE 제약에 걸려도 이메일 중복으로 처리한다")
    void 동시_등록은_UNIQUE_제약으로_처리된다() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        User currentUser = User.builder().userId(1L).build();
        when(currentMemberResolver.resolve(httpRequest)).thenReturn(currentUser);
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.findByEmail("race@example.com")).thenReturn(Optional.empty());
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "could not execute statement", new SQLException("duplicate key", "23505"), "users_email_key");
        doThrow(new DataIntegrityViolationException("insert failed", constraintViolation))
                .when(userRepository).addEmailLogin(eq(1L), eq("race@example.com"), any());

        assertThatThrownBy(() -> service.addEmailLogin(
                new EmailLoginMethodRequest("race@example.com", "password123"), httpRequest))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("같은 회원이 동시에 두 번 요청해 갱신이 0건이면 이미 등록된 것으로 처리한다")
    void 같은_회원의_동시_요청은_이미_등록된_것으로_처리() {
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        User currentUser = User.builder().userId(1L).build();
        when(currentMemberResolver.resolve(httpRequest)).thenReturn(currentUser);
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.addEmailLogin(1L, "new@example.com", "HASHED")).thenReturn(0);

        assertThatThrownBy(() -> service.addEmailLogin(
                new EmailLoginMethodRequest("new@example.com", "password123"), httpRequest))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.EMAIL_LOGIN_ALREADY_SET);
    }
}
