package com.telme.faq.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// @WithMockUser는 권한 규칙만 보고 로그인 경로는 건너뛴다.
// V8이 넣은 해시가 실제 비밀번호와 맞는지, 로그인 때 role이 권한으로 붙는지는 여기서만 잡힌다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminFaqLoginIntegrationTest {

    private static final String ADMIN_LOGIN = "{\"email\":\"admin@example.com\",\"password\":\"admin1234\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("dev 시드 ADMIN 계정으로 로그인하면 관리자 FAQ 조회가 200을 반환한다")
    void 시드_관리자_계정으로_로그인해_조회한다() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADMIN_LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email").value("admin@example.com"));

        mockMvc.perform(get("/api/v1/admin/faqs").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.faqs").isArray());
    }

    @Test
    @DisplayName("로그아웃하면 관리자 FAQ 조회가 다시 401을 반환한다")
    void 로그아웃하면_다시_401이_된다() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADMIN_LOGIN))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/faqs").session(session))
                .andExpect(status().isUnauthorized());
    }
}
