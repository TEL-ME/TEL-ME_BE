package com.telme.intent.service;

import com.telme.chat.service.ChatContext;
import java.util.regex.Pattern;

// 명확한 단독 비교 요청만 한 상담 질문으로 유지한다.
final class ComparisonQuestionPolicy {
    private static final Pattern COMPARISON_REQUEST_END = Pattern.compile(
            "(?:비교\\s*(?:해\\s*(?:줘|주세요)|해서\\s*알려\\s*(?:줘|주세요))"
                    + "|차이(?:점|가|를|는)?\\s*(?:알려\\s*(?:줘|주세요)|뭐야|무엇인가요))\\s*[?!.]*$");
    private static final Pattern EARLIER_INDEPENDENT_REQUEST = Pattern.compile(
            "(?:알려\\s*주고|찾아\\s*주고|추천\\s*해\\s*주고|설명\\s*해\\s*주고"
                    + "|확인\\s*해\\s*주고|알려\\s*줘\\s*(?:그리고|또)|찾아\\s*줘\\s*(?:그리고|또))");
    private static final Pattern CONTEXT_REFERENCE = Pattern.compile(
            "그거|그건|거기|그\\s*지역|아까|앞서|그때|이거|저거|방금|그러면|그럼");

    private ComparisonQuestionPolicy() {}

    static boolean isStandaloneComparison(String question, ChatContext context) {
        if (question == null || !COMPARISON_REQUEST_END.matcher(question).find()) {
            return false;
        }
        if (EARLIER_INDEPENDENT_REQUEST.matcher(question).find()) {
            return false;
        }
        // 원문의 지시어를 그대로 검색하면 이전 대화의 비교 대상이 사라질 수 있다.
        return context == null || !CONTEXT_REFERENCE.matcher(question).find();
    }
}
