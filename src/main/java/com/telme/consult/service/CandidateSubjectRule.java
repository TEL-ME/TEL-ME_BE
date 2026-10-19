package com.telme.consult.service;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 후보 근거 검증이 고른 FAQ가 질문과 같은 대상(상품·서비스)을 다루는지 낱말로 확인한다(TELME-184).
 * 회색 구간 판정기는 주제가 같고 대상이 다른 FAQ(eSIM 비용에 유심 재발급 비용, 부가서비스 해지에 회선 해지)를 통과시킨다.
 * 모델을 부르지 않는다. 판정기보다 먼저 확인해, 걸리면 판정기를 부르지 않는다.
 */
public final class CandidateSubjectRule {

    enum Subject {
        ESIM("e\\s*sim|이심"),
        USIM("유심"),
        ADDON("부가\\s*서비스"),
        NAME_CHANGE("명의\\s*(변경|이전)|명의(를|로|도)?\\s*(바꾸|바꿔|바뀌|넘기|넘겨|돌리|옮기|옮겨|가져)"
                + "|이름으로\\s*(바꾸|바꿔|돌리|옮기|가져)|앞으로\\s*(넘기|돌리|옮기|하려)|양도|양수"),
        PORTING("번호\\s*이동|번이|통신사(를|로)?\\s*(옮기|옮겨|바꾸|바꿔|갈아|돌아)|번호\\s*그대로|넘어오|넘어왔"
                + "|옮기고\\s*나서|다른\\s*통신사"),
        ROAMING("로밍"),
        SUBSCRIBE("개통|신규\\s*가입|새로\\s*(만들|만드|가입)|폰을?\\s*(새로\\s*)?만들|처음\\s*만드"
                + "|회선을?\\s*(하나\\s*)?(더\\s*)?만들"),
        MEMBERSHIP("멤버십"),
        MICROPAY("소액\\s*결제"),
        BUNDLE("결합");

        private final Pattern pattern;

        Subject(String regex) {
            this.pattern = Pattern.compile(regex);
        }
    }

    // FAQ 제목에 있으면 질문 쪽에도 있어야 하는 대상. 다른 업무의 FAQ로 잘못 가는 일이 잦은 것만 둔다
    private static final Set<Subject> TITLE_EXCLUSIVE = EnumSet.of(Subject.ESIM, Subject.ADDON, Subject.NAME_CHANGE,
            Subject.PORTING, Subject.ROAMING, Subject.MEMBERSHIP, Subject.MICROPAY);
    private static final Pattern SENTENCE_END = Pattern.compile("[?.!？]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final int PART_KEY_LENGTH = 12;

    private CandidateSubjectRule() {
    }

    /**
     * question: 답할 하위 질문, customerMessage: 나누기 전 고객 원문(없으면 null).
     * 하위 질문에 대상 낱말이 있으면 모두 FAQ 문답에 있어야 한다. 없으면 하위 질문이 있는 문장과 바로 앞 문장의 대상 중
     * 하나라도 FAQ 문답에 있어야 한다. FAQ 제목의 대상(TITLE_EXCLUSIVE)은 질문 쪽에도 있어야 한다.
     */
    public static boolean passes(String question, String customerMessage, String faqQuestion, String faqAnswer) {
        Set<Subject> faq = subjects(faqQuestion + " " + faqAnswer);
        Set<Subject> asked = subjects(question);
        Set<Subject> nearby = subjects(localContext(question, customerMessage));
        if (!asked.isEmpty()) {
            if (!faq.containsAll(asked)) {
                return false;
            }
        } else if (!nearby.isEmpty() && nearby.stream().noneMatch(faq::contains)) {
            return false;
        }
        Set<Subject> context = EnumSet.noneOf(Subject.class);
        context.addAll(asked);
        context.addAll(nearby);
        Set<Subject> title = subjects(faqQuestion);
        title.retainAll(TITLE_EXCLUSIVE);
        return context.containsAll(title);
    }

    static Set<Subject> subjects(String text) {
        Set<Subject> found = EnumSet.noneOf(Subject.class);
        if (text == null) {
            return found;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (Subject subject : Subject.values()) {
            if (subject.pattern.matcher(lower).find()) {
                found.add(subject);
            }
        }
        return found;
    }

    // 하위 질문이 있는 문장과 바로 앞 문장. 복합 질문은 다른 질문의 대상이 섞이므로 원문 전체를 보지 않는다
    static String localContext(String question, String customerMessage) {
        if (customerMessage == null || customerMessage.isBlank()) {
            return question;
        }
        String plain = SPACES.matcher(customerMessage).replaceAll("");
        String part = SPACES.matcher(question).replaceAll("");
        String key = part.substring(0, Math.min(PART_KEY_LENGTH, part.length()));
        int at = key.isEmpty() ? -1 : plain.indexOf(key);
        if (at < 0) {
            return customerMessage;
        }
        int previousEnd = 0;
        int lastEnd = 0;
        Matcher ends = SENTENCE_END.matcher(plain.substring(0, at));
        while (ends.find()) {
            previousEnd = lastEnd;
            lastEnd = ends.end();
        }
        int start = previousEnd;
        int afterKey = at + key.length();
        Matcher following = SENTENCE_END.matcher(plain.substring(afterKey));
        int end = following.find() ? afterKey + following.end() : plain.length();
        return plain.substring(start, end);
    }
}
