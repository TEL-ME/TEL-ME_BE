package com.telme.chat.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContextMessage;
import com.telme.chat.service.ChatTokenEstimator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatSummaryConverterTest {
    private final ChatSummaryConverter converter = new ChatSummaryConverter(new ObjectMapper());
    private final ChatTokenEstimator estimator = new ChatTokenEstimator();
    private final ChatContextMessage customer = new ChatContextMessage(1L, 1, ChatMessage.Role.USER,
            ChatMessage.MessageType.QUESTION, "부모님 명의이고 아직 변경하지 않았어요.", null);
    private final ChatContextMessage answer = new ChatContextMessage(2L, 2, ChatMessage.Role.ASSISTANT,
            ChatMessage.MessageType.ANSWER, "유심 재발급은 무료라고 안내했습니다.", null);

    @Test
    void preservesExactSpeakerNegationAndStatusInsteadOfModelParaphrases() {
        String stored = store("{\"messageIds\":[1]}", null, List.of(customer));
        assertThat(converter.sources(stored)).containsExactly(customer);
        assertThat(converter.render(stored)).contains("고객 원문", "부모님 명의이고 아직 변경하지 않았어요.");
    }

    @Test
    void selectingAnswerAlsoKeepsItsQuestionWithoutTreatingItAsPolicyEvidence() {
        String stored = store("{\"messageIds\":[2,1]}", null, List.of(customer, answer));
        assertThat(converter.sources(stored)).containsExactly(customer, answer);
        assertThat(converter.render(stored)).contains("상담사 이력(정책 근거 아님)");
    }

    @Test
    void mergesFromOriginalMessagesAcrossRepeatedSummaries() {
        String first = store("{\"messageIds\":[1]}", null, List.of(customer));
        var corrected = new ChatContextMessage(3L, 3, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "명의자는 부모님이 아니라 저예요. 아직 변경하지 않았어요.", null);
        String next = store("{\"messageIds\":[3]}", first, List.of(corrected));
        assertThat(converter.sources(next)).containsExactly(corrected);
        assertThat(converter.render(next)).doesNotContain(customer.content());
    }

    @Test
    void keepsLegacySummaryExplicitlyUnverifiedDuringFormatTransition() {
        String stored = store("{\"messageIds\":[1]}", "예전 상담 요약", List.of(customer));
        assertThat(converter.render(stored)).contains("미검증 이전 요약", "예전 상담 요약");
        assertThat(converter.sources(stored)).containsExactly(customer);
        assertThat(converter.render("기존 일반 문장")).isEqualTo("기존 일반 문장");
    }

    @Test
    void rejectsUnknownIdsDuplicateIdsNonNumericIdsAndUnrequestedModelContent() {
        for (String output : List.of("{\"messageIds\":[9]}", "{\"messageIds\":[1,1]}",
                "{\"messageIds\":[\"1\"]}", "{\"messageIds\":[1],\"summary\":\"변경 완료\"}",
                "{\"messageIds\":[]}")) {
            assertThatThrownBy(() -> store(output, null, List.of(customer)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void omitsAnUnfittableOriginalWithoutCuttingNegationOrKeepingOlderConditions() {
        String previous = store("{\"messageIds\":[1]}", "이전 명의자는 부모님", List.of(customer));
        var correction = new ChatContextMessage(3L, 3, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "명의자는 부모님이 아니라 저예요." + "정정".repeat(400), null);
        String stored = converter.validateAndStore("{\"messageIds\":[1,3]}", previous,
                List.of(correction), 80, estimator);
        assertThat(converter.sources(stored)).isEmpty();
        assertThat(converter.render(stored)).isNull();
    }

    @Test
    void selectingAnEllipticalCorrectionRetainsTheOriginalConsultationSubject() {
        var question = new ChatContextMessage(1L, 1, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "유심 재발급 비용이 궁금해요.", null);
        var correction = new ChatContextMessage(5L, 5, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "그 비용은 제가 말한 사실이 아니니 확인해 주세요.", null);
        String stored = store("{\"messageIds\":[5]}", null, List.of(question, correction));
        assertThat(converter.sources(stored)).containsExactly(question, correction);
    }

    @Test
    void socialOnlyBatchCanAdvanceCoverageWithoutInventingAnEmptyFact() {
        var greeting = new ChatContextMessage(1L, 1, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "감사합니다", null);
        String stored = store("{\"messageIds\":[]}", null, List.of(greeting));
        assertThat(converter.sources(stored)).isEmpty();
        assertThat(converter.render(stored)).isNull();
    }

    private String store(String response, String previous, List<ChatContextMessage> sources) {
        return converter.validateAndStore(response, previous, sources, 512, estimator);
    }

    @Test
    void storeSnapshotMustAlsoFitTheMemoryBudget() {
        var storeAnswer = new ChatContextMessage(2L, 2, ChatMessage.Role.ASSISTANT,
                ChatMessage.MessageType.STORE_RESULT, "매장 안내", "[{\"address\":\"" + "주소".repeat(1000) + "\"}]");
        String stored = store("{\"messageIds\":[1,2]}", null, List.of(customer, storeAnswer));
        assertThat(converter.sources(stored)).containsExactly(customer);
        assertThat(converter.render(stored)).isEqualTo(converter.render(store(
                "{\"messageIds\":[1]}", null, List.of(customer))));
    }

    @Test
    void boundsTooManySelectedOriginalsByKeepingMostRecentCompleteMessages() {
        List<ChatContextMessage> messages = java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(index -> new ChatContextMessage((long) index, index, ChatMessage.Role.USER,
                        ChatMessage.MessageType.QUESTION, "로밍 상품 질문 " + index, null)).toList();
        String ids = messages.stream().map(message -> message.messageId().toString())
                .collect(java.util.stream.Collectors.joining(","));
        String stored = converter.validateAndStore("{\"messageIds\":[" + ids + "]}", null,
                messages, 512, estimator);
        assertThat(converter.sources(stored)).hasSize(16).containsExactlyElementsOf(messages.subList(4, 20));
        assertThat(estimator.estimatePromptPart(converter.render(stored))).isLessThanOrEqualTo(512);
    }

    @Test
    void budgetFallbackDoesNotStoreAnEllipticalUtteranceWithoutItsSubject() {
        var subject = new ChatContextMessage(1L, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "유심 재발급 " + "조건".repeat(200), null);
        var followup = new ChatContextMessage(3L, 3, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "그 비용은 얼마예요?", null);
        String stored = converter.validateAndStore("{\"messageIds\":[3]}", null,
                List.of(subject, followup), 80, estimator);
        assertThat(converter.sources(stored)).isEmpty();
    }

    @Test
    void budgetFallbackKeepsTheCompleteLatestCorrectionInsteadOfTheEarlierCondition() {
        var corrected = new ChatContextMessage(3L, 3, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION,
                "제 명의예요.", null);
        int budget = estimator.estimatePromptPart(converter.render(store(
                "{\"messageIds\":[3]}", null, List.of(corrected))));
        String stored = converter.validateAndStore("{\"messageIds\":[1,3]}", null,
                List.of(customer, corrected), budget, estimator);
        assertThat(converter.sources(stored)).containsExactly(corrected);
        assertThat(converter.render(stored)).doesNotContain("부모님");
    }

    @Test
    void nullAndDuplicateStoredSourcesAreRejected() {
        assertThatThrownBy(() -> converter.sources("{\"format\":\"telme-summary-v1\",\"messages\":[null]}"))
                .isInstanceOf(IllegalArgumentException.class);
        String stored = store("{\"messageIds\":[1]}", null, List.of(customer));
        String duplicate = stored.replace("\"messages\":[", "\"messages\":["
                + new ObjectMapper().valueToTree(customer) + ",");
        assertThatThrownBy(() -> converter.sources(duplicate)).isInstanceOf(IllegalArgumentException.class);
    }
}
