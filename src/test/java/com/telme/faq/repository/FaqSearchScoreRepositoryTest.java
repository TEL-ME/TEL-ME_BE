package com.telme.faq.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class FaqSearchScoreRepositoryTest {

    private static final Instant CUTOFF = Instant.parse("2100-01-01T00:00:00Z");
    
    @Autowired
    private FaqSearchScoreRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Test
    @DisplayName("기준 시각보다 먼저 생긴 기록만 지운다")
    void 기준_시각_이전만_지운다() {
        insert("2099-12-31T23:59:59Z");
        insert("2100-01-01T00:00:00Z"); // 기준 시각과 같으면 남는다
        long before = countFrom("2099-12-31T00:00:00Z");

        int deleted = repository.deleteCreatedBefore(CUTOFF);

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(countFrom("2099-12-31T00:00:00Z")).isEqualTo(before - 1);
    }

    private void insert(String createdAt) {
        jdbcTemplate.update("""
                INSERT INTO faq_search_scores (kind, qa_top_score, qa_threshold, passed, created_at)
                VALUES ('ORIGINAL', 0.8, 0.72, true, ?::timestamptz)
                """, createdAt);
    }

    private long countFrom(String from) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM faq_search_scores WHERE created_at >= ?::timestamptz", Long.class, from);
    }
}
