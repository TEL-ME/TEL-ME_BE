package com.telme.chat.translation;

import java.util.List;
import java.util.stream.Collectors;

public final class TranslationGlossary {

    // english의 첫 값을 한국어 → 영어 번역의 표기로 쓴다
    record Term(String korean, List<String> english) {
    }
    
    static final List<Term> TERMS = List.of(
            new Term("요금제", List.of("plan", "rate plan", "mobile plan")),
            new Term("유심", List.of("USIM", "SIM", "SIM card")),
            new Term("eSIM", List.of("eSIM")),
            new Term("데이터", List.of("data")),
            new Term("로밍", List.of("roaming")),
            new Term("일시 정지", List.of("suspension", "pause", "suspend")),
            new Term("번호이동", List.of("number porting", "port my number", "switch carriers")),
            new Term("명의변경", List.of("ownership transfer", "name transfer")),
            new Term("해지", List.of("cancellation", "cancel", "terminate")),
            new Term("개통", List.of("activation", "activate")),
            new Term("기기변경", List.of("device change", "phone upgrade")),
            new Term("부가서비스", List.of("add-on service", "add-on")),
            new Term("소액결제", List.of("carrier billing", "mobile micropayment")),
            new Term("청구서", List.of("bill", "invoice")),
            new Term("납부", List.of("payment", "pay")),
            new Term("미납", List.of("unpaid", "overdue")),
            new Term("자동이체", List.of("auto-pay", "automatic payment")),
            new Term("위약금", List.of("early termination fee", "cancellation fee")),
            new Term("약정", List.of("contract", "commitment")),
            new Term("결합", List.of("bundle")),
            new Term("할부", List.of("installment")),
            new Term("분실", List.of("lost", "loss")),
            new Term("환불", List.of("refund")),
            new Term("멤버십", List.of("membership"))
    );
    
    private TranslationGlossary() {
    }
    
    // 영어 → 한국어 번역 프롬프트에 넣는다. 예: "plan, rate plan, mobile plan → 요금제"
    static String toKoreanLines() {
        return TERMS.stream()
                .map(term -> String.join(", ", term.english()) + " → " + term.korean())
                .collect(Collectors.joining("\n"));
    }
}
