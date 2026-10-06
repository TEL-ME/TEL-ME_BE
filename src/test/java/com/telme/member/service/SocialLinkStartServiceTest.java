package com.telme.member.service;

import static com.telme.member.entity.SocialAccount.Provider.GOOGLE;
import static com.telme.member.entity.SocialAccount.Provider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.member.entity.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SocialLinkStartServiceTest {

    private final CurrentMemberResolver currentMemberResolver = mock(CurrentMemberResolver.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);
    private final SocialLinkRequestStore socialLinkRequestStore = new SocialLinkRequestStore(clock);
    private final SocialEmailMatchStore socialEmailMatchStore = new SocialEmailMatchStore(clock);
    private final SocialLinkStartService service =
            new SocialLinkStartService(currentMemberResolver, socialLinkRequestStore, socialEmailMatchStore);

    @Test
    @DisplayName("현재 회원을 pending으로 저장하고 카카오 인가 URL을 반환한다")
    void 정상_시작() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(currentMemberResolver.resolve(request)).thenReturn(User.builder().userId(30L).build());

        String redirect = service.start(request, KAKAO);

        assertThat(redirect).startsWith("/oauth2/authorization/kakao?link_token=");
        String token = redirect.substring(redirect.indexOf('=') + 1);
        socialLinkRequestStore.bind(request, token, "link-state", "kakao");
        request.setParameter("state", "link-state");
        assertThat(socialLinkRequestStore.consume(request).targetUserId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("구글 연결은 현재 회원과 GOOGLE 공급자를 pending으로 저장하고 구글 인가 URL을 반환한다")
    void 구글_연결_시작() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(currentMemberResolver.resolve(request)).thenReturn(User.builder().userId(30L).build());

        String redirect = service.start(request, GOOGLE);

        assertThat(redirect).startsWith("/oauth2/authorization/google?link_token=");
        String token = redirect.substring(redirect.indexOf('=') + 1);
        socialLinkRequestStore.bind(request, token, "link-state", "google");
        request.setParameter("state", "link-state");
        SocialLinkRequest pending = socialLinkRequestStore.consume(request);
        assertThat(pending.targetUserId()).isEqualTo(30L);
        assertThat(pending.provider()).isEqualTo(GOOGLE);
    }

    @Test
    @DisplayName("이전에 남아있던 B(이메일 계정 발견) pending을 지운다")
    void 이전_B_pending을_지운다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        socialEmailMatchStore.issue(request, KAKAO, "old-kakao-id", 99L, "old@example.com");
        when(currentMemberResolver.resolve(request)).thenReturn(User.builder().userId(30L).build());

        service.start(request, KAKAO);

        assertThat(request.getSession(false).getAttribute(SocialEmailMatchStore.SESSION_ATTRIBUTE)).isNull();
    }
}
