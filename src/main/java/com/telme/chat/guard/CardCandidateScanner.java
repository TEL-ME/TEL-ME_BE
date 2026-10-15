package com.telme.chat.guard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** 숫자 묶음의 모든 시작점을 확인해 앞 후보의 실패가 뒤 번호 검사를 건너뛰지 않게 한다. */
final class CardCandidateScanner {
    private static final int MIN_DIGITS = 13;
    private static final int MAX_DIGITS = 19;

    private CardCandidateScanner() {}

    record Candidate(int start, int end) {}

    private record DigitGroup(int start, int end) {}

    static List<Candidate> findCandidates(String numeric, Predicate<String> validator) {
        List<DigitGroup> groups = findGroups(numeric);
        List<Candidate> candidates = new ArrayList<>();
        for (int start = 0; start < groups.size(); start++) {
            StringBuilder digits = new StringBuilder(MAX_DIGITS);
            for (int end = start; end < groups.size(); end++) {
                DigitGroup group = groups.get(end);
                if (end > start && !connected(numeric, groups.get(end - 1).end(), group.start())) {
                    break;
                }
                if (digits.length() + group.end() - group.start() > MAX_DIGITS) {
                    break;
                }
                digits.append(numeric, group.start(), group.end());
                if (digits.length() >= MIN_DIGITS && validator.test(digits.toString())) {
                    candidates.add(new Candidate(groups.get(start).start(), group.end()));
                }
            }
        }
        return candidates;
    }

    private static List<DigitGroup> findGroups(String numeric) {
        List<DigitGroup> groups = new ArrayList<>();
        int cursor = 0;
        while (cursor < numeric.length()) {
            if (!digit(numeric.charAt(cursor))) {
                cursor++;
                continue;
            }
            int start = cursor;
            while (cursor < numeric.length() && digit(numeric.charAt(cursor))) {
                cursor++;
            }
            groups.add(new DigitGroup(start, cursor));
        }
        return groups;
    }

    private static boolean connected(String numeric, int previousEnd, int nextStart) {
        return nextStart == previousEnd + 1
                && (numeric.charAt(previousEnd) == ' ' || numeric.charAt(previousEnd) == '-');
    }

    private static boolean digit(char value) {
        return value >= '0' && value <= '9';
    }
}
