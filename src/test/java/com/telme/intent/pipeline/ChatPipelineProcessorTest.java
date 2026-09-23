package com.telme.intent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextBuilder;
import com.telme.chat.service.ChatExecutionService;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatOutputMessage;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.JdbcConsultStateStore.MessageLinks;
import com.telme.consult.service.ConsultService;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.service.QueryRoutingService;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import com.telme.rag.service.AnswerGenerator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class ChatPipelineProcessorTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatExecutionService chatExecutionService;

    @Mock
    private ChatContextBuilder chatContextBuilder;

    @Mock
    private QueryRoutingService queryRoutingService;

    @Mock
    private FaqSearchService faqSearchService;

    @Mock
    private AnswerGenerator answerGenerator;

    @Mock
    private ConsultService consultService;

    @Mock
    private ObjectProvider<ConsultService> consultServiceProvider;

    private ChatPipelineProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ChatPipelineProcessor(
                chatMessageRepository,
                chatSessionRepository,
                chatExecutionService,
                chatContextBuilder,
                queryRoutingService,
                faqSearchService,
                answerGenerator,
                consultServiceProvider
        );
    }

    @Test
    @DisplayName("FAQ 질의 처리: 원문 userQuery 전달, FAQ 검색, startAnswer 및 completeAnswer 정상 호출 검증")
    void handleFaqIntent_success() {
        Long executionId = 100L;
        Long sessionId = 10L;
        Long messageId = 1L;
        String userRawText = "5G 요금제 알려줘";

        ChatSession session = ChatSession.builder().sessionId(sessionId).status(ChatSession.Status.ACTIVE).build();
        ChatMessage message = ChatMessage.builder().messageId(messageId).session(session).content(userRawText).build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));

        IntentRouteResponse routing = new IntentRouteResponse(
                1L, messageId, QueryRouting.Intent.FAQ, "5G 요금제 안내",
                BigDecimal.valueOf(0.95), QueryRouting.Method.LLM,
                Collections.emptyMap(), Collections.emptyList()
        );
        given(queryRoutingService.route(eq(message), any())).willReturn(routing);

        List<FaqSearchResponse> faqs = List.of(new FaqSearchResponse(1L, "BILLING", "5G 요금제", "5G 요금제 설명", 0.9, 1, LocalDate.now(), 1));
        given(faqSearchService.search(any())).willReturn(faqs);

        AnswerResult answerResult = AnswerResult.builder()
                .answer("5G 요금제는 월 55,000원부터 시작합니다.")
                .answerBasis(ChatMessage.AnswerBasis.GROUNDED)
                .sources(Collections.emptyList())
                .build();
        given(answerGenerator.generate(any(), any())).willReturn(answerResult);

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userRawText);
        processor.request(command);

        verify(chatExecutionService).startAnswer(executionId);

        ArgumentCaptor<AnswerRequest> captor = ArgumentCaptor.forClass(AnswerRequest.class);
        verify(answerGenerator).generate(captor.capture(), any());
        assertThat(captor.getValue().userQuery()).isEqualTo(userRawText);
        assertThat(captor.getValue().executionId()).isEqualTo(executionId);
        assertThat(captor.getValue().searchResults()).hasSize(1);

        verify(chatExecutionService).completeAnswer(eq(executionId), argThat(ans ->
                ans.messageType() == ChatMessage.MessageType.ANSWER &&
                ans.answerBasis() == ChatMessage.AnswerBasis.GROUNDED &&
                ans.content().contains("5G 요금제는 월 55,000원부터")));
    }

    @Test
    @DisplayName("조립된 ChatContext(이전 대화·요약)를 라우팅 입력으로 함께 전달한다")
    void route_receivesAssembledChatContext() {
        Long executionId = 122L;
        Long sessionId = 10L;
        Long messageId = 22L;
        String userRawText = "거기 영업시간은?";

        ChatSession session = ChatSession.builder().sessionId(sessionId).status(ChatSession.Status.ACTIVE).build();
        ChatMessage message = ChatMessage.builder().messageId(messageId).session(session).content(userRawText).build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));

        ChatContext context = new ChatContext(
                sessionId, messageId, "고객이 강남역 유심 교체 매장을 문의했다.",
                Collections.emptyList(), userRawText, 42);
        given(chatContextBuilder.build(any(), anyInt())).willReturn(context);

        IntentRouteResponse routing = new IntentRouteResponse(
                12L, messageId, QueryRouting.Intent.STORE, "강남역 매장 영업시간",
                BigDecimal.valueOf(0.93), QueryRouting.Method.LLM,
                Map.of("location", "강남역"), Collections.emptyList()
        );
        given(queryRoutingService.route(eq(message), any())).willReturn(routing);

        processor.request(new ChatProcessingCommand(executionId, sessionId, messageId, userRawText));

        verify(queryRoutingService).route(message, context);
    }

    @Test
    @DisplayName("Context 조립이 실패해도 분류가 막히지 않도록 질문만으로 라우팅한다")
    void route_whenContextBuildFails_routesWithoutContext() {
        Long executionId = 123L;
        Long sessionId = 10L;
        Long messageId = 23L;
        String userRawText = "가까운 대리점 어디야?";

        ChatSession session = ChatSession.builder().sessionId(sessionId).status(ChatSession.Status.ACTIVE).build();
        ChatMessage message = ChatMessage.builder().messageId(messageId).session(session).content(userRawText).build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));
        given(chatContextBuilder.build(any(), anyInt()))
                .willThrow(new IllegalArgumentException("채팅 실행을 찾을 수 없습니다."));

        IntentRouteResponse routing = new IntentRouteResponse(
                13L, messageId, QueryRouting.Intent.STORE, "가까운 대리점",
                BigDecimal.valueOf(0.80), QueryRouting.Method.RULE,
                Collections.emptyMap(), Collections.emptyList()
        );
        given(queryRoutingService.route(eq(message), any())).willReturn(routing);

        processor.request(new ChatProcessingCommand(executionId, sessionId, messageId, userRawText));

        verify(queryRoutingService).route(eq(message), isNull());
        verify(chatExecutionService).askClarification(eq(executionId), argThat(q -> q.contains("지역")));
    }

    @Test
    @DisplayName("RAG 답변 생성이 실패해 기본 문구로 내려가면 GROUNDED가 아닌 NO_EVIDENCE로 마감한다")
    void handleFaqIntent_whenGenerationFails_marksNoEvidence() {
        Long executionId = 120L;
        Long sessionId = 10L;
        Long messageId = 20L;
        String userRawText = "위약금 얼마예요?";

        ChatSession session = ChatSession.builder().sessionId(sessionId).status(ChatSession.Status.ACTIVE).build();
        ChatMessage message = ChatMessage.builder().messageId(messageId).session(session).content(userRawText).build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));

        IntentRouteResponse routing = new IntentRouteResponse(
                10L, messageId, QueryRouting.Intent.FAQ, "위약금 안내",
                BigDecimal.valueOf(0.95), QueryRouting.Method.LLM,
                Collections.emptyMap(), Collections.emptyList()
        );
        given(queryRoutingService.route(eq(message), any())).willReturn(routing);
        given(faqSearchService.search(any())).willReturn(Collections.emptyList());
        given(answerGenerator.generate(any(), any())).willThrow(new IllegalStateException("LLM 연결 실패"));

        processor.request(new ChatProcessingCommand(executionId, sessionId, messageId, userRawText));

        verify(chatExecutionService).completeAnswer(eq(executionId), argThat(ans ->
                ans.answerBasis() == ChatMessage.AnswerBasis.NO_EVIDENCE));
    }

    @Test
    @DisplayName("입력이 비어 있으면 묻지 않은 FAQ를 근거로 붙이지 않도록 검색과 답변 생성을 건너뛴다")
    void handleFaqIntent_whenBlankInput_skipsSearchAndGeneration() {
        Long executionId = 121L;
        Long sessionId = 10L;
        Long messageId = 21L;

        ChatSession session = ChatSession.builder().sessionId(sessionId).status(ChatSession.Status.ACTIVE).build();
        ChatMessage message = ChatMessage.builder().messageId(messageId).session(session).content("   ").build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));

        IntentRouteResponse routing = new IntentRouteResponse(
                11L, messageId, QueryRouting.Intent.FAQ, "",
                BigDecimal.valueOf(0.50), QueryRouting.Method.RULE,
                Collections.emptyMap(), Collections.emptyList()
        );
        given(queryRoutingService.route(eq(message), any())).willReturn(routing);

        processor.request(new ChatProcessingCommand(executionId, sessionId, messageId, "   "));

        verifyNoInteractions(faqSearchService);
        verifyNoInteractions(answerGenerator);
        verify(chatExecutionService).completeAnswer(eq(executionId), argThat(ans ->
                ans.answerBasis() == ChatMessage.AnswerBasis.OUT_OF_SCOPE));
    }

    @Test
    @DisplayName("매장 질의 시 지역 조건이 없으면 되묻기(askClarification)를 호출한다")
    void handleStoreIntent_withoutLocation_asksClarification() {
        Long executionId = 101L;
        Long sessionId = 10L;
        Long messageId = 2L;
        String userRawText = "가까운 대리점 어디야?";

        ChatSession session = ChatSession.builder().sessionId(sessionId).status(ChatSession.Status.ACTIVE).build();
        ChatMessage message = ChatMessage.builder().messageId(messageId).session(session).content(userRawText).build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));

        IntentRouteResponse routing = new IntentRouteResponse(
                2L, messageId, QueryRouting.Intent.STORE, "대리점 위치",
                BigDecimal.valueOf(0.92), QueryRouting.Method.LLM,
                Collections.emptyMap(), Collections.emptyList()
        );
        given(queryRoutingService.route(eq(message), any())).willReturn(routing);

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userRawText);
        processor.request(command);

        verify(chatExecutionService).askClarification(eq(executionId), argThat(q -> q.contains("지역")));
    }

    @Test
    @DisplayName("매장 질의에 지역 조건이 포함되어 있으면 매장 결과로 completeAnswer를 호출한다")
    void handleStoreIntent_withLocation_completesStoreResult() {
        Long executionId = 102L;
        Long sessionId = 10L;
        Long messageId = 3L;
        String userRawText = "강남역 대리점 찾아줘";

        ChatSession session = ChatSession.builder().sessionId(sessionId).status(ChatSession.Status.ACTIVE).build();
        ChatMessage message = ChatMessage.builder().messageId(messageId).session(session).content(userRawText).build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));

        IntentRouteResponse routing = new IntentRouteResponse(
                3L, messageId, QueryRouting.Intent.STORE, "강남역 대리점",
                BigDecimal.valueOf(0.95), QueryRouting.Method.LLM,
                Map.of("location", "강남역"), Collections.emptyList()
        );
        given(queryRoutingService.route(eq(message), any())).willReturn(routing);

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userRawText);
        processor.request(command);

        verify(chatExecutionService).completeAnswer(eq(executionId), argThat(ans ->
                ans.messageType() == ChatMessage.MessageType.STORE_RESULT &&
                ans.content().contains("강남역")));
    }

    @Test
    @DisplayName("되묻기 대기 세션: 후속 분석이 돌려준 consultRequestId와 조건 Map을 prepareTurn에 그대로 전달한다")
    void handleClarificationReply_usesWaitingConsultRequestId() {
        Long executionId = 103L;
        Long sessionId = 10L;
        Long messageId = 4L;
        Long waitingConsultRequestId = 42L;
        String userFollowUpText = "강남역이요";

        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .status(ChatSession.Status.NEED_CLARIFICATION)
                .build();
        ChatMessage message = ChatMessage.builder()
                .messageId(messageId)
                .session(session)
                .content(userFollowUpText)
                .build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));

        // 원문("강남역이요")이 아닌 정규화된 값("강남역")이 돌아오는 상황
        given(queryRoutingService.analyzeFollowUp(sessionId, userFollowUpText)).willReturn(
                new FollowUpRouteResponse(
                        waitingConsultRequestId,
                        Map.of("location", "강남역"),
                        Collections.emptySet(),
                        QueryRouting.Method.LLM));

        given(consultServiceProvider.getIfAvailable()).willReturn(consultService);

        DialogueDecision askDecision = DialogueDecision.builder()
                .consultRequestId(waitingConsultRequestId)
                .action(Action.ASK)
                .conditions(Map.of("location", Condition.pending()))
                .waitingField("location")
                .message("어느 지역의 매장을 찾으시나요?")
                .messageOrigin(DialogueDecision.MessageOrigin.TEMPLATE)
                .build();
        ConsultService.PreparedTurn preparedTurn = ConsultService.PreparedTurn.builder()
                .sessionId(sessionId)
                .expectedVersion(1)
                .decision(askDecision)
                .build();
        ConsultService.PreparationResult prepResult = ConsultService.PreparationResult.builder()
                .prepared(preparedTurn)
                .build();

        Map<String, Condition> expectedUpdates = Map.of("location", Condition.filled("강남역"));

        given(consultService.prepareTurn(
                eq(sessionId), eq(waitingConsultRequestId), eq(Purpose.NEARBY_STORE),
                eq(expectedUpdates), eq(LocationStatus.AVAILABLE))).willReturn(prepResult);

        Long clarificationMessageId = 555L;
        given(chatExecutionService.askClarification(eq(executionId), any())).willReturn(
                new ChatOutputMessage(sessionId, executionId, clarificationMessageId, 2,
                        ChatMessage.MessageType.CLARIFICATION, ChatMessage.Status.COMPLETED));

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userFollowUpText);
        processor.request(command);

        verify(consultService).prepareTurn(
                sessionId, waitingConsultRequestId, Purpose.NEARBY_STORE, expectedUpdates, LocationStatus.AVAILABLE);
        verify(chatExecutionService).askClarification(eq(executionId), eq("어느 지역의 매장을 찾으시나요?"));
        // 되묻기 질문과 방금 받은 답변 메시지를 이어 붙여 상담에 저장해야 한다
        verify(consultService).persist(eq(preparedTurn), eq(new MessageLinks(clarificationMessageId, messageId, "location")));
    }

    @Test
    @DisplayName("되묻기 거절: 조건을 DECLINED로 전달하고 LocationStatus.DECLINED로 상담 모듈을 호출한다")
    void handleClarificationReply_whenDeclined_passesDeclinedStatus() {
        Long executionId = 108L;
        Long sessionId = 10L;
        Long messageId = 9L;
        Long waitingConsultRequestId = 44L;
        String userFollowUpText = "그냥 알려주기 싫어요";

        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .status(ChatSession.Status.NEED_CLARIFICATION)
                .build();
        ChatMessage message = ChatMessage.builder()
                .messageId(messageId)
                .session(session)
                .content(userFollowUpText)
                .build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));
        given(queryRoutingService.analyzeFollowUp(sessionId, userFollowUpText)).willReturn(
                new FollowUpRouteResponse(
                        waitingConsultRequestId,
                        Collections.emptyMap(),
                        Set.of("location"),
                        QueryRouting.Method.RULE));
        given(consultServiceProvider.getIfAvailable()).willReturn(consultService);

        DialogueDecision guidanceDecision = DialogueDecision.builder()
                .consultRequestId(waitingConsultRequestId)
                .action(Action.ALTERNATIVE_GUIDANCE)
                .conditions(Map.of("location", Condition.declined()))
                .message("검색 지역 없이는 가까운 매장을 안내하기 어려워요.")
                .messageOrigin(DialogueDecision.MessageOrigin.TEMPLATE)
                .build();
        ConsultService.PreparationResult prepResult = ConsultService.PreparationResult.builder()
                .prepared(ConsultService.PreparedTurn.builder()
                        .sessionId(sessionId)
                        .expectedVersion(1)
                        .decision(guidanceDecision)
                        .build())
                .build();

        Map<String, Condition> expectedUpdates = Map.of("location", Condition.declined());
        given(consultService.prepareTurn(
                eq(sessionId), eq(waitingConsultRequestId), eq(Purpose.NEARBY_STORE),
                eq(expectedUpdates), eq(LocationStatus.DECLINED))).willReturn(prepResult);

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userFollowUpText);
        processor.request(command);

        verify(consultService).prepareTurn(
                sessionId, waitingConsultRequestId, Purpose.NEARBY_STORE, expectedUpdates, LocationStatus.DECLINED);
        verify(chatExecutionService).completeAnswer(eq(executionId), argThat(ans ->
                ans.answerBasis() == ChatMessage.AnswerBasis.OUT_OF_SCOPE &&
                ans.content().contains("검색 지역 없이는")));
        // PENDING -> DECLINED 반영이 실제로 저장되어야 한다
        verify(consultService).persist(any(), eq(new MessageLinks(null, messageId, "location")));
    }

    @Test
    @DisplayName("되묻기 대기 상담이 없으면 임의의 상담을 건드리지 않고 상담 모듈 호출 없이 안내한다")
    void handleClarificationReply_whenNoWaitingConsult_skipsConsultService() {
        Long executionId = 109L;
        Long sessionId = 10L;
        Long messageId = 10L;
        String userFollowUpText = "신촌";

        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .status(ChatSession.Status.NEED_CLARIFICATION)
                .build();
        ChatMessage message = ChatMessage.builder()
                .messageId(messageId)
                .session(session)
                .content(userFollowUpText)
                .build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));
        given(queryRoutingService.analyzeFollowUp(sessionId, userFollowUpText))
                .willReturn(FollowUpRouteResponse.noTarget());
        given(consultServiceProvider.getIfAvailable()).willReturn(consultService);

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userFollowUpText);
        processor.request(command);

        verifyNoInteractions(consultService);
        verify(chatExecutionService).completeAnswer(eq(executionId), argThat(ans ->
                ans.messageType() == ChatMessage.MessageType.STORE_RESULT &&
                ans.content().contains("신촌")));
    }

    @Test
    @DisplayName("이미 보낸 되묻기 질문의 답을 기다리는 중이면 중복 답변 없이 출력 없는 종료로 마감한다")
    void handleClarificationReply_whenWaitingForReply_completesWithoutOutput() {
        Long executionId = 110L;
        Long sessionId = 10L;
        Long messageId = 11L;
        Long waitingConsultRequestId = 45L;
        String userFollowUpText = "음 글쎄요";

        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .status(ChatSession.Status.NEED_CLARIFICATION)
                .build();
        ChatMessage message = ChatMessage.builder()
                .messageId(messageId)
                .session(session)
                .content(userFollowUpText)
                .build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));
        given(queryRoutingService.analyzeFollowUp(sessionId, userFollowUpText)).willReturn(
                new FollowUpRouteResponse(waitingConsultRequestId, Collections.emptyMap(),
                        Collections.emptySet(), QueryRouting.Method.RULE));
        given(consultServiceProvider.getIfAvailable()).willReturn(consultService);

        ConsultService.PreparationResult waiting = ConsultService.PreparationResult.builder()
                .pendingMessageId(777L)
                .build();
        given(consultService.prepareTurn(
                eq(sessionId), eq(waitingConsultRequestId), eq(Purpose.NEARBY_STORE),
                eq(Collections.emptyMap()), eq(LocationStatus.MISSING))).willReturn(waiting);

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userFollowUpText);
        processor.request(command);

        verify(chatExecutionService).completeWithoutOutput(executionId);
        verify(chatExecutionService, never()).completeAnswer(any(), any());
    }

    @Test
    @DisplayName("되묻기 후속 답변: PROCEED 시 원문이 아닌 상담 모듈이 정규화한 조건값으로 매장 결과를 마감한다")
    void handleClarificationReply_whenProceed_completesStoreResult() {
        Long executionId = 105L;
        Long sessionId = 10L;
        Long messageId = 6L;
        Long waitingConsultRequestId = 43L;
        String userFollowUpText = "홍대입구역 쪽으로 가려고요";

        ChatSession session = ChatSession.builder()
                .sessionId(sessionId)
                .status(ChatSession.Status.NEED_CLARIFICATION)
                .build();
        ChatMessage message = ChatMessage.builder()
                .messageId(messageId)
                .session(session)
                .content(userFollowUpText)
                .build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));
        given(queryRoutingService.analyzeFollowUp(sessionId, userFollowUpText)).willReturn(
                new FollowUpRouteResponse(
                        waitingConsultRequestId,
                        Map.of("location", "홍대입구역"),
                        Collections.emptySet(),
                        QueryRouting.Method.LLM));
        given(consultServiceProvider.getIfAvailable()).willReturn(consultService);

        DialogueDecision proceedDecision = DialogueDecision.builder()
                .consultRequestId(waitingConsultRequestId)
                .action(Action.PROCEED)
                .conditions(Map.of("location", Condition.filled("홍대입구역")))
                .messageOrigin(DialogueDecision.MessageOrigin.NONE)
                .build();
        ConsultService.PreparedTurn preparedTurn = ConsultService.PreparedTurn.builder()
                .sessionId(sessionId)
                .expectedVersion(1)
                .decision(proceedDecision)
                .build();
        ConsultService.PreparationResult prepResult = ConsultService.PreparationResult.builder()
                .prepared(preparedTurn)
                .build();

        Map<String, Condition> expectedUpdates = Map.of("location", Condition.filled("홍대입구역"));

        given(consultService.prepareTurn(
                eq(sessionId), eq(waitingConsultRequestId), eq(Purpose.NEARBY_STORE),
                eq(expectedUpdates), eq(LocationStatus.AVAILABLE))).willReturn(prepResult);

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userFollowUpText);
        processor.request(command);

        verify(chatExecutionService).completeAnswer(eq(executionId), argThat(ans ->
                ans.messageType() == ChatMessage.MessageType.STORE_RESULT &&
                ans.content().contains("홍대입구역") &&
                !ans.content().contains("가려고요")));
        // PENDING -> FILLED 반영이 실제로 저장되어야 한다
        verify(consultService).persist(eq(preparedTurn), eq(new MessageLinks(null, messageId, "location")));
    }

    @Test
    @DisplayName("복합 질의(BOTH) 처리: FAQ 검색 및 RAG 답변과 위치 기반 매장 정보를 종합하여 하나의 답변으로 마감한다")
    void handleBothIntent_withLocation_combinesFaqAndStore() {
        Long executionId = 106L;
        Long sessionId = 10L;
        Long messageId = 7L;
        String userRawText = "5G 요금제 알려주고 신촌 대리점도 찾아줘";

        ChatSession session = ChatSession.builder().sessionId(sessionId).status(ChatSession.Status.ACTIVE).build();
        ChatMessage message = ChatMessage.builder().messageId(messageId).session(session).content(userRawText).build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));

        IntentRouteResponse.IntentSubQueryResponse subFaq = new IntentRouteResponse.IntentSubQueryResponse(
                1L, (short) 1, ConsultRequest.Intent.FAQ, "5G 요금제 안내", Collections.emptyMap()
        );
        IntentRouteResponse.IntentSubQueryResponse subStore = new IntentRouteResponse.IntentSubQueryResponse(
                2L, (short) 2, ConsultRequest.Intent.STORE, "신촌 대리점 찾기", Map.of("location", "신촌")
        );

        IntentRouteResponse routing = new IntentRouteResponse(
                5L, messageId, QueryRouting.Intent.BOTH, "5G 요금제 및 신촌 매장",
                BigDecimal.valueOf(0.98), QueryRouting.Method.LLM,
                Map.of("location", "신촌"), List.of(subFaq, subStore)
        );
        given(queryRoutingService.route(eq(message), any())).willReturn(routing);

        List<FaqSearchResponse> faqs = List.of(new FaqSearchResponse(1L, "BILLING", "5G 요금제 안내", "5G 요금제 상세", 0.9, 1, LocalDate.now(), 1));
        given(faqSearchService.search(any())).willReturn(faqs);

        AnswerResult answerResult = AnswerResult.builder()
                .answer("5G 요금제는 월 55,000원부터 85,000원까지 구성되어 있습니다.")
                .answerBasis(ChatMessage.AnswerBasis.GROUNDED)
                .sources(Collections.emptyList())
                .build();
        given(answerGenerator.generate(any(), any())).willReturn(answerResult);

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userRawText);
        processor.request(command);

        verify(chatExecutionService).startAnswer(executionId);

        // BOTH에서도 서브질의가 아닌 사용자 원문이 넘어가야 한다
        ArgumentCaptor<AnswerRequest> captor = ArgumentCaptor.forClass(AnswerRequest.class);
        verify(answerGenerator).generate(captor.capture(), any());
        assertThat(captor.getValue().userQuery()).isEqualTo(userRawText);

        verify(chatExecutionService).completeAnswer(eq(executionId), argThat(ans ->
                ans.messageType() == ChatMessage.MessageType.ANSWER &&
                ans.content().contains("5G 요금제는 월 55,000원") &&
                ans.content().contains("[매장 안내]") &&
                ans.content().contains("신촌 인근에서 방문 가능한 매장") &&
                ans.storeResults() != null && !ans.storeResults().isEmpty()));
    }

    @Test
    @DisplayName("복합 질의(BOTH) 위치 미입력: FAQ 답변과 함께 매장 위치를 묻는 안내 문구를 결합하여 반환한다")
    void handleBothIntent_withoutLocation_answersFaqAndAsksLocation() {
        Long executionId = 107L;
        Long sessionId = 10L;
        Long messageId = 8L;
        String userRawText = "5G 요금제 알려주고 가까운 대리점도 찾아줘";

        ChatSession session = ChatSession.builder().sessionId(sessionId).status(ChatSession.Status.ACTIVE).build();
        ChatMessage message = ChatMessage.builder().messageId(messageId).session(session).content(userRawText).build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));

        IntentRouteResponse.IntentSubQueryResponse subFaq = new IntentRouteResponse.IntentSubQueryResponse(
                1L, (short) 1, ConsultRequest.Intent.FAQ, "5G 요금제 안내", Collections.emptyMap()
        );
        IntentRouteResponse.IntentSubQueryResponse subStore = new IntentRouteResponse.IntentSubQueryResponse(
                2L, (short) 2, ConsultRequest.Intent.STORE, "가까운 대리점 찾기", Collections.emptyMap()
        );

        IntentRouteResponse routing = new IntentRouteResponse(
                6L, messageId, QueryRouting.Intent.BOTH, "5G 요금제 및 대리점 찾기",
                BigDecimal.valueOf(0.95), QueryRouting.Method.LLM,
                Collections.emptyMap(), List.of(subFaq, subStore)
        );
        given(queryRoutingService.route(eq(message), any())).willReturn(routing);

        List<FaqSearchResponse> faqs = List.of(new FaqSearchResponse(1L, "BILLING", "5G 요금제 안내", "5G 요금제 상세", 0.9, 1, LocalDate.now(), 1));
        given(faqSearchService.search(any())).willReturn(faqs);

        AnswerResult answerResult = AnswerResult.builder()
                .answer("5G 요금제는 월 55,000원부터 이용 가능합니다.")
                .answerBasis(ChatMessage.AnswerBasis.GROUNDED)
                .sources(Collections.emptyList())
                .build();
        given(answerGenerator.generate(any(), any())).willReturn(answerResult);

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userRawText);
        processor.request(command);

        verify(chatExecutionService).startAnswer(executionId);
        verify(chatExecutionService).completeAnswer(eq(executionId), argThat(ans ->
                ans.messageType() == ChatMessage.MessageType.ANSWER &&
                ans.content().contains("5G 요금제는 월 55,000원") &&
                ans.content().contains("[매장 안내]") &&
                ans.content().contains("지역(역 이름이나 동네)을 알려주세요") &&
                ans.storeResults() == null));
    }

    @Test
    @DisplayName("UNKNOWN 의도: 통신 무관/일상 질의 시 친절한 안내 문구와 OUT_OF_SCOPE 근거로 답변 마감")
    void handleUnknownIntent_friendlyFallback() {
        Long executionId = 104L;
        Long sessionId = 10L;
        Long messageId = 5L;
        String userRawText = "오늘 점심 메뉴 추천해줘";

        ChatSession session = ChatSession.builder().sessionId(sessionId).status(ChatSession.Status.ACTIVE).build();
        ChatMessage message = ChatMessage.builder().messageId(messageId).session(session).content(userRawText).build();

        given(chatMessageRepository.findByIdWithSession(messageId)).willReturn(Optional.of(message));

        IntentRouteResponse routing = new IntentRouteResponse(
                4L, messageId, QueryRouting.Intent.UNKNOWN, "",
                BigDecimal.valueOf(0.50), QueryRouting.Method.RULE,
                Collections.emptyMap(), Collections.emptyList()
        );
        given(queryRoutingService.route(eq(message), any())).willReturn(routing);

        ChatProcessingCommand command = new ChatProcessingCommand(executionId, sessionId, messageId, userRawText);
        processor.request(command);

        verify(chatExecutionService).startAnswer(executionId);
        verify(chatExecutionService).completeAnswer(eq(executionId), argThat(ans ->
                ans.messageType() == ChatMessage.MessageType.ANSWER &&
                ans.answerBasis() == ChatMessage.AnswerBasis.OUT_OF_SCOPE &&
                ans.content().contains("LG U+ 통신 고객센터 AI 상담 어시스턴트")));
    }

    @Test
    @DisplayName("Null 방어: 유효하지 않은 command나 존재하지 않는 messageId 요청 시 NPE 없이 안전하게 실패 처리")
    void defensiveNullHandling() {
        processor.request(null);

        given(chatMessageRepository.findByIdWithSession(999L)).willReturn(Optional.empty());
        ChatProcessingCommand invalidCmd = new ChatProcessingCommand(200L, 10L, 999L, "안녕");
        processor.request(invalidCmd);

        verify(chatExecutionService).fail(eq(200L), argThat(f -> f.errorCode().equals("MESSAGE_NOT_FOUND")));
    }
}
