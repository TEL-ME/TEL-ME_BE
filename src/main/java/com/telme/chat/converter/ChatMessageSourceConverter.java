package com.telme.chat.converter;

import com.telme.chat.dto.res.ChatMessageSourceResponse;
import com.telme.chat.dto.res.ChatMessageSourcesResponse;
import com.telme.chat.service.ChatMessageSourceQueryPort.Source;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageSourceConverter {

    public ChatMessageSourcesResponse toResponse(Long messageId, List<Source> sources) {
        List<ChatMessageSourceResponse> items = sources.stream()
                .map(this::toItemResponse)
                .toList();
        return new ChatMessageSourcesResponse(messageId, items);
    }

    private ChatMessageSourceResponse toItemResponse(Source source) {
        return new ChatMessageSourceResponse(
                source.faqId(),
                source.title(),
                source.searchRank(),
                source.score()
        );
    }
}
