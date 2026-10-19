package com.telme.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class ChatEndSummaryInquiryLinker {
    private static final Pattern FOLLOWUP = Pattern.compile(
            "그(?:럼|건|거|유심|재발급|교체|회선)|해당|아까|앞서|추가로|준비물|신분증|무료|비용|절차|방문");
    private static final Pattern RESET = Pattern.compile(
            "다른회선|다른번호|다른사람|다른(?:가족|(?:(?:친|사촌|큰|작은)?형)(?:님)?|(?:직장)?동료)"
                    + "|별도(?:의)?(?:회선|신청|문의)"
                    + "|새(?:로운)?문의|새신청건|별개|다른질문|다른유심|다른기기");
    private static final Pattern BACK_REFERENCE = Pattern.compile(
            "그건|그거|그유심|그재발급|그교체|그회선|아까.*(?:유심|회선)|앞서.*(?:유심|회선)");
    private static final Pattern VISITOR_REPLY = Pattern.compile(
            "^(?:(?:네|예)[,.]?)?(?:제가|저는|본인이|본인은|대리인이|대리인은|어머니가|어머니는"
                    + "|아버지가|아버지는|배우자가|배우자는)"
                    + "(?:(?:직접|대신)|(?:매장|대리점)(?:에|으로)?"
                    + "|(?:본인)?신분증을?(?:지참(?:하고|해서|해)|가지고))*"
                    + "(?:방문(?:합니다|할게요|할거예요|해요|하겠습니다)|갑니다|갈게요|가요|가겠습니다)[.!]*$");

    private ChatEndSummaryInquiryLinker() {}

    static Map<Long, Long> resolveRoots(ChatEndSummaryData.Input input) {
        Map<Long, Long> roots = new LinkedHashMap<>();
        Map<Long, ChatEndSummaryData.Message> questions = new LinkedHashMap<>();
        Map<String, Episode> episodes = new LinkedHashMap<>();
        Episode previous = null;
        for (var message : input.messages()) {
            if (!message.role().equals("USER")) {
                continue;
            }
            questions.put(message.id(), message);
            String text = normalizeText(message.content());
            String queries = input.requests().stream().filter(r -> r.originMessageId() == message.id())
                    .map(ChatEndSummaryData.Request::query).reduce("", (a, b) -> a + " " + b);
            String domain = ChatEndSummaryInquiryClassifier.classifyMessage(input, message.id());
            if (domain.equals("UNKNOWN")) {
                domain = identifyPurpose(message.content());
            }
            if (domain.equals("UNKNOWN")) {
                domain = identifyPurpose(queries);
            }
            String person = ChatEndSummaryPersonScope.createGroupingIdentity(message.content());
            String product = identifyProduct(text);
            String line = extractLine(text);
            String operation = identifyOperation(text);
            boolean reset = RESET.matcher(text).find();
            boolean correction = text.matches("(?s).*(?:정정|아니라|잘못말|확인해보니).*");
            boolean followup = FOLLOWUP.matcher(text).find();
            boolean backReference = BACK_REFERENCE.matcher(text).find();
            if (domain.equals("UNKNOWN") && previous != null && previous.domain.equals("USIM")
                    && followup && !reset) {
                domain = "USIM";
            }
            if (text.contains("로밍") && (text.contains("유심") || text.contains("esim"))) {
                for (String kind : List.of("USIM", "ROAMING")) {
                    episodes.put(message.id() + ":" + kind, new Episode(message.id(), kind,
                            person.isBlank() ? "SELF" : person, product,
                            line.isBlank() ? "DEFAULT" : line, operation));
                }
                roots.put(message.id(), message.id());
                previous = null;
                continue;
            }
            Episode target = null;
            boolean ambiguousReference = false;
            if (!reset && domain.equals("USIM") && backReference) {
                List<Episode> candidates = new ArrayList<>();
                for (var episode : episodes.values()) {
                    if (episode.domain.equals("USIM")
                            && isCompatible(episode, person, product, line, operation, true)) {
                        candidates.add(episode);
                    }
                }
                // 최근 문답에 붙이기 전에 대상 후보 전체를 확인해 사람이나 회선의 혼동을 막는다.
                if (candidates.size() == 1) {
                    target = candidates.getFirst();
                } else if (candidates.size() > 1) {
                    ambiguousReference = true;
                }
            }
            if (target == null && !ambiguousReference && !reset && previous != null
                    && domain.equals(previous.domain)
                    && isCompatible(previous, person, product, line, operation, backReference)) {
                if ((domain.equals("USIM") && followup) || correction) {
                    target = previous;
                }
            }
            if (target == null) {
                String resolvedPerson = person.isBlank() ? "SELF" : person;
                String resolvedLine = line.isBlank() ? "DEFAULT" : line;
                target = new Episode(message.id(), domain, resolvedPerson, product, resolvedLine, operation);
                episodes.put(message.id() + ":" + domain, target);
            } else {
                if (!product.isBlank()) {
                    target.product = product;
                }
                if (target.operation.isBlank() && !operation.isBlank()) {
                    target.operation = operation;
                }
            }
            roots.put(message.id(), target.root);
            previous = target;
        }
        for (var condition : input.conditions()) {
            var origin = questions.get(condition.originMessageId());
            var reply = questions.get(condition.answeredMessageId());
            // 앞단이 다른 사람의 질문을 조건 응답으로 저장해도 이미 나눈 대상 묶음을 덮어쓰지 않는다.
            if (origin != null && reply != null && roots.containsKey(condition.originMessageId())
                    && hasSameConditionScope(origin.content(), reply.content())) {
                roots.put(condition.answeredMessageId(), roots.get(condition.originMessageId()));
            }
        }
        return roots;
    }

    static String identifyPurpose(String text) {
        String value = normalizeText(text);
        if (value.contains("로밍")) {
            return "ROAMING";
        }
        if (value.contains("해지")) {
            return "CANCELLATION";
        }
        if (value.matches("(?s).*(?:매장.*(?:위치|어디)|근처.*매장).*") && !value.contains("재발급")) {
            return "STORE";
        }
        if (value.contains("유심") || value.contains("esim") || value.contains("이심")) {
            return "USIM";
        }
        return value.contains("요금제") ? "PLAN" : "UNKNOWN";
    }

    static String normalizeText(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT)
                .replace("엄마", "어머니").replace("아빠", "아버지");
    }

    static boolean hasSameConditionScope(String original, String followup) {
        // 방문자만 답한 짧은 문장은 명의자가 바뀐 새 문의와 구별해 기존 정상 응답을 유지한다.
        return VISITOR_REPLY.matcher(normalizeText(followup)).matches()
                || hasSameClarificationScope(original, followup);
    }

    static boolean hasSameClarificationScope(String original, String followup) {
        String before = normalizeText(original);
        String after = normalizeText(followup);
        if (RESET.matcher(after).find() || ChatEndSummaryPersonScope.hasUnspecifiedPerson(followup)) {
            return false;
        }
        String person = extractPerson(original);
        String line = extractLine(before);
        var source = new Episode(0, "UNKNOWN", person.isBlank() ? "SELF" : person, identifyProduct(before),
                line.isBlank() ? "DEFAULT" : line, identifyOperation(before));
        return !source.person.equals("MULTIPLE") && isCompatible(source, extractPerson(followup),
                identifyProduct(after), extractLine(after), identifyOperation(after), false);
    }

    private static boolean isCompatible(Episode episode, String person, String product, String line,
            String operation, boolean reference) {
        // 같은 명시적 대상 집합끼리만 묶고 일부 대상이나 모호한 지시어로 축소하지 않는다.
        if ((episode.person.startsWith("MULTIPLE:") || person.startsWith("MULTIPLE:"))
                && !episode.person.equals(person)) {
            return false;
        }
        if (!person.isBlank() && !episode.person.equals(person)) {
            return false;
        }
        if (!line.isBlank() && !episode.line.equals(line)) {
            return false;
        }
        if (!product.isBlank() && !episode.product.isBlank() && !product.equals(episode.product)) {
            return false;
        }
        if (product.equals("ESIM") && episode.product.isBlank() && !episode.operation.isBlank()) {
            return false;
        }
        return operation.isBlank() || episode.operation.isBlank() || operation.equals(episode.operation) || reference;
    }

    static String extractPerson(String text) {
        return ChatEndSummaryPersonScope.extract(text);
    }

    private static String identifyProduct(String text) {
        if (text.contains("esim") || text.contains("이심")) {
            return "ESIM";
        }
        return text.contains("실물") || text.contains("물리유심") ? "PHYSICAL" : "";
    }

    private static String identifyOperation(String text) {
        if (text.contains("최초등록") || text.contains("처음등록") || text.contains("활성화")) {
            return "ACTIVATE";
        }
        if (text.contains("재발급") || text.contains("분실")) {
            return "REISSUE";
        }
        return text.contains("교체") ? "REPLACE" : "";
    }

    private static String extractLine(String text) {
        var matcher = Pattern.compile(
                "(?:첫번째|두번째|세번째|[0-9]+번|[ab])회선|회선(?:[ab]|[0-9]+번?)").matcher(text);
        if (!matcher.find()) {
            return "";
        }
        String name = matcher.group().replace("회선", "").replaceFirst("번$", "");
        // 같은 회선의 앞뒤 표현과 순번 표현만 통일하고 회선 간 추측 연결은 하지 않는다.
        return switch (name) {
            case "첫번째" -> "1회선";
            case "두번째" -> "2회선";
            case "세번째" -> "3회선";
            default -> name + "회선";
        };
    }

    private static final class Episode {
        private final long root;
        private final String domain;
        private final String person;
        private String product;
        private final String line;
        private String operation;

        private Episode(long root, String domain, String person, String product, String line, String operation) {
            this.root = root;
            this.domain = domain;
            this.person = person;
            this.product = product;
            this.line = line;
            this.operation = operation;
        }
    }
}
