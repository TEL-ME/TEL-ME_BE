package com.telme.chat.service;

import com.telme.faq.entity.FaqCategory;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 문장 전체가 일반 주제일 때만 지원 별칭을 연결하며 서로 다른 세부 업무는 합치지 않는다. */
final class ChatEndSummaryGeneralTopic {
    private static final String ENDING = "(?:은|는|을|를|이|가|에대해|에대해서)?(?:좀)?"
            + "(?:요|이요|알려(?:줘|줘요|주세요|줄래|줄래요|주실래요)"
            + "|(?:안내|설명)(?:해줘|해주세요|부탁해요|부탁드려요|부탁드립니다)"
            + "|문의(?:요|드려요|드립니다|할게요|합니다)|궁금(?:해|해요|합니다)"
            + "|알고싶(?:어|어요|습니다))?[.!?]*$";
    private static final Map<FaqCategory, Pattern> TOPICS = Arrays.stream(FaqCategory.values())
            .collect(Collectors.toUnmodifiableMap(Function.identity(), category -> compile(switch (category) {
                case BILLING -> "요금|청구|청구서|납부";
                case PLAN -> "요금제";
                case USIM -> "유심|esim|이심";
                case SUBSCRIBE -> "가입|개통|신규가입|신규개통";
                case PORTING -> "번호이동";
                case NAME_CHANGE -> "명의변경";
                case TERMINATE -> "해지";
                case DEVICE -> "단말|단말기|기기|휴대폰|핸드폰|할부";
                case ROAMING -> "로밍";
                case SERVICE -> "부가서비스|멤버십";
            })));
    private static final Pattern STORE = compile("매장");
    private static final Pattern GENERAL_FEE = compile("요금");
    private static final Pattern TOPIC_CHANGE = Pattern.compile("말고|대신|아니라");

    private ChatEndSummaryGeneralTopic() {}

    static boolean isSameGeneralTopic(String first, String second) {
        String topic = extractGeneralTopic(first);
        String next = extractGeneralTopic(second);
        // 신규 여부까지 특정한 문의를 더 넓은 주제로 되돌려 조건을 지우지 않는다.
        return !topic.isEmpty() && normalizeTopic(topic).equals(normalizeTopic(next))
                && (!topic.startsWith("신규") || next.startsWith("신규"));
    }

    private static String extractGeneralTopic(String question) {
        String text = ChatEndSummaryInquiryLinker.normalizeText(ConsultationQuestion.extract(question));
        if (STORE.matcher(text).matches()) {
            return "매장";
        }
        for (var pattern : TOPICS.values()) {
            var match = pattern.matcher(text);
            if (match.matches()) {
                return match.group(1);
            }
        }
        return "";
    }

    private static String normalizeTopic(String topic) {
        return switch (topic) {
            case "신규가입" -> "가입";
            case "신규개통" -> "개통";
            case "휴대폰", "핸드폰", "단말", "단말기" -> "기기";
            case "청구서" -> "청구";
            case "esim" -> "이심";
            default -> topic;
        };
    }

    static boolean accepts(String question, String category, String followup) {
        if (TOPIC_CHANGE.matcher(ChatEndSummaryInquiryLinker.normalizeText(followup)).find()) {
            return false;
        }
        String text = ChatEndSummaryInquiryLinker.normalizeText(ConsultationQuestion.extract(question));
        if (GENERAL_FEE.matcher(text).matches()) {
            return Set.of("PLAN", "BILLING").contains(category);
        }
        if (STORE.matcher(text).matches()) {
            return category.equals("STORE") || category.equals("SERVICE")
                    && ChatEndSummaryInquiryLinker.normalizeText(followup).contains("매장");
        }
        for (var entry : TOPICS.entrySet()) {
            String expected = entry.getKey() == FaqCategory.TERMINATE ? "CANCELLATION" : entry.getKey().name();
            var match = entry.getValue().matcher(text);
            if (category.equals(expected) && match.matches()) {
                return hasSameSubject(match.group(1), followup);
            }
        }
        return false;
    }

    private static boolean hasSameSubject(String topic, String followup) {
        String after = ChatEndSummaryInquiryLinker.normalizeText(ConsultationQuestion.extract(followup));
        // 여러 업무를 함께 담은 분류는 그 안의 다른 업무에 대한 답변으로 해결하지 않는다.
        return switch (topic) {
            case "청구", "청구서" -> after.contains("청구");
            case "납부" -> after.contains("납부") || after.contains("납입");
            case "신규가입", "신규개통" -> after.contains(topic);
            case "할부", "멤버십", "부가서비스" -> after.contains(topic);
            default -> true;
        };
    }

    private static Pattern compile(String topics) {
        return Pattern.compile("^(" + topics + ")" + ENDING);
    }
}
