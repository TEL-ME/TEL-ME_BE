package com.telme.intent.service;

import com.telme.consult.entity.ConsultRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** FAQ와 매장이 섞인 질문을 상담 처리기가 부분 처리하지 않도록 알리는 예외다. */
public final class UnsupportedCompoundQuestionException extends IllegalStateException {
    // 라우터가 나눈 하위 질문. 하나씩 보낼 버튼을 만들 때 쓴다. FAQ와 매장이 섞인 경우에만 담고 나머지는 비어 있다
    private final transient List<Part> parts;

    public UnsupportedCompoundQuestionException() {
        this(List.of());
    }

    public UnsupportedCompoundQuestionException(List<Part> parts) {
        super("현재 Chat 상담 연결은 FAQ와 매장 복합 질문을 지원하지 않습니다.");
        this.parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public UnsupportedCompoundQuestionException(String reason) {
        super(reason);
        this.parts = List.of();
    }

    public List<Part> parts() {
        return parts;
    }

    public record Part(ConsultRequest.Intent intent, String queryText, Map<String, String> conditions) {
        public Part {
            conditions = conditions == null ? Map.of() : conditions.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }
}
