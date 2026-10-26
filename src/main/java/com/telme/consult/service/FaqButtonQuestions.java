package com.telme.consult.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;

// 후속 질문 버튼으로 내놓는 FAQ 대표 질문. 버튼을 누르면 그 FAQ 답이 나와야 한다
final class FaqButtonQuestions {

    private static final String PATH = "suggested-question/policy-links.json";
    private static final Set<String> QUESTIONS = load();

    private FaqButtonQuestions() {
    }

    static boolean contains(String question) {
        return question != null && QUESTIONS.contains(normalize(question));
    }

    private static Set<String> load() {
        Set<String> questions = new HashSet<>();
        try (InputStream input = new ClassPathResource(PATH).getInputStream()) {
            JsonNode representatives = new ObjectMapper().readTree(input).path("representativeQuestions");
            representatives.forEach(entry -> questions.add(normalize(entry.path("question").asText())));
        } catch (IOException exception) {
            throw new IllegalStateException(PATH + "를 읽을 수 없습니다.", exception);
        }
        questions.remove("");
        return Set.copyOf(questions);
    }

    private static String normalize(String question) {
        return question.replaceAll("\\s+", "");
    }
}
