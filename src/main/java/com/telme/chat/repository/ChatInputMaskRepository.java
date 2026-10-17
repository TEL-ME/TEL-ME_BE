package com.telme.chat.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.guard.MaskSpan;
import com.telme.chat.safety.OutputMaskContext;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatInputMaskRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OutputMaskContext read(long sessionId, int throughSequence) {
        List<OutputMaskContext.Source> sources = jdbc.query("""
                SELECT COALESCE(original_content, content), input_mask_spans::text FROM chat_messages
                WHERE session_id=? AND sequence_no<=? AND role='USER' AND message_type='QUESTION'
                    AND status='COMPLETED' AND input_mask_spans IS NOT NULL
                ORDER BY sequence_no
                """, (rs, row) -> decode(rs.getString(1), rs.getString(2)), sessionId, throughSequence);
        return new OutputMaskContext(sources);
    }

    private OutputMaskContext.Source decode(String content, String json) {
        try {
            List<MaskSpan> spans = mapper.readValue(json, new TypeReference<>() {});
            return new OutputMaskContext.Source(content, spans);
        } catch (IOException exception) {
            throw new IllegalArgumentException("저장된 마스크 위치를 읽을 수 없습니다.");
        }
    }
}
