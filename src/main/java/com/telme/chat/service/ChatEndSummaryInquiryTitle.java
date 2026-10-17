package com.telme.chat.service;

import java.util.ArrayList;
import java.util.List;

final class ChatEndSummaryInquiryTitle {
    private ChatEndSummaryInquiryTitle() {}

    static String create(String purpose, String questions) {
        String text = ChatEndSummaryInquiryLinker.normalizeText(questions);
        return switch (purpose) {
            case "USIM" -> createUsimTitle(text);
            case "ROAMING" -> text.contains("기간") && (text.contains("금액") || text.contains("요금"))
                    ? "해외 로밍 기간·요금" : "해외 로밍 문의";
            case "CANCELLATION" -> text.contains("비용") ? "해지 예상 비용" : "해지 문의";
            case "STORE" -> text.matches(".*(?:위치|어디|근처|지역|가까운).*") ? "방문 매장 위치" : "매장 문의";
            case "PLAN" -> text.contains("변경") ? "요금제 변경 문의" : "요금제 문의";
            case "PORTING" -> "번호이동 문의";
            case "NAME_CHANGE" -> "명의변경 문의";
            case "SUBSCRIBE" -> "가입·개통 문의";
            case "BILLING" -> text.contains("요금제변경") ? "요금제 변경 문의" : "요금·청구 문의";
            case "DEVICE" -> "단말·할부 문의";
            case "SERVICE" -> "부가서비스·기타 문의";
            default -> "추가 문의";
        };
    }

    private static String extractScope(String text) {
        String person = "";
        for (String name : List.of("어머니", "아버지", "배우자", "자녀", "남편", "아내")) {
            if (text.contains(name)) {
                person = name + " ";
                break;
            }
        }
        if (person.isBlank() && (text.contains("본인") || text.contains("제유심") || text.contains("제회선"))) {
            person = "본인 ";
        }
        var line = java.util.regex.Pattern.compile("(?:첫번째|두번째|세번째|[0-9]+번|[ab])회선").matcher(text);
        return person + (line.find() ? line.group() + " " : "");
    }

    private static String createUsimTitle(String text) {
        String product = text.contains("esim") || text.contains("이심") ? "eSIM" : "유심";
        String subject;
        if (text.contains("최초등록") || text.contains("처음등록") || text.contains("활성화")) {
            subject = product + " 최초 등록";
        } else if (text.contains("재발급") || text.contains("분실")) {
            subject = product + " 재발급";
        } else if (text.contains("교체")) {
            subject = product + " 교체";
        } else {
            subject = product;
        }
        List<String> facets = new ArrayList<>();
        if (text.contains("비용") || text.contains("얼마") || text.contains("금액")) {
            facets.add("비용");
        }
        if (text.contains("무료")) {
            facets.add("무료 조건");
        }
        if (text.contains("준비물") || text.contains("신분증")) {
            facets.add("준비물");
        }
        if (text.contains("절차") || text.contains("방법") || text.contains("방문")) {
            facets.add(subject.contains("최초 등록") ? "방법" : "절차");
        }
        return extractScope(text) + subject + (facets.isEmpty() ? " 문의" : " " + String.join("·", facets));
    }
}
