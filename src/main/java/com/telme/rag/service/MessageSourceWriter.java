package com.telme.rag.service;

import com.telme.chat.repository.ChatMessageRepository;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import com.telme.rag.entity.MessageSource;
import com.telme.rag.repository.MessageSourceRepository;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class MessageSourceWriter {

    // message_sources.title_snapshot 길이 제한
    private static final int TITLE_MAX_LENGTH = 200;

    private final MessageSourceRepository messageSourceRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional
    public void write(Long answerMessageId, List<AnswerSource> sources) {
        messageSourceRepository.deleteByMessageId(answerMessageId);
        messageSourceRepository.saveAll(sources.stream()
                .map(source -> toEntity(answerMessageId, source))
                .toList());
    }

    // varchar 길이는 코드포인트 기준이라 length()로 판단하면 이모지가 불필요하게 잘린다
    private String truncateTitle(String title) {
        if (title == null || title.codePointCount(0, title.length()) <= TITLE_MAX_LENGTH) {
            return title;
        }
        return title.substring(0, title.offsetByCodePoints(0, TITLE_MAX_LENGTH));
    }

    private MessageSource toEntity(Long answerMessageId, AnswerSource source) {
        return MessageSource.builder()
                .message(chatMessageRepository.getReferenceById(answerMessageId))
                .faqId(source.faqId())
                .titleSnapshot(truncateTitle(source.titleSnapshot()))
                .faqVersion(source.faqVersion())
                .faqUpdatedAt(source.faqUpdatedAt())
                .searchRank(source.searchRank())
                .score(source.score())
                .build();
    }
}
