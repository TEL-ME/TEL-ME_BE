package com.telme.chat.service;

import com.telme.chat.converter.AdminChatMonitoringConverter;
import com.telme.chat.converter.ChatExecutionTraceConverter;
import com.telme.chat.dto.req.AdminChatMonitoringSearchRequest;
import com.telme.chat.dto.res.AdminChatMonitoringDetailResponse;
import com.telme.chat.dto.res.AdminChatMonitoringListItemResponse;
import com.telme.chat.dto.res.AdminChatMonitoringListResponse;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.AdminChatMonitoringRepository;
import com.telme.chat.repository.AdminChatMonitoringRow;
import com.telme.chat.repository.ChatExecutionTraceRepository;
import com.telme.global.common.exception.GeneralException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminChatMonitoringQueryService {

    private final AdminChatMonitoringRepository monitoringRepository;
    private final ChatExecutionTraceRepository traceRepository;
    private final ChatExecutionTraceConverter traceConverter;
    private final AdminChatMonitoringConverter converter;
    private final AdminChatMonitoringStageSummarizer summarizer;

    public AdminChatMonitoringListResponse getExecutions(AdminChatMonitoringSearchRequest request) {
        // size+1개 읽어 다음 페이지 여부 판단
        List<AdminChatMonitoringRow> rows = monitoringRepository.findPage(
                request.beforeIdOrMax(), request.status(), request.size() + 1);
        boolean hasNext = rows.size() > request.size();
        List<AdminChatMonitoringListItemResponse> items = rows.stream()
                .limit(request.size())
                .map(row -> converter.toListItem(row, summarizer.summarize(row)))
                .toList();
        return converter.toListResponse(items, hasNext);
    }

    public AdminChatMonitoringDetailResponse getExecution(long executionId, Long adminId) {
        // 조회 기록 로그
        log.info("[AdminMonitoring] 조회 executionId={} adminId={}", executionId, adminId);
        var execution = traceRepository.findExecutionForAdmin(executionId)
                .orElseThrow(() -> new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND));
        long inputMessageId = (Long) execution.get("inputMessageId");
        var trace = traceConverter.toResponse(execution,
                traceRepository.findRouting(inputMessageId),
                traceRepository.findModelAttemptsWithTime(executionId));
        AdminChatMonitoringRow row = monitoringRepository.findByExecutionId(executionId, inputMessageId)
                .orElseThrow(() -> new GeneralException(ChatErrorCode.EXECUTION_NOT_FOUND));
        return converter.toDetail(trace, row, summarizer.summarize(row));
    }
}
