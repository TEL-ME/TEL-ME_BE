package com.telme.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.member.dto.req.SignUpRequest;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 두 가입 요청이 실제로 동시에 DB UNIQUE 제약에 부딪힐 때 하나만 200을, 나머지는 409를 받는지 실제 HTTP 요청으로 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
class MemberAuthConcurrentSignUpIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String email;

    @AfterEach
    void cleanUpUser() {
        if (email != null) {
            jdbcTemplate.update("delete from users where email = ?", email);
        }
    }

    @Test
    void 동시에_같은_이메일로_가입하면_하나만_성공하고_나머지는_409를_받는다() throws Exception {
        email = "concurrent-signup-" + UUID.randomUUID() + "@example.com";
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<MvcResult> first = executor.submit(() -> signUp(ready, start));
            Future<MvcResult> second = executor.submit(() -> signUp(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> statuses = List.of(
                    first.get(10, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(10, TimeUnit.SECONDS).getResponse().getStatus());

            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from users where email = ?", Integer.class, email))
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult signUp(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        return mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignUpRequest(email, "password123"))))
                .andReturn();
    }
}
