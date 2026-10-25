package com.telme.faq.repository;

import com.telme.faq.dto.req.FaqSearchKind;
import com.telme.faq.entity.FaqGrayZoneOutcome;
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
    
    public record Summary(long total, long scored, long passed,long refinedPassed, long aboveThreshold,
            OutcomeCounts outcomeCounts) {
    }

    // 회색 구간 판정 뒤 결과(gray_zone_outcome, FaqGrayZoneOutcome)별 건수. 결과를 남긴 검색만 센다.
    // 질문별 첫 검색의 여섯 건수는 겹치지 않아 합이 recorded다. refinedFinalPassed는 정제 검색에서 근거를 찾은 수다
    public record OutcomeCounts(long recorded, long passedDirectly, long passedByJudge, long passedOnJudgeFailure,
            long rejectedByJudge, long droppedOnJudgeFailure, long belowFloor, long refinedFinalPassed) {
    }
    
    public record BucketCount(int bucket, long count) {
    }
    
    // 검색이 바깥 트랜잭션 안에서 불려도 기록 실패가 그 트랜잭션을 망치지 않도록 따로 저장한다.
    // 회색 구간 판정이 끝난 뒤 같은 기록에 결과를 채울 수 있게 기록 번호를 돌려준다
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 1)
    public Long save(FaqSearchKind kind, Double qaTopScore, boolean passed, Double qaThreshold) {
        return jdbcTemplate.queryForObject("INSERT INTO faq_search_scores (kind, qa_top_score, qa_threshold, passed) "
                + "VALUES (?, ?, ?, ?) RETURNING search_score_id", Long.class, kind.name(), qaTopScore, qaThreshold, passed);
    }

    // 회색 구간 판정 뒤 결과를 남긴다. save와 같은 이유로 따로 저장한다. 이미 지워진 기록이면 0을 돌려준다
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 1)
    public int markOutcome(long searchScoreId, FaqGrayZoneOutcome outcome) {
        return jdbcTemplate.update("UPDATE faq_search_scores SET gray_zone_outcome = ? WHERE search_score_id = ?",
                outcome.name(), searchScoreId);
    }

    public int deleteCreatedBefore(Instant cutoff) {
        return jdbcTemplate.update("DELETE FROM faq_search_scores WHERE created_at < ?", Timestamp.from(cutoff));
    }
    
    // 질문 수는 질문별 첫 검색만 센다. 첫 검색은 원문(ORIGINAL)이거나, 이전 대화로 지시어를 풀어 쓴 질문(RESOLVED)이다.
    // 한 질문에는 둘 중 하나만 돈다(FaqSearchAnswerProvider). 정제 검색(REFINED)은 첫 검색이 빈 질문에만
    // 한 번 더 돌아 같이 세면 실패가 두 번 잡히므로 refined_passed에만 쓴다.
    // scored는 분포(buckets)에 들어가는 건수다. 점수가 없는 검색은 total에만 들어간다.
    // 임계값 이상 여부는 검색 당시 임계값으로 센다. passed도 당시 설정으로 정해져 둘을 비교할 수 있다.
    // passed·above_threshold는 회색 구간 판정 전 기준이고, 판정 뒤 실제 결과는 outcomeCounts로 따로 센다.
    // gray_zone_outcome 값은 FaqGrayZoneOutcome 이름이다. 정제 검색의 근거 찾음은 PASSED로 시작하는 세 값이다
    public Summary findSummary(Instant from, Instant to) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED')) AS total,
                       count(qa_top_score) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED')) AS scored,
                       count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED') AND passed) AS passed,
                       count(*) FILTER (WHERE kind = 'REFINED' AND passed) AS refined_passed,
                       count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED') AND qa_top_score >= qa_threshold) AS above_threshold,
                       count(gray_zone_outcome) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED')) AS outcome_recorded,
                       count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED') AND gray_zone_outcome = 'PASSED_DIRECTLY') AS passed_directly,
                       count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED') AND gray_zone_outcome = 'PASSED_BY_JUDGE') AS passed_by_judge,
                       count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED') AND gray_zone_outcome = 'PASSED_ON_JUDGE_FAILURE') AS passed_on_judge_failure,
                       count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED') AND gray_zone_outcome = 'REJECTED_BY_JUDGE') AS rejected_by_judge,
                       count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED') AND gray_zone_outcome = 'DROPPED_ON_JUDGE_FAILURE') AS dropped_on_judge_failure,
                       count(*) FILTER (WHERE kind IN ('ORIGINAL', 'RESOLVED') AND gray_zone_outcome = 'BELOW_FLOOR') AS below_floor,
                       count(*) FILTER (WHERE kind = 'REFINED' AND gray_zone_outcome IN ('PASSED_DIRECTLY', 'PASSED_BY_JUDGE', 'PASSED_ON_JUDGE_FAILURE')) AS refined_final_passed
                FROM faq_search_scores
                WHERE created_at >= ? AND created_at < ?
                """, (rs, i) -> new Summary(rs.getLong("total"), rs.getLong("scored"), rs.getLong("passed"),
                      rs.getLong("refined_passed"), rs.getLong("above_threshold"),
                      new OutcomeCounts(rs.getLong("outcome_recorded"), rs.getLong("passed_directly"),
                              rs.getLong("passed_by_judge"), rs.getLong("passed_on_judge_failure"),
                              rs.getLong("rejected_by_judge"), rs.getLong("dropped_on_judge_failure"),
                              rs.getLong("below_floor"), rs.getLong("refined_final_passed"))),
                Timestamp.from(from), Timestamp.from(to));
    }
    
    // 음수(코사인 유사도는 -1까지 나온다)는 첫 칸, 1.0은 마지막 칸에 넣는다. 점수가 없는 검색과 정제 검색은 뺀다
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
