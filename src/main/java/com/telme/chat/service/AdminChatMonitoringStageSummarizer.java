package com.telme.chat.service;

import com.telme.chat.dto.res.AdminChatMonitoringStageResponse;
import com.telme.chat.dto.res.AdminChatMonitoringStagesResponse;
import com.telme.chat.repository.AdminChatMonitoringRow;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

// 모니터링 칸별 상태 계산
@Component
public class AdminChatMonitoringStageSummarizer {

    public static final String PASSED = "PASSED";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";
    public static final String SKIPPED = "SKIPPED";
    public static final String RUNNING = "RUNNING";

    private static final AdminChatMonitoringStageResponse SKIP = stage(SKIPPED, null);
    // flow 배열에서 판정 칸 위치(빈 칸 채우기에서 건너뜀)
    private static final int JUDGMENT = 2;

    public AdminChatMonitoringStagesResponse summarize(AdminChatMonitoringRow row) {
        if (row.blocked()) {
            return AdminChatMonitoringStagesResponse.builder()
                    .inputCheck(inputCheck(row))
                    .routing(SKIP).search(SKIP).judgment(SKIP).generation(SKIP).outputCheck(SKIP)
                    .result(stage(FAILED, "차단"))
                    .build();
        }
        // 기록 없는 칸은 null → fillMissing에서 채움
        AdminChatMonitoringStageResponse[] flow = {
                routing(row), search(row), judgment(row), generation(row), outputCheck(row)};
        fillMissing(flow, row.running());
        return AdminChatMonitoringStagesResponse.builder()
                .inputCheck(inputCheck(row))
                .routing(flow[0])
                .search(flow[1])
                .judgment(flow[JUDGMENT])
                .generation(flow[3])
                .outputCheck(flow[4])
                .result(result(row))
                .build();
    }

    // 진행 중이면 기록된 마지막 칸 다음을 RUNNING으로. 판정은 안 하는 검색도 있어 기록으로만 RUNNING
    private void fillMissing(AdminChatMonitoringStageResponse[] flow, boolean running) {
        int last = -1;
        for (int i = 0; i < flow.length; i++) {
            if (flow[i] != null && i != JUDGMENT) last = i;
            // 이미 RUNNING 칸 있으면 추가 안 함
            if (flow[i] != null && RUNNING.equals(flow[i].state())) running = false;
        }
        int next = last + 1 == JUDGMENT ? last + 2 : last + 1;
        for (int i = 0; i < flow.length; i++) {
            if (flow[i] == null) {
                flow[i] = running && i == next ? stage(RUNNING, null) : SKIP;
            }
        }
    }

    // 검색 요청이 결과보다 많으면 아직 끝나지 않은 검색이 있다
    private boolean searching(AdminChatMonitoringRow row) {
        return row.running() && row.searchRequestCount() > row.searchStatuses().size();
    }

    // 판정은 검색 결과 기록 전에 grayZoneJudgments로 남는다. 그 전이면 판정 중
    private boolean judging(AdminChatMonitoringRow row) {
        return searching(row) && row.lastSearchGrayZone() && !"grayZoneJudgments".equals(row.lastStage());
    }

    private AdminChatMonitoringStageResponse inputCheck(AdminChatMonitoringRow row) {
        List<String> actions = row.inputActions();
        if (row.blocked()) {
            if (actions.contains("RESTRICTED")) return stage(FAILED, "제한");
            if (actions.contains("WARNED")) return stage(FAILED, "경고");
            if (actions.contains("REWRITE_REQUIRED")) return stage(FAILED, "재입력 요청");
            return stage(FAILED, "차단");
        }
        if (actions.contains("MASKED")) return stage(PARTIAL, "마스킹");
        return stage(PASSED, null);
    }

    private AdminChatMonitoringStageResponse routing(AdminChatMonitoringRow row) {
        String action = row.analysisAction();
        if (action == null && row.intent() == null) return null;
        if ("ASK".equals(action) || "WAITING".equals(action)) return stage(PARTIAL, "되묻기");
        return stage(PASSED, row.intent() != null ? row.intent() : action);
    }

