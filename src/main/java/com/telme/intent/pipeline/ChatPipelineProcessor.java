package com.telme.intent.pipeline;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextBuilder;
import com.telme.chat.service.ChatExecutionService;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.ChatStreamPublisher;
import com.telme.chat.service.ChatStreamRelay;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.JdbcConsultStateStore.MessageLinks;
import com.telme.consult.service.ConsultService;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.service.QueryRoutingService;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import com.telme.rag.service.AnswerGenerator;
import com.telme.rag.service.AnswerPromptTemplates;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "telme.chat.pipeline.enabled", havingValue = "true")
@RequiredArgsConstructor
public class ChatPipelineProcessor implements ChatProcessingPort {

    private static final String DEFAULT_LOCATION = "선택지역";
    private static final String LOCATION_ASK_MESSAGE = "어느 지역의 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.";
    private static final String LOCATION_DECLINED_MESSAGE = "검색 지역 없이는 가까운 매장을 안내하기 어려워요.";
    private static final String STORE_PHONE = "02-1234-5678";
    private static final int FAQ_SEARCH_TOP_K = 3;
    // 라우팅은 분류만 하면 되므로 답변 생성보다 훨씬 작은 예산을 쓴다
    private static final int ROUTING_CONTEXT_TOKEN_BUDGET = 1024;
    private static final String EMPTY_QUESTION_MESSAGE = "궁금하신 내용을 문장으로 입력해 주시면 확인해 드리겠습니다.";

    private static final String USER_CANCELLED = "USER_CANCELLED";

    private final ChatMessageRepository chatMessageRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatExecutionService chatExecutionService;
    private final ChatContextBuilder chatContextBuilder;
    private final QueryRoutingService queryRoutingService;
    private final FaqSearchService faqSearchService;
    private final AnswerGenerator answerGenerator;
    private final ObjectProvider<ConsultService> consultServiceProvider;
    private final ChatStreamPublisher chatStreamPublisher;

    @Override
    public void request(ChatProcessingCommand command) {
        if (command == null || command.executionId() == null || command.inputMessageId() == null) {
            log.warn("[파이프라인] 유효하지 않은 처리 명령입니다: {}", command);
            return;
        }

        try {
            processInternal(command);
        } catch (LlmStreamCancelledException e) {
            log.info("[파이프라인] 구독자 이탈로 답변 생성 중단: executionId={}", command.executionId());
            fail(command.executionId(), new ChatFailure(ChatMessage.Status.CANCELLED, USER_CANCELLED));
        } catch (Exception e) {
            log.error("[파이프라인] AI 처리 중 예외 발생: executionId={}", command.executionId(), e);
            fail(command.executionId(), new ChatFailure(ChatMessage.Status.FAILED, "AI_PROCESSING_ERROR"));
        }
    }

    private void processInternal(ChatProcessingCommand command) {
        // 파이프라인은 트랜잭션 밖에서 돌아 session을 지연 로딩하면 LazyInitializationException이 난다
        ChatMessage inputMessage =
                chatMessageRepository.findByIdWithSession(command.inputMessageId()).orElse(null);
        if (inputMessage == null) {
            fail(command.executionId(), new ChatFailure(ChatMessage.Status.FAILED, "MESSAGE_NOT_FOUND"));
            return;
        }

        ChatSession session = inputMessage.getSession();
        if (session == null && command.sessionId() != null) {
            session = chatSessionRepository.findById(command.sessionId()).orElse(null);
        }

        String rawContent = command.content() != null ? command.content().trim() : "";

        if (session != null && session.getStatus() == ChatSession.Status.NEED_CLARIFICATION) {
            handleClarificationReply(command, session, rawContent);
            return;
        }

        IntentRouteResponse routing = queryRoutingService.route(inputMessage, buildRoutingContext(command));
        QueryRouting.Intent intent = routing != null && routing.intent() != null
                ? routing.intent()
                : QueryRouting.Intent.UNKNOWN;

        switch (intent) {
            case STORE -> handleStoreIntent(command, routing, rawContent);
            case BOTH -> handleBothIntent(command, routing, rawContent);
            case UNKNOWN -> handleUnknownIntent(command);
            case FAQ -> handleFaqOrGeneralIntent(command, routing, rawContent);
        }
    }

