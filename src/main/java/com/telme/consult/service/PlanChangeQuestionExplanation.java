package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.consult.dto.PlanChangeConditions;
import com.telme.consult.service.FollowupContextService.Context;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** 대기 조건의 뜻만 설명한다. 고객 조건·가능 여부·새 정책은 생성하지 않는다. */
final class PlanChangeQuestionExplanation {
    private PlanChangeQuestionExplanation() {}

    static Optional<ChatAnswer> answer(Context context) {
        if (context.candidates().size() != 1) {
            return Optional.empty();
        }
        var candidate = context.candidates().getFirst();
        String field = candidate.field();
        if (!"FAQ".equals(candidate.intent()) || !PlanChangeConditions.KEYS.contains(field)) {
            return Optional.empty();
        }
        String text = context.message().replaceAll("[\\s?!.,~]", "");
        if (Pattern.compile("유심|로밍|매장|명의|청구|배송").matcher(text).find()
                || !PlanChangeConditions.extract(context.message(), Set.of(field)).isEmpty()) {
            return Optional.empty();
        }
        boolean joined = Pattern.compile("가입한달|가입월|가입여부").matcher(text).find();
        boolean changed = Pattern.compile("변경이력|변경여부|바꾼적").matcher(text).find();
        boolean meaning =
                PlanChangeConditions.isClarificationRequest(context.message())
                        || Pattern.compile("무슨뜻|무슨말|뜻이|의미|설명|뭔가요|뭐예요|뭐에요|무엇인가요|이해가안")
                                .matcher(text)
                                .find();
        boolean generic =
                text.matches(
                        "(?:(?:이|그|방금)?질문(?:은|이|을)?|방금질문)?"
                                + "(?:무슨뜻(?:이에요|인가요|이죠)|무슨말(?:이에요|인가요)|뜻이뭐(?:예요|에요)|"
                                + "설명(?:좀)?(?:해주세요|해줘|해주실래요)|이해가안(?:돼요|되네요))");
        if (!meaning || (!joined && !changed && !generic) || joined && changed) {
            return Optional.empty();
        }
        String explainedField =
                joined
                        ? PlanChangeConditions.JOINED
                        : changed ? PlanChangeConditions.CHANGED : field;
        String explanation =
                PlanChangeConditions.JOINED.equals(explainedField)
                        ? "가입한 달은 가입 날짜가 속한 달력상의 달을 뜻해요. 가입일부터 한 달이 지났는지가 아니라, 이번 달에 가입했는지를 확인하는"
                              + " 질문이에요."
                        : "변경 이력은 요금제를 바꾼 적이 있는지를 뜻해요. 지금 질문은 이번 달에 이미 요금제를 바꾼 적이 있는지 확인하는 거예요.";
        return Optional.of(
                new ChatAnswer(
                        ChatMessage.MessageType.ANSWER,
                        explanation + " " + candidate.questionText(),
                        null,
                        List.of(),
                        null));
    }
}
