package com.telme.rag.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** 업무와 조건이 없는 단일 공통 안내에서 동일한 신청 방법을 고르는 경우만 허용한다. */
final class GroundedApplicationChoice {
    private static final Pattern REQUEST_END = Pattern.compile(
            "(?<action>신청|접수|가입)(?:하시면됩니다|하세요|하십시오)[.!?]?$");
    private static final Pattern CHOICE_END = Pattern.compile("(?:중|가운데)편하신(?:방법|창구)(?:으로|로)$");
    private static final Pattern CHANNEL = Pattern.compile(
            "(?<name>고객센터|콜센터|홈페이지|사이트|앱|매장|대리점|지점)(?:에서|으로|에|로)?"
                    + "(?<method>전화|방문|가시)?(?<connection>하시거나|하시어|하여|해서|거나)?");
    private static final Pattern SEPARATOR = Pattern.compile("(?:,|또는|혹은|이나|나)+");

    private GroundedApplicationChoice() {}

    static boolean isSupported(String sentence, List<String> evidence) {
        if (evidence.size() != 1) {
            return false;
        }
        var answer = parse(sentence, true);
        if (answer == null || answer.channels().size() < 2) {
            return false;
        }
        var source = parse(evidence.getFirst(), false);
        return source != null && answer.action().equals(source.action()) && answer.channels().equals(source.channels());
    }

    static boolean isNeutralChoice(String sentence) {
        var choice = parse(sentence, true);
        return choice != null && choice.channels().size() >= 2;
    }

    private static Choice parse(String sentence, boolean requireChoice) {
        if (sentence == null) {
            return null;
        }
        String compact = sentence.replaceAll("(?U)\\s+", "");
        var request = REQUEST_END.matcher(compact);
        if (!request.find()) {
            return null;
        }
        String prefix = compact.substring(0, request.start());
        var choice = CHOICE_END.matcher(prefix);
        if (choice.find()) {
            prefix = prefix.substring(0, choice.start());
        } else if (requireChoice) {
            return null;
        }
        Set<Channel> channels = parseChannels(prefix);
        return channels.isEmpty() ? null : new Choice(request.group("action"), channels);
    }

    private static Set<Channel> parseChannels(String prefix) {
        Set<Channel> channels = new LinkedHashSet<>();
        int offset = 0;
        var channel = CHANNEL.matcher(prefix);
        var separator = SEPARATOR.matcher(prefix);
        while (offset < prefix.length()) {
            if (!channel.region(offset, prefix.length()).lookingAt()) {
                return Set.of();
            }
            String method = channel.group("method");
            String normalizedMethod = method == null ? "" : method.equals("가시") ? "방문" : method;
            channels.add(new Channel(channel.group("name"), normalizedMethod));
            String connection = channel.group("connection");
            offset = channel.end();
            if (offset == prefix.length()) {
                return Set.copyOf(channels);
            }
            // 선행 절차 뒤의 쉼표를 선택지 구분자로 해석하면 필요한 단계를 생략하게 된다.
            if (connection != null && !connection.endsWith("거나")) {
                return Set.of();
            }
            if (separator.region(offset, prefix.length()).lookingAt()) {
                offset = separator.end();
                if (offset == prefix.length()) {
                    return Set.of();
                }
            } else if (connection == null || !connection.endsWith("거나")) {
                return Set.of();
            }
        }
        return Set.of();
    }

    private record Choice(String action, Set<Channel> channels) {}

    private record Channel(String name, String method) {}
}
