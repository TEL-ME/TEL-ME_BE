package com.telme.chat.service;

import static com.telme.chat.service.AdminChatMonitoringStageSummarizer.FAILED;
import static com.telme.chat.service.AdminChatMonitoringStageSummarizer.PARTIAL;
import static com.telme.chat.service.AdminChatMonitoringStageSummarizer.PASSED;
import static com.telme.chat.service.AdminChatMonitoringStageSummarizer.RUNNING;
import static com.telme.chat.service.AdminChatMonitoringStageSummarizer.SKIPPED;
import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.dto.res.AdminChatMonitoringStageResponse;
import com.telme.chat.repository.AdminChatMonitoringRow;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminChatMonitoringStageSummarizerTest {

    private final AdminChatMonitoringStageSummarizer summarizer = new AdminChatMonitoringStageSummarizer();

    @Test
    @DisplayName("근거 답변: 모든 칸이 통과하고 검색 건수를 보여준다")
    void 근거_답변() {
        var stages = summarizer.summarize(grounded().build());

        assertStage(stages.inputCheck(), PASSED, null);
        assertStage(stages.routing(), PASSED, "FAQ");
        assertStage(stages.search(), PASSED, "3건");
        assertStage(stages.judgment(), SKIPPED, null);
        assertStage(stages.generation(), PASSED, null);
        assertStage(stages.outputCheck(), PASSED, null);
        assertStage(stages.result(), PASSED, "근거 답변");
    }

    @Test
    @DisplayName("검색 0건: 검색 실패, 생성은 근거 없음으로 건너뜀")
    void 검색_0건() {
        var stages = summarizer.summarize(grounded()
                .searchStatuses(List.of("EMPTY")).sourceFaqIds(List.of())
                .guardOutcome("NOT_RUN").guardReason("NO_SEARCH_RESULTS")
                .answerBasis("NO_EVIDENCE").build());

        assertStage(stages.search(), FAILED, "0건");
        assertStage(stages.generation(), SKIPPED, "근거 없음");
        assertStage(stages.result(), PARTIAL, "근거 없음");
    }

    @Test
    @DisplayName("재검색이 있으면 검색 칸은 일부, 겹친 FAQ는 한 번만 센다")
    void 재검색() {
        var stages = summarizer.summarize(grounded()
                .searchStatuses(List.of("FOUND", "FOUND"))
                .sourceFaqIds(List.of(1L, 2L, 2L, 3L))
                .refinedSearched(List.of(true)).build());

        assertStage(stages.search(), PARTIAL, "재검색 3건");
    }

    @Test
    @DisplayName("회색 구간 판정: 정상 판정으로 일부·전부 걸러지면 PARTIAL, 모두 통과는 PASSED")
    void 회색_구간_판정() {
        assertStage(summarizer.summarize(grounded().judgmentKept(List.of(true, false, true))
                        .judgmentVerdicts(List.of("ANSWERABLE", "NOT_ANSWERABLE", "ANSWERABLE")).build()).judgment(),
                PARTIAL, "2/3");
        assertStage(summarizer.summarize(grounded().judgmentKept(List.of(true, true))
                        .judgmentVerdicts(List.of("ANSWERABLE", "ANSWERABLE")).build()).judgment(),
                PASSED, "2/2");
    }

    @Test
    @DisplayName("판정기가 정상적으로 전부 NOT_ANSWERABLE로 거르면 장애가 아니라 PARTIAL")
    void 정상_판정으로_전부_걸러짐() {
        var stages = summarizer.summarize(grounded().judgmentKept(List.of(false, false, false))
                .judgmentVerdicts(List.of("NOT_ANSWERABLE", "NOT_ANSWERABLE", "NOT_ANSWERABLE")).build());

        assertStage(stages.judgment(), PARTIAL, "0/3");
    }

    @Test
    @DisplayName("판정 호출이 실패하면 위로 넓힌 구간이라 통과했어도 FAILED")
    void 판정_실패는_통과해도_FAILED() {
        var stages = summarizer.summarize(grounded().judgmentKept(List.of(true, true, true))
                .judgmentVerdicts(java.util.Arrays.asList("FAILED", "FAILED", "ANSWERABLE")).build());

        assertStage(stages.judgment(), FAILED, "판정 실패 2건");
    }

    @Test
    @DisplayName("verdict가 null(slot_id 없어 판정 없이 거부)이면 정상 걸러짐")
    void 판정_없이_거부() {
        var stages = summarizer.summarize(grounded().judgmentKept(List.of(true, false))
                .judgmentVerdicts(java.util.Arrays.asList("ANSWERABLE", null)).build());

        assertStage(stages.judgment(), PARTIAL, "1/2");
    }

    @Test
    @DisplayName("첫 검색에서 판정 중이면 검색·판정 칸이 RUNNING, 생성은 아직 SKIPPED")
    void 첫_검색_판정_중() {
        var stages = summarizer.summarize(running()
                .searchRequestCount(1).lastSearchGrayZone(true).lastStage("searchRequests").build());

        assertStage(stages.search(), RUNNING, null);
        assertStage(stages.judgment(), RUNNING, null);
        assertStage(stages.generation(), SKIPPED, null);
    }

    @Test
    @DisplayName("두 번째 검색에서 판정 중이면 앞 검색 요약은 유지하고 판정 칸이 RUNNING, 생성은 SKIPPED")
    void 두번째_검색_판정_중() {
        var stages = summarizer.summarize(running()
                .searchRequestCount(2).lastSearchGrayZone(true).lastStage("searchRequests")
                .searchStatuses(List.of("FOUND")).sourceFaqIds(List.of(1L, 2L)).build());

        assertStage(stages.search(), PASSED, "2건");
        assertStage(stages.judgment(), RUNNING, null);
        assertStage(stages.generation(), SKIPPED, null);
    }

    @Test
    @DisplayName("판정이 기록되고 검색 결과 기록 전이면 판정 칸은 결과, 검색 칸이 RUNNING")
    void 판정_끝나고_결과_기록_전() {
        var stages = summarizer.summarize(running()
                .searchRequestCount(1).lastSearchGrayZone(true).lastStage("grayZoneJudgments")
                .judgmentKept(List.of(true)).judgmentVerdicts(List.of("ANSWERABLE")).build());

        assertStage(stages.search(), RUNNING, null);
        assertStage(stages.judgment(), PASSED, "1/1");
        assertStage(stages.generation(), SKIPPED, null);
    }

    @Test
    @DisplayName("판정 없는 두 번째 검색이 진행 중이면 검색 칸이 RUNNING")
    void 판정_없는_재검색_중() {
        var stages = summarizer.summarize(running()
                .searchRequestCount(2).lastSearchGrayZone(false).lastStage("searchRequests")
                .searchStatuses(List.of("EMPTY")).build());

        assertStage(stages.search(), RUNNING, null);
        assertStage(stages.judgment(), SKIPPED, null);
        assertStage(stages.generation(), SKIPPED, null);
    }

    @Test
    @DisplayName("검색이 끝나고 판정 기록이 있으면 다음 진행 칸은 생성")
    void 판정_뒤_생성_대기() {
        var stages = summarizer.summarize(running()
                .searchRequestCount(1).lastSearchGrayZone(true).lastStage("searchResults")
                .searchStatuses(List.of("FOUND")).sourceFaqIds(List.of(1L))
                .judgmentKept(List.of(true)).judgmentVerdicts(List.of("ANSWERABLE")).build());

        assertStage(stages.judgment(), PASSED, "1/1");
        assertStage(stages.generation(), RUNNING, null);
    }

    @Test
    @DisplayName("출력 검사 차단: 출력 검사 실패, 결과 실패")
    void 출력_검사_차단() {
        var stages = summarizer.summarize(grounded()
                .executionStatus("FAILED").outputSafetyOutcome("BLOCKED").finalTransmissionStatus(null)
                .answerBasis(null).build());

        assertStage(stages.outputCheck(), FAILED, "차단");
        assertStage(stages.result(), FAILED, "실패");
    }

    @Test
    @DisplayName("진행 중: 기록된 마지막 칸 다음이 RUNNING, 그 뒤는 SKIPPED")
    void 진행_중() {
        var stages = summarizer.summarize(AdminChatMonitoringRow.builder()
                .messageType("QUESTION").executionStatus("RUNNING").analysisAction("PROCEED").build());

        assertStage(stages.routing(), PASSED, "PROCEED");
        assertStage(stages.search(), RUNNING, null);
        assertStage(stages.generation(), SKIPPED, null);
        assertStage(stages.outputCheck(), SKIPPED, null);
        assertStage(stages.result(), RUNNING, "진행 중");
    }

    @Test
    @DisplayName("진행 중 생성: 생성 입력만 있고 Guard 결과가 없으면 생성 칸이 RUNNING")
    void 생성_중() {
        var stages = summarizer.summarize(grounded()
                .executionStatus("RUNNING").guardOutcome(null).finalTransmissionStatus(null)
                .generationStarted(true).build());

        assertStage(stages.generation(), RUNNING, null);
        assertStage(stages.outputCheck(), SKIPPED, null);
    }

    @Test
    @DisplayName("입력 차단: 입력 검사 실패, 나머지 SKIPPED, 결과 차단")
    void 입력_차단() {
        var stages = summarizer.summarize(AdminChatMonitoringRow.builder()
                .messageType("BLOCKED").inputActions(List.of("WARNED")).build());

        assertStage(stages.inputCheck(), FAILED, "경고");
        assertStage(stages.routing(), SKIPPED, null);
        assertStage(stages.search(), SKIPPED, null);
        assertStage(stages.generation(), SKIPPED, null);
        assertStage(stages.result(), FAILED, "차단");
    }

    @Test
    @DisplayName("마스킹: 입력 검사 일부")
    void 마스킹() {
        var stages = summarizer.summarize(grounded().inputActions(List.of("MASKED")).build());

        assertStage(stages.inputCheck(), PARTIAL, "마스킹");
    }

    @Test
    @DisplayName("되묻기: 라우팅과 결과 모두 되묻기")
    void 되묻기() {
        var stages = summarizer.summarize(AdminChatMonitoringRow.builder()
                .messageType("QUESTION").executionStatus("COMPLETED").analysisAction("ASK").intent("FAQ")
                .answerType("CLARIFICATION").finalTransmissionStatus("DISPATCH_RETURNED").build());

        assertStage(stages.routing(), PARTIAL, "되묻기");
        assertStage(stages.search(), SKIPPED, null);
        assertStage(stages.result(), PARTIAL, "되묻기");
    }

    private AdminChatMonitoringRow.AdminChatMonitoringRowBuilder running() {
        return AdminChatMonitoringRow.builder()
                .messageType("QUESTION").executionStatus("RUNNING").analysisAction("PROCEED").intent("FAQ");
    }

    private AdminChatMonitoringRow.AdminChatMonitoringRowBuilder grounded() {
        return AdminChatMonitoringRow.builder()
                .messageType("QUESTION")
                .executionStatus("COMPLETED")
                .analysisAction("PROCEED")
                .intent("FAQ")
                .searchRequestCount(1).searchStatuses(List.of("FOUND")).sourceFaqIds(List.of(1L, 2L, 3L))
                .generationStarted(true)
                .guardOutcome("KEPT")
                .finalTransmissionStatus("DISPATCH_RETURNED")
                .answerType("ANSWER")
                .answerBasis("GROUNDED");
    }

    private void assertStage(AdminChatMonitoringStageResponse stage, String state, String label) {
        assertThat(stage.state()).isEqualTo(state);
        assertThat(stage.label()).isEqualTo(label);
    }
}
