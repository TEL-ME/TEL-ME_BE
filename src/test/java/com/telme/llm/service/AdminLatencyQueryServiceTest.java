package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.telme.llm.dto.req.AdminLatencySearchRequest;
import com.telme.llm.dto.res.AdminLatencyResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AdminLatencyQueryServiceTest {

    private static final Instant FROM = Instant.parse("2100-01-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2100-01-02T00:00:00Z");
    private static final long ANSWER_MESSAGE_ID = 2L;
    
    @Autowired
    private AdminLatencyQueryService service;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @MockitoBean
    private Clock clock;
    
    @BeforeEach
    void setUp() {
        for (int i = 1; i <= 4; i++) {
            generation("RAG_ANSWER", "SUCCESS", 100 * i, 1000 * i, 60);
        }
        // 실패는 빠져야 한다: 시간 초과까지 기다린 값이라 섞으면 평균이 부푼다
        generation("RAG_ANSWER", "TIMEOUT", null, 30000, 60);
        generation("ROUTING", "SUCCESS", null, 500, 60);
        // 기간 밖(끝 시각과 같음)은 빠져야 한다
        generation("ROUTING", "SUCCESS", null, 9999, 24 * 60 * 60);

        // 실행: 완료 2건(2초·4초), 실패 1건(60초)
        execution("COMPLETED", 2000, ANSWER_MESSAGE_ID);
        execution("COMPLETED", 4000, ANSWER_MESSAGE_ID);
        execution("FAILED", 60000, null);
        execution("COMPLETED", 10000, null);
    }
    
    @Test
    @DisplayName("전체는 답변을 저장하고 완료된 실행만, 질문부터 답변 저장까지 시간으로 센다")
    void 전체는_완료된_실행만_센다() {
        AdminLatencyResponse.Stats overall = service.getLatency(new AdminLatencySearchRequest(FROM, TO)).overall();

        assertThat(overall.count()).isEqualTo(2);
        assertThat(overall.avgMs()).isEqualTo(3000);
        assertThat(overall.p50Ms()).isEqualTo(3000);
    }

    @Test
    @DisplayName("첫 토큰은 성공한 스트리밍 호출만 센다")
    void 첫_토큰은_성공한_스트리밍_호출만_센다() {
        AdminLatencyResponse.Stats firstToken =
                service.getLatency(new AdminLatencySearchRequest(FROM, TO)).firstToken();

        assertThat(firstToken.count()).isEqualTo(4);
        assertThat(firstToken.avgMs()).isEqualTo(250);
        assertThat(firstToken.p50Ms()).isEqualTo(250);
        assertThat(firstToken.p95Ms()).isEqualTo(385);
    }

    @Test
    @DisplayName("작업별은 성공한 시도만 세고, 호출이 없던 작업도 0건으로 나온다")
    void 작업별은_성공만_세고_모든_작업이_나온다() {
        Map<String, AdminLatencyResponse.TaskStats> tasks = service.getLatency(new AdminLatencySearchRequest(FROM, TO))
                .tasks().stream()
                .collect(Collectors.toMap(AdminLatencyResponse.TaskStats::taskType, Function.identity()));

        assertThat(tasks.get("RAG_ANSWER").count()).isEqualTo(4);
        assertThat(tasks.get("RAG_ANSWER").avgMs()).isEqualTo(2500);
        assertThat(tasks.get("ROUTING").count()).isEqualTo(1);
        assertThat(tasks.get("SUMMARY").count()).isZero();
        assertThat(tasks.get("SUMMARY").avgMs()).isNull();
    }
    
    @Test
    @DisplayName("기간을 비우면 Clock 기준 최근 24시간을 센다")
    void 기간을_비우면_최근_24시간이다() {
        when(clock.instant()).thenReturn(TO);

        AdminLatencyResponse response = service.getLatency(new AdminLatencySearchRequest(null, null));

        assertThat(response.from()).isEqualTo(FROM);
        assertThat(response.to()).isEqualTo(TO);
        assertThat(response.overall().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("기간 안에 기록이 없으면 0건과 null을 반환한다")
    void 기록이_없으면_0건이다() {
        AdminLatencyResponse response = service.getLatency(new AdminLatencySearchRequest(
                Instant.parse("2200-01-01T00:00:00Z"), Instant.parse("2200-01-02T00:00:00Z")));

        assertThat(response.overall().count()).isZero();
        assertThat(response.overall().avgMs()).isNull();
        assertThat(response.firstToken().p95Ms()).isNull();
    }

    
    private void generation(String taskType, String status, Integer firstTokenMs, int totalMs, int secondsAfterFrom) {
        jdbcTemplate.update("""
                INSERT INTO llm_generations (execution_id, task_type, attempt, status, first_token_ms, total_ms, created_at)
                VALUES (1, ?, 1, ?, ?, ?, ?::timestamptz + make_interval(secs => ?))
                """, taskType, status, firstTokenMs, totalMs, FROM.toString(), secondsAfterFrom);
    }
    
    private void execution(String status, int elapsedMs, Long outputMessageId) {
        jdbcTemplate.update("""
                INSERT INTO chat_executions (session_id, input_message_id, output_message_id, status, started_at, ended_at)
                VALUES (1, 1, ?, ?, ?::timestamptz + interval '1 hour',
                        ?::timestamptz + interval '1 hour' + make_interval(secs => ? / 1000.0))
                """, outputMessageId, status, FROM.toString(), FROM.toString(), elapsedMs);
    }
}
