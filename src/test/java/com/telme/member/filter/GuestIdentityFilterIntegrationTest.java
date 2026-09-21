package com.telme.member.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.telme.chat.service.HttpSessionChatActorProvider;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 실제 필터 체인 전체를 태워 세션 쿠키 생성 여부까지 확인 (단위 테스트는 필터 로직만 봄)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuestIdentityFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("적용 대상이 아닌 URL은 세션 자체를 만들지 않는다")
    void 대상_URL이_아니면_세션을_만들지_않는다() throws Exception {
        var result = mockMvc.perform(get("/actuator/health")).andReturn();

        // 필터가 request.getSession()을 호출하지 않았다면 새 세션이 생기지 않는다
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    @DisplayName("대상 URL은 세션이 생기고 guestId가 저장된다")
    void 대상_URL은_세션과_guestId를_만든다() throws Exception {
        var result = mockMvc.perform(get("/api/v1/chat/sessions")).andReturn();

        HttpSession session = result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE)).isNotNull();
    }
}
