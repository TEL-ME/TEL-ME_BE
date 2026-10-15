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
    public record Inquiry(String title, String text) {}

    public record Remaining(String title, String text, String kind) {}

    public record Content(List<String> topics, List<String> guidance, List<String> pendingItems,
            List<Inquiry> inquiries, List<Remaining> remaining) {
        public Content(List<String> topics, List<String> guidance, List<String> pendingItems) {
            this(topics, guidance, pendingItems, List.of(), List.of());
        }

        public Content {
            topics = List.copyOf(topics);
            guidance = List.copyOf(guidance);
            pendingItems = List.copyOf(pendingItems);
            inquiries = List.copyOf(inquiries);
            remaining = List.copyOf(remaining);
        }
    }
}
