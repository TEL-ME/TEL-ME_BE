package com.telme.faq.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.dto.req.AdminSearchScoreRequest;
import com.telme.faq.dto.res.AdminSearchScoreResponse;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AdminSearchScoreQueryServiceTest {

    // 다른 테스트가 남긴 기록과 섞이지 않도록 먼 미래의 하루만 본다
    private static final Instant FROM = Instant.parse("2100-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2100-01-02T00:00:00Z");

    @Autowired
    private AdminSearchScoreQueryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        insert(0.95, true);
        insert(1.0, true);    // 마지막 칸(0.95~1.0)에 들어가야 한다
        insert(0.73, true);
        insert(0.71, true);   // 임계값 미만이지만 질문 벡터로 통과한 검색
        insert(0.40, false);
        insert(-0.10, false); // 음수는 첫 칸
        insert(null, false);  // 후보가 없던 검색: 건수에는 들어가고 분포에서는 빠진다
        jdbcTemplate.update("INSERT INTO faq_search_scores (qa_top_score, passed, created_at) VALUES (0.99, true, ?)",
                Timestamp.from(TO)); // 기간 밖(끝 시각과 같음)
    }

    @Test
    @DisplayName("검색 수, 실제 통과 수, 임계값 이상 수와 현재 임계값을 반환한다")
    void 요약을_반환한다() {
        AdminSearchScoreResponse response = service.getScores(new AdminSearchScoreRequest(FROM, TO));

        assertThat(response.threshold()).isEqualTo(0.72);
        assertThat(response.total()).isEqualTo(7);
        assertThat(response.passed()).isEqualTo(4);
        assertThat(response.aboveThreshold()).isEqualTo(3);
    }

    @Test
    @DisplayName("1위 점수를 0.05 간격 20칸으로 나누고 빈 칸은 0으로 채운다")
    void 분포를_20칸으로_나눈다() {
        var buckets = service.getScores(new AdminSearchScoreRequest(FROM, TO)).buckets();

        assertThat(buckets).hasSize(20);
        assertThat(buckets.get(0).count()).isEqualTo(1);   // -0.10
        assertThat(buckets.get(8).count()).isEqualTo(1);   // 0.40
        assertThat(buckets.get(14).count()).isEqualTo(2);  // 0.71, 0.73
        assertThat(buckets.get(14).min()).isEqualTo(0.7);
        assertThat(buckets.get(19).count()).isEqualTo(2);  // 0.95, 1.0
        assertThat(buckets.get(19).max()).isEqualTo(1.0);
        assertThat(buckets.get(10).count()).isZero();
    }

    private void insert(Double score, boolean passed) {
        jdbcTemplate.update("""
                INSERT INTO faq_search_scores (qa_top_score, passed, created_at)
                VALUES (?, ?, ?::timestamptz + interval '1 hour')
                """, score, passed, FROM.toString());
    }
}
