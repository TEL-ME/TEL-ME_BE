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

    // message_sources.title_snapshot 길이 제한
    private static final int TITLE_MAX_LENGTH = 200;

    private final MessageSourceRepository messageSourceRepository;
    private final ChatMessageRepository chatMessageRepository;

    // 답변 메시지는 호출 전에 이미 커밋된다. 저장 실패가 호출한 쪽 트랜잭션을
    // 롤백 대상으로 만들지 않도록 별도 트랜잭션으로 분리한다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(Long answerMessageId, List<AnswerSource> sources) {
        // 같은 답변으로 다시 호출돼도 근거가 쌓이지 않도록 먼저 지운다
        messageSourceRepository.deleteByMessageId(answerMessageId);
        messageSourceRepository.saveAll(sources.stream()
                .map(source -> toEntity(answerMessageId, source))
                .toList());
    }

    // title_snapshot 컬럼 길이를 넘기면 근거 전체가 저장되지 않아 AnswerContextConverter에 이어 여기서도 막는다.
    // varchar 길이는 코드포인트 기준이라 length()가 아니라 codePointCount로 판단한다
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
