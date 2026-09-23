package com.telme.member.service;

import static com.telme.member.entity.SocialAccount.Provider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.SocialAccount;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.SocialAccountRepository;
import com.telme.member.repository.UserRepository;
import java.sql.SQLException;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class SocialMemberFinderTest {

    private final SocialAccountRepository socialAccountRepository = mock(SocialAccountRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final MemberStatusChecker memberStatusChecker = new MemberStatusChecker();
    private final TransactionTemplate transactionTemplate = new TransactionTemplate() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    };
    private final SocialMemberFinder finder = new SocialMemberFinder(
            socialAccountRepository, userRepository, memberStatusChecker, transactionTemplate);

    @Test
    @DisplayName("이미 연결된 계정이 있으면 그 회원을 반환하고 새로 만들지 않는다")
    void 기존_연결이_있으면_그대로_반환() {
        User user = User.builder().userId(1L).build();
        SocialAccount account = SocialAccount.builder().user(user).provider(KAKAO).providerUserId("kakao-1").build();
        when(socialAccountRepository.findByProviderAndProviderUserId(KAKAO, "kakao-1"))
                .thenReturn(Optional.of(account));

        User result = finder.findOrCreate(KAKAO, "kakao-1", "a@example.com");

        assertThat(result).isEqualTo(user);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("연결된 회원이 정지 상태면 예외를 던진다")
    void 정지된_회원은_거부() {
        User user = User.builder().userId(1L).status(User.Status.SUSPENDED).build();
        SocialAccount account = SocialAccount.builder().user(user).provider(KAKAO).providerUserId("kakao-2").build();
        when(socialAccountRepository.findByProviderAndProviderUserId(KAKAO, "kakao-2"))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> finder.findOrCreate(KAKAO, "kakao-2", "a@example.com"))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.ACCOUNT_SUSPENDED);
    }

    @Test
    @DisplayName("연결이 없으면 회원과 소셜 계정을 새로 만든다")
    void 신규_생성() {
        when(socialAccountRepository.findByProviderAndProviderUserId(KAKAO, "kakao-3")).thenReturn(Optional.empty());
        User saved = User.builder().userId(2L).build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        User result = finder.findOrCreate(KAKAO, "kakao-3", "b@example.com");

        assertThat(result).isEqualTo(saved);
        verify(socialAccountRepository).saveAndFlush(any(SocialAccount.class));
    }

    @Test
    @DisplayName("동시 최초 로그인으로 UNIQUE 충돌이 나면 재조회해서 승자의 회원을 반환한다")
    void 동시_최초_로그인_충돌시_승자를_반환() {
        User winnerUser = User.builder().userId(3L).build();
        SocialAccount winnerAccount = SocialAccount.builder()
                .user(winnerUser).provider(KAKAO).providerUserId("kakao-4").build();
        when(socialAccountRepository.findByProviderAndProviderUserId(KAKAO, "kakao-4"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winnerAccount));
        when(userRepository.save(any(User.class))).thenReturn(User.builder().userId(99L).build());
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "could not execute statement", new SQLException("duplicate key", "23505"), "uk_social_provider");
        doThrow(new DataIntegrityViolationException("insert failed", constraintViolation))
                .when(socialAccountRepository).saveAndFlush(any(SocialAccount.class));

        User result = finder.findOrCreate(KAKAO, "kakao-4", null);

        assertThat(result).isEqualTo(winnerUser);
    }

    @Test
    @DisplayName("소셜 계정 UNIQUE 위반이 아닌 데이터 무결성 오류는 그대로 전파된다")
    void 다른_제약_위반은_그대로_전파() {
        when(socialAccountRepository.findByProviderAndProviderUserId(KAKAO, "kakao-5")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(User.builder().userId(4L).build());
        doThrow(new DataIntegrityViolationException("value too long"))
                .when(socialAccountRepository).saveAndFlush(any(SocialAccount.class));

        assertThatThrownBy(() -> finder.findOrCreate(KAKAO, "kakao-5", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .isNotInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("이메일이 기존 회원과 일치하면 새 회원을 만들지 않고 연결 필요 예외를 던진다")
    void 이메일이_일치하면_생성을_중단한다() {
        when(socialAccountRepository.findByProviderAndProviderUserId(KAKAO, "kakao-6")).thenReturn(Optional.empty());
        User existing = User.builder().userId(7L).email("match@example.com").build();
        when(userRepository.findByEmail("match@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> finder.findOrCreate(KAKAO, "kakao-6", "match@example.com"))
                .isInstanceOf(SocialEmailAlreadyLinkedException.class)
                .extracting(e -> ((SocialEmailAlreadyLinkedException) e).getMatchedUserId())
                .isEqualTo(7L);
        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("linkExisting은 기존 회원에 새 소셜 계정을 연결한다")
    void linkExisting_정상_연결() {
        User existing = User.builder().userId(8L).email("link@example.com").build();

        User result = finder.linkExisting(KAKAO, "kakao-7", "provider@example.com", existing);

        assertThat(result).isEqualTo(existing);
        verify(socialAccountRepository).saveAndFlush(argThat(account ->
                account.getUser().equals(existing)
                        && account.getProvider() == KAKAO
                        && account.getProviderUserId().equals("kakao-7")
                        && account.getEmail().equals("provider@example.com")));
    }

    @Test
    @DisplayName("소셜 제공자가 이메일을 주지 않으면 연결 계정의 이메일도 null로 저장한다")
    void linkExisting_소셜_이메일이_없으면_null_저장() {
        User existing = User.builder().userId(8L).email("telme@example.com").build();

        finder.linkExisting(KAKAO, "kakao-no-email", null, existing);

        verify(socialAccountRepository).saveAndFlush(argThat(account -> account.getEmail() == null));
    }

    @Test
    @DisplayName("이미 해당 provider가 연결된 회원이면 거부한다")
    void linkExisting_이미_provider가_있으면_거부() {
        User existing = User.builder().userId(9L).email("link2@example.com").build();
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "could not execute statement", new SQLException("duplicate key", "23505"), "uk_social_user_provider");
        doThrow(new DataIntegrityViolationException("insert failed", constraintViolation))
                .when(socialAccountRepository).saveAndFlush(any(SocialAccount.class));

        assertThatThrownBy(() -> finder.linkExisting(KAKAO, "kakao-8", "provider@example.com", existing))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
    }

    @Test
    @DisplayName("같은 회원의 동시 중복 연결 요청은 이미 연결된 상태로 그대로 성공 처리한다")
    void linkExisting_같은_회원의_동시_요청은_성공_처리() {
        User existing = User.builder().userId(10L).email("link3@example.com").build();
        SocialAccount alreadyLinked = SocialAccount.builder().user(existing).provider(KAKAO).providerUserId("kakao-9").build();
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "could not execute statement", new SQLException("duplicate key", "23505"), "uk_social_provider");
        doThrow(new DataIntegrityViolationException("insert failed", constraintViolation))
                .when(socialAccountRepository).saveAndFlush(any(SocialAccount.class));
        when(socialAccountRepository.findByProviderAndProviderUserId(KAKAO, "kakao-9"))
                .thenReturn(Optional.of(alreadyLinked));

        User result = finder.linkExisting(KAKAO, "kakao-9", "provider@example.com", existing);

        assertThat(result).isEqualTo(existing);
    }

    @Test
    @DisplayName("다른 회원에게 이미 연결된 소셜 계정이면 거부한다")
    void linkExisting_다른_회원에_연결된_계정이면_거부() {
        User existing = User.builder().userId(11L).email("link4@example.com").build();
        User otherUser = User.builder().userId(99L).build();
        SocialAccount linkedToOther = SocialAccount.builder().user(otherUser).provider(KAKAO).providerUserId("kakao-10").build();
        ConstraintViolationException constraintViolation = new ConstraintViolationException(
                "could not execute statement", new SQLException("duplicate key", "23505"), "uk_social_provider");
        doThrow(new DataIntegrityViolationException("insert failed", constraintViolation))
                .when(socialAccountRepository).saveAndFlush(any(SocialAccount.class));
        when(socialAccountRepository.findByProviderAndProviderUserId(KAKAO, "kakao-10"))
                .thenReturn(Optional.of(linkedToOther));

        assertThatThrownBy(() -> finder.linkExisting(KAKAO, "kakao-10", "provider@example.com", existing))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED);
    }
}
