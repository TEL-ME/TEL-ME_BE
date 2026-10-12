package com.telme.member.service;

import static com.telme.member.entity.SocialAccount.Provider.GOOGLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.member.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SocialLoginServiceTest {

    private final SocialMemberFinder socialMemberFinder = mock(SocialMemberFinder.class);
    private final SocialLoginService service = new SocialLoginService(
            socialMemberFinder,
            mock(GuestSuccessionService.class),
            mock(GuestIdResolver.class),
            mock(LoginCompletionService.class),
            mock(org.springframework.transaction.support.TransactionTemplate.class));

    @Test
    @DisplayName("Google principal의 공통 값을 공급자 중립 회원 조회·생성 서비스에 전달한다")
    void 구글_principal로_회원_조회_생성() {
        SocialOAuth2Principal principal = mock(SocialOAuth2Principal.class);
        User user = User.builder().userId(10L).name("구글 사용자").build();
        when(principal.getProvider()).thenReturn(GOOGLE);
        when(principal.getProviderUserId()).thenReturn("google-sub-1");
        when(principal.getEmail()).thenReturn("user@example.com");
        when(principal.getDisplayName()).thenReturn("구글 사용자");
        when(socialMemberFinder.findOrCreate(
                GOOGLE, "google-sub-1", "user@example.com", "구글 사용자"))
                .thenReturn(user);

        User result = service.findOrCreate(principal);

        assertThat(result).isSameAs(user);
        verify(socialMemberFinder).findOrCreate(
                GOOGLE, "google-sub-1", "user@example.com", "구글 사용자");
    }
}