    // Context 조립 실패로 분류 자체가 막히면 안 되므로 질문만으로 라우팅하도록 떨어뜨린다
    private ChatContext buildRoutingContext(ChatProcessingCommand command) {
        try {
            return chatContextBuilder.build(command, ROUTING_CONTEXT_TOKEN_BUDGET);
        } catch (Exception e) {
            log.warn("[파이프라인] 라우팅 Context 조립 실패, 현재 질문만으로 분류합니다: executionId={}, reason={}",
                    command.executionId(), e.getMessage());
            return null;
        }
    }

    private void handleClarificationReply(ChatProcessingCommand command, ChatSession session, String rawContent) {
        Long sessionId = session != null ? session.getSessionId() : command.sessionId();
        if (sessionId == null) {
            log.warn("[파이프라인] 되묻기 응답의 세션을 확인할 수 없습니다: executionId={}", command.executionId());
            fail(command.executionId(), new ChatFailure(ChatMessage.Status.FAILED, "SESSION_NOT_FOUND"));
            return;
        }

        FollowUpRouteResponse followUp = queryRoutingService.analyzeFollowUp(sessionId, rawContent);
        ConsultService consultService = consultServiceProvider.getIfAvailable();

        // 대기 중인 상담이 없을 때 임의의 상담을 갱신하지 않도록 바로 매장 안내로 끝낸다
        if (consultService == null || !followUp.hasTarget()) {
            completeStoreResult(command, resolveLocation(followUp, rawContent), List.of());
            return;
        }

        Map<String, Condition> updates = toConsultUpdates(followUp);

        // 상태 충돌이나 저장 오류를 매장 안내로 감추면 실패가 성공처럼 보이므로 실행 실패로 전달한다
        ConsultService.PreparationResult prep = consultService.prepareTurn(
                sessionId,
                followUp.consultRequestId(),
                Purpose.NEARBY_STORE,
                updates,
                resolveLocationStatus(followUp));

        if (prep == null) {
            completeStoreResult(command, resolveLocation(followUp, rawContent), List.of());
            return;
        }

        // 이미 보낸 되묻기 질문은 그대로 두고, 정정된 조건만 저장한다
        if (prep.waitingForReply()) {
            if (prep.prepared() != null) {
                consultService.persistWaitingChanges(prep);
            }
            log.info("[파이프라인] 되묻기 응답 대기 유지: executionId={}, pendingMessageId={}",
                    command.executionId(), prep.pendingMessageId());
            ChatExecutionState state = chatExecutionService.completeWithoutOutput(command.executionId());
            chatStreamPublisher.publishCompleted(command.executionId(), state);
            return;
        }

        if (prep.prepared() == null || prep.prepared().decision() == null) {
            completeStoreResult(command, resolveLocation(followUp, rawContent), List.of());
            return;
        }

        DialogueDecision decision = prep.prepared().decision();
        String answeredField = resolveAnsweredField(followUp);
        Long answerMessageId = answeredField != null ? command.inputMessageId() : null;

        switch (decision.action()) {
            case ASK -> {
                // asked_message_id를 남기려면 되묻기 메시지를 만든 뒤에 저장해야 한다
                ChatExecutionState asked = chatExecutionService.askClarification(
                        command.executionId(), messageOr(decision, LOCATION_ASK_MESSAGE));
                consultService.persist(
                        prep.prepared(),
                        new MessageLinks(asked.outputMessage().messageId(), answerMessageId, answeredField));
                notifyCompleted(command.executionId(), asked, messageOr(decision, LOCATION_ASK_MESSAGE));
            }
            // 조건 저장이 실패하면 사용자에게 성공 답변이 나가지 않도록 저장을 먼저 한다
            case ALTERNATIVE_GUIDANCE -> {
                consultService.persist(
                        prep.prepared(), new MessageLinks(null, answerMessageId, answeredField));
                ChatExecutionState guided = completeAnswer(
                        command.executionId(),
                        new ChatAnswer(
                                ChatMessage.MessageType.ANSWER,
                                messageOr(decision, LOCATION_DECLINED_MESSAGE),
                                ChatMessage.AnswerBasis.OUT_OF_SCOPE,
                                Collections.emptyList(),
                                null),
                        "");
                completeConsults(command, List.of(followUp.consultRequestId()), guided);
            }
            // 사용자 원문("강남역이요") 대신 상담 모듈이 정규화한 값을 써야 안내 문구가 자연스럽다
            case PROCEED -> {
                consultService.persist(
                        prep.prepared(), new MessageLinks(null, answerMessageId, answeredField));
                completeStoreResult(
                        command, resolveLocation(decision, followUp, rawContent), List.of(followUp.consultRequestId()));
            }
        }
    }

