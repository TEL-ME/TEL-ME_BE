package com.telme.rag.service;

import com.telme.chat.service.ChatMessageSourceQueryPort;
import com.telme.rag.entity.MessageSource;
import com.telme.rag.repository.MessageSourceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MessageSourceQueryAdapter implements ChatMessageSourceQueryPort {

    private final MessageSourceRepository messageSourceRepository;

    @Override
    public List<Source> findByMessageId(Long messageId) {
        return messageSourceRepository.findByMessage_MessageIdOrderBySearchRankAscSourceIdAsc(messageId).stream()
                .map(this::toSource)
                .toList();
    }

    private Source toSource(MessageSource source) {
        return new Source(
                source.getFaqId(),
                source.getTitleSnapshot(),
                source.getSearchRank(),
                source.getScore()
        );
    }
}
