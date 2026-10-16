package com.telme.chat.translation;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatMessageTranslationRepository {

    private final JdbcTemplate jdbc;
    
    public Optional<String> find(long messageId, String language) {
        return jdbc.queryForList("""
                SELECT content FROM chat_message_translations
                WHERE message_id = ? AND target_language = ?
                """, String.class, messageId, language).stream().findFirst();
    }
    
    // 동시에 저장하면 먼저 들어간 번역을 남긴다
    public void saveIfAbsent(long messageId, String language, String content, String model, String promptVersion) {
        jdbc.update("""
                INSERT INTO chat_message_translations (message_id, target_language, content, model, prompt_version)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (message_id, target_language) DO NOTHING
                """, messageId, language, content, model, promptVersion);
    }
    
    // 번역 호출 기록(llm_generations)을 답변을 만든 실행에 붙이려고 찾는다. 답변의 reply_to가 그 실행의 입력 질문이다
    public Long findExecutionId(long messageId) {
        return jdbc.queryForList("""
                SELECT e.execution_id FROM chat_messages m
                  JOIN chat_executions e ON e.input_message_id = m.reply_to_id
                 WHERE m.message_id = ?
                 ORDER BY e.execution_id DESC
                 LIMIT 1
                """, Long.class, messageId).stream().findFirst().orElse(null);
    }
}