    // 상담이 되묻기 질문과 답변 메시지를 이어 붙일 수 있도록 방금 답한 조건 이름을 고른다
    private String resolveAnsweredField(FollowUpRouteResponse followUp) {
        if (!followUp.conditions().isEmpty()) {
            return followUp.conditions().keySet().iterator().next();
        }
        if (!followUp.declinedKeys().isEmpty()) {
            return followUp.declinedKeys().iterator().next();
        }
        return null;
    }

    // 상담 모듈이 Map을 직접 받는 진입점이 생기기 전까지 prepareTurn 시그니처에 맞춰 변환한다
    private Map<String, Condition> toConsultUpdates(FollowUpRouteResponse followUp) {
        Map<String, Condition> updates = new LinkedHashMap<>();
        followUp.conditions().forEach((key, value) -> updates.put(key, Condition.filled(value)));
        followUp.declinedKeys().forEach(key -> updates.put(key, Condition.declined()));
        return updates;
    }

    private LocationStatus resolveLocationStatus(FollowUpRouteResponse followUp) {
        if (followUp.declinedKeys().contains(FollowUpRouteResponse.LOCATION_KEY)) {
            return LocationStatus.DECLINED;
        }
        return followUp.conditions().containsKey(FollowUpRouteResponse.LOCATION_KEY)
                ? LocationStatus.AVAILABLE
                : LocationStatus.MISSING;
    }

    private String resolveLocation(FollowUpRouteResponse followUp, String rawContent) {
        String location = followUp.conditions().get(FollowUpRouteResponse.LOCATION_KEY);
        if (location != null && !location.isBlank()) {
            return location;
        }
        return !rawContent.isBlank() ? rawContent : DEFAULT_LOCATION;
    }

    private String resolveLocation(DialogueDecision decision, FollowUpRouteResponse followUp, String rawContent) {
        Condition location = decision.conditions().get(FollowUpRouteResponse.LOCATION_KEY);
        if (location != null && location.status() == ConditionStatus.FILLED) {
            return location.value();
        }
        return resolveLocation(followUp, rawContent);
    }

    private String messageOr(DialogueDecision decision, String fallback) {
        return (decision.message() != null && !decision.message().isBlank()) ? decision.message() : fallback;
    }

