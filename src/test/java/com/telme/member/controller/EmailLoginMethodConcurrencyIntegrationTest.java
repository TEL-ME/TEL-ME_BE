package com.telme.member.controller;

import static com.telme.chat.service.HttpSessionChatActorProvider.USER_ID_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.member.dto.req.EmailLoginMethodRequest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

// 같은 카카오 회원이 이메일 로그인 방법 추가를 동시에 두 번 요청해도 하나만 성공하는지 실제 HTTP 요청으로 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
class EmailLoginMethodConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            jdbcTemplate.update("delete from users where user_id = ?", userId);
        }
    }

    @Test
    void 같은_회원이_동시에_요청해도_하나만_성공한다() throws Exception {
        userId = jdbcTemplate.queryForObject(
                "insert into users default values returning user_id", Long.class);
        MockHttpSession session = authenticatedSessionFor(userId);
        String email = "kakao-member-" + UUID.randomUUID() + "@example.com";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> first = executor.submit(() -> addEmailLogin(session, email, ready, start));
            Future<MvcResult> second = executor.submit(() -> addEmailLogin(session, email, ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(10, TimeUnit.SECONDS).getResponse().getStatus());

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from users where user_id = ? and email = ?", Integer.class, userId, email))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult addEmailLogin(MockHttpSession session, String email, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mockMvc.perform(post("/api/v1/auth/login-methods/email")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmailLoginMethodRequest(email, "password123"))))
                .andReturn();
    }

    private MockHttpSession authenticatedSessionFor(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(USER_ID_ATTRIBUTE, userId);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
