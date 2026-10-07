package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;

import java.util.List;
import java.util.Objects;

/** 상담 목적에 맞는 답변 경로를 선택한다. */
public final class PurposeRoutingAnswerProvider implements AnswerProvider {
    private static final String STORE_UNAVAILABLE_MESSAGE =
            "현재 가까운 매장 정보를 바로 확인하기 어려워요. 잠시 후 다시 시도하거나 고객센터를 이용해 주세요.";

    private final AnswerProvider faqAnswers;
    private final AnswerProvider storeAnswers;

    public PurposeRoutingAnswerProvider(AnswerProvider faqAnswers) {
        this(faqAnswers, null);
    }

    public PurposeRoutingAnswerProvider(AnswerProvider faqAnswers, AnswerProvider storeAnswers) {
        this.faqAnswers = Objects.requireNonNull(faqAnswers);
        this.storeAnswers = storeAnswers;
    }

    @Override
    public boolean clarifies() {
        return faqAnswers.clarifies();
    }

    @Override
    public Prepared prepare(AnswerInput input) {
        Objects.requireNonNull(input, "input");
        // 매장 경로는 되묻기를 지역 조건으로 이미 끝낸 뒤라 여기서 다시 판단하지 않는다
        return input.purpose() == Purpose.GENERAL_FAQ ? faqAnswers.prepare(input) : Prepared.none();
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input, Prepared prepared) {
        Objects.requireNonNull(input, "input");
        if (input.purpose() == Purpose.GENERAL_FAQ) {
            return faqAnswers.generate(input, prepared);
        }
        return generate(input);
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input) {
        Objects.requireNonNull(input, "input");
        if (input.purpose() == Purpose.GENERAL_FAQ) {
            return faqAnswers.generate(input);
        }
        if (storeAnswers != null) {
            return storeAnswers.generate(input);
        }
        return GeneratedAnswer.withoutSources(
                new ChatAnswer(
                        ChatMessage.MessageType.ANSWER,
                        STORE_UNAVAILABLE_MESSAGE,
                        ChatMessage.AnswerBasis.NO_EVIDENCE,
                        List.of("고객센터 연결"),
                        null));
    }
}
