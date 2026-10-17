package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextMessage;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatQuestionResolver;
import com.telme.chat.service.ExecutionTrace;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.service.LlmClient;
import com.telme.rag.dto.req.AnswerRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProcedureEvidenceAnswerTest {
    private static final String FIRST = "유심 재발급 비용과 무료 조건을 알고 싶어요. 절차만 설명해 주세요.";
    private static final String ANSWER = "비용은 7,700원입니다. 기기 불량이면 무료입니다. "
            + "신분증을 가지고 매장을 방문해야 합니다. 이번 상담에서는 재발급 신청을 처리하지 않았습니다.";
    private final FaqSearchResponse source = new FaqSearchResponse(1L, null, "USIM", FIRST, ANSWER,
            .9, 1, LocalDate.of(2026, 10, 15), 1, null);

    @Test
    @DisplayName("단일 유심 절차 안내는 조건과 미처리 상태를 포함한 FAQ 원문을 사용한다")
    void 명시적인_단일_절차는_근거_원문을_사용한다() {
        assertThat(ProcedureEvidenceAnswer.select(new AnswerRequest(1L, FIRST, Map.of(), List.of(source))))
                .isEqualTo(ANSWER);
    }

    @Test
    @DisplayName("검증된 동일 문의 재질문은 고객 가입 기간을 무료 조건의 원인으로 만들지 않는다")
    void 검증된_재질문에_새로운_인과관계를_추가하지_않는다() {
        String question = "가입 기간은 18개월입니다. 같은 유심 재발급의 신분증 지참과 매장 방문 절차를 다시 알려주세요.";
        var history = new ChatContextMessage(1L, 1, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, FIRST, null);
        var context = new ChatContext(1L, 3L, null, List.of(history), question, 100);
        var resolver = new ChatQuestionResolver(mock(LlmClient.class), new ObjectMapper(), ExecutionTrace.noop());
        var resolution = resolver.resolve(new ChatProcessingCommand(2L, 1L, 3L, question), context);
        var request = new AnswerRequest(2L, null, question, Map.of(), Map.of(), List.of(source),
                resolution.question(), resolver.contextFor(resolution, context));

        assertThat(ProcedureEvidenceAnswer.select(request)).isEqualTo(ANSWER).doesNotContain("18개월");
    }

    @Test
    @DisplayName("여러 FAQ나 확정 조건 또는 개인별 무료 자격 판단은 원문 자동 안내로 우회하지 않는다")
    void 복합_근거와_개별_자격은_자동_인용하지_않는다() {
        assertThat(ProcedureEvidenceAnswer.select(new AnswerRequest(1L, FIRST,
                Map.of(), List.of(source, source)))).isNull();
        assertThat(ProcedureEvidenceAnswer.select(new AnswerRequest(1L, FIRST,
                Map.of("agent_visit", "true"), List.of(source)))).isNull();
        assertThat(ProcedureEvidenceAnswer.select(new AnswerRequest(1L,
                "가입 18개월이면 무료인가요? 유심 재발급 절차만 설명해 주세요.", Map.of(), List.of(source)))).isNull();
    }
}
