package com.telme.rag.service;

import com.telme.chat.service.ChatRepeatedInquiryResolver;
import com.telme.rag.dto.req.AnswerRequest;
import java.util.regex.Pattern;

/** 단일 유심 FAQ의 명시적인 절차 안내는 원문을 전달해 숫자에 새 인과관계를 붙이지 않는다. */
final class ProcedureEvidenceAnswer {
    private static final Pattern REPEAT_REQUEST = Pattern.compile(
            "(?:같은|동일한)유심재발급(?:의)?(?:신분증지참과매장방문)?절차(?:를)?다시알려(?:주세요|줘)[.!?]*$");
    private static final Pattern OTHER_SCOPE = Pattern.compile(
            "어머니|아버지|부모|자녀|배우자|대리인|명의|회선|esim|이심|e심|로밍|요금제|번호이동|정지|해지",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ELIGIBILITY = Pattern.compile(
            "(?:개월|년)(?:이면|이라서|기준)|때문|(?:무료|대상|가능)(?:인가|한가|해요|한지)");

    private ProcedureEvidenceAnswer() {}

    static String select(AnswerRequest request) {
        if (request.searchResults().size() != 1 || !request.conditions().isEmpty()) {
            return null;
        }
        String question = request.userQuery().replaceAll("(?U)\\s+", "");
        if (ELIGIBILITY.matcher(question).find()) {
            return null;
        }
        boolean direct = question.contains("유심") && question.contains("재발급")
                && question.contains("절차만설명") && !OTHER_SCOPE.matcher(question).find();
        boolean repeated = REPEAT_REQUEST.matcher(question).find()
                && ChatRepeatedInquiryResolver.isVerifiedRepeat(request.userQuery(),
                        request.resolvedQuery(), request.chatContext());
        if (!direct && !repeated) {
            return null;
        }
        var source = request.searchResults().getFirst();
        String answer = source.answer();
        String subject = source.question() == null ? "" : source.question();
        if (answer == null || !subject.contains("유심") || !subject.contains("재발급")
                || !answer.contains("신분증") || !answer.contains("매장") || !answer.contains("방문")) {
            return null;
        }
        return answer;
    }
}
