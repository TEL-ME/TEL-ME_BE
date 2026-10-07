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
        insert(0.75, false, 0.80); // 당시 임계값이 0.80이던 검색: 지금 기준(0.72)으로는 넘지만 당시 기준으로는 미달
        // 원문이 빈 질문의 정제 검색: 질문 수·분포에는 들어가지 않고 정제로 근거를 찾은 수에만 들어간다
        insertRefined(0.74, true);
        insertRefined(0.30, false);
        jdbcTemplate.update("INSERT INTO faq_search_scores (qa_top_score, passed, created_at) VALUES (0.99, true, ?)",
                Timestamp.from(TO)); // 기간 밖(끝 시각과 같음)
    }

    @Test
    @DisplayName("검색 수, 분포 대상 수, 실제 통과 수, 당시 임계값 이상 수와 현재 임계값을 반환한다")
    void 요약을_반환한다() {
        AdminSearchScoreResponse response = service.getScores(new AdminSearchScoreRequest(FROM, TO));

        assertThat(response.threshold()).isEqualTo(0.72);
        assertThat(response.total()).isEqualTo(8);
        assertThat(response.scored()).isEqualTo(7);
        assertThat(response.passed()).isEqualTo(4);
        assertThat(response.refinedPassed()).isEqualTo(1);
        // 0.95, 1.0, 0.73만. 0.75는 당시 임계값(0.80) 미만이라 빠진다
        assertThat(response.aboveThreshold()).isEqualTo(3);
        assertThat(response.buckets().stream().mapToLong(AdminSearchScoreResponse.Bucket::count).sum())
                .isEqualTo(response.scored());
    }

    @Test
    @DisplayName("1위 점수를 0.05 간격 20칸으로 나누고 빈 칸은 0으로 채운다")
    void 분포를_20칸으로_나눈다() {
        var buckets = service.getScores(new AdminSearchScoreRequest(FROM, TO)).buckets();

        assertThat(buckets).hasSize(20);
        assertThat(buckets.get(0).count()).isEqualTo(1);   // -0.10
        assertThat(buckets.get(8).count()).isEqualTo(1);   // 0.40
        assertThat(buckets.get(14).count()).isEqualTo(2);  // 0.71, 0.73 (정제 검색 0.74는 빠진다)
        assertThat(buckets.get(14).min()).isEqualTo(0.7);
        assertThat(buckets.get(19).count()).isEqualTo(2);  // 0.95, 1.0
        assertThat(buckets.get(19).max()).isEqualTo(1.0);
        assertThat(buckets.get(10).count()).isZero();
    }

    private void insert(Double score, boolean passed) {
        insert(score, passed, 0.72);
    }

    private void insert(Double score, boolean passed, double threshold) {
        insert("ORIGINAL", score, passed, threshold);
    }

    private void insertRefined(Double score, boolean passed) {
        insert("REFINED", score, passed, 0.72);
    }

    private void insert(String kind, Double score, boolean passed, double threshold) {
        jdbcTemplate.update("""
                INSERT INTO faq_search_scores (kind, qa_top_score, qa_threshold, passed, created_at)
                VALUES (?, ?, ?, ?, ?::timestamptz + interval '1 hour')
                """, kind, score, threshold, passed, FROM.toString());
    }
}
