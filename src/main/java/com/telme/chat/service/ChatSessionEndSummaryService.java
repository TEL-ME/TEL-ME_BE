package com.telme.chat.service;

import com.telme.chat.config.ChatEndSummaryProperties;
import com.telme.chat.dto.res.ChatSessionEndSummaryResponse;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.safety.ChatOutputBlockedException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatSessionEndSummaryService {
    private final ChatSessionEndSummaryStore store;
    private final ChatSessionEndSummaryInputReader reader;
    private final ChatSessionEndSummaryGenerator generator;
    private final ChatEndSummaryProperties properties;
    private final ApplicationEventPublisher publisher;

    public void request(ChatSession session) {
        if (session.getStatus() != ChatSession.Status.CLOSED) {
            throw new IllegalArgumentException("종료된 상담의 요약만 요청할 수 있습니다.");
        }
        if (properties.enabled() && store.request(session.getSessionId())) {
            publisher.publishEvent(new Requested(session.getSessionId()));
        }
    }

    public ChatSessionEndSummaryResponse read(ChatActor actor, long sessionId) {
        return store.read(actor, sessionId);
    }

    void process(long sessionId) {
        if (!properties.enabled()) {
            return;
        }
        Optional<ChatEndSummaryData.Lease> lease = store.claim(sessionId);
        if (lease.isEmpty()) {
            return;
        }
        ChatEndSummaryData.Input input = null;
        try {
            input = reader.read(sessionId);
            if (input.messages().isEmpty()) {
                store.finish(lease.get(), input, null, "EMPTY", null);
                return;
            }
            var result = generator.generate(input, lease.get().deadline());
            store.finish(lease.get(), input, result, "COMPLETED", null);
        } catch (ChatOutputBlockedException exception) {
            store.finish(lease.get(), input, null, "FAILED", "OUTPUT_BLOCKED");
        } catch (IllegalArgumentException exception) {
            String reason = exception.getMessage();
            String code = reason != null && java.util.Set.of("INPUT_LIMIT", "WORK_LIMIT", "INVALID_OUTPUT",
                    "INVALID_INPUT", "NO_SOURCE_EXECUTION", "INVALID_GUIDANCE", "INVALID_SELECTION").contains(reason)
                    ? reason : "INVALID_OUTPUT";
            store.finish(lease.get(), input, null, "FAILED", code);
        } catch (RuntimeException exception) {
            // 후보 원문·사용자 문장·예외 본문을 새 로그나 실패 결과에 노출하지 않는다.
            store.finish(lease.get(), input, null, "FAILED", "GENERATION_FAILED");
        }
    }

    public record Requested(long sessionId) {}
}
