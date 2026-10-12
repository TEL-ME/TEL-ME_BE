package com.telme.intent.service;

import com.telme.chat.service.ChatContext;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 명확한 단독 비교 요청만 한 상담 질문으로 유지한다.
public final class ComparisonQuestionPolicy {
    private static final Pattern COMPARISON_REQUEST_END = Pattern.compile(
            "(?:비교(?:\\s*(?:해\\s*(?:줘|주세요)|해서\\s*알려\\s*(?:줘|주세요)))?"
                    + "|차이(?:점|가|를|는)?(?:\\s*(?:알려\\s*(?:줘|주세요)|뭐야|무엇인가요))?)\\s*[?!.]*$");
    private static final Pattern EARLIER_INDEPENDENT_REQUEST = Pattern.compile(
            "(?:알려\\s*주고|찾아\\s*주고|추천\\s*해\\s*주고|설명\\s*해\\s*주고"
                    + "|확인\\s*해\\s*주고|알려\\s*줘\\s*(?:그리고|또)|찾아\\s*줘\\s*(?:그리고|또))");
    private static final Pattern EARLIER_SENTENCE_REQUEST = Pattern.compile(
            "(?:알려\\s*(?:줘|주세요|주십시오)|찾아\\s*(?:줘|주세요|주십시오)"
                    + "|(?:추천|설명|확인|비교)\\s*해\\s*(?:줘|주세요|주십시오))"
                    + "\\s*(?:[.!?。]+|\\R)\\s*(?=[^\\s.!?。])");
    private static final Pattern EARLIER_QUESTION = Pattern.compile(
            "(?:어떻게|무엇|얼마|어디|언제|몇|뭐|가능)[^.!?\\r\\n]*?"
                    + "(?:\\?|(?:나요|까요|죠|야|예요|인가요)\\s*(?:[.!。]+|\\R))\\s*(?=[^\\s.!?。])");
    private static final Pattern CONTEXT_REFERENCE = Pattern.compile(
            "그거|그건|거기|그\\s*지역|아까|앞서|그때|이거|저거|방금|그러면|그럼");

    private ComparisonQuestionPolicy() {}

    public static boolean isStandaloneComparison(String question, ChatContext context) {
        if (question == null || !COMPARISON_REQUEST_END.matcher(question).find()) {
            return false;
        }
        // 앞 문장이 독립 요청이면 끝의 비교 요청 때문에 전체 질문을 합치지 않는다.
        if (EARLIER_INDEPENDENT_REQUEST.matcher(question).find()
                || EARLIER_SENTENCE_REQUEST.matcher(question).find()
                || EARLIER_QUESTION.matcher(question).find()) {
            return false;
        }
        // 원문의 지시어를 그대로 검색하면 이전 대화의 비교 대상이 사라질 수 있다.
        return context == null || !CONTEXT_REFERENCE.matcher(question).find();
    }

    public static TrailingComparison trailingComparison(String question, ChatContext context) {
        if (question == null || isStandaloneComparison(question, context)) {
            return null;
        }
        List<Integer> boundaries = new ArrayList<>();
        for (Pattern pattern : List.of(EARLIER_INDEPENDENT_REQUEST, EARLIER_SENTENCE_REQUEST, EARLIER_QUESTION)) {
            Matcher matcher = pattern.matcher(question);
            while (matcher.find()) boundaries.add(matcher.end());
        }
        if (boundaries.isEmpty()) return null;
        boundaries = boundaries.stream().distinct().sorted().toList();
        int boundary = boundaries.getLast();
        String comparison = question.substring(boundary).strip();
        if (!isStandaloneComparison(comparison, context)) return null;
        List<String> precedingRequests = new ArrayList<>();
        int start = 0;
        for (int end : boundaries) {
            precedingRequests.add(question.substring(start, end).strip());
            start = end;
        }
        return new TrailingComparison(precedingRequests, comparison);
    }

    public record TrailingComparison(List<String> precedingRequests, String comparison) {}
}