    private void handleBothIntent(ChatProcessingCommand command, IntentRouteResponse routing, String rawContent) {
        // 빈 입력으로 검색하면 묻지 않은 FAQ가 근거로 붙는다
        if (rawContent.isBlank()) {
            answerEmptyQuestion(command);
            return;
        }

        String faqQueryText = rawContent;
        if (routing != null && routing.subQueries() != null) {
            faqQueryText = routing.subQueries().stream()
                    .filter(sq -> sq.intent() == ConsultRequest.Intent.FAQ)
                    .map(IntentRouteResponse.IntentSubQueryResponse::queryText)
                    .findFirst()
                    .orElse(rawContent);
        }

        Map<String, String> extractedConditions = routing != null && routing.extractedConditions() != null
                ? routing.extractedConditions()
                : Collections.emptyMap();

        List<FaqSearchResponse> searchResults = searchFaq(faqQueryText);

        startAnswer(command.executionId());

        ChatStreamRelay relay = new ChatStreamRelay(command.executionId(), chatStreamPublisher);
        AnswerResult answerResult = generateAnswer(command, rawContent, searchResults, extractedConditions, relay);
        String faqAnswerText = answerTextOr(answerResult);
        ChatMessage.AnswerBasis answerBasis = answerBasisOf(answerResult);

        String location = extractedConditions.get(FollowUpRouteResponse.LOCATION_KEY);

        if (location != null && !location.isBlank()) {
            Map<String, Object> storeInfo = Map.of(
                    "name", location + " 직영점",
                    "address", location + " 인근",
                    "phone", STORE_PHONE
            );

            String combinedContent = faqAnswerText + "\n\n[매장 안내]\n" + location + " 인근에서 방문 가능한 매장 검색 결과입니다.";
            ChatAnswer answer = new ChatAnswer(
                    ChatMessage.MessageType.ANSWER,
                    combinedContent,
                    answerBasis,
                    List.of("영업시간 문의", "매장 방문 예약"),
                    List.of(storeInfo)
            );
            ChatExecutionState completed = completeAnswer(command.executionId(), answer, relay.relayed());
            completeConsults(
                    command,
                    consultRequestIdsOf(routing, ConsultRequest.Intent.FAQ, ConsultRequest.Intent.STORE),
                    completed);
        } else {
            String combinedContent = faqAnswerText + "\n\n[매장 안내]\n가까운 매장 방문을 원하시면 지역(역 이름이나 동네)을 알려주세요.";
            ChatAnswer answer = new ChatAnswer(
                    ChatMessage.MessageType.ANSWER,
                    combinedContent,
                    answerBasis,
                    List.of("가까운 매장 찾기", "고객센터 연결"),
                    null
            );
            ChatExecutionState completed = completeAnswer(command.executionId(), answer, relay.relayed());
            // 매장 부분은 지역을 묻는 안내로 끝나 아직 답하지 않았으므로 FAQ 상담만 닫는다
            completeConsults(command, consultRequestIdsOf(routing, ConsultRequest.Intent.FAQ), completed);
        }
    }

    private void handleUnknownIntent(ChatProcessingCommand command) {
        startAnswer(command.executionId());

        ChatAnswer answer = new ChatAnswer(
                ChatMessage.MessageType.ANSWER,
                "안녕하세요! LG U+ 통신 고객센터 AI 상담 어시스턴트입니다. 요금제, 부가서비스, 결합할인 등 통신 서비스 관련 문의나 가까운 대리점/매장 찾기 질문을 입력해 주시면 친절히 안내해 드리겠습니다.",
                ChatMessage.AnswerBasis.OUT_OF_SCOPE,
                List.of("5G 요금제 추천", "가까운 매장 찾기"),
                null
        );

        completeAnswer(command.executionId(), answer, "");
    }

    private void handleStoreIntent(ChatProcessingCommand command, IntentRouteResponse routing, String rawContent) {
        String location = routing.extractedConditions() != null
                ? routing.extractedConditions().get(FollowUpRouteResponse.LOCATION_KEY)
                : null;

        if (location == null || location.isBlank()) {
            ChatExecutionState asked =
                    chatExecutionService.askClarification(command.executionId(), LOCATION_ASK_MESSAGE);
            notifyCompleted(command.executionId(), asked, LOCATION_ASK_MESSAGE);
            return;
        }

        completeStoreResult(command, location, consultRequestIdsOf(routing, ConsultRequest.Intent.STORE));
    }

