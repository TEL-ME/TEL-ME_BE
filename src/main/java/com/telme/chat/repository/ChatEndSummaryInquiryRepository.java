package com.telme.chat.repository;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ChatEndSummaryInquiryRepository {
    private final JdbcTemplate jdbc;

    public record Classification(String intent, List<String> faqCategories) {
        public Classification {
            faqCategories = List.copyOf(faqCategories);
        }
    }

    public Map<Long, Classification> findBySessionId(long sessionId) {
        var rows = jdbc.query("""
                SELECT m.message_id, r.intent, f.category
                FROM chat_messages m
                LEFT JOIN consult_requests r ON r.session_id=m.session_id
                    AND r.origin_message_id=coalesce(m.reply_to_id,m.message_id)
                LEFT JOIN message_sources s ON s.message_id=m.message_id AND m.role='ASSISTANT'
                LEFT JOIN faqs f ON f.faq_id=s.faq_id AND f.version=s.faq_version
                WHERE m.session_id=? AND m.status='COMPLETED'
                ORDER BY m.sequence_no, r.subquery_order, s.source_id
                """, (rs, n) -> new Row(rs.getLong("message_id"), rs.getString("intent"), rs.getString("category")),
                sessionId);
        Map<Long, Builder> builders = new LinkedHashMap<>();
        for (var row : rows) {
            var builder = builders.computeIfAbsent(row.messageId(), ignored -> new Builder());
            if (row.intent() != null) {
                builder.intents.add(row.intent());
            }
            if (row.category() != null) {
                builder.categories.add(row.category());
            }
        }
        Map<Long, Classification> result = new LinkedHashMap<>();
        builders.forEach((id, builder) -> result.put(id, builder.build()));
        return Map.copyOf(result);
    }

    private record Row(long messageId, String intent, String category) {}

    private static final class Builder {
        private final Set<String> intents = new LinkedHashSet<>();
        private final Set<String> categories = new LinkedHashSet<>();

        private Classification build() {
            return new Classification(intents.size() == 1 ? intents.iterator().next() : "",
                    List.copyOf(categories));
        }
    }
}
