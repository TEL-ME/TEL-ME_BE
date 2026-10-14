package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 요금제 목록 질문은 FAQ 검색 없이 요금제 테이블로 답하고, 그 밖의 질문은 FAQ 답변 경로에 맡긴다. */
public final class PlanAnswerProvider implements AnswerProvider {
    private final AnswerProvider faqAnswers;
    private final PlanQuestionClassifier classifier;
    private final PlanCatalogAnswerer catalog;
    private final ComparisonEvidenceResolver comparisonEvidence;
    private final ExecutionTrace trace;
    private final boolean enabled;

    public PlanAnswerProvider(AnswerProvider faqAnswers, PlanQuestionClassifier classifier,
            PlanCatalogAnswerer catalog, ComparisonEvidenceResolver comparisonEvidence, ExecutionTrace trace,
            boolean enabled) {
        this.faqAnswers = Objects.requireNonNull(faqAnswers);
        this.classifier = Objects.requireNonNull(classifier);
        this.catalog = Objects.requireNonNull(catalog);
        this.comparisonEvidence = Objects.requireNonNull(comparisonEvidence);
        this.trace = Objects.requireNonNull(trace);
        this.enabled = enabled;
    }

    @Override
    public boolean clarifies() {
        return faqAnswers.clarifies();
    }

    @Override
    public Prepared prepare(AnswerInput input, boolean plansClarification) {
        GeneratedAnswer planAnswer = planAnswer(input);
        return planAnswer != null ? Prepared.answered(planAnswer) : faqAnswers.prepare(input, plansClarification);
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input, Prepared prepared) {
        return faqAnswers.generate(input, prepared);
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input) {
        GeneratedAnswer planAnswer = planAnswer(input);
        return planAnswer != null ? planAnswer : faqAnswers.generate(input);
    }

    private GeneratedAnswer planAnswer(AnswerInput input) {
        // 비교 질문은 기존 비교 답변 경로가 양쪽 FAQ 근거를 확인해 답한다
        if (!enabled || comparisonEvidence.applies(input.originalUserQuery())) {
            return null;
        }
        String question = input.resolvedUserQuery() == null || input.resolvedUserQuery().isBlank()
                ? input.originalUserQuery() : input.resolvedUserQuery();
        PlanQuestionType type = classifier.classify(input.executionId(), question);
        if (type != PlanQuestionType.PLAN_CATALOG) {
            return null;
        }
        String content = catalog.answer(question);
        if (content == null) {
            return null;
        }
        trace.stage(input.executionId(), "planAnswer", Map.of("type", type.name()));
        return GeneratedAnswer.withoutSources(
                new ChatAnswer(ChatMessage.MessageType.ANSWER, content, ChatMessage.AnswerBasis.GROUNDED, List.of(), null));
    }
}