    private void completeStoreResult(ChatProcessingCommand command, String location, List<Long> consultRequestIds) {
        Map<String, Object> storeInfo = Map.of(
                "name", location + " 직영점",
                "address", location + " 인근",
                "phone", STORE_PHONE
        );

        ChatAnswer answer = new ChatAnswer(
                ChatMessage.MessageType.STORE_RESULT,
                location + " 인근 매장 검색 결과입니다.",
                ChatMessage.AnswerBasis.GROUNDED,
                List.of("영업시간 문의", "주차 가능 여부"),
                List.of(storeInfo)
        );

        ChatExecutionState completed = completeAnswer(command.executionId(), answer, "");
        completeConsults(command, consultRequestIds, completed);
    }

    private void handleFaqOrGeneralIntent(
            ChatProcessingCommand command, IntentRouteResponse routing, String rawContent) {
        // 빈 입력을 "요금제 안내"로 대신 검색하면 묻지 않은 FAQ가 근거로 붙는다
        if (rawContent.isBlank()) {
            answerEmptyQuestion(command);
            return;
        }

        List<FaqSearchResponse> searchResults = searchFaq(rawContent);

        startAnswer(command.executionId());

        ChatStreamRelay relay = new ChatStreamRelay(command.executionId(), chatStreamPublisher);
        AnswerResult answerResult = generateAnswer(command, rawContent, searchResults, Collections.emptyMap(), relay);

        ChatAnswer answer = new ChatAnswer(
                ChatMessage.MessageType.ANSWER,
                answerTextOr(answerResult),
                answerBasisOf(answerResult),
                List.of("관련 요금제 보기", "고객센터 연결"),
                null
        );

        ChatExecutionState completed = completeAnswer(command.executionId(), answer, relay.relayed());
        completeConsults(command, consultRequestIdsOf(routing, ConsultRequest.Intent.FAQ), completed);
    }

    private void answerEmptyQuestion(ChatProcessingCommand command) {
        startAnswer(command.executionId());

        ChatAnswer answer = new ChatAnswer(
                ChatMessage.MessageType.ANSWER,
                EMPTY_QUESTION_MESSAGE,
                ChatMessage.AnswerBasis.OUT_OF_SCOPE,
                List.of("5G 요금제 추천", "가까운 매장 찾기"),
                null
        );

        completeAnswer(command.executionId(), answer, "");
    }

    private void startAnswer(Long executionId) {
        ChatExecutionState started = chatExecutionService.startAnswer(executionId);
        chatStreamPublisher.publishStarted(executionId, started);
    }

    // 저장을 먼저 끝내고 알린다. 모델이 넘기지 않은 나머지(고정 문구, 덧붙인 매장 안내, 생성 실패 폴백)만 이어 보내고,
    // 넘긴 문구와 저장한 답변이 어긋나면(AnswerGuard가 잘라낸 경우 등) 화면은 완료 알림 뒤 저장된 답변으로 맞춘다
    private ChatExecutionState completeAnswer(Long executionId, ChatAnswer answer, String relayed) {
        ChatExecutionState completed = chatExecutionService.completeAnswer(executionId, answer);
        String content = answer.content();
        String rest = content.startsWith(relayed) ? content.substring(relayed.length()) : "";
        notifyCompleted(executionId, completed, rest);
        return completed;
    }

    // 이미 저장이 끝났으니 구독자가 떠났어도 멈출 이유가 없어 전송 결과는 보지 않는다
    private void notifyCompleted(Long executionId, ChatExecutionState state, String rest) {
        if (!rest.isEmpty()) {
            chatStreamPublisher.publishToken(executionId, rest);
        }
        chatStreamPublisher.publishCompleted(executionId, state);
    }

