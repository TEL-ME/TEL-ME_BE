package com.telme.rag.service;

import com.telme.chat.repository.ChatMessageRepository;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import com.telme.rag.entity.MessageSource;
import com.telme.rag.repository.MessageSourceRepository;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class MessageSourceWriter {

    private final MessageSourceRepository messageSourceRepository;
    private final ChatMessageRepository chatMessageRepository;

    // 답변 메시지는 호출 전에 이미 커밋된다. 저장 실패가 호출한 쪽 트랜잭션을
    // 롤백 대상으로 만들지 않도록 별도 트랜잭션으로 분리한다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(Long answerMessageId, List<AnswerSource> sources) {
        messageSourceRepository.saveAll(sources.stream()
                .map(source -> toEntity(answerMessageId, source))
                .toList());
    }

    private MessageSource toEntity(Long answerMessageId, AnswerSource source) {
        return MessageSource.builder()
                .message(chatMessageRepository.getReferenceById(answerMessageId))
                .faqId(source.faqId())
                .titleSnapshot(source.titleSnapshot())
                .faqVersion(source.faqVersion())
                .faqUpdatedAt(source.faqUpdatedAt())
                .searchRank(source.searchRank())
                .score(source.score())
                .build();
    }
}
