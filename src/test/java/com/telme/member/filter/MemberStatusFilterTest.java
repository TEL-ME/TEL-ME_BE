package com.telme.member.filter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// 로그인 이후 DB에서 상태·권한을 바꾸고 다음 요청이 그걸 따라가는지 본다.
// 각 테스트는 트랜잭션 롤백으로 시드에 영향 없음
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MemberStatusFilterTest {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_LOGIN = "{\"email\":\"admin@example.com\",\"password\":\"admin1234\"}";
    private static final String ADMIN_API = "/api/v1/admin/faqs";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("정상 회원은 그대로 통과한다")
    void 정상_회원은_통과한다() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get(ADMIN_API).session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인 뒤 계정이 정지되면 다음 요청부터 막힌다")
    void 정지된_계정은_막힌다() throws Exception {
        MockHttpSession session = login();
        changeStatus(User.Status.SUSPENDED);

        mockMvc.perform(get(ADMIN_API).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER403-0"));
    }

    @Test
    @DisplayName("로그인 뒤 계정이 탈퇴 처리되면 다음 요청부터 막힌다")
    void 탈퇴한_계정은_막힌다() throws Exception {
        MockHttpSession session = login();
        changeStatus(User.Status.WITHDRAWN);

        mockMvc.perform(get(ADMIN_API).session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER403-1"));
    }

    @Test
    @DisplayName("로그인 뒤 권한이 낮아지면 관리자 API가 막힌다")
    void 권한이_낮아지면_막힌다() throws Exception {
        MockHttpSession session = login();
        changeRole(User.Role.USER);

        mockMvc.perform(get(ADMIN_API).session(session)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("정지된 계정도 로그아웃은 할 수 있다")
    void 정지되어도_로그아웃은_된다() throws Exception {
        MockHttpSession session = login();
        changeStatus(User.Status.SUSPENDED);

        mockMvc.perform(post("/api/v1/auth/logout").session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인이 필요한 인증 API도 정지되면 막힌다")
    void 인증_API도_상태_검사를_지나간다() throws Exception {
        MockHttpSession session = login();
        changeStatus(User.Status.SUSPENDED);

        // /api/v1/auth/** 를 통째로 빼면 이 경로가 중앙 검사를 건너뛴다
        mockMvc.perform(post("/api/v1/auth/kakao/link-start").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MEMBER403-0"));
    }

    @Test
    @DisplayName("로그인 뒤 회원이 사라지면 401이 된다")
    void 회원이_사라지면_401이_된다() throws Exception {
        MockHttpSession session = signUp("gone@example.com");
        entityManager.createQuery("delete from User u where u.email = :email")
                .setParameter("email", "gone@example.com")
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(ADMIN_API).session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MEMBER401-1"));
    }

    @Test
    @DisplayName("로그인 뒤 권한이 올라가면 관리자 API가 바로 열린다")
    void 권한이_올라가면_열린다() throws Exception {
        MockHttpSession session = signUp("promote@example.com");
        mockMvc.perform(get(ADMIN_API).session(session)).andExpect(status().isForbidden());

        changeRole("promote@example.com", User.Role.ADMIN);

        mockMvc.perform(get(ADMIN_API).session(session)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("로그인하지 않은 요청은 그대로 401이다")
    void 비인증_요청은_401이다() throws Exception {
        mockMvc.perform(get(ADMIN_API)).andExpect(status().isUnauthorized());
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ADMIN_LOGIN))
                .andExpect(status().isOk());
        return session;
    }

    // 가입하면 자동 로그인된다. 시드 계정을 지우면 FAQ 작성자 FK에 걸려 별도 계정을 만든다
    private MockHttpSession signUp(String email) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/v1/auth/signup")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"filtertest1234\"}"))
                .andExpect(status().isOk());
        return session;
    }

    private void changeStatus(User.Status status) {
        update("update User u set u.status = :value where u.email = :email", status, ADMIN_EMAIL);
    }

    private void changeRole(User.Role role) {
        update("update User u set u.role = :value where u.email = :email", role, ADMIN_EMAIL);
    }

    private void changeRole(String email, User.Role role) {
        update("update User u set u.role = :value where u.email = :email", role, email);
    }

    // 필터가 1차 캐시에 남은 예전 값을 읽지 않도록 비운다
    private void update(String jpql, Object value, String email) {
        entityManager.createQuery(jpql)
                .setParameter("value", value)
                .setParameter("email", email)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
