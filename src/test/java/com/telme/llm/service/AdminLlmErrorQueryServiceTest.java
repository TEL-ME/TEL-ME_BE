package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.llm.dto.req.AdminLlmErrorSearchRequest;
import com.telme.llm.dto.req.AdminLlmErrorType;
import com.telme.llm.dto.res.AdminLlmErrorListItemResponse;
import com.telme.llm.dto.res.AdminLlmErrorListResponse;
import com.telme.llm.entity.LlmGeneration.TaskType;
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
class AdminLlmErrorQueryServiceTest {

    // 다른 테스트가 남긴 기록보다 위에 오도록 먼 미래 시각으로 넣는다
    private static final String BASE_TIME = "2100-01-01 00:00:00+00";
    
    @Autowired
    private AdminLlmErrorQueryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @BeforeEach
    void setUp() {
        // 시드의 execution 1번에 붙인다. 최신순: TIMEOUT 2회차 → TIMEOUT 1회차 → 성공 → 취소 → 연결 실패
        insert("RAG_ANSWER", 2, "TIMEOUT", "LLM 응답 시간이 초과되었습니다.", 50);
        insert("RAG_ANSWER", 1, "TIMEOUT", "LLM 응답 시간이 초과되었습니다.", 40);
        insert("RAG_ANSWER", 3, "SUCCESS", null, 30);
        insert("RAG_ANSWER", 1, "CANCELLED", "cancelled", 20);
        insert("ROUTING", 1, "CONNECTION_FAILED", "LLM 서버에 연결할 수 없습니다.", 10);
    }
    
    @Test
    @DisplayName("성공·취소는 빼고 오류만 최신순으로, 시각·종류·시도 횟수·메시지를 담아 반환한다")
    void 오류만_최신순으로_반환한다() {
        AdminLlmErrorListResponse response = service.getErrors(request(null, null));
        
        assertThat(response.errors()).extracting(AdminLlmErrorListItemResponse::errorType)
                  .startsWith("TIMEOUT", "TIMEOUT", "CONNECTION_FAILED");
        AdminLlmErrorListItemResponse latest = response.errors().get(0);
        assertThat(latest.attempt()).isEqualTo(2);
        assertThat(latest.taskType()).isEqualTo("RAG_ANSWER");
        assertThat(latest.errorMessage()).isEqualTo("LLM 응답 시간이 초과되었습니다.");
        assertThat(latest.executionId()).isEqualTo(1L);
        assertThat(latest.createdAt()).isNotNull();
    }
    
    @Test
    @DisplayName("오류 종류와 작업 종류로 거를 수 있다")
    void 오류_종류와_작업_종류로_거른다() {
        assertThat(service.getErrors(request(AdminLlmErrorType.CONNECTION_FAILED, null)).errors())
                .extracting(AdminLlmErrorListItemResponse::errorType)
                .containsOnly("CONNECTION_FAILED");
        assertThat(service.getErrors(request(null, TaskType.ROUTING)).errors())
                .extracting(AdminLlmErrorListItemResponse::taskType)
                .containsOnly("ROUTING");
    }

    @Test
    @DisplayName("페이지를 나눠도 같은 기록이 두 번 나오지 않는다")
    void 페이지를_나눈다() {
        AdminLlmErrorListResponse first = service.getErrors(new AdminLlmErrorSearchRequest(null, null, null, null, 0, 2));
        AdminLlmErrorListResponse second = service.getErrors(new AdminLlmErrorSearchRequest(null, null, null, null, 1, 2));

        assertThat(first.errors()).hasSize(2);
        assertThat(second.errors().get(0).errorType()).isEqualTo("CONNECTION_FAILED");
        assertThat(first.totalElements()).isGreaterThanOrEqualTo(3);
    }
    
    @Test
    @DisplayName("같은 시각에 기록된 오류는 나중에 들어온 기록이 먼저 나온다")
    void 같은_시각이면_id_역순이다() {
        // setUp의 어떤 행보다 늦은 같은 시각에 두 건을 넣는다
        insert("ROUTING", 1, "MODEL_ERROR", "모델 오류", 100);
        insert("ROUTING", 2, "MODEL_ERROR", "모델 오류", 100);

        AdminLlmErrorListResponse response = service.getErrors(request(null, null));

        assertThat(response.errors().get(0).createdAt()).isEqualTo(response.errors().get(1).createdAt());
        assertThat(response.errors().get(0).generationId()).isGreaterThan(response.errors().get(1).generationId());
        assertThat(response.errors().get(0).attempt()).isEqualTo(2);
    }
    
    @Test
    @DisplayName("기간을 주면 from 이상, to 미만에 생긴 오류만 반환한다")
    void 기간으로_거른다() {
        AdminLlmErrorListResponse response = service.getErrors(new AdminLlmErrorSearchRequest(null, null,
                Instant.parse("2100-01-01T00:00:15Z"), Instant.parse("2100-01-01T00:00:50Z"), null, null));

        assertThat(response.errors()).extracting(AdminLlmErrorListItemResponse::attempt).containsExactly(1);
        assertThat(response.totalElements()).isEqualTo(1);
    }
    
    private AdminLlmErrorSearchRequest request(AdminLlmErrorType errorType, TaskType taskType) {
        return new AdminLlmErrorSearchRequest(errorType, taskType, null, null, null, null);
    }

    private void insert(String taskType, int attempt, String status, String errorMessage, int secondsAfterBase) {
        jdbcTemplate.update("""
                INSERT INTO llm_generations (execution_id, task_type, attempt, model, status, error_message, created_at)
                VALUES (1, ?, ?, 'qwen', ?, ?, ?::timestamptz + make_interval(secs => ?))
                """, taskType, attempt, status, errorMessage, BASE_TIME, secondsAfterBase);
    }

}
