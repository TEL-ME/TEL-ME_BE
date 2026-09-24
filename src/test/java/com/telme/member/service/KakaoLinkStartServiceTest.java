package com.telme.member.service;

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

class KakaoLinkStartServiceTest {

    private final CurrentMemberResolver currentMemberResolver = mock(CurrentMemberResolver.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-24T00:00:00Z"), ZoneOffset.UTC);
    private final KakaoLinkRequestStore kakaoLinkRequestStore = new KakaoLinkRequestStore(clock);
    private final KakaoEmailMatchStore kakaoEmailMatchStore = new KakaoEmailMatchStore(clock);
    private final KakaoLinkStartService service =
            new KakaoLinkStartService(currentMemberResolver, kakaoLinkRequestStore, kakaoEmailMatchStore);

    @Test
    @DisplayName("현재 회원을 pending으로 저장하고 카카오 인가 URL을 반환한다")
    void 정상_시작() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(currentMemberResolver.resolve(request)).thenReturn(User.builder().userId(30L).build());

        String redirect = service.start(request);

        assertThat(redirect).startsWith("/oauth2/authorization/kakao?link_token=");
        String token = redirect.substring(redirect.indexOf('=') + 1);
        kakaoLinkRequestStore.bind(request, token, "link-state");
        request.setParameter("state", "link-state");
        assertThat(kakaoLinkRequestStore.consume(request).targetUserId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("이전에 남아있던 B(이메일 계정 발견) pending을 지운다")
    void 이전_B_pending을_지운다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        kakaoEmailMatchStore.issue(request, "old-kakao-id", 99L, "old@example.com");
        when(currentMemberResolver.resolve(request)).thenReturn(User.builder().userId(30L).build());

        service.start(request);

        assertThat(request.getSession(false).getAttribute(KakaoEmailMatchStore.SESSION_ATTRIBUTE)).isNull();
    }
}
