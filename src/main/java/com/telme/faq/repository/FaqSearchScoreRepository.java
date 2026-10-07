package com.telme.faq.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class FaqSearchScoreRepository {

    // 관리자 분포 그래프의 막대 수. 0~1을 0.05 간격으로 나눈다
    public static final int BUCKET_COUNT = 20;
    
    private final JdbcTemplate jdbcTemplate;
    
    public record Summary(long total, long passed, long aboveThreshold) {
    }
    
    public record BucketCount(int bucket, long count) {
    }
    
    // 검색이 바깥 트랜잭션 안에서 불려도 기록 실패가 그 트랜잭션을 망치지 않도록 따로 저장한다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(Double qaTopScore, boolean passed) {
        jdbcTemplate.update("INSERT INTO faq_search_scores (qa_top_score, passed) VALUES (?, ?)", qaTopScore, passed);
    }
    
    public Summary findSummary(Instant from, Instant to, double threshold) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE passed) AS passed,
                       count(*) FILTER (WHERE qa_top_score >= ?) AS above_threshold
                FROM faq_search_scores
                WHERE created_at >= ? AND created_at < ?
                """, (rs, i) -> new Summary(rs.getLong("total"), rs.getLong("passed"),
                        rs.getLong("above_threshold")), threshold, Timestamp.from(from), Timestamp.from(to));
    }
    
    // 음수(코사인 유사도는 -1까지 나온다)는 첫 칸, 1.0은 마지막 칸에 넣는다. 점수가 없는 검색은 뺀다
    public List<BucketCount> findBuckets(Instant from, Instant to) {
        return jdbcTemplate.query("""
                SELECT least(greatest(floor(qa_top_score * ?)::int, 0), ? - 1) AS bucket, count(*) AS count
                FROM faq_search_scores
                WHERE qa_top_score IS NOT NULL AND created_at >= ? AND created_at < ?
                GROUP BY bucket
                """, (rs, i) -> new BucketCount(rs.getInt("bucket"), rs.getLong("count")),
                     BUCKET_COUNT, BUCKET_COUNT, Timestamp.from(from), Timestamp.from(to));
    }
}