    // 이번 턴에 답한 상담만 닫는다. 답변은 이미 저장·전송됐으므로 완료 기록이 실패해도 실행을 실패로 되돌리지 않는다
    private void completeConsults(
            ChatProcessingCommand command, List<Long> consultRequestIds, ChatExecutionState completed) {
        if (consultRequestIds.isEmpty() || command.sessionId() == null
                || completed == null || completed.outputMessage() == null) {
            return;
        }
        ConsultService consultService = consultServiceProvider.getIfAvailable();
        if (consultService == null) {
            return;
        }
        Long finalMessageId = completed.outputMessage().messageId();
        for (Long consultRequestId : consultRequestIds) {
            try {
                consultService.complete(command.sessionId(), consultRequestId, finalMessageId);
            } catch (Exception e) {
                log.warn("[파이프라인] 상담 완료 처리 실패: sessionId={}, consultRequestId={}",
                        command.sessionId(), consultRequestId, e);
            }
        }
    }

    private List<Long> consultRequestIdsOf(IntentRouteResponse routing, ConsultRequest.Intent... intents) {
        if (routing == null || routing.subQueries() == null) {
            return List.of();
        }
        List<ConsultRequest.Intent> answered = List.of(intents);
        return routing.subQueries().stream()
                .filter(subQuery -> answered.contains(subQuery.intent()))
                .map(IntentRouteResponse.IntentSubQueryResponse::consultRequestId)
                .filter(Objects::nonNull)
                .toList();
    }

    // 실패 기록이 안 되더라도(타임아웃 정리로 이미 끝난 실행 등) 구독은 닫아야 화면이 SSE 타임아웃까지 기다리지 않는다
    private void fail(Long executionId, ChatFailure failure) {
        try {
            chatExecutionService.fail(executionId, failure);
        } catch (Exception e) {
            log.info("[파이프라인] 실행 실패 기록 생략: {}", e.getMessage());
        } finally {
            chatStreamPublisher.publishFailed(executionId, failure);
        }
    }

    private List<FaqSearchResponse> searchFaq(String queryText) {
        try {
            List<FaqSearchResponse> results =
                    faqSearchService.search(new FaqSearchRequest(queryText, FAQ_SEARCH_TOP_K));
            return results != null ? results : Collections.emptyList();
        } catch (Exception e) {
            log.warn("[파이프라인] FAQ 검색 실패, 검색 결과 없이 답변 생성: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private AnswerResult generateAnswer(
            ChatProcessingCommand command,
            String userQuery,
            List<FaqSearchResponse> searchResults,
            Map<String, String> conditions,
            ChatStreamRelay relay) {

        // 분해된 서브질의가 아닌 사용자 원문을 넘긴다
        AnswerRequest answerRequest = AnswerRequest.builder()
                .executionId(command.executionId())
                .userQuery(userQuery)
                .conditions(conditions)
                .searchResults(searchResults)
                .build();

        try {
            return answerGenerator.generate(answerRequest, relay);
        } catch (LlmStreamCancelledException e) {
            // 구독자가 떠나 멈춘 생성을 근거 없음 답변으로 완료하면 이력과 품질 지표가 틀어지므로 취소로 올려보낸다
            throw e;
        } catch (Exception e) {
            log.error("[파이프라인] RAG 답변 생성 실패: {}", e.getMessage());
            return null;
        }
    }

    // 생성 실패 시 answerBasisOf()가 NO_EVIDENCE를 반환하므로, 폴백 문구도 "확인됨"을
    // 암시하지 않는 rag 모듈의 근거 없음 문구를 그대로 써서 근거·문구 불일치를 막는다
    private String answerTextOr(AnswerResult answerResult) {
        return (answerResult != null && answerResult.answer() != null && !answerResult.answer().isBlank())
                ? answerResult.answer()
                : AnswerPromptTemplates.NO_EVIDENCE_ANSWER;
    }

    // 생성이 실패해 기본 문구로 내려가면 근거가 없으므로 GROUNDED로 저장하지 않는다
    private ChatMessage.AnswerBasis answerBasisOf(AnswerResult answerResult) {
        if (answerResult == null || answerResult.answerBasis() == null
                || answerResult.answer() == null || answerResult.answer().isBlank()) {
            return ChatMessage.AnswerBasis.NO_EVIDENCE;
        }
        return answerResult.answerBasis();
    }
}
