package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatEndSummaryProperties;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.safety.ChatOutputGuard;
import com.telme.chat.safety.OutputMaskContext;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryGuidanceLimitTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final LlmClient client = mock(LlmClient.class);
    private final List<LlmRequest> calls = new ArrayList<>();

    @BeforeEach
    void 유효한_번호만_선택하는_모델_대역을_준비한다() {
        when(client.generate(any())).thenAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            calls.add(request);
            var prompt = mapper.readTree(request.userPrompt());
            var result = mapper.createObjectNode();
            var topics = result.putObject("topics");
            prompt.path("requests").forEach(query -> topics.put(query.path("originMessageId").asText(), "유심 문의"));
            var ids = result.putArray("selectedSentenceIds");
            for (var candidate : prompt.path("guidanceCandidates")) {
                if (ids.size() < 8) {
                    ids.add(candidate.path("sentenceId").asInt());
                }
            }
            result.putArray("pendingItems");
            return result.toString();
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {480, 481, 1400, 6000})
    @DisplayName("긴 원문은 모델 입력에서만 나누고 조건과 방문 절차를 온전히 복원한다")
    void 긴_문단의_시작과_끝을_누락하거나_재작성하지_않는다(int length) throws Exception {
        String answer = longAnswer(length);
        var result = generate(answer);

        assertThat(result.summary().content().guidance()).containsExactly(answer);
        assertThat(result.summary().content().inquiries()).hasSize(1);
        assertThat(result.summary().json()).doesNotContain("처리 대기", "신청 완료");
        assertModelLimits();
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 17, 64})
    @DisplayName("호출마다 8개 이하를 선택해도 전체 상담의 9개 이상 안내는 모두 보존한다")
    void 호출별_선택_한도와_전체_안내_개수를_분리한다(int count) throws Exception {
        var paragraphs = new ArrayList<String>();
        for (int index = 1; index <= count; index++) {
            paragraphs.add("유심 안내 " + index + "의 비용은 " + index + ",000원이며 명의자 본인 방문이 필요합니다.");
        }
        var result = generate(String.join("\n\n", paragraphs));

        for (String paragraph : paragraphs) {
            assertThat(result.summary().content().guidance().getFirst()).contains(paragraph);
        }
        assertThat(result.summary().content().inquiries()).hasSize(1);
        assertThat(calls).hasSizeGreaterThanOrEqualTo((count + 7) / 8);
        assertModelLimits();
    }

    @Test
    @DisplayName("긴 원문의 일부 번호만 선택해도 해당 문단의 조건과 미접수 상태는 모두 복원한다")
    void 선택된_긴_문단을_임의로_짧게_만들지_않는다() {
        doReturn("{\"topics\":{\"1\":\"유심 문의\"},\"selectedSentenceIds\":[1],\"pendingItems\":[]}")
                .when(client).generate(any());
        String answer = longAnswer(1400);

        assertThat(generate(answer).summary().content().guidance()).containsExactly(answer);
    }

    @Test
    @DisplayName("하나의 긴 답변이 여러 호출로 나뉘어도 원문 순서와 마지막 조건을 보존한다")
    void 여러_호출의_조각을_원래_한_문단으로_복원한다() throws Exception {
        var sentences = new ArrayList<String>();
        for (int index = 1; index <= 100; index++) {
            sentences.add("유심 방문 준비 항목 " + index + "은 명의자 확인과 필요한 서류를 점검하는 절차입니다.");
        }
        String answer = String.join(" ", sentences) + " 재발급 비용은 7,700원이며 기기 불량 확인 시에만 무료입니다."
                + " 명의자 본인이 신분증을 지참해야 합니다. 신청은 접수되지 않았습니다.";

        assertThat(generate(answer).summary().content().guidance()).containsExactly(answer);
        assertThat(calls).hasSizeGreaterThan(1);
        assertModelLimits();
    }

    @Test
    @DisplayName("긴 원문 복원에도 지원 민감번호 마스킹과 정상 숫자 보존을 적용한다")
    void 긴_원문에서도_민감번호를_다시_노출하지_않는다() throws Exception {
        String answer = longAnswer(1400) + " 합성 주민번호 900101-1234567, 카드번호 4111111111111111입니다.";
        var result = generate(answer);

        assertThat(result.summary().json()).doesNotContain("900101", "1234567", "4111111111111111")
                .contains("***", "7,700원", "18개월", "명의자 본인", "신청은 접수되지 않았습니다");
        for (var call : calls) {
            assertThat(call.userPrompt()).doesNotContain("900101", "1234567", "4111111111111111");
        }
        assertModelLimits();
    }

    @Test
    @DisplayName("전체 결과가 늘어도 모델이 한 번에 9개 번호를 출력하는 것은 거부한다")
    void 호출별_출력_한도는_완화하지_않는다() {
        doReturn("{\"topics\":{\"1\":\"유심 문의\"},"
                + "\"selectedSentenceIds\":[1,2,3,4,5,6,7,8,9],\"pendingItems\":[]}")
                .when(client).generate(any());

        assertThatThrownBy(() -> generate("유심 재발급은 명의자 본인이 방문해야 합니다."))
                .hasMessage("INVALID_SELECTION");
    }

    @Test
    @DisplayName("전체 안내 상한은 별도로 유지하며 65개 원문을 일부만 성공 처리하지 않는다")
    void 전체_작업_한도를_넘으면_누락된_성공_결과를_제공하지_않는다() {
        var paragraphs = new ArrayList<String>();
        for (int index = 1; index <= 65; index++) {
            paragraphs.add("유심 안내 " + index + "의 조건을 확인했습니다.");
        }

        assertThatThrownBy(() -> generate(String.join("\n\n", paragraphs))).hasMessage("WORK_LIMIT");
        assertThat(calls).isEmpty();
    }

    @Test
    @DisplayName("전체 원문 길이 상한은 모델 호출 전에 확인한다")
    void 전체_원문_길이_상한은_유지한다() {
        assertThatThrownBy(() -> generate(longAnswer(32769))).hasMessage("WORK_LIMIT");
        assertThat(calls).isEmpty();
    }

    @Test
    @DisplayName("분할 경계의 보조 평면 문자도 깨뜨리지 않고 원문으로 복원한다")
    void 문자_경계를_보존한다() throws Exception {
        String answer = "가".repeat(479) + "😀" + "나".repeat(481) + " 명의자 본인 방문이 필요합니다.";

        assertThat(generate(answer).summary().content().guidance()).containsExactly(answer);
        for (var call : calls) {
            var prompt = mapper.readTree(call.userPrompt());
            for (var candidate : prompt.path("guidanceCandidates")) {
                String piece = candidate.path("text").asText();
                assertThat(Character.isHighSurrogate(piece.charAt(piece.length() - 1))).isFalse();
                assertThat(Character.isLowSurrogate(piece.charAt(0))).isFalse();
            }
        }
    }

    private void assertModelLimits() throws Exception {
        for (var call : calls) {
            var prompt = mapper.readTree(call.userPrompt());
            assertThat(prompt.path("guidanceCandidates")).hasSizeLessThanOrEqualTo(8);
            for (JsonNode item : prompt.path("guidanceCandidates")) {
                assertThat(item.path("text").asText().length()).isLessThanOrEqualTo(480);
            }
            var estimator = new ChatTokenEstimator();
            assertThat(estimator.estimatePromptPart(call.systemPrompt())
                    + estimator.estimatePromptPart(call.userPrompt())).isLessThanOrEqualTo(3072);
            var schema = mapper.valueToTree(call.responseSchema());
            assertThat(schema.path("properties").path("selectedSentenceIds").path("maxItems").asInt())
                    .isEqualTo(8);
        }
    }

    private ChatEndSummaryGroundedGenerator.Result generate(String answer) {
        var input = new ChatEndSummaryData.Input(1, 2, 1L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", "유심 재발급 절차를 알려주세요.",
                        null, "COMPLETED"),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER", answer, "GROUNDED", "COMPLETED")),
                List.of(new ChatEndSummaryData.Request(1, 1, "유심 재발급 절차", "DONE")), Set.of(), List.of(),
                OutputMaskContext.empty());
        var inspector = new ChatInputInspector(mapper);
        var guard = new ChatOutputGuard(mapper);
        var target = new ChatSessionEndSummaryGenerator(mapper, client, new ChatTokenEstimator(), inspector, guard,
                new ChatEndSummaryProperties(true, false, false, 3072, 768, 400, 8, 2,
                        Duration.ofMinutes(5), Duration.ofMinutes(15)), Clock.systemUTC());
        return new ChatEndSummaryGroundedGenerator(mapper, inspector, guard)
                .generate(input, target.generate(input, Instant.now().plusSeconds(300)));
    }

    private String longAnswer(int length) {
        String start = "유심 재발급 비용은 7,700원이며 기기 불량 확인 시에만 무료입니다. 가입 기간 18개월은 별도 정보입니다. ";
        String end = " 명의자 본인이 신분증을 지참하고 매장을 방문해야 합니다. 신청은 접수되지 않았습니다.";
        return start + "가".repeat(length - start.length() - end.length()) + end;
    }
}
