package com.telme.intent.service;

import com.telme.consult.entity.ConsultRequest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 하위 질문을 안전하게 구분할 수 없을 때 부분 저장과 처리를 막는다.
public final class UnsupportedCompoundQuestionException extends IllegalStateException {
    // 안전한 분해를 확인하지 못했을 때 하나씩 다시 보낼 버튼을 만드는 데 쓴다.
    private final transient List<Part> parts;

    public UnsupportedCompoundQuestionException() {
        this(List.of());
    }

    public UnsupportedCompoundQuestionException(List<Part> parts) {
        super("하위 질문을 안전하게 구분해 처리할 수 없습니다.");
        this.parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public UnsupportedCompoundQuestionException(String reason) {
        super(reason);
        this.parts = List.of();
    }

    // transient라 직렬화를 거치면 null이 된다. 호출부가 빈 목록을 기대하므로 null 대신 빈 목록을 준다
    public List<Part> parts() {
        return parts == null ? List.of() : parts;
    }

    public record Part(ConsultRequest.Intent intent, String queryText, Map<String, String> conditions) {
        public Part {
            conditions = conditions == null ? Map.of() : conditions.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }
}