    private AdminChatMonitoringStageResponse search(AdminChatMonitoringRow row) {
        List<String> statuses = row.searchStatuses();
        // 판정 중이면 판정 칸이 RUNNING. 앞선 검색 결과가 있으면 그 요약을 보여 준다
        if (searching(row) && (!judging(row) || statuses.isEmpty())) return stage(RUNNING, null);
        if (statuses.isEmpty()) {
            if (row.planAnswer()) return stage(PASSED, "요금제");
            if (row.comparisonAnswer()) return stage(PASSED, "비교");
            if (row.faqCandidateAnswer()) return stage(PASSED, "후보 확인");
            return null;
        }
        if (statuses.contains("ERROR")) return stage(FAILED, "오류");
        if (!statuses.contains("FOUND")) {
            return statuses.contains("EMPTY") ? stage(FAILED, "0건") : stage(FAILED, "취소");
        }
        // 원문, 재검색 결과에 같은 FAQ가 겹칠 수 있어 중복 제거
        String count = row.sourceFaqIds().stream().filter(Objects::nonNull).distinct().count() + "건";
        return row.refinedSearched().contains(Boolean.TRUE)
                ? stage(PARTIAL, "재검색 " + count)
                : stage(PASSED, count);
    }

    // 판정기 장애(verdict FAILED)는 빨강, 정상 판정으로 걸러진 건 노랑. 전부 걸러져 0건이면 검색 칸이 빨강이다
    private AdminChatMonitoringStageResponse judgment(AdminChatMonitoringRow row) {
        if (judging(row)) return stage(RUNNING, null);
        int total = row.judgmentKept().size();
        if (total == 0) return null;
        long failed = row.judgmentVerdicts().stream().filter("FAILED"::equals).count();
        if (failed > 0) return stage(FAILED, "판정 실패 " + failed + "건");
        long kept = row.judgmentKept().stream().filter(Boolean.TRUE::equals).count();
        String label = kept + "/" + total;
        return kept < total ? stage(PARTIAL, label) : stage(PASSED, label);
    }

    private AdminChatMonitoringStageResponse generation(AdminChatMonitoringRow row) {
        String outcome = row.guardOutcome();
        if (outcome == null) {
            if (!row.generationStarted()) return null;
            // Guard 결과 없음
            if (row.running()) return stage(RUNNING, null);
            return "FAILED".equals(row.executionStatus()) ? stage(FAILED, "실패") : stage(PASSED, null);
        }
        return switch (outcome) {
            case "KEPT" -> stage(PASSED, null);
            case "MODIFIED" -> stage(PARTIAL, "일부 수정");
            case "REPLACED" -> stage(PARTIAL, "안내 대체");
            case "NOT_RUN" -> "GENERATION_FAILED".equals(row.guardReason())
                    ? stage(FAILED, "실패")
                    : stage(SKIPPED, notRunLabel(row.guardReason()));
            default -> stage(PASSED, outcome);
        };
    }

    private String notRunLabel(String reason) {
        if (reason == null) return null;
        return switch (reason) {
            case "NO_SEARCH_RESULTS" -> "근거 없음";
            case "EVIDENCE_NOT_RELEVANT" -> "근거 부적합";
            case "COMPARISON_EVIDENCE_INCOMPLETE" -> "비교 근거 부족";
            default -> reason;
        };
    }

    private AdminChatMonitoringStageResponse outputCheck(AdminChatMonitoringRow row) {
        String safety = row.outputSafetyOutcome();
        if (safety != null) {
            return "BLOCKED".equals(safety) ? stage(FAILED, "차단") : stage(FAILED, "확인 실패");
        }
        String transmission = row.finalTransmissionStatus();
        if (transmission == null) return null;
        return "DISPATCH_ERROR".equals(transmission) ? stage(PARTIAL, "전송 오류") : stage(PASSED, null);
    }

    private AdminChatMonitoringStageResponse result(AdminChatMonitoringRow row) {
        String status = row.executionStatus();
        if (status == null) return SKIP;
        return switch (status) {
            case "RUNNING" -> stage(RUNNING, "진행 중");
            case "FAILED" -> stage(FAILED, "실패");
            case "CANCELLED" -> stage(SKIPPED, "취소");
            default -> completed(row);
        };
    }

    private AdminChatMonitoringStageResponse completed(AdminChatMonitoringRow row) {
        if ("CLARIFICATION".equals(row.answerType())) return stage(PARTIAL, "되묻기");
        if ("STORE_RESULT".equals(row.answerType())) return stage(PASSED, "매장 안내");
        if ("NO_EVIDENCE".equals(row.answerBasis())) return stage(PARTIAL, "근거 없음");
        if ("OUT_OF_SCOPE".equals(row.answerBasis())) return stage(PARTIAL, "범위 밖");
        if ("GROUNDED".equals(row.answerBasis())) return stage(PASSED, "근거 답변");
        return stage(PASSED, "답변");
    }

    private static AdminChatMonitoringStageResponse stage(String state, String label) {
        return new AdminChatMonitoringStageResponse(state, label);
    }
}
