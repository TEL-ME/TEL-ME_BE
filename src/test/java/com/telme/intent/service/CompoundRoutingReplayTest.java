package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

// 실측에서 저장한 실제 라우팅 모델 출력을 그대로 넣어, 서버 보정이 바뀌어도 기존 처리 결과가 유지되는지 확인한다.
@ExtendWith(MockitoExtension.class)
class CompoundRoutingReplayTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock LlmClient llmClient;
    @Mock QueryRoutingRepository queryRoutingRepository;
    @Mock ConsultRequestRepository consultRequestRepository;
    @Mock TransactionTemplate transactionTemplate;

    QueryRoutingService service;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        service = new QueryRoutingService(llmClient, MAPPER, queryRoutingRepository, consultRequestRepository,
                new RuleBasedRoutingFallback(), new IntentConverter(MAPPER), transactionTemplate);
        Mockito.lenient().when(queryRoutingRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(consultRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Mockito.lenient().when(queryRoutingRepository.findByMessage_MessageId(any()))
                .thenReturn(java.util.Optional.empty());
    }

    static Stream<JsonNode> cases() throws Exception {
        try (InputStream input = CompoundRoutingReplayTest.class
                .getResourceAsStream("/routing/compound-faq-store-model-outputs.json")) {
            List<JsonNode> cases = new ArrayList<>();
            MAPPER.readTree(input).path("cases").forEach(cases::add);
            return cases.stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void realModelOutputKeepsRecordedRoutingResult(JsonNode fixture) throws Exception {
        String output = fixture.path("modelOutput").asText();
        Mockito.lenient().doAnswer(call -> {
            LlmRequest request = call.getArgument(0);
            if ((RoutingPromptTemplates.FAQ_QUERY_FAITHFULNESS_PROMPT.equals(request.systemPrompt())
                || RoutingPromptTemplates.FAQ_SINGLE_QUERY_FAITHFULNESS_PROMPT.equals(request.systemPrompt()))) {
                int count = MAPPER.readTree(request.userPrompt()).path("subQueries").size();
                return MAPPER.writeValueAsString(Map.of("unsafe", java.util.Collections.nCopies(count, false)));
            }
            if (RoutingPromptTemplates.REQUEST_INVENTORY_PROMPT.equals(request.systemPrompt())) {
                int count = MAPPER.readTree(output).path("subQueries").size();
                return MAPPER.writeValueAsString(Map.of("decision", "MULTIPLE", "requestCount", count));
            }
            return output;
        }).when(llmClient).generate(any());

        JsonNode actual = MAPPER.valueToTree(route(fixture.path("question").asText()));
        assertThat(actual).isEqualTo(fixture.path("expected"));
    }

    private Map<String, Object> route(String question) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            var routed = service.routeSingleConsult(ChatMessage.builder().messageId(1L)
                    .session(ChatSession.builder().build()).content(question).build(), null);
            result.put("intent", routed.intent().name());
            result.put("method", routed.method().name());
            List<Map<String, Object>> subs = new ArrayList<>();
            for (var sub : routed.subQueries()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("intent", sub.intent().name());
                item.put("queryText", sub.queryText());
                item.put("conditions", new java.util.TreeMap<>(sub.conditions()));
                subs.add(item);
            }
            result.put("subQueries", subs);
        } catch (RuntimeException failure) {
            result.put("error", failure.getClass().getSimpleName());
        }
        return result;
    }
}
