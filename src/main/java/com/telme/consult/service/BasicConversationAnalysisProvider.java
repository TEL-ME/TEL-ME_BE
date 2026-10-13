package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.BasicConversationClassifier;
import com.telme.chat.service.BasicConversationResponder;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ConsultationQuestion;
import com.telme.chat.service.ConversationInput;
import com.telme.consult.dto.ClarificationReask;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisProvider;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisResult;
import com.telme.consult.service.FollowupContextService.Context;

import java.util.List;
import java.util.Objects;

/** 기본 대화 여부만 판단하고 상담 상태를 변경하지 않는다. */
public final class BasicConversationAnalysisProvider implements AnalysisProvider {
    private final AnalysisProvider delegate;

    public BasicConversationAnalysisProvider(AnalysisProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public AnalysisResult analyze(Context context) {
        ChatAnswer answer = answerFor(context);
        return answer == null ? delegate.analyze(context) : AnalysisResult.direct(answer);
    }

    public static boolean preservesWaitingState(String content) {
        return BasicConversationClassifier.classify(content).isPure()
                || !ConversationInput.hasSemanticContent(content);
    }

    public static ChatAnswer answerFor(Context context) {
        Objects.requireNonNull(context, "context");
        var result = BasicConversationClassifier.classify(context.message());
        if (!result.isPure() && ConversationInput.hasSemanticContent(context.message())) return null;
        String response = result.isPure() ? BasicConversationResponder.respond(result.type())
                : "궁금한 내용을 글로 알려주시면 도와드릴게요.";
        if (result.hasGreeting() && result.type() != BasicConversationClassifier.Type.GREETING) {
            response = "안녕하세요! " + response;
        }
        if (!context.candidates().isEmpty()) {
            var questions = context.candidates().stream().map(candidate -> candidate.questionMessageId())
                    .distinct().toList();
            response += questions.size() == 1
                    ? "\n\n" + ConsultationQuestion.extract(
                            ClarificationReask.questionOf(context.candidates().getFirst().questionText()))
                    : "\n\n앞서 안내드린 질문에도 답해 주시면 상담을 이어갈게요.";
        }
        return new ChatAnswer(ChatMessage.MessageType.ANSWER, response, null, List.of(), null);
    }
}
