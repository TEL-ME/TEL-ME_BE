package com.telme.chat.service;

import java.util.List;

/**
 * 종료 요약에서 문의로 보지 않는 감사·작별 인사. 실시간 경로는 "네 감사합니다 수고하세요~" 같은 말을 후속 질문으로
 * 접수해 근거 없음이나 주제 확인으로 답하기도 하는데, 그런 결과는 하위 질문 기록만으로 인사와 구분할 수 없다.
 * 고객 발화의 낱말만 본다. 상담사 답변 글자는 해석하지 않는다.
 */
final class ChatEndSummaryClosingRemarks {
    private ChatEndSummaryClosingRemarks() {}

    // 감사·작별 인사로만 이뤄진 발화의 낱말. 하나라도 다른 낱말이 있거나 물음이 있으면 인사가 아니다
    private static final List<String> WORDS = List.of("네", "넵", "넹", "예", "아", "아하", "오", "ㅇㅋ", "ㅇㅇ",
            "오케이", "알겠", "감사", "고마", "고맙", "ㄱㅅ", "ㄳ", "땡큐", "수고", "좋은", "하루", "되세요", "보내세요",
            "안녕히", "계세요", "가세요", "덕분", "해결", "도움", "정말", "진짜", "많이", "잘", "또", "다음에", "올게요",
            "바이", "됐", "ㅋ", "ㅎ");
    // 이 중 하나는 있어야 인사다("네 알겠습니다"는 조건 답일 수 있어 인사로 보지 않는다)
    private static final List<String> CORE = List.of("감사", "고마", "고맙", "ㄱㅅ", "ㄳ", "땡큐", "수고", "안녕히",
            "좋은", "바이");

    static boolean isClosingRemark(String content) {
        if (content == null || content.contains("?")) {
            return false;
        }
        String text = content.replaceAll("[^\\p{L}\\p{N}\\s]", " ").strip();
        if (text.isEmpty()) {
            return false;
        }
        boolean core = false;
        for (String word : text.split("\\s+")) {
            if (WORDS.stream().noneMatch(word::startsWith)) {
                return false;
            }
            core |= CORE.stream().anyMatch(word::startsWith);
        }
        return core;
    }
}
