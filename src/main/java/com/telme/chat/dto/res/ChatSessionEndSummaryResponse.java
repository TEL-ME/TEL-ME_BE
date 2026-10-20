package com.telme.chat.dto.res;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ChatSessionEndSummaryResponse(
        Long sessionId,
        String sessionStatus,
        @Schema(description = "NOT_REQUESTED, PENDING, GENERATING, COMPLETED, EMPTY, FAILED")
        String summaryStatus,
        Content summary,
        Instant generatedAt,
        String message
) {
    /**
     * topics·guidance·pendingItems는 예전 세 칸 화면용이다. inquiries·remaining은 같은 내용을 문의별로 묶은 것으로,
     * 화면이 "이 안내가 어느 문의에 대한 답인지"를 제목과 함께 보여 준다. 이 필드가 생기기 전 요약은 빈 목록이다
     */
    public record Content(List<String> topics, List<String> guidance, List<String> pendingItems,
            List<Inquiry> inquiries, List<Remaining> remaining) {
        public Content {
            topics = List.copyOf(topics);
            guidance = List.copyOf(guidance);
            pendingItems = List.copyOf(pendingItems);
            inquiries = List.copyOf(inquiries);
            remaining = List.copyOf(remaining);
        }

        public Content(List<String> topics, List<String> guidance, List<String> pendingItems) {
            this(topics, guidance, pendingItems, List.of(), List.of());
        }
    }

    @Schema(description = "문의별 안내. title은 문의(복합 질문은 하위 질문), text는 그 문의에 대해 안내받은 원문 발췌다")
    public record Inquiry(String title, String text) {}

    @Schema(description = "남은 사항. kind는 NEEDS_CONFIRMATION(물어본 조건에 답하지 않음) 또는 UNANSWERED(답을 받지 못함)")
    public record Remaining(String title, String text, String kind) {}
}
