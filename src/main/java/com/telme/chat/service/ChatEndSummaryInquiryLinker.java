package com.telme.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class ChatEndSummaryInquiryLinker {
    private static final Pattern FOLLOWUP = Pattern.compile(
            "그(?:럼|건|거|유심|재발급|교체|회선)|해당|아까|앞서|추가로|준비물|신분증|무료|비용|절차|방문");
    private static final Pattern RESET = Pattern.compile(
            "다른회선|다른번호|다른사람|별도(?:의)?(?:회선|신청|문의)|새(?:로운)?문의|새신청건|별개|다른질문|다른유심|다른기기");
    private static final Pattern BACK_REFERENCE = Pattern.compile(
            "그건|그거|그유심|그재발급|그교체|그회선|아까.*(?:유심|회선)|앞서.*(?:유심|회선)");

    private ChatEndSummaryInquiryLinker() {}

    static Map<Long, Long> roots(ChatEndSummaryData.Input input) {
        Map<Long, Long> roots = new LinkedHashMap<>();
        Map<String, Episode> episodes = new LinkedHashMap<>();
        Episode previous = null;
        for (var message : input.messages()) {
            if (!message.role().equals("USER")) {
                continue;
            }
            String text = compact(message.content());
            String queries = input.requests().stream().filter(r -> r.originMessageId() == message.id())
                    .map(ChatEndSummaryData.Request::query).reduce("", (a, b) -> a + " " + b);
            String domain = purpose(message.content());
            if (domain.equals("UNKNOWN")) {
                domain = purpose(queries);
            }
            String person = person(text);
            String product = product(text);
            String line = line(text);
            String operation = operation(text);
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
                            && compatible(episode, person, product, line, operation, true)) {
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
                    && compatible(previous, person, product, line, operation, backReference)) {
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
            if (condition.answeredMessageId() != null && roots.containsKey(condition.originMessageId())) {
                roots.put(condition.answeredMessageId(), roots.get(condition.originMessageId()));
            }
        }
        return roots;
    }

    static String purpose(String text) {
        String value = compact(text);
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

    static String compact(String text) {
        return text.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT)
                .replace("엄마", "어머니").replace("아빠", "아버지");
    }

    private static boolean compatible(Episode episode, String person, String product, String line,
            String operation, boolean reference) {
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

    private static String person(String text) {
        Set<String> people = new LinkedHashSet<>();
        for (String name : List.of("어머니", "아버지", "배우자", "자녀", "남편", "아내")) {
            if (text.contains(name)) {
                people.add(name);
            }
        }
        if (text.contains("본인") || text.contains("저는") || text.contains("제유심") || text.contains("제회선")) {
            people.add("SELF");
        }
        return people.size() > 1 ? "MULTIPLE" : people.stream().findFirst().orElse("");
    }

    private static String product(String text) {
        if (text.contains("esim") || text.contains("이심")) {
            return "ESIM";
        }
        return text.contains("실물") || text.contains("물리유심") ? "PHYSICAL" : "";
    }

    private static String operation(String text) {
        if (text.contains("최초등록") || text.contains("처음등록") || text.contains("활성화")) {
            return "ACTIVATE";
        }
        if (text.contains("재발급") || text.contains("분실")) {
            return "REISSUE";
        }
        return text.contains("교체") ? "REPLACE" : "";
    }

    private static String line(String text) {
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
