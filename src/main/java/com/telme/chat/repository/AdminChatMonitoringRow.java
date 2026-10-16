package com.telme.chat.repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;

// 모니터링 목록 한 줄(조회 결과)
@Builder
public record AdminChatMonitoringRow(
        long messageId,
        String messageType,
        long sessionId,
        String content,
        Instant messageCreatedAt,
        Long userId,
        String userName,
        String guestId,
        Long executionId,
        String executionStatus,
        Instant startedAt,
        Instant endedAt,
        String errorCode,
        String answerType,
        String answerBasis,
        String intent,
        List<String> inputActions,
        String analysisAction,
        // searchResults[*].status
        List<String> searchStatuses,
        // searchResults[*].sources[*].faqId
        List<Long> sourceFaqIds,
        // refinedQueryChecks[*].searched
        List<Boolean> refinedSearched,
        // grayZoneJudgments[*].judgments[*].kept / verdict
        List<Boolean> judgmentKept,
        List<String> judgmentVerdicts,
        boolean planAnswer,
        boolean comparisonAnswer,
        boolean faqCandidateAnswer,
        boolean generationStarted,
        String guardOutcome,
        String guardReason,
        String outputSafetyOutcome,
        String finalTransmissionStatus,
        String lastStage
) {
    public AdminChatMonitoringRow {
        inputActions = copy(inputActions);
        searchStatuses = copy(searchStatuses);
        sourceFaqIds = copy(sourceFaqIds);
        refinedSearched = copy(refinedSearched);
        judgmentKept = copy(judgmentKept);
        judgmentVerdicts = copy(judgmentVerdicts);
    }

    public boolean blocked() {
        return "BLOCKED".equals(messageType);
    }

    public boolean running() {
        return "RUNNING".equals(executionStatus);
    }

    // null 원소(값 없는 verdict 등)가 있어 List.copyOf 대신 unmodifiableList
    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
