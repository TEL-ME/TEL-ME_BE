package com.telme.chat.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.service.LlmClient;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

//답변 번역 API를 실제 DB로 확인한다. 모델 출력만 고정한다.
//설정과 대신하는 빈을 ChatQuestionTranslationIntegrationTest와 같게 두어 테스트 컨텍스트를 함께 쓴다
@SpringBootTest(properties = {"llm.provider=ollama", "rag.evidence-check.enabled=false",
                "chat.summary.trigger-messages=1000", "chat.summary.trigger-tokens=100000"})
@AutoConfigureMockMvc
class ChatMessageTranslationApiIntegrationTest {

    private static final String KOREAN = "재발급 비용은 7,700원입니다.";
    private static final String ENGLISH = "The reissue fee is KRW 7,700.";
    private static final String URL = "/api/v1/chat/messages/{messageId}/translation";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean FaqSearchService search;
    @MockitoBean(name = "baseLlmClient", enforceOverride = true) LlmClient model;

    private long userId;
    private long sessionId;
    private long questionId;
    private long executionId;
    private long answerId;
    private MockHttpSession identity;
    
    // 질문 → 실행 → 답변을 실제 저장 순서대로 넣는다. 번역 호출 기록이 실행에 붙는지 보려고 커밋된 데이터로 둔다
    @BeforeEach
    void prepare() {
        userId = insertUser("translation-api");
        sessionId = jdbc.queryForObject("INSERT INTO chat_sessions(user_id,title) VALUES (?,'번역 API') RETURNING session_id",
                Long.class, userId);
        questionId = jdbc.queryForObject("""
                INSERT INTO chat_messages(session_id, sequence_no, role, message_type, content, status, completed_at)
                VALUES (?, 1, 'USER', 'QUESTION', '유심 재발급 비용은 얼마인가요?', 'COMPLETED', now()) RETURNING message_id
                """, Long.class, sessionId);
        executionId = jdbc.queryForObject("""
                INSERT INTO chat_executions(session_id, input_message_id, status, ended_at)
                VALUES (?, ?, 'COMPLETED', now()) RETURNING execution_id
                """, Long.class, sessionId, questionId);
        answerId = jdbc.queryForObject("""
                INSERT INTO chat_messages(session_id, sequence_no, reply_to_id, role, message_type, content, status,
                                          answer_basis, completed_at)
                VALUES (?, 2, ?, 'ASSISTANT', 'ANSWER', ?, 'COMPLETED', 'GROUNDED', now()) RETURNING message_id
                """, Long.class, sessionId, questionId, KOREAN);
        jdbc.update("UPDATE chat_executions SET output_message_id=? WHERE execution_id=?", answerId, executionId);
        identity = memberSession(userId);
        when(model.generate(any())).thenReturn(ENGLISH);
    }
    
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM chat_sessions WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM users WHERE user_id=? OR email LIKE 'translation-other-%'", userId);
    }
    
    @Test
    @DisplayName("처음 요청하면 번역해 저장하고, 다시 요청하면 모델을 부르지 않고 저장한 번역을 돌려준다")
    void 번역을_저장하고_다시_쓴다() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(get(URL, answerId).param("lang", "en").session(identity))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messageId").value(answerId))
                .andExpect(jsonPath("$.result.language").value("en"))
                .andExpect(jsonPath("$.result.content").value(ENGLISH));
        }
        
        verify(model, times(1)).generate(any());
        Map<String, Object> saved = jdbc.queryForMap(
                "SELECT content, model, prompt_version"
                + " FROM chat_message_translations WHERE message_id=? AND target_language='en'",
                answerId);
        assertThat(saved).containsEntry("content", ENGLISH)
                .containsEntry("prompt_version", ChatMessageTranslationService.PROMPT_VERSION);
        assertThat(saved.get("model")).isNotNull();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM llm_generations WHERE execution_id=? AND task_type='TRANSLATION'",
                Integer.class, executionId)).isEqualTo(1);
    }
    
    @Test
    @DisplayName("다른 사람의 답변은 없는 메시지로 답한다")
    void 다른_사람의_답변은_번역하지_않는다() throws Exception {
        MockHttpSession other = memberSession(insertUser("translation-other"));
        
        mvc.perform(get(URL, answerId).session(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-2"));
    }
    
    @Test
    @DisplayName("사용자 질문은 번역하지 않고, 오류 문구는 요청 언어를 따른다")
    void 사용자_질문은_번역하지_않는다() throws Exception {
        mvc.perform(get(URL, questionId).session(identity).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSLATION400-0"))
                .andExpect(jsonPath("$.message").value("This message cannot be translated."));
    }
    
    @Test
    @DisplayName("숫자가 바뀐 번역은 저장하지 않고 실패로 알린다")
    void 숫자가_바뀐_번역은_저장하지_않는다() throws Exception {
        when(model.generate(any())).thenReturn("The reissue fee is KRW 7,000.");
        
        mvc.perform(get(URL, answerId).session(identity))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TRANSLATION502-0"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message_translations WHERE message_id=?",
                Integer.class, answerId)).isZero();
    }
    
    @Test
    @DisplayName("지원하지 않는 언어는 검증 오류다")
    void 지원하지_않는_언어는_막는다() throws Exception {
        mvc.perform(get(URL, answerId).param("lang", "ja").session(identity))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.lang").value("번역할 수 있는 언어는 en뿐입니다."));
    }
    
    private long insertUser(String prefix) {
        return jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'번역 검증') RETURNING user_id",
                Long.class, prefix + "-" + UUID.randomUUID() + "@example.com");
    }
    
    private static MockHttpSession memberSession(long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        return session;
    }
}
