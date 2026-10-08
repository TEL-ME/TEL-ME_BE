package com.telme.faq.repository;

import com.telme.faq.dto.req.FaqSearchKind;
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
    
    public record Summary(long total, long scored, long passed,long refinedPassed, long aboveThreshold) {
    }
    
    public record BucketCount(int bucket, long count) {
    }
    
    // 검색이 바깥 트랜잭션 안에서 불려도 기록 실패가 그 트랜잭션을 망치지 않도록 따로 저장한다
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 1)
    public void save(FaqSearchKind kind, Double qaTopScore, boolean passed, Double qaThreshold) {
        jdbcTemplate.update("INSERT INTO faq_search_scores (kind, qa_top_score, qa_threshold, passed) "
                + "VALUES (?, ?, ?, ?)", kind.name(), qaTopScore, qaThreshold, passed);
    }
    
    public int deleteCreatedBefore(Instant cutoff) {
        return jdbcTemplate.update("DELETE FROM faq_search_scores WHERE created_at < ?", Timestamp.from(cutoff));
    }
    
    // 질문 수는 원문 검색(ORIGINAL)만 센다. 정제 검색은 원문이 빈 질문에만 한 번 더 돌아 같이 세면 실패가 두 번 잡힌다.
    // scored는 분포(buckets)에 들어가는 건수다. 점수가 없는 검색은 total에만 들어간다.
    // 임계값 이상 여부는 검색 당시 임계값으로 센다. passed도 당시 설정으로 정해져 둘을 비교할 수 있다
    public Summary findSummary(Instant from, Instant to) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED')) AS total,
                       count(qa_top_score) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED')) AS scored,
                       count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED') AND passed) AS passed,
                       count(*) FILTER (WHERE kind = 'REFINED' AND passed) AS refined_passed,
                       count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED') AND qa_top_score >= qa_threshold) AS above_threshold
                FROM faq_search_scores
                WHERE created_at >= ? AND created_at < ?
                """, (rs, i) -> new Summary(rs.getLong("total"), rs.getLong("scored"), rs.getLong("passed"),
                      rs.getLong("refined_passed"), rs.getLong("above_threshold")), Timestamp.from(from), Timestamp.from(to));
    }
    
    // 음수(코사인 유사도는 -1까지 나온다)는 첫 칸, 1.0은 마지막 칸에 넣는다. 점수가 없는 검색은 뺀다
    public List<BucketCount> findBuckets(Instant from, Instant to) {
        return jdbcTemplate.query("""
                SELECT least(greatest(floor(qa_top_score * ?)::int, 0), ? - 1) AS bucket, count(*) AS count
                FROM faq_search_scores
                WHERE kind IN ('ORIGINAL', 'RESOLVED') AND qa_top_score IS NOT NULL AND created_at >= ? AND created_at < ?
                GROUP BY bucket
                """, (rs, i) -> new BucketCount(rs.getInt("bucket"), rs.getLong("count")),
                     BUCKET_COUNT, BUCKET_COUNT, Timestamp.from(from), Timestamp.from(to));
    }
}
