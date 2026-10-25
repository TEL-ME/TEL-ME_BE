package com.telme.faq.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.dto.req.FaqSearchKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

// save, markOutcome은 따로 커밋하는 트랜잭션이라 테스트 트랜잭션으로 되돌릴 수 없다. 만든 기록은 끝나고 직접 지운다
@SpringBootTest
class FaqSearchScoreOutcomeRepositoryTest {

    @Autowired
    private FaqSearchScoreRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> saved = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        saved.forEach(id -> jdbcTemplate.update("DELETE FROM faq_search_scores WHERE search_score_id = ?", id));
    }

    @Test
    @DisplayName("저장하면 기록 번호를 돌려주고, 판정 결과 칸은 비어 있다")
    void 저장하면_번호를_돌려준다() {
        long id = save();

        Map<String, Object> row = row(id);
        assertThat(row.get("passed")).isEqualTo(true);
        assertThat(row.get("judged")).isNull();
        assertThat(row.get("final_passed")).isNull();
        assertThat(row.get("passed_by_judge")).isNull();
    }

    @Test
    @DisplayName("판정 결과를 같은 기록에 채운다")
    void 판정_결과를_채운다() {
        long id = save();

        int updated = repository.markOutcome(id, true, true, false);

        assertThat(updated).isEqualTo(1);
        Map<String, Object> row = row(id);
        assertThat(row.get("judged")).isEqualTo(true);
        assertThat(row.get("final_passed")).isEqualTo(true);
        assertThat(row.get("passed_by_judge")).isEqualTo(false);
        // 판정 전 기준 통과 여부는 그대로 남는다
        assertThat(row.get("passed")).isEqualTo(true);
    }

    @Test
    @DisplayName("없는 기록이면 아무것도 바꾸지 않는다")
    void 없는_기록은_0() {
        assertThat(repository.markOutcome(-1L, true, true, true)).isZero();
    }

    private long save() {
        Long id = repository.save(FaqSearchKind.ORIGINAL, 0.75, true, 0.72);
        saved.add(id);
        return id;
    }

    private Map<String, Object> row(long id) {
        return jdbcTemplate.queryForMap(
                "SELECT passed, judged, final_passed, passed_by_judge FROM faq_search_scores WHERE search_score_id = ?", id);
    }
}
