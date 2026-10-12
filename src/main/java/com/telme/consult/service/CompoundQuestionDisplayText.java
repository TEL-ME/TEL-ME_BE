package com.telme.consult.service;

import com.telme.intent.service.RoutingQuestionNormalizer;

/**
 * 복합 질문의 검색어는 라우팅 정규화 값을 유지하되, 화면에는 고객이 입력한 띄어쓰기 구간을 보여 준다.
 */
final class CompoundQuestionDisplayText {
    private CompoundQuestionDisplayText() {}

    static String fromOriginal(String originalQuestion, String queryText) {
        if (originalQuestion == null || originalQuestion.isBlank()
                || queryText == null || queryText.isBlank()) {
            return queryText;
        }
        String target = RoutingQuestionNormalizer.normalize(queryText);
        if (target.isBlank()) {
            return queryText;
        }

        StringBuilder normalized = new StringBuilder();
        java.util.List<Integer> originalIndexes = new java.util.ArrayList<>();
        for (int index = 0; index < originalQuestion.length();) {
            int character = originalQuestion.codePointAt(index);
            String value = new String(Character.toChars(character));
            String normalizedCharacter = RoutingQuestionNormalizer.normalize(value);
            if (!normalizedCharacter.isEmpty()) {
                normalized.append(normalizedCharacter);
                originalIndexes.add(index);
            }
            index += Character.charCount(character);
        }

        int start = normalized.indexOf(target);
        if (start < 0 || start + target.length() > originalIndexes.size()) {
            return queryText;
        }
        int originalStart = originalIndexes.get(start);
        int lastCharacterIndex = originalIndexes.get(start + target.length() - 1);
        int originalEnd = lastCharacterIndex
                + Character.charCount(originalQuestion.codePointAt(lastCharacterIndex));
        return originalQuestion.substring(originalStart, originalEnd).strip();
    }
}
