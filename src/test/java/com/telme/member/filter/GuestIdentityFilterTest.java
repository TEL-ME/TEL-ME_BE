package com.telme.member.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.service.ChatActor;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.member.service.GuestIdentityService;
import jakarta.servlet.FilterChain;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

// GuestIdentityService 목킹 단위 테스트. DB 저장 확인은 GuestIdentityServiceTest에서
class GuestIdentityFilterTest {

    private final GuestIdentityService guestIdentityService = mock(GuestIdentityService.class);
    private final GuestIdentityFilter filter = new GuestIdentityFilter(guestIdentityService);

    @Test
    @DisplayName("신원이 없으면 Guest를 발급하고 세션에 저장한다")
    void 신원이_없으면_발급한다() throws Exception {
        UUID issued = UUID.randomUUID();
        when(guestIdentityService.issueGuest()).thenReturn(issued);

        MockHttpSession session = session();
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = requestWithSession("/api/v1/chat/sessions", session);
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE)).isEqualTo(issued);
        verify(guestIdentityService, times(1)).issueGuest();
        verify(chain).doFilter(any(), any());
    }

    @Test
    @DisplayName("기존 guestId가 있으면 추가로 발급하지 않는다")
    void 기존_guestId가_있으면_발급하지_않는다() throws Exception {
        UUID existing = UUID.randomUUID();
        MockHttpSession session = session();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, existing);

        filter.doFilter(requestWithSession("/api/v1/chat/sessions", session), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE)).isEqualTo(existing);
        verify(guestIdentityService, never()).issueGuest();
    }

    @Test
    @DisplayName("userId가 있으면 guestId를 발급하지 않는다")
    void userId가_있으면_발급하지_않는다() throws Exception {
        MockHttpSession session = session();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, 1L);

        filter.doFilter(requestWithSession("/api/v1/chat/sessions", session), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE)).isNull();
        verify(guestIdentityService, never()).issueGuest();
    }

    @Test
    @DisplayName("userId·guestId 둘 다 있으면 그대로 둔다")
    void 둘_다_있으면_변경하지_않는다() throws Exception {
        UUID existingGuest = UUID.randomUUID();
        MockHttpSession session = session();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, 1L);
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, existingGuest);

        filter.doFilter(requestWithSession("/api/v1/chat/sessions", session), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(session.getAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE)).isEqualTo(1L);
        assertThat(session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE)).isEqualTo(existingGuest);
        verify(guestIdentityService, never()).issueGuest();
    }

    @Test
    @DisplayName("적용 대상이 아닌 URL에서는 세션에 guestId를 만들지 않는다")
    void 대상_URL이_아니면_발급하지_않는다() throws Exception {
        MockHttpSession session = session();

        filter.doFilter(requestWithSession("/actuator/health", session), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE)).isNull();
        verify(guestIdentityService, never()).issueGuest();
    }

    @Test
    @DisplayName("OPTIONS 요청이면 세션에 guestId를 만들지 않는다")
    void OPTIONS_요청이면_발급하지_않는다() throws Exception {
        MockHttpSession session = session();

        filter.doFilter(requestWithSession("OPTIONS", "/api/v1/chat/sessions", session), new MockHttpServletResponse(), mock(FilterChain.class));

        assertThat(session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE)).isNull();
        verify(guestIdentityService, never()).issueGuest();
    }

    @Test
    @DisplayName("같은 세션의 동시 요청에서도 Guest가 한 번만 발급된다")
    void 동시_요청에서도_한_번만_발급한다() throws Exception {
        when(guestIdentityService.issueGuest()).thenAnswer(invocation -> {
            Thread.sleep(30); // 경합 창을 넓혀 뮤텍스가 없으면 실패하도록 유도
            return UUID.randomUUID();
        });
        MockHttpSession session = session();
        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                filter.doFilter(requestWithSession("/api/v1/chat/sessions", session), new MockHttpServletResponse(), mock(FilterChain.class));
                return null;
            }));
        }
        start.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        verify(guestIdentityService, times(1)).issueGuest();
    }

    @Test
    @DisplayName("쿠키 없는 최초 방문에서 서로 다른 세션으로 동시 요청이 오면 guest가 중복 발급된다(알려진 한계)")
    void 서로_다른_세션의_동시_요청은_각각_발급한다() throws Exception {
        when(guestIdentityService.issueGuest()).thenAnswer(invocation -> UUID.randomUUID());
        MockHttpSession sessionA = session();
        MockHttpSession sessionB = session();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<?> futureA = pool.submit(() -> {
            start.await();
            filter.doFilter(requestWithSession("/api/v1/chat/sessions", sessionA), new MockHttpServletResponse(), mock(FilterChain.class));
            return null;
        });
        Future<?> futureB = pool.submit(() -> {
            start.await();
            filter.doFilter(requestWithSession("/api/v1/chat/sessions", sessionB), new MockHttpServletResponse(), mock(FilterChain.class));
            return null;
        });
        start.countDown();
        futureA.get();
        futureB.get();
        pool.shutdown();

        verify(guestIdentityService, times(2)).issueGuest();
        assertThat(sessionA.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE))
                .isNotEqualTo(sessionB.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE));
    }

    @Test
    @DisplayName("필터 통과 후 HttpSessionChatActorProvider가 같은 guestId를 반환한다")
    void 필터_이후_ChatActorProvider가_같은_guestId를_반환한다() throws Exception {
        UUID issued = UUID.randomUUID();
        when(guestIdentityService.issueGuest()).thenReturn(issued);
        MockHttpSession session = session();
        MockHttpServletRequest request = requestWithSession("/api/v1/chat/sessions", session);

        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        ChatActor actor = new HttpSessionChatActorProvider().getCurrentActor(request);
        assertThat(actor.guestId()).isEqualTo(issued);
        assertThat(actor.isMember()).isFalse();
    }

    private MockHttpSession session() {
        return new MockHttpSession();
    }

    private MockHttpServletRequest requestWithSession(String uri, MockHttpSession session) {
        return requestWithSession("GET", uri, session);
    }

    private MockHttpServletRequest requestWithSession(String method, String uri, MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setSession(session);
        return request;
    }
}
