package com.telme.member.controller;

import static com.telme.chat.service.HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE;
import static com.telme.chat.service.HttpSessionChatActorProvider.USER_ID_ATTRIBUTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.member.dto.req.SocialLinkConfirmRequest;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.GuestRepository;
import com.telme.member.repository.UserRepository;
import com.telme.member.service.GoogleOidcUser;
import com.telme.member.service.KakaoOAuth2User;
import com.telme.member.service.SocialEmailMatchStore;
import com.telme.member.service.SocialLoginSuccessHandler;
import com.telme.member.service.SocialOAuth2Principal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.util.UriComponentsBuilder;

// 소셜 로그인 전체 흐름을 실제 빈·DB로 확인한다. 공급자와의 토큰 교환만 실행할 수 없어서, OAuth 콜백 단계는
// 공급자 인증이 끝난 principal로 성공 핸들러 빈을 직접 호출한다. 나머지(연결 시작, 인가 요청, 연결 확인, /me)는 실제 HTTP 요청이다.
@SpringBootTest
@AutoConfigureMockMvc
class SocialLoginFlowIntegrationTest {

    private static final String SUCCESS_REDIRECT = "http://localhost:3000/oauth/callback?success=true";
    private static final String FAILURE_REDIRECT_PREFIX = "http://localhost:3000/oauth/callback?success=false&reason=";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SocialLoginSuccessHandler socialLoginSuccessHandler;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    private final String runId = UUID.randomUUID().toString();
    private final List<Long> createdUserIds = new ArrayList<>();
    private final List<UUID> createdGuestIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        List<Long> userIds = new ArrayList<>(createdUserIds);
        userIds.addAll(jdbcTemplate.queryForList(
                "select user_id from social_accounts where provider_user_id like ?", Long.class, runId + "%"));
        for (UUID guestId : createdGuestIds) {
            jdbcTemplate.update("delete from chat_sessions where guest_id = ?", guestId);
            jdbcTemplate.update("delete from guests where guest_id = ?", guestId);
        }
        for (Long userId : userIds) {
            jdbcTemplate.update("delete from chat_sessions where user_id = ?", userId);
            jdbcTemplate.update("delete from users where user_id = ?", userId);
        }
    }

    @Test
    @DisplayName("신규 Google 회원은 가입과 동시에 로그인되고 /me에 GOOGLE이 표시된다")
    void 신규_구글_회원가입_로그인() throws Exception {
        MockHttpSession session = new MockHttpSession();

        MockHttpServletResponse response = callback(session, google("new", uniqueEmail(), "구글신규"), null);

        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_REDIRECT);
        Long userId = (Long) session.getAttribute(USER_ID_ATTRIBUTE);
        assertThat(userId).isNotNull();
        assertThat(socialAccountCount(userId, "GOOGLE")).isEqualTo(1);
        assertThat(userRepository.findById(userId).orElseThrow().getName()).isEqualTo("구글신규");
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.authenticated").value(true))
                .andExpect(jsonPath("$.result.loginMethods.length()").value(1))
                .andExpect(jsonPath("$.result.loginMethods[0]").value("GOOGLE"));
    }

    @Test
    @DisplayName("이미 가입한 Google 회원이 다시 로그인하면 같은 회원으로 로그인되고 계정이 늘지 않는다")
    void 기존_구글_회원_재로그인() throws Exception {
        String email = uniqueEmail();
        MockHttpSession first = new MockHttpSession();
        callback(first, google("again", email, "구글재방문"), null);
        Long firstUserId = (Long) first.getAttribute(USER_ID_ATTRIBUTE);

        MockHttpSession second = new MockHttpSession();
        MockHttpServletResponse response = callback(second, google("again", email, "구글재방문"), null);

        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_REDIRECT);
        assertThat(second.getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(firstUserId);
        assertThat(socialAccountCount(firstUserId, "GOOGLE")).isEqualTo(1);
    }

    @Test
    @DisplayName("Google 이메일이 기존 이메일 회원과 같으면 자동 연결하지 않고 비밀번호 확인 대기 상태로 보낸다")
    void 같은_이메일의_기존_회원_발견() throws Exception {
        String email = uniqueEmail();
        User emailUser = createEmailUser(email, "password123");
        MockHttpSession session = new MockHttpSession();

        MockHttpServletResponse response = callback(session, google("match", email, "구글"), null);

        assertThat(response.getRedirectedUrl())
                .isEqualTo(FAILURE_REDIRECT_PREFIX + MemberErrorCode.EMAIL_LINK_REQUIRED.getCode());
        assertThat(session.getAttribute(USER_ID_ATTRIBUTE)).isNull();
        assertThat(session.getAttribute(SocialEmailMatchStore.SESSION_ATTRIBUTE)).isNotNull();
        assertThat(socialAccountCount(emailUser.getUserId(), "GOOGLE")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from social_accounts where provider_user_id = ?", Integer.class, runId + "match"))
                .isZero();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 연결하지 않고, 맞으면 기존 회원에 Google을 연결하고 로그인한다")
    void 비밀번호_확인_후_구글_연결() throws Exception {
        String email = uniqueEmail();
        User emailUser = createEmailUser(email, "password123");
        MockHttpSession session = new MockHttpSession();
        callback(session, google("confirm", email, "구글"), null);

        mockMvc.perform(post("/api/v1/auth/social/link").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SocialLinkConfirmRequest("wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(MemberErrorCode.INVALID_CREDENTIALS.getCode()));
        assertThat(socialAccountCount(emailUser.getUserId(), "GOOGLE")).isZero();

        mockMvc.perform(post("/api/v1/auth/social/link").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SocialLinkConfirmRequest("password123"))))
                .andExpect(status().isOk());

        assertThat(socialAccountCount(emailUser.getUserId(), "GOOGLE")).isEqualTo(1);
        assertThat(session.getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(emailUser.getUserId());
        assertThat(session.getAttribute(SocialEmailMatchStore.SESSION_ATTRIBUTE)).isNull();
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.result.loginMethods[0]").value("EMAIL"))
                .andExpect(jsonPath("$.result.loginMethods[1]").value("GOOGLE"));

        // 연결 후에는 같은 Google 계정으로 바로 로그인된다
        MockHttpSession next = new MockHttpSession();
        assertThat(callback(next, google("confirm", email, "구글"), null).getRedirectedUrl())
                .isEqualTo(SUCCESS_REDIRECT);
        assertThat(next.getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(emailUser.getUserId());
    }

    @Test
    @DisplayName("로그인한 이메일 회원이 Google 연결을 시작하면 인가 요청을 거쳐 현재 회원에 Google이 연결된다")
    void 로그인_상태에서_구글_추가_연결() throws Exception {
        User emailUser = createEmailUser(uniqueEmail(), "password123");
        MockHttpSession session = authenticatedSessionFor(emailUser);

        String state = startLinkAndAuthorize(session, "google");
        // 이메일이 달라도 로그인 세션으로 본인 확인이 끝났으므로 그대로 연결한다
        MockHttpServletResponse response = callback(session, google("link", uniqueEmail(), "구글"), state);

        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_REDIRECT);
        assertThat(socialAccountCount(emailUser.getUserId(), "GOOGLE")).isEqualTo(1);
        assertThat(session.getAttribute(USER_ID_ATTRIBUTE)).isEqualTo(emailUser.getUserId());
        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(jsonPath("$.result.loginMethods[0]").value("EMAIL"))
                .andExpect(jsonPath("$.result.loginMethods[1]").value("GOOGLE"));
    }

    @Test
    @DisplayName("카카오 연결용 토큰으로 Google 인가를 시작하면 연결 만료로 실패한다")
    void 공급자_바꿔치기는_거부() throws Exception {
        User emailUser = createEmailUser(uniqueEmail(), "password123");
        MockHttpSession session = authenticatedSessionFor(emailUser);
        String kakaoAuthorizePath = mockMvc.perform(get("/api/v1/auth/kakao/link-start").session(session))
                .andReturn().getResponse().getRedirectedUrl();
        String token = kakaoAuthorizePath.substring(kakaoAuthorizePath.indexOf("link_token=") + "link_token=".length());

        MockHttpServletResponse response = mockMvc.perform(
                        get("/oauth2/authorization/google").param("link_token", token).session(session))
                .andReturn().getResponse();

        assertThat(response.getRedirectedUrl())
                .isEqualTo(FAILURE_REDIRECT_PREFIX + MemberErrorCode.SOCIAL_LINK_SESSION_EXPIRED.getCode());
        assertThat(socialAccountCount(emailUser.getUserId(), "GOOGLE")).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = User.Status.class, names = {"SUSPENDED", "WITHDRAWN"})
    @DisplayName("정지·탈퇴한 Google 회원은 로그인을 거부하고 세션에 로그인 정보를 남기지 않는다")
    void 정지_탈퇴_회원_거부(User.Status userStatus) throws Exception {
        String email = uniqueEmail();
        MockHttpSession first = new MockHttpSession();
        callback(first, google("blocked-" + userStatus, email, "구글"), null);
        Long userId = (Long) first.getAttribute(USER_ID_ATTRIBUTE);
        jdbcTemplate.update("update users set status = ? where user_id = ?", userStatus.name(), userId);
        SecurityContextHolder.clearContext();

        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse response = callback(session, google("blocked-" + userStatus, email, "구글"), null);

        MemberErrorCode expected = userStatus == User.Status.SUSPENDED
                ? MemberErrorCode.ACCOUNT_SUSPENDED : MemberErrorCode.ACCOUNT_WITHDRAWN;
        assertThat(response.getRedirectedUrl()).isEqualTo(FAILURE_REDIRECT_PREFIX + expected.getCode());
        assertThat(session.getAttribute(USER_ID_ATTRIBUTE)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("게스트로 채팅하다 Google로 가입하면 게스트 채팅 이력이 새 회원으로 승계된다")
    void 게스트_채팅_이력_승계() throws Exception {
        Guest guest = guestRepository.save(Guest.issue(Duration.ofDays(30), clock));
        createdGuestIds.add(guest.getGuestId());
        ChatSession chat = chatSessionRepository.save(ChatSession.builder().guestId(guest.getGuestId()).build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(GUEST_ID_ATTRIBUTE, guest.getGuestId());

        MockHttpServletResponse response = callback(session, google("guest", uniqueEmail(), "구글"), null);

        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_REDIRECT);
        Long userId = (Long) session.getAttribute(USER_ID_ATTRIBUTE);
        assertThat(jdbcTemplate.queryForObject(
                "select user_id from chat_sessions where session_id = ?", Long.class, chat.getSessionId()))
                .isEqualTo(userId);
        assertThat(jdbcTemplate.queryForObject(
                "select merged_user_id from guests where guest_id = ?", Long.class, guest.getGuestId()))
                .isEqualTo(userId);
        assertThat(session.getAttribute(GUEST_ID_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("카카오 회귀 - 신규 가입, 로그인 상태 연결 시작, 이메일 충돌 후 기존 /kakao/link 연결이 그대로 동작한다")
    void 카카오_기존_흐름_회귀() throws Exception {
        // 신규 가입
        MockHttpSession signUp = new MockHttpSession();
        assertThat(callback(signUp, kakao("new", null), null).getRedirectedUrl()).isEqualTo(SUCCESS_REDIRECT);
        assertThat(socialAccountCount((Long) signUp.getAttribute(USER_ID_ATTRIBUTE), "KAKAO")).isEqualTo(1);

        // 로그인 상태에서 카카오 연결 시작
        User linkUser = createEmailUser(uniqueEmail(), "password123");
        MockHttpSession linkSession = authenticatedSessionFor(linkUser);
        String state = startLinkAndAuthorize(linkSession, "kakao");
        assertThat(callback(linkSession, kakao("link", null), state).getRedirectedUrl()).isEqualTo(SUCCESS_REDIRECT);
        assertThat(socialAccountCount(linkUser.getUserId(), "KAKAO")).isEqualTo(1);

        // 이메일 충돌 후 기존 경로(/kakao/link)로 비밀번호 확인 연결
        String email = uniqueEmail();
        User matchUser = createEmailUser(email, "password123");
        MockHttpSession matchSession = new MockHttpSession();
        assertThat(callback(matchSession, kakao("match", email), null).getRedirectedUrl())
                .isEqualTo(FAILURE_REDIRECT_PREFIX + MemberErrorCode.EMAIL_LINK_REQUIRED.getCode());
        mockMvc.perform(post("/api/v1/auth/kakao/link").session(matchSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SocialLinkConfirmRequest("password123"))))
                .andExpect(status().isOk());
        assertThat(socialAccountCount(matchUser.getUserId(), "KAKAO")).isEqualTo(1);
    }

    // 연결 시작 API → 실제 인가 요청 필터(공급자 대조 포함)까지 거쳐, 공급자로 보낼 state를 돌려준다
    private String startLinkAndAuthorize(MockHttpSession session, String registrationId) throws Exception {
        String authorizePath = mockMvc.perform(get("/api/v1/auth/" + registrationId + "/link-start").session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(authorizePath).startsWith("/oauth2/authorization/" + registrationId + "?link_token=");

        String providerUrl = mockMvc.perform(get(authorizePath).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        String state = UriComponentsBuilder.fromUriString(providerUrl).build().getQueryParams().getFirst("state");
        assertThat(state).isNotBlank();
        return URLDecoder.decode(state, StandardCharsets.UTF_8);
    }

    private MockHttpServletResponse callback(MockHttpSession session, SocialOAuth2Principal principal, String state)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/login/oauth2/code/" + principal.getProvider().name().toLowerCase());
        request.setSession(session);
        if (state != null) {
            request.setParameter("state", state);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        socialLoginSuccessHandler.onAuthenticationSuccess(request, response,
                new OAuth2AuthenticationToken(principal, List.of(), principal.getProvider().name().toLowerCase()));
        return response;
    }

    private GoogleOidcUser google(String sub, String email, String name) {
        Instant now = Instant.now();
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token")
                .subject(runId + sub)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("email", email)
                .claim("email_verified", true)
                .claim("name", name)
                .build();
        return new GoogleOidcUser(runId + sub, email, name, new DefaultOidcUser(List.of(), idToken));
    }

    private KakaoOAuth2User kakao(String id, String email) {
        return new KakaoOAuth2User(runId + id, email, "카카오", Map.of("id", runId + id));
    }

    private User createEmailUser(String email, String password) {
        User user = userRepository.saveAndFlush(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .name("이메일회원")
                .build());
        createdUserIds.add(user.getUserId());
        return user;
    }

    private MockHttpSession authenticatedSessionFor(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(USER_ID_ATTRIBUTE, user.getUserId());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                user.getUserId(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private int socialAccountCount(Long userId, String provider) {
        return jdbcTemplate.queryForObject(
                "select count(*) from social_accounts where user_id = ? and provider = ?",
                Integer.class, userId, provider);
    }

    private String uniqueEmail() {
        return "social-flow-" + UUID.randomUUID() + "@example.com";
    }
}
