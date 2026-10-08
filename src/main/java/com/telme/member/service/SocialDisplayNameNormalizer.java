package com.telme.member.service;

// 공급자가 준 표시 이름을 users.name에 넣을 수 있게 정리한다 — 카카오·구글이 같은 규칙을 쓰도록 한곳에 둔다
final class SocialDisplayNameNormalizer {

    static final int MAX_MEMBER_NAME_LENGTH = 50;

    private SocialDisplayNameNormalizer() {
    }

    // 앞뒤 공백(전각 공백 포함)을 지우고, 공백뿐이면 null, 길면 서로게이트 쌍을 쪼개지 않게 자른다
    static String normalize(String name) {
        if (name == null) {
            return null;
        }
        String normalized = name.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() <= MAX_MEMBER_NAME_LENGTH) {
            return normalized;
        }
        int endIndex = MAX_MEMBER_NAME_LENGTH;
        if (Character.isHighSurrogate(normalized.charAt(endIndex - 1))) {
            endIndex--;
        }
        return normalized.substring(0, endIndex);
    }
}
