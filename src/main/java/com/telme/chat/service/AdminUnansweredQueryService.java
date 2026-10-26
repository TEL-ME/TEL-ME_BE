package com.telme.chat.service;

import com.telme.chat.converter.AdminUnansweredConverter;
import com.telme.chat.dto.req.AdminUnansweredSearchRequest;
import com.telme.chat.dto.res.AdminUnansweredDetailResponse;
import com.telme.chat.dto.res.AdminUnansweredListResponse;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.AdminUnansweredRepository;
import com.telme.chat.repository.UnansweredOriginQuestionFinder;
import com.telme.global.common.exception.GeneralException;
import com.telme.rag.repository.MessageSourceRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUnansweredQueryService {

    private final AdminUnansweredRepository unansweredRepository;
    private final MessageSourceRepository messageSourceRepository;
    private final AdminUnansweredConverter converter;
    private final UnansweredOriginQuestionFinder originQuestionFinder;

    public AdminUnansweredListResponse getUnanswered(AdminUnansweredSearchRequest request) {
        // 시작이 끝보다 뒤면 결과가 늘 비어 조건을 잘못 넣은 것을 알아채기 어렵다
        if (request.periodReversed()) {
            throw new GeneralException(ChatErrorCode.INVALID_PERIOD);
        }
        Page<ChatMessage> messages = find(request);
        return converter.toListResponse(messages, originQuestions(messages.getContent()));
    }

    public AdminUnansweredDetailResponse getUnanswered(long messageId) {
        ChatMessage message = unansweredRepository.findUnansweredById(messageId)
                .orElseThrow(() -> new GeneralException(ChatErrorCode.MESSAGE_NOT_FOUND));
        return converter.toDetail(
                message,
                messageSourceRepository.findByMessage_MessageIdOrderBySearchRankAscSourceIdAsc(messageId),
                originQuestions(List.of(message)).get(messageId));
    }

    private Page<ChatMessage> find(AdminUnansweredSearchRequest request) {
        List<ChatMessage.AnswerBasis> bases = request.basisTypes().stream()
                .map(type -> ChatMessage.AnswerBasis.valueOf(type.name())).toList();
        List<ChatMessage.Status> statuses = request.statusTypes().stream()
                .map(type -> ChatMessage.Status.valueOf(type.name())).toList();
        Instant from = request.fromOrMin();
        Instant to = request.toOrMax();
        Pageable page = PageRequest.of(request.page(), request.size());

        if (bases.isEmpty() && statuses.isEmpty()) {
            return unansweredRepository.findUnanswered(from, to, page);
        }
        if (statuses.isEmpty()) {
            return unansweredRepository.findUnansweredByBases(bases, from, to, page);
        }
        if (bases.isEmpty()) {
            return unansweredRepository.findUnansweredByStatuses(statuses, from, to, page);
        }
        return unansweredRepository.findUnansweredByBasesOrStatuses(bases, statuses, from, to, page);
    }

    // 목록 한 페이지의 원래 질문을 한 번에 읽는다. 행마다 읽으면 조회가 페이지 크기만큼 늘어난다
    private Map<Long, String> originQuestions(List<ChatMessage> messages) {
        return originQuestionFinder.findByAnswerIds(
                messages.stream().map(ChatMessage::getMessageId).toList());
    }
}
