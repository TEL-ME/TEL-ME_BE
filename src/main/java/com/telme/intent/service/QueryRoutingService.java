package com.telme.intent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ConsultationQuestion;
import com.telme.consult.dto.ClarificationReask;
import com.telme.consult.entity.ConsultCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.dto.res.FollowUpRouteResponse.Disposition;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
import com.telme.intent.dto.res.LlmFollowUpPayload;
import com.telme.intent.dto.res.LlmFollowUpPayload.ConditionPayload;
import com.telme.intent.dto.res.LlmFollowUpPayload.ResponseType;
import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.dto.res.LlmRequestInventoryPayload;
import com.telme.intent.dto.res.LlmRequestInventoryPayload.Decision;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.exception.IntentErrorCode;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.llm.exception.LlmErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

@Service
@Slf4j
public class QueryRoutingService {
    private static final int MAX_CONSULT_SUB_QUERIES = 3;

    // LLM이 정의 밖의 키를 만들어내도 여기서 걸러진다
    private static final Set<String> KNOWN_CONDITION_KEYS = Set.of(
        FollowUpRouteResponse.LOCATION_KEY, FollowUpRouteResponse.SERVICE_TYPE_KEY);

    // 상담 모듈의 DialogueInput.Condition이 255자를 넘기면 예외를 던진다
    private static final int MAX_CONDITION_VALUE_LENGTH = 255;
    private static final BigDecimal MIN_USABLE_CONFIDENCE = new BigDecimal("0.5");
    private static final Set<String> SERVICE_TYPES = Set.of(
        "NEW_LINE", "PORT_IN", "NAME_CHANGE", "USIM_REISSUE");
    private static final Pattern NUMBER = Pattern.compile(
        "\\d+(?:[.,]\\d+)?\\s*(?:TB|GB|MB|KB|G|테라바이트|기가바이트|메가바이트|테라|기가|메가|만원|원|개월|달|년|일|시간|분|회|개|%)?",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACE = Pattern.compile(
        "(?<![가-힣A-Za-z0-9])[가-힣A-Za-z0-9]{2,14}(?:역|읍|면|공항|터미널|사거리)");
    private static final Pattern CONDITIONAL_ENDING = Pattern.compile("(?:하면|려면|되면|으면)$");
    private static final Pattern ADMIN_AREA_BEFORE = Pattern.compile("(?:시|군|구)\\s+$");
    private static final Pattern LOCATION_PARTICLE_AFTER = Pattern.compile("^(?:에서|에|으로|로)");
    private static final Pattern CONTEXT_REFERENCE = Pattern.compile(
        "그거|그건|거기|그\\s*지역|아까|앞서|그때|이거|저거|방금|그러면|그럼");
    private static final Pattern ACTION_REQUEST = Pattern.compile("싶|하려고|할래");
    // 매장 하위 질문의 원문 구간에 있어야 하는 장소 찾기·방문 표현. 지명은 PLACE와 원문 근거가 있는 지역 조건으로 따로 확인한다.
    // "어디서", "어디로"는 "어디서 확인해요", "어디로 문의해요"처럼, "가면"은 "해외 가면"처럼 FAQ에도 쓰여 넣지 않는다.
    private static final Pattern STORE_LOOKUP = Pattern.compile(
        "매장|대리점|지점|직영점|판매점|역점|근처|가까운|가까이|주변|인근|동네|곳|위치|방문|가야|찾아가|들러|들를");
    private static final Pattern SECTION_WORD = Pattern.compile("[가-힣A-Za-z0-9]+");
    private static final String PREVIOUS_UTTERANCE_HEADER = "[대상을 확인할 이전 고객 발언]\n";
    private static final String CURRENT_QUESTION_HEADER = "\n[현재 후속 질문]\n";
    private static final Set<String> SECTION_FILLERS = Set.of(
            "비교", "차이", "차이점", "안내", "설명", "및", "그리고", "또", "와", "과", "하고", "랑");

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final QueryRoutingRepository queryRoutingRepository;
    private final ConsultRequestRepository consultRequestRepository;
    private final RuleBasedRoutingFallback ruleBasedFallback;
    private final IntentConverter intentConverter;
    private final TransactionTemplate transactionTemplate;
    private final UnknownFaqRecheck unknownFaqRecheck;
    // 끄면 의도 분류가 나눈 FAQ 하위 질문 개수를 그대로 쓰고 하위 질문마다 원문 근거만 확인한다
    @Value("${telme.routing.request-inventory-enabled:true}")
    private boolean requestInventoryEnabled = true;

    public QueryRoutingService(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            QueryRoutingRepository queryRoutingRepository,
            ConsultRequestRepository consultRequestRepository,
            RuleBasedRoutingFallback ruleBasedFallback,
            IntentConverter intentConverter,
            TransactionTemplate transactionTemplate) {
        this(llmClient, objectMapper, queryRoutingRepository, consultRequestRepository, ruleBasedFallback,
                intentConverter, transactionTemplate, UnknownFaqRecheck.none());
    }

    @Autowired
    public QueryRoutingService(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            QueryRoutingRepository queryRoutingRepository,
            ConsultRequestRepository consultRequestRepository,
            RuleBasedRoutingFallback ruleBasedFallback,
            IntentConverter intentConverter,
            TransactionTemplate transactionTemplate,
            ObjectProvider<UnknownFaqRecheck> unknownFaqRecheck) {
        this(llmClient, objectMapper, queryRoutingRepository, consultRequestRepository, ruleBasedFallback,
                intentConverter, transactionTemplate, unknownFaqRecheck.getIfAvailable(UnknownFaqRecheck::none));
    }

    public QueryRoutingService(
            LlmClient llmClient,
            ObjectMapper objectMapper,
            QueryRoutingRepository queryRoutingRepository,
            ConsultRequestRepository consultRequestRepository,
            RuleBasedRoutingFallback ruleBasedFallback,
            IntentConverter intentConverter,
            TransactionTemplate transactionTemplate,
            UnknownFaqRecheck unknownFaqRecheck) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.queryRoutingRepository = queryRoutingRepository;
        this.consultRequestRepository = consultRequestRepository;
        this.ruleBasedFallback = ruleBasedFallback;
        this.intentConverter = intentConverter;
        this.transactionTemplate = transactionTemplate;
        this.unknownFaqRecheck = Objects.requireNonNull(unknownFaqRecheck);
    }

    // Context 없이 질문만으로 분류한다. 멀티턴 지시어를 풀어야 하면 아래 오버로드를 쓴다
    public IntentRouteResponse route(ChatMessage userMessage) {
        return route(userMessage, null);
    }

    public IntentRouteResponse route(ChatMessage userMessage, ChatContext context) {
        return route(userMessage, context, false);
    }

    // 상담 연결용 진입점. 하위 요청별 검색이 가능한 FAQ와 매장 복합 질문을 유지한다.
    public IntentRouteResponse routeSingleConsult(ChatMessage userMessage, ChatContext context) {
        return routeSingleConsult(userMessage, context, (Long) null);
    }

    // executionId가 있어야 LLM 호출 기록이 남는다. 채팅 실행에서는 이 오버로드를 쓴다
    public IntentRouteResponse routeSingleConsult(ChatMessage userMessage, ChatContext context, Long executionId) {
        return route(userMessage, context, true, null, executionId);
    }

    private IntentRouteResponse route(
            ChatMessage userMessage, ChatContext context, boolean singleConsultOnly) {
        return route(userMessage, context, singleConsultOnly, null, null);
    }

    // 원본 메시지는 그대로 저장하고, 고객 원문을 연결한 문맥 질문으로 분류한다.
    public IntentRouteResponse routeSingleConsult(
            ChatMessage userMessage, ChatContext context, String resolvedQuestion) {
        return routeSingleConsult(userMessage, context, resolvedQuestion, null);
    }

    public IntentRouteResponse routeSingleConsult(
            ChatMessage userMessage, ChatContext context, String resolvedQuestion, Long executionId) {
        return route(userMessage, context, true, resolvedQuestion, executionId);
    }

    // 검색이 실패하면 모델 분류(UNKNOWN)를 그대로 둔다
    private boolean hasFaqForUnknown(String question) {
        try {
            return unknownFaqRecheck.hasFaq(question);
        } catch (RuntimeException e) {
            log.warn("[라우팅] UNKNOWN 재확인 검색 실패, 모델 분류를 유지합니다: {}", e.getMessage());
            return false;
        }
    }

    private IntentRouteResponse route(ChatMessage userMessage, ChatContext context, boolean singleConsultOnly,
            String resolvedQuestion, Long executionId) {
        if (userMessage == null) {
            throw new IllegalArgumentException("사용자 메시지는 필수입니다.");
        }

        if (userMessage.getMessageId() != null) {
            Optional<QueryRouting> existing = queryRoutingRepository.findByMessage_MessageId(userMessage.getMessageId());
            if (existing.isPresent()) {
                log.info("[라우팅] 이미 라우팅된 메시지입니다. 기존 결과를 반환합니다: messageId={}", userMessage.getMessageId());
                IntentRouteResponse result =
                    runInTransaction(() -> buildExistingResponse(existing.get(), userMessage.getMessageId()));
                ensureSingleConsultSupported(result, singleConsultOnly, userMessage.getContent());
                return result;
            }
        }

        boolean restored = resolvedQuestion != null && context != null;
        String currentQuestion = ConsultationQuestion.extract(userMessage.getContent());
        String question = restored ? resolvedQuestion.strip()
                : ConsultationQuestion.extract(resolvedQuestion != null ? resolvedQuestion : currentQuestion);
        question = question == null ? "" : question.strip();
        String promptQuestion = restored ? currentQuestion : question;
        // 요청 개수·인용 근거는 현재 발화로만 판정한다. 복원된 이전 발언은 검색 대상의 근거로만 쓴다.
        String analysisQuestion = RoutingQuestionNormalizer.normalize(promptQuestion);
        String previousSubject = restored ? previousSubject(question, promptQuestion) : "";

        if (analysisQuestion.isBlank()) {
            log.info("[라우팅] 질문 내용이 비어 있어 UNKNOWN으로 처리합니다.");
            LlmRoutingPayload fallbackPayload = ruleBasedFallback.classify(question);
            ensureSingleConsultSupported(fallbackPayload, singleConsultOnly, question);
            return executeInTransaction(userMessage, fallbackPayload, QueryRouting.Method.RULE);
        }

        LlmRoutingPayload payload;
        QueryRouting.Method method;

        try {
            LlmRequest request = LlmRequest.builder()
                .executionId(executionId)
                .taskType(TaskType.ROUTING)
                .systemPrompt(RoutingPromptTemplates.ROUTING_SYSTEM_PROMPT)
                // 복원 출처는 문맥으로 전달하고, 분해 대상에는 현재 발화만 넣는다.
                .userPrompt(RoutingPromptTemplates.routingUserPrompt(context, analysisQuestion))
                .format(ResponseFormat.JSON)
                .temperature(0.0)
                .maxTokens(768)
                .promptVersion("routing-request-v4-single-search")
                .build();

            String json = llmClient.generate(request);
            if (json == null || json.isBlank()) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }

            try {
                payload = objectMapper.readValue(json, LlmRoutingPayload.class);
            } catch (JsonProcessingException e) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }

            if (payload == null) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }

            payload = normalizeLlmPayload(payload, question, context,
                    restored ? promptQuestion.strip() : question);

            method = QueryRouting.Method.LLM;

            log.info("[라우팅] LLM 분류 완료 -> intent={}, confidence={}",
                    payload.intent(), payload.confidence());

        } catch (GeneralException e) {
            if (e.getErrorCode() instanceof LlmErrorCode
                    || e.getErrorCode() == IntentErrorCode.LLM_CONNECTION_FAILED
                    || e.getErrorCode() == IntentErrorCode.LLM_RESPONSE_PARSE_FAILED) {
                log.warn("[라우팅] LLM 오류 또는 파싱 실패, Rule Fallback으로 전환: {}", e.getMessage());
                payload = ruleBasedFallback.classify(question);
                method = QueryRouting.Method.RULE;
            } else {
                throw e;
            }
        } catch (RestClientException e) {
            log.warn("[라우팅] LLM 호출 실패, Rule Fallback으로 전환: {}", e.getMessage());
            payload = ruleBasedFallback.classify(question);
            method = QueryRouting.Method.RULE;
        }

        // 모델이 직접 UNKNOWN이라고 한 질문만 다시 본다. 낮은 신뢰도로 보류하는 아래 경우는 되묻기 안내를 유지한다
        if (method == QueryRouting.Method.LLM
                && payload.intent() == QueryRouting.Intent.UNKNOWN
                && hasFaqForUnknown(question)) {
            log.info("[라우팅] UNKNOWN이지만 FAQ 검색 결과가 있어 FAQ로 보정합니다: messageId={}",
                    userMessage.getMessageId());
            payload = new LlmRoutingPayload(
                    QueryRouting.Intent.FAQ, payload.confidence(), question,
                    Collections.emptyMap(),
                    List.of(new LlmRoutingPayload.SubQueryPayload(
                            (short) 1, ConsultRequest.Intent.FAQ, question, Collections.emptyMap())));
            method = QueryRouting.Method.SEARCH;
        }

        boolean llmRouting = method == QueryRouting.Method.LLM;
        if (method == QueryRouting.Method.LLM
                && payload.intent() != QueryRouting.Intent.UNKNOWN
                && payload.confidence().compareTo(MIN_USABLE_CONFIDENCE) < 0) {
            log.info("[라우팅] 낮은 분류 신뢰도로 상담을 보류합니다: messageId={}, confidence={}",
                    userMessage.getMessageId(), payload.confidence());
            payload = new LlmRoutingPayload(
                    QueryRouting.Intent.UNKNOWN, payload.confidence(), question,
                    Collections.emptyMap(), Collections.emptyList());
        }

        if (method == QueryRouting.Method.LLM
                && payload.intent() == QueryRouting.Intent.STORE
                && RoutingIntentCorrection.isGeneralStorePolicy(question, context,
                        payload.extractedConditions().get(FollowUpRouteResponse.LOCATION_KEY))) {
            log.info("[라우팅] 일반 매장 운영 질문을 FAQ로 보정합니다: messageId={}",
                    userMessage.getMessageId());
            payload = new LlmRoutingPayload(
                    QueryRouting.Intent.FAQ, payload.confidence(), question,
                    Collections.emptyMap(),
                    List.of(new LlmRoutingPayload.SubQueryPayload(
                            (short) 1, ConsultRequest.Intent.FAQ, question, Collections.emptyMap())));
            method = QueryRouting.Method.RULE;
        }

        LlmRequestInventoryPayload inventory = null;
        boolean trustSplit = llmRouting && !requestInventoryEnabled
                && payload.intent() == QueryRouting.Intent.FAQ && payload.subQueries().size() > 1;
        if (requestInventoryEnabled && llmRouting
                && payload.intent() == QueryRouting.Intent.FAQ && payload.subQueries().size() > 1) {
            inventory = requestInventory(analysisQuestion, executionId);
            if (inventory.decision() != Decision.MULTIPLE) {
                // 단일 문의로 묶어도 재검색 후보는 보존한다. 사용 직전 원문·복원 문맥과 의미를 검증한다.
                String wholeQuery = restored
                    ? groundedSingleFaqQuery(payload.refinedQuery(), promptQuestion.strip(), previousSubject,
                            executionId)
                    : payload.refinedQuery() == null || payload.refinedQuery().isBlank()
                            ? question : payload.refinedQuery().strip();
                payload = new LlmRoutingPayload(QueryRouting.Intent.FAQ, payload.confidence(), wholeQuery,
                        payload.extractedConditions(), List.of(new LlmRoutingPayload.SubQueryPayload(
                                (short) 1, ConsultRequest.Intent.FAQ, wholeQuery, Collections.emptyMap())));
                method = QueryRouting.Method.RULE;
            }
        }

        if (method == QueryRouting.Method.LLM
                && payload.intent() == QueryRouting.Intent.FAQ
                && payload.subQueries().size() > 1
                && ComparisonQuestionPolicy.isStandaloneComparison(question, context)) {
            log.info("[라우팅] 단독 비교 질문의 FAQ 하위 질문을 한 건으로 보정합니다: messageId={}",
                    userMessage.getMessageId());
            payload = new LlmRoutingPayload(
                    QueryRouting.Intent.FAQ, payload.confidence(), question,
                    payload.extractedConditions(),
                    List.of(new LlmRoutingPayload.SubQueryPayload(
                            (short) 1, ConsultRequest.Intent.FAQ, question, Collections.emptyMap())));
            method = QueryRouting.Method.RULE;
        }

        if (method == QueryRouting.Method.LLM && payload.intent() == QueryRouting.Intent.FAQ) {
            var comparison = ComparisonQuestionPolicy.trailingComparison(question, context);
            if (comparison != null) {
                payload = preserveTrailingComparison(payload, comparison);
                method = QueryRouting.Method.RULE;
            }
        }

        if (inventory != null && inventory.decision() == Decision.MULTIPLE) {
            if (inventory.requestCount() != payload.subQueries().size()) {
                throw new UnsupportedCompoundQuestionException("질문 분해와 독립 요청 개수가 일치하지 않습니다.");
            }
            IndependentQuestionPolicy.validate(payload, analysisQuestion);
        } else if (trustSplit) {
            IndependentQuestionPolicy.validate(payload, analysisQuestion);
        }
        validateFaqSearchQueries(payload, question, analysisQuestion);
        payload = preserveFaqQuestionMeaning(
                payload, analysisQuestion, question, previousSubject, context, restored, executionId);
        ensureSingleConsultSupported(payload, singleConsultOnly, question);
        IntentRouteResponse result = executeInTransaction(userMessage, payload, method);
        ensureSingleConsultSupported(result, singleConsultOnly, question);
        return result;
    }

    private LlmRoutingPayload preserveTrailingComparison(
            LlmRoutingPayload payload, ComparisonQuestionPolicy.TrailingComparison comparison) {
        List<LlmRoutingPayload.SubQueryPayload> independent = new ArrayList<>(
                Collections.nCopies(comparison.precedingRequests().size(), null));
        int comparisonParts = 0;
        for (var sub : payload.subQueries()) {
            if (sub.queryText() == null || sub.queryText().isBlank()) {
                throw new UnsupportedCompoundQuestionException("독립 질문과 비교 요청의 내용이 필요합니다.");
            }
            int precedingIndex = -1;
            for (int index = 0; index < comparison.precedingRequests().size(); index++) {
                if (matchesSection(sub, comparison.precedingRequests().get(index))) {
                    if (precedingIndex >= 0) {
                        throw new UnsupportedCompoundQuestionException("독립 질문의 소속을 안전하게 구분할 수 없습니다.");
                    }
                    precedingIndex = index;
                }
            }
            boolean comparing = matchesSection(sub, comparison.comparison());
            // 양쪽에 걸치거나 어느 쪽에도 속하지 않으면 질문을 임의로 소비하지 않는다.
            if ((precedingIndex >= 0) == comparing) {
                throw new UnsupportedCompoundQuestionException("독립 질문과 비교 요청을 안전하게 구분할 수 없습니다.");
            }
            if (comparing) {
                comparisonParts++;
            } else {
                if (independent.get(precedingIndex) != null) {
                    throw new UnsupportedCompoundQuestionException("독립 질문이 중복으로 분해됐습니다.");
                }
                independent.set(precedingIndex, new LlmRoutingPayload.SubQueryPayload((short) (precedingIndex + 1),
                        sub.intent(), sub.queryText(), sub.conditions(), sub.requestQuote()));
            }
        }
        if (comparisonParts == 0 || independent.contains(null)) {
            throw new UnsupportedCompoundQuestionException("독립 질문과 비교 요청이 모두 필요합니다.");
        }
        independent.add(new LlmRoutingPayload.SubQueryPayload((short) (independent.size() + 1),
                ConsultRequest.Intent.FAQ, comparison.comparison(), Collections.emptyMap(), comparison.comparison()));
        return new LlmRoutingPayload(payload.intent(), payload.confidence(), payload.refinedQuery(),
                payload.extractedConditions(), independent);
    }

    private boolean matchesSection(String query, String section) {
        String normalized = section.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        List<String> words = SECTION_WORD.matcher(query).results()
                .map(match -> match.group().toLowerCase(Locale.ROOT))
                .map(word -> word.length() > 2 ? word.replaceFirst("[은는이가을를의도]$", "") : word)
                .filter(word -> !SECTION_FILLERS.contains(word))
                .toList();
        return !words.isEmpty() && words.stream().allMatch(normalized::contains);
    }

    private boolean matchesSection(LlmRoutingPayload.SubQueryPayload sub, String section) {
        String quote = RoutingQuestionNormalizer.quoteKey(sub.requestQuote());
        if (!quote.isBlank()) {
            return RoutingQuestionNormalizer.quoteKey(section).contains(quote);
        }
        return matchesSection(sub.queryText(), section);
    }

    private LlmRequestInventoryPayload requestInventory(String question, Long executionId) {
        LlmRequest request = LlmRequest.builder().executionId(executionId).taskType(TaskType.ROUTING)
                .systemPrompt(RoutingPromptTemplates.REQUEST_INVENTORY_PROMPT)
                .userPrompt(RoutingQuestionNormalizer.normalize(question))
                .format(ResponseFormat.JSON).temperature(0.0).maxTokens(256)
                .promptVersion("routing-request-inventory-v4").build();
        try {
            String response = llmClient.generate(request);
            if (response == null || response.isBlank()) {
                throw new UnsupportedCompoundQuestionException("독립 요청 판정이 비어 있습니다.");
            }
            var root = objectMapper.readTree(response);
            if (root == null || !root.isObject() || root.size() != 2
                    || !root.path("decision").isTextual() || !root.path("requestCount").isIntegralNumber()
                    || !root.path("requestCount").canConvertToInt()) {
                throw new UnsupportedCompoundQuestionException("독립 요청 판정 형식이 올바르지 않습니다.");
            }
            Decision decision = Decision.valueOf(root.path("decision").asText());
            int count = root.path("requestCount").intValue();
            if (decision != Decision.MULTIPLE) {
                // 단일 판정의 개수는 코드가 1로 정한다. 모델이 부연 항목을 세어도 새 요청으로 쓰지 않는다.
                count = 1;
            } else if (count < 2) {
                throw new UnsupportedCompoundQuestionException("복합 요청 판정에는 두 개 이상의 요청이 필요합니다.");
            }
            return new LlmRequestInventoryPayload(decision, count);
        } catch (JsonProcessingException | GeneralException | RestClientException | IllegalArgumentException failed) {
            log.warn("[라우팅] 독립 요청 재확인에 실패해 질문 분해를 보류합니다: errorType={}",
                    failed.getClass().getSimpleName());
            throw new UnsupportedCompoundQuestionException("독립 요청을 안전하게 판정할 수 없습니다.");
        }
    }

    private String previousSubject(String resolved, String current) {
        int split = resolved.lastIndexOf(CURRENT_QUESTION_HEADER);
        if (!resolved.startsWith(PREVIOUS_UTTERANCE_HEADER) || split <= PREVIOUS_UTTERANCE_HEADER.length()
                || !RoutingQuestionNormalizer.quoteKey(ConsultationQuestion.extract(
                        resolved.substring(split + CURRENT_QUESTION_HEADER.length())))
                        .equals(RoutingQuestionNormalizer.quoteKey(current))) {
            return "";
        }
        return resolved.substring(PREVIOUS_UTTERANCE_HEADER.length(), split).strip();
    }

    private String groundedSingleFaqQuery(String candidate, String current, String previousSubject,
            Long executionId) {
        if (candidate == null || candidate.isBlank() || previousSubject.isBlank()
                || RoutingQuestionNormalizer.quoteKey(candidate)
                        .equals(RoutingQuestionNormalizer.quoteKey(current))) {
            return current;
        }
        var sub = new LlmRoutingPayload.SubQueryPayload(
                (short) 1, ConsultRequest.Intent.FAQ, candidate, Collections.emptyMap(), current);
        var single = new LlmRoutingPayload(QueryRouting.Intent.FAQ, BigDecimal.ONE, candidate,
                Collections.emptyMap(), List.of(sub));
        if (!removeUngroundedQueryWords(single, sub, current, previousSubject).equals(candidate)) {
            return current;
        }
        return findUnsafeFaqQueries(single, current, previousSubject, false, executionId).getFirst() ? current : candidate;
    }

    /**
     * 단일 FAQ의 원문 검색이 0건일 때 재검색에 쓸 문장을 고른다. 원문 검색이 성공하면 부르지 않아 검증 비용이 없다.
     * 의미가 같으면 후보를, 업무 수행 의사를 밝힌 질문은 원문 근거가 있는 단어만 남긴 문장을 돌려준다.
     * 쓸 문장이 없으면 원문을 그대로 돌려주며, 호출자는 이때 재검색하지 않는다.
     * executionId는 검증 모델 호출을 채팅 실행에 기록하기 위한 값이다.
     */
    public String refinedSearchQuery(String question, String candidate, Long executionId) {
        if (question == null || question.isBlank() || candidate == null || candidate.isBlank()
                || RoutingQuestionNormalizer.quoteKey(candidate).equals(RoutingQuestionNormalizer.quoteKey(question))) {
            return question;
        }
        // 원문에 없는 업무 대상을 추측해 넣은 문장은 모델 의미 검증이 통과시키는 경우가 있어 먼저 거절한다.
        if (SearchTopicPolicy.introducesNewTopic(question, candidate)) {
            log.info("[라우팅] 원문에 없는 업무 대상이 생긴 재검색 문장을 쓰지 않습니다.");
            return question;
        }
        var sub = new LlmRoutingPayload.SubQueryPayload(
                (short) 1, ConsultRequest.Intent.FAQ, candidate.strip(), Collections.emptyMap(), question);
        var single = new LlmRoutingPayload(QueryRouting.Intent.FAQ, BigDecimal.ONE, candidate.strip(),
                Collections.emptyMap(), List.of(sub));
        // 동의어는 원문의 단어 포함 여부로 검사하지 않는다. 업무와 조건의 의미를 검증한다.
        if (!findUnsafeFaqQueries(single, question, "", true, executionId).getFirst()) {
            return candidate.strip();
        }
        if (!ACTION_REQUEST.matcher(question).find()) {
            return question;
        }
        // 원문에 없는 단어를 뺀 문장도 조건이나 부정을 빠뜨릴 수 있어 같은 의미 검증을 거친다.
        String grounded = removeUngroundedQueryWords(single, sub, question, "");
        if (grounded == null || grounded.isBlank() || grounded.equals(candidate.strip())
                || RoutingQuestionNormalizer.quoteKey(grounded).equals(RoutingQuestionNormalizer.quoteKey(question))) {
            return question;
        }
        var groundedSub = new LlmRoutingPayload.SubQueryPayload(
                (short) 1, ConsultRequest.Intent.FAQ, grounded, Collections.emptyMap(), question);
        var groundedSingle = new LlmRoutingPayload(QueryRouting.Intent.FAQ, BigDecimal.ONE, grounded,
                Collections.emptyMap(), List.of(groundedSub));
        return findUnsafeFaqQueries(groundedSingle, question, "", true, executionId).getFirst() ? question : grounded;
    }

    // current는 현재 발화, question은 복원된 발언을 포함한 검색 대상 근거다.
    private LlmRoutingPayload preserveFaqQuestionMeaning(LlmRoutingPayload payload, String current,
            String question, String previousSubject, ChatContext context, boolean restored, Long executionId) {
        if (payload.intent() != QueryRouting.Intent.FAQ || payload.subQueries().isEmpty()) {
            return payload;
        }
        if (payload.subQueries().size() == 1) {
            // 원문은 메시지에 보존하고, 검색 문장은 후보로만 저장한다.
            // 후보는 원문 검색이 0건일 때 refinedSearchQuery로 의미를 검증한 뒤에만 검색에 쓴다.
            if (!restored && (context == null || !CONTEXT_REFERENCE.matcher(question).find())) {
                var original = payload.subQueries().getFirst();
                String candidate = original.queryText();
                String searchQuery = candidate != null && !candidate.isBlank()
                        && !RoutingQuestionNormalizer.quoteKey(candidate)
                                .equals(RoutingQuestionNormalizer.quoteKey(question))
                        && !ComparisonQuestionPolicy.isStandaloneComparison(question, context)
                        ? candidate.strip() : question;
                return new LlmRoutingPayload(payload.intent(), payload.confidence(), searchQuery,
                        payload.extractedConditions(), List.of(new LlmRoutingPayload.SubQueryPayload(
                                original.order(), original.intent(), searchQuery,
                                original.conditions(), original.requestQuote())));
            }
            return payload;
        }

        List<LlmRoutingPayload.SubQueryPayload> groundedQuotes = payload.subQueries().stream()
                .map(sub -> new LlmRoutingPayload.SubQueryPayload(sub.order(), sub.intent(),
                        sub.queryText(), sub.conditions(), IndependentQuestionPolicy.verifiedQuote(sub, current)))
                .toList();
        payload = new LlmRoutingPayload(payload.intent(), payload.confidence(), payload.refinedQuery(),
                payload.extractedConditions(), groundedQuotes);

        List<Boolean> unsafe = findUnsafeFaqQueries(payload, current, previousSubject, false, executionId);
        List<LlmRoutingPayload.SubQueryPayload> safe = new ArrayList<>();
        for (int index = 0; index < payload.subQueries().size(); index++) {
            var sub = payload.subQueries().get(index);
            String queryText = sub.queryText();
            if (unsafe.get(index)) {
                // 의미 검증이 위험하다고 본 검색어는 공통 단어가 있어도 신뢰하지 않는다.
                String grounded = removeUngroundedQueryWords(payload, sub, current, previousSubject);
                queryText = RoutingQuestionNormalizer.quoteKey(grounded).equals(
                        RoutingQuestionNormalizer.quoteKey(sub.requestQuote()))
                        ? grounded : sub.requestQuote();
                if (!queryText.equals(sub.queryText())) {
                    log.info("[라우팅] 원문과 다른 FAQ 검색어를 인용구로 대체합니다: order={}", sub.order());
                }
            } else {
                queryText = removeUngroundedQueryWords(payload, sub, current, previousSubject);
            }
            safe.add(new LlmRoutingPayload.SubQueryPayload(
                    sub.order(), sub.intent(), queryText, sub.conditions(), sub.requestQuote()));
        }
        // 복원용 내부 표식과 이전 발언은 라우팅 결과에 저장하지 않는다.
        return new LlmRoutingPayload(payload.intent(), payload.confidence(), restored ? current : question,
                payload.extractedConditions(), safe);
    }

    private String removeUngroundedQueryWords(LlmRoutingPayload payload,
            LlmRoutingPayload.SubQueryPayload sub, String current, String previousSubject) {
        String quote = payload.subQueries().size() > 1
                ? RoutingQuestionNormalizer.quoteKey(sub.requestQuote()) : "";
        String original = RoutingQuestionNormalizer.quoteKey(current);
        String restoredContext = RoutingQuestionNormalizer.quoteKey(previousSubject);
        if (!quote.isBlank()
                && !RoutingQuestionNormalizer.quoteKey(sub.queryText()).contains(quote)) {
            return sub.requestQuote();
        }
        Matcher words = SECTION_WORD.matcher(sub.queryText());
        List<String> grounded = new ArrayList<>();
        boolean removed = false;
        while (words.find()) {
            String word = RoutingQuestionNormalizer.quoteKey(words.group());
            if (quote.contains(word)) {
                grounded.add(words.group());
                continue;
            }
            // 자신의 인용구 밖의 표현은 다른 요청의 전용 구간이 아니라면 사용할 수 있다.
            if (original.contains(word) && (wordOutsideOtherQuotes(payload, sub, original, word)
                    || payload.subQueries().stream().allMatch(candidate ->
                            RoutingQuestionNormalizer.quoteKey(candidate.queryText()).contains(word)))) {
                grounded.add(words.group());
                continue;
            }
            // 현재 발화에 없는 대상은 복원된 이전 발언에 있을 때만 쓴다. 현재 요청끼리의 구간 소유와는 무관하다.
            if (!original.contains(word) && restoredContext.contains(word)) {
                grounded.add(words.group());
                continue;
            }
            if (word.equals("방법") && (current.contains("어떻게")
                    || ACTION_REQUEST.matcher(current).find())) {
                grounded.add(words.group());
                continue;
            }
            removed = true;
        }
        if (!removed) {
            return sub.queryText();
        }
        String corrected = String.join(" ", grounded);
        if (!RoutingQuestionNormalizer.quoteKey(corrected).contains(quote)) {
            return sub.requestQuote();
        }
        log.info("[라우팅] 원문 근거가 없는 검색어 표현을 제거합니다: order={}", sub.order());
        return corrected;
    }

    private boolean wordOutsideOtherQuotes(LlmRoutingPayload payload,
            LlmRoutingPayload.SubQueryPayload current, String original, String word) {
        StringBuilder unclaimed = new StringBuilder(original);
        for (var other : payload.subQueries()) {
            if (other == current) {
                continue;
            }
            String quote = RoutingQuestionNormalizer.quoteKey(other.requestQuote());
            if (quote.isBlank()) {
                return false;
            }
            int offset = original.indexOf(quote);
            if (offset < 0) {
                return false;
            }
            while (offset >= 0) {
                for (int index = offset; index < offset + quote.length(); index++) {
                    unclaimed.setCharAt(index, ' ');
                }
                offset = original.indexOf(quote, offset + quote.length());
            }
        }
        return unclaimed.toString().contains(word);
    }

    // searchCandidate는 원문 검색 0건 뒤 재검색 후보 검증이다. 이때만 조건을 하나씩 대조하는 단일 검색어 프롬프트를 쓴다.
    // 복합 하위 질문과 문맥 복원 단일 질문(groundedSingleFaqQuery)은 기존 프롬프트를 그대로 쓴다(PR #160 리뷰)
    private List<Boolean> findUnsafeFaqQueries(
            LlmRoutingPayload payload, String current, String previousSubject, boolean searchCandidate,
            Long executionId) {
        String prompt;
        try {
            // Map.of는 JVM마다 키 순서가 달라 같은 입력에도 판정이 바뀐다. 키 순서를 고정한다
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("question", current);
            input.put("previousSubject", previousSubject);
            input.put("subQueries", payload.subQueries().stream().map(sub -> {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("quote", sub.requestQuote() == null ? "" : sub.requestQuote());
                item.put("query", sub.queryText() == null ? "" : sub.queryText());
                return item;
            }).toList());
            prompt = objectMapper.writeValueAsString(input);
            // 복합 하위 질문에 쓰면 멀쩡한 검색어까지 막는다. 재검색 후보는 항상 한 건이다
            boolean single = searchCandidate && payload.subQueries().size() == 1;
            LlmRequest request = LlmRequest.builder().executionId(executionId).taskType(TaskType.ROUTING)
                    .systemPrompt(single ? RoutingPromptTemplates.FAQ_SINGLE_QUERY_FAITHFULNESS_PROMPT
                            : RoutingPromptTemplates.FAQ_QUERY_FAITHFULNESS_PROMPT)
                    .userPrompt(prompt).format(ResponseFormat.JSON)
                    .temperature(0.0).maxTokens(256)
                    .promptVersion(single ? "routing-faq-faithfulness-single-v3"
                            : "routing-faq-faithfulness-v2-single-search").build();
            String response = llmClient.generate(request);
            var root = objectMapper.readTree(response);
            var results = root == null ? null : root.path("unsafe");
            if (root == null || !root.isObject() || root.size() != 1
                    || results == null || !results.isArray()
                    || results.size() != payload.subQueries().size()) {
                throw new IllegalArgumentException("FAQ 검색어 검증 응답 형식 오류");
            }
            List<Boolean> unsafe = new ArrayList<>();
            for (var result : results) {
                if (!result.isBoolean()) {
                    throw new IllegalArgumentException("FAQ 검색어 검증 결과가 불리언이 아닙니다.");
                }
                unsafe.add(result.booleanValue());
            }
            return unsafe;
        } catch (JsonProcessingException | RuntimeException failed) {
            log.warn("[라우팅] FAQ 검색어 검증 실패로 원문 인용구를 사용합니다: errorType={}",
                    failed.getClass().getSimpleName());
            return Collections.nCopies(payload.subQueries().size(), true);
        }
    }

    private void validateFaqSearchQueries(
            LlmRoutingPayload payload, String question, String currentQuestion) {
        if (payload.intent() != QueryRouting.Intent.FAQ || payload.subQueries().size() <= 1) {
            return;
        }
        for (var sub : payload.subQueries()) {
            if (sub.queryText() == null || sub.queryText().isBlank() || sub.queryText().equals(question)
                    || RoutingQuestionNormalizer.quoteKey(sub.queryText()).equals(currentQuestion)) {
                throw new UnsupportedCompoundQuestionException(
                        "FAQ 하위 질문을 원문과 분리해 안전하게 검색할 수 없습니다.");
            }
        }
    }

    private LlmRoutingPayload normalizeLlmPayload(
            LlmRoutingPayload payload, String question, ChatContext context, String safeFallback) {
        if (payload.confidence().compareTo(BigDecimal.ZERO) < 0
                || payload.confidence().compareTo(BigDecimal.ONE) > 0
                || payload.subQueries().size() > Short.MAX_VALUE) {
            throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
        }

        List<LlmRoutingPayload.SubQueryPayload> normalized = new ArrayList<>();
        boolean hasFaq = false;
        boolean hasStore = false;
        boolean allSubIntentsGiven = true;
        for (LlmRoutingPayload.SubQueryPayload sub : payload.subQueries()) {
            if (sub == null) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }
            ConsultRequest.Intent intent = sub.intent();
            allSubIntentsGiven &= intent != null;
            if (intent == null) {
                intent = switch (payload.intent()) {
                    case FAQ -> ConsultRequest.Intent.FAQ;
                    case STORE -> ConsultRequest.Intent.STORE;
                    default -> null;
                };
            }
            if (intent == null) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }
            hasFaq |= intent == ConsultRequest.Intent.FAQ;
            hasStore |= intent == ConsultRequest.Intent.STORE;
            Map<String, String> conditions = intent == ConsultRequest.Intent.STORE
                    ? validConditions(sub.conditions(), question, context)
                    : Collections.emptyMap();
            normalized.add(new LlmRoutingPayload.SubQueryPayload(
                    (short) (normalized.size() + 1), intent, sub.queryText(), conditions, sub.requestQuote()));
        }

        QueryRouting.Intent topIntent = payload.intent();
        String refinedSource = payload.refinedQuery();
        boolean ungroundedBoth = false;
        if (topIntent == QueryRouting.Intent.BOTH && hasFaq && hasStore && allSubIntentsGiven) {
            // 모델이 처음부터 BOTH로 적은 출력도 하위 질문마다 원문에 서로 다른 근거가 있어야 한다.
            try {
                IndependentQuestionPolicy.validateParts(normalized, question);
            } catch (UnsupportedCompoundQuestionException unsupported) {
                // 매장을 말한 질문은 요청 일부를 버리지 않고 나눠 보내기 안내로 넘긴다.
                if (RuleBasedRoutingFallback.mentionsStore(question)) {
                    throw unsupported;
                }
                ungroundedBoth = true;
            }
        }
        if ((topIntent == QueryRouting.Intent.FAQ || ungroundedBoth) && hasFaq && hasStore && allSubIntentsGiven
                && !RuleBasedRoutingFallback.mentionsStore(question)) {
            // "어디서 볼 수 있나요"처럼 매장을 말하지 않은 질문에 모델이 매장 찾기를 덧붙인 출력이다.
            // 고객이 요청하지 않은 매장 하위 질문만 빼고 FAQ로 처리한다.
            // BOTH 출력은 매장 요청에 원문 근거가 없을 때만 뺀다. "근처 개통 가능한 곳"처럼 매장이라 말하지 않은 요청도 있다.
            log.info("[라우팅] 원문에 매장 언급이 없어 모델이 만든 매장 하위 질문을 제외합니다.");
            normalized.removeIf(sub -> sub.intent() == ConsultRequest.Intent.STORE);
            for (int index = 0; index < normalized.size(); index++) {
                var sub = normalized.get(index);
                normalized.set(index, new LlmRoutingPayload.SubQueryPayload((short) (index + 1), sub.intent(),
                        sub.queryText(), sub.conditions(), sub.requestQuote()));
            }
            hasStore = false;
            topIntent = QueryRouting.Intent.FAQ;
            // 모델의 전체 정제 질문에도 매장 찾기가 섞여 있어 원문을 쓴다.
            refinedSource = question;
        }
        if ((topIntent == QueryRouting.Intent.FAQ || topIntent == QueryRouting.Intent.STORE)
                && hasFaq && hasStore && allSubIntentsGiven) {
            // 하위 질문은 FAQ와 매장으로 나눴는데 전체 의도만 한쪽으로 적은 출력이다. 버리면 질문 일부가 사라진다.
            // 하위 질문마다 원문에 서로 다른 근거가 있을 때만 BOTH로 계산하고, 아니면 나눠 보내기 안내로 넘긴다.
            IndependentQuestionPolicy.validateParts(normalized, question);
            log.info("[라우팅] 하위 질문에 FAQ와 매장이 함께 있어 전체 의도를 BOTH로 계산합니다: modelIntent={}",
                    topIntent);
            topIntent = QueryRouting.Intent.BOTH;
        }
        if (topIntent == QueryRouting.Intent.BOTH && hasFaq && hasStore && allSubIntentsGiven) {
            // 인용이 원문에 있어도 장소를 찾는 요청이라는 뜻은 아니다. "유심 재발급 비용 알려줘"를 매장 요청으로 둔 출력은
            // 그 요청을 버리지 않고 FAQ로 처리한다. 이후 FAQ 복합 질문의 요청 수와 검색어 검증을 그대로 거친다.
            for (int index = 0; index < normalized.size(); index++) {
                var sub = normalized.get(index);
                String section = sub.intent() == ConsultRequest.Intent.STORE
                        ? IndependentQuestionPolicy.ownSectionText(normalized, sub, question) : null;
                // "서울 강남구"처럼 역 이름 꼴이 아닌 지역도, 모델의 지역 조건이 이 요청의 원문 구간에 있으면 장소 요청으로 본다.
                boolean namedPlace = section != null && validConditions(sub.conditions(), section, context)
                        .containsKey(FollowUpRouteResponse.LOCATION_KEY);
                if (section != null && !namedPlace && !STORE_LOOKUP.matcher(section).find()
                        && tokens(PLACE, section).isEmpty()) {
                    log.info("[라우팅] 장소를 찾는 표현이 없는 매장 하위 질문을 FAQ로 처리합니다: order={}", sub.order());
                    normalized.set(index, new LlmRoutingPayload.SubQueryPayload(sub.order(),
                            ConsultRequest.Intent.FAQ, section, Collections.emptyMap(), sub.requestQuote()));
                }
            }
            hasStore = normalized.stream().anyMatch(sub -> sub.intent() == ConsultRequest.Intent.STORE);
            if (!hasStore) {
                topIntent = QueryRouting.Intent.FAQ;
                refinedSource = question;
            }
        }
        Map<String, String> extracted = topIntent == QueryRouting.Intent.UNKNOWN
                ? Collections.emptyMap()
                : validConditions(payload.extractedConditions(), question, context);
        extracted = withRuleServiceType(topIntent, extracted, question);

        boolean inconsistent = switch (topIntent) {
            case FAQ -> hasStore;
            case STORE -> hasFaq;
            case BOTH -> !normalized.isEmpty() && (!hasFaq || !hasStore);
            case UNKNOWN -> !normalized.isEmpty();
        };
        if (inconsistent) {
            throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
        }
        long storeCount = normalized.stream()
                .filter(sub -> sub.intent() == ConsultRequest.Intent.STORE)
                .count();
        String refined = safeQueryText(refinedSource, question, context, true, safeFallback);
        List<LlmRoutingPayload.SubQueryPayload> safeSubQueries = new ArrayList<>();
        for (LlmRoutingPayload.SubQueryPayload sub : normalized) {
            String queryText = safeQueryText(
                    sub.queryText(), question, context,
                    topIntent == QueryRouting.Intent.FAQ && normalized.size() == 1,
                    safeFallback);
            // 모델 검색어는 다른 하위 질문의 낱말을 섞어 쓰기도 해, 이 요청의 구간과 공통 구간을 근거로 쓴다.
            String ownSection = normalized.size() > 1
                    ? IndependentQuestionPolicy.withoutOtherQuotes(normalized, sub, question) : null;
            if (sub.intent() == ConsultRequest.Intent.FAQ && topIntent == QueryRouting.Intent.BOTH
                    && queryText != null && (ownSection == null
                        || SearchTopicPolicy.introducesNewTopic(groundingText(ownSection, context), queryText))) {
                // FAQ만 있는 복합 질문의 의미 검증처럼, 다른 요청의 업무로 바뀐 검색어는 이 요청의 원문 구간으로 되돌린다.
                // 구간을 확정하지 못하면 검사를 건너뛰지 않고 원문 근거만 쓴다.
                log.info("[라우팅] 복합 질문의 FAQ 검색어를 원문 근거로 확인하지 못해 원문 구간을 사용합니다: order={}",
                        sub.order());
                String section = IndependentQuestionPolicy.ownSectionText(normalized, sub, question);
                queryText = section != null ? section : IndependentQuestionPolicy.verifiedQuote(sub, question);
            }
            Map<String, String> conditions = sub.conditions();
            if (sub.intent() == ConsultRequest.Intent.STORE && storeCount == 1
                    && topIntent == QueryRouting.Intent.STORE) {
                Map<String, String> merged = new LinkedHashMap<>(extracted);
                merged.putAll(conditions);
                conditions = merged;
            }
            if (sub.intent() == ConsultRequest.Intent.STORE && normalized.size() > 1) {
                // FAQ 또는 다른 매장 요청의 업무와 지역을 이 요청에 섞지 않는다.
                // 원문 구간을 확정하지 못하면 모델 검색어로 되돌아가지 않고 조건을 비워 되묻는다.
                conditions = validConditions(conditions, ownSection != null ? ownSection : "", context);
                // 모델이 업무를 비워도 이 요청의 원문 인용 구간에 업무가 있으면 채운다. 다른 요청의 원문은 보지 않는다.
                // 인용이 없으면 채우지 않는다. 검색어는 모델이 다시 쓴 문장이라 업무 근거로 쓰지 않는다.
                String section = sub.requestQuote() == null || sub.requestQuote().isBlank()
                        ? null : IndependentQuestionPolicy.ownSectionText(normalized, sub, question);
                if (section != null && !section.isBlank()) {
                    conditions = withRuleServiceType(conditions, section);
                }
            }
            safeSubQueries.add(new LlmRoutingPayload.SubQueryPayload(
                    sub.order(), sub.intent(), queryText, conditions, sub.requestQuote()));
        }
        return new LlmRoutingPayload(
                topIntent, payload.confidence(), refined, extracted, safeSubQueries);
    }

    private String safeQueryText(
            String candidate, String question, ChatContext context, boolean preserveNumbers,
            String safeFallback) {
        if (candidate == null || candidate.isBlank()) {
            return candidate;
        }
        if (hasUnsupportedToken(NUMBER, candidate, referenceText(NUMBER, question, context))
                || hasUnsupportedToken(PLACE, candidate, referenceText(PLACE, question, context))
                || (preserveNumbers && hasUnsupportedToken(NUMBER, question, candidate))) {
            log.info("[라우팅] 검색 질문의 수치 또는 지역이 원문과 맞지 않아 원문을 사용합니다.");
            return safeFallback;
        }
        return candidate.strip();
    }

    private boolean hasUnsupportedToken(Pattern pattern, String source, String reference) {
        Set<String> referenceTokens = tokens(pattern, reference);
        Set<String> sourceTokens = tokens(pattern, source, pattern == PLACE);
        if (pattern == PLACE) {
            sourceTokens.removeIf(token -> containsPlacePhrase(reference, token));
        }
        return !referenceTokens.containsAll(sourceTokens);
    }

    private boolean containsPlacePhrase(String text, String placePhrase) {
        if (text == null) {
            return false;
        }
        Pattern phrase = Pattern.compile("(?<![가-힣A-Za-z0-9])" + Pattern.quote(placePhrase));
        return phrase.matcher(text).find();
    }

    private Set<String> tokens(Pattern pattern, String text) {
        return tokens(pattern, text, false);
    }

    private Set<String> tokens(Pattern pattern, String text, boolean candidatePlace) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (pattern == PLACE && !looksLikePlace(value, text, matcher.start(), matcher.end(), candidatePlace)) {
                continue;
            }
            values.add(pattern == NUMBER
                    ? value.replace(",", "").replaceAll("\\s+", "").toUpperCase(Locale.ROOT)
                    : value);
        }
        return values;
    }

    private boolean looksLikePlace(String value, String text, int start, int end, boolean candidatePlace) {
        if (value.endsWith("지역") || value.endsWith("영역") || value.endsWith("내역")) {
            return false;
        }
        // 모델이 만든 검색어는 더 넓게 검사해 단독 '면' 지명도 근거 없이 추가되지 못하게 한다.
        if (candidatePlace && value.endsWith("면")) {
            return !CONDITIONAL_ENDING.matcher(value).find();
        }
        // '면'은 조건 어미와 겹치므로 행정구역 표기나 바로 붙은 장소 조사로만 판별한다.
        return !value.endsWith("면")
                || ADMIN_AREA_BEFORE.matcher(text.substring(0, start)).find()
                || LOCATION_PARTICLE_AFTER.matcher(text.substring(end)).find();
    }

    private String referenceText(Pattern pattern, String question, ChatContext context) {
        // 현재 질문에 새 조건이 명시되면 이전 대화의 같은 종류 조건은 검증 근거로 사용하지 않는다.
        return tokens(pattern, question).isEmpty() ? groundingText(question, context) : question;
    }

    private String groundingText(String question, ChatContext context) {
        if (context == null || !CONTEXT_REFERENCE.matcher(question).find()) {
            return question;
        }
        return question + " " + context.summarySources().stream()
                        .filter(item -> item.role() == ChatMessage.Role.USER)
                        .map(item -> item.content() == null ? "" : item.content())
                        .collect(Collectors.joining(" ")) + " "
                + context.history().stream()
                        .filter(item -> item.role() == ChatMessage.Role.USER)
                        .map(item -> item.content() == null ? "" : item.content())
                        .collect(Collectors.joining(" "));
    }

    private Map<String, String> validConditions(
            Map<String, String> conditions, String question, ChatContext context) {
        if (conditions == null || conditions.isEmpty()) {
            return Collections.emptyMap();
        }
        String locationEvidence = referenceText(PLACE, question, context);
        String serviceTypeEvidence = ruleBasedFallback.hasServiceTypeMention(question)
                ? question : groundingText(question, context);
        Map<String, String> valid = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : conditions.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().strip();
            if (!KNOWN_CONDITION_KEYS.contains(key)
                    || value.isBlank()
                    || value.length() > MAX_CONDITION_VALUE_LENGTH) {
                continue;
            }
            if (FollowUpRouteResponse.SERVICE_TYPE_KEY.equals(key)
                    && (!SERVICE_TYPES.contains(value)
                        || !ruleBasedFallback.matchesServiceType(value, serviceTypeEvidence))) {
                continue;
            }
            if (FollowUpRouteResponse.LOCATION_KEY.equals(key) && RelativeLocation.isOnlyRelative(value)) {
                continue;
            }
            if (FollowUpRouteResponse.LOCATION_KEY.equals(key)
                    && (!locationEvidence.contains(value)
                        || (!tokens(PLACE, value).isEmpty()
                            && !tokens(PLACE, locationEvidence).containsAll(tokens(PLACE, value))))) {
                continue;
            }
            valid.put(key, value);
        }
        return valid;
    }

    // route()와 달리 새 상담 요청을 만들지 않는다. 조건 반영(PENDING -> FILLED)은 상담 도메인이 한다
    public FollowUpRouteResponse analyzeFollowUp(Long sessionId, String followUpText) {
        return analyzeFollowUp(sessionId, followUpText, null);
    }

    // executionId가 있어야 LLM 호출 기록이 남는다. 채팅 실행에서는 이 오버로드를 쓴다
    public FollowUpRouteResponse analyzeFollowUp(Long sessionId, String followUpText, Long executionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("세션 ID는 필수입니다.");
        }

        WaitingConsult waiting = runInTransaction(() -> loadWaitingConsult(sessionId));
        if (waiting == null) {
            log.info("[후속분석] 되묻기 대기 중인 상담 요청이 없습니다: sessionId={}", sessionId);
            return FollowUpRouteResponse.noTarget();
        }

        String reply = followUpText != null ? ConsultationQuestion.extract(followUpText).strip() : "";
        if (reply.isBlank()) {
            log.info("[후속분석] 후속 답변이 비어 있어 조건 없이 반환합니다: consultRequestId={}", waiting.consultRequestId());
            return new FollowUpRouteResponse(
                waiting.consultRequestId(), Collections.emptyMap(), Collections.emptySet(),
                QueryRouting.Method.RULE);
        }

        // 버튼을 그대로 누르거나 업무 이름만 짧게 답한 것은 모델 없이 읽는다. 네 가지뿐이라 LLM이 판단할 여지가 없다
        String pickedServiceType = ruleBasedFallback.serviceTypeOfShortReply(reply);
        if (pickedServiceType != null
                && waiting.pendingKeys().contains(FollowUpRouteResponse.SERVICE_TYPE_KEY)) {
            log.info("[후속분석] 업무 선택지를 코드로 읽었습니다: consultRequestId={}, serviceType={}",
                    waiting.consultRequestId(), pickedServiceType);
            return new FollowUpRouteResponse(
                waiting.consultRequestId(),
                Map.of(FollowUpRouteResponse.SERVICE_TYPE_KEY, pickedServiceType),
                Collections.emptySet(),
                QueryRouting.Method.RULE);
        }

        // 지역 질문 중 업무 버튼 문구는 같은 상담의 업무 조건 (모델은 새 질문으로 읽음)
        if (pickedServiceType != null
                && waiting.pendingKeys().contains(FollowUpRouteResponse.LOCATION_KEY)) {
            log.info("[후속분석] 지역을 묻는 중 업무 선택지가 와서 업무 조건으로 반영합니다: consultRequestId={}, serviceType={}",
                    waiting.consultRequestId(), pickedServiceType);
            return new FollowUpRouteResponse(
                waiting.consultRequestId(),
                Map.of(FollowUpRouteResponse.SERVICE_TYPE_KEY, pickedServiceType),
                Collections.emptySet(),
                QueryRouting.Method.RULE);
        }

        LlmFollowUpPayload payload;
        QueryRouting.Method method;

        try {
            payload = requestFollowUpAnalysis(reply, waiting, executionId);
            method = QueryRouting.Method.LLM;
            log.info("[후속분석] LLM 조건 추출 완료: consultRequestId={}, 추출 건수={}",
                    waiting.consultRequestId(), payload.conditions().size());
        } catch (GeneralException e) {
            if (e.getErrorCode() instanceof LlmErrorCode
                    || e.getErrorCode() == IntentErrorCode.LLM_CONNECTION_FAILED
                    || e.getErrorCode() == IntentErrorCode.LLM_RESPONSE_PARSE_FAILED) {
                log.warn("[후속분석] LLM 오류 또는 파싱 실패, Rule Fallback으로 전환: {}", e.getMessage());
                payload = ruleBasedFallback.classifyFollowUp(reply, waiting.pendingKeys());
                method = QueryRouting.Method.RULE;
            } else {
                throw e;
            }
        } catch (RestClientException e) {
            log.warn("[후속분석] LLM 호출 실패, Rule Fallback으로 전환: {}", e.getMessage());
            payload = ruleBasedFallback.classifyFollowUp(reply, waiting.pendingKeys());
            method = QueryRouting.Method.RULE;
        }

        ExtractedConditions extracted = toExtractedConditions(payload, waiting.pendingKeys());

        // LLM이 명시적으로 새 질문이라고 판단한 결과는 규칙이 조건 답변으로 덮지 않는다.
        // 그 외에 조건이 하나도 안 잡힌 경우에만 되묻기 반복을 막기 위해 규칙으로 한 번 더 시도한다.
        if (extracted.isEmpty()
                && method == QueryRouting.Method.LLM
                && payload.responseType() != ResponseType.NEW_QUESTION) {
            LlmFollowUpPayload rulePayload =
                ruleBasedFallback.classifyFollowUp(reply, waiting.pendingKeys());
            ExtractedConditions ruleConditions = toExtractedConditions(rulePayload, waiting.pendingKeys());
            if (!ruleConditions.isEmpty()
                    || rulePayload.responseType() == ResponseType.NEW_QUESTION) {
                payload = rulePayload;
                extracted = ruleConditions;
                method = QueryRouting.Method.RULE;
            }
        }

        return new FollowUpRouteResponse(
            waiting.consultRequestId(),
            extracted.values(),
            extracted.declinedKeys(),
            method,
            disposition(payload.responseType(), extracted));
    }

    private Disposition disposition(ResponseType responseType, ExtractedConditions extracted) {
        if (!extracted.isEmpty()) {
            return Disposition.CONDITION_RESPONSE;
        }
        return responseType == ResponseType.NEW_QUESTION
            ? Disposition.NEW_QUESTION
            : Disposition.DEFERRED;
    }

    private void ensureSingleConsultSupported(
            LlmRoutingPayload payload, boolean singleConsultOnly, String originalQuestion) {
        if (!singleConsultOnly || payload == null) {
            return;
        }
        int subQueryCount = payload.subQueries() == null ? 0 : payload.subQueries().size();
        if (payload.intent() == QueryRouting.Intent.BOTH && subQueryCount < 2
                || subQueryCount > 1 && payload.subQueries().stream().anyMatch(sub ->
                sub.queryText() == null || sub.queryText().isBlank()
                        || sub.queryText().strip().equals(originalQuestion.strip()))) {
            throw new UnsupportedCompoundQuestionException(payload.subQueries() == null ? List.of()
                    : payload.subQueries().stream()
                            .map(sub -> new UnsupportedCompoundQuestionException.Part(
                                    sub.intent(), sub.queryText(), sub.conditions()))
                            .toList());
        }
        if (subQueryCount > MAX_CONSULT_SUB_QUERIES) {
            throw new TooManyFaqQuestionsException(MAX_CONSULT_SUB_QUERIES);
        }
    }

    private void ensureSingleConsultSupported(
            IntentRouteResponse response, boolean singleConsultOnly, String originalQuestion) {
        if (!singleConsultOnly || response == null) {
            return;
        }
        int subQueryCount = response.subQueries() == null ? 0 : response.subQueries().size();
        if (response.intent() == QueryRouting.Intent.BOTH && subQueryCount < 2
                || subQueryCount > 1 && response.subQueries().stream().anyMatch(sub ->
                sub.queryText() == null || sub.queryText().isBlank()
                        || sub.queryText().strip().equals(originalQuestion == null ? "" : originalQuestion.strip()))) {
            throw new UnsupportedCompoundQuestionException(response.subQueries() == null ? List.of()
                    : response.subQueries().stream()
                            .map(sub -> new UnsupportedCompoundQuestionException.Part(
                                    sub.intent(), sub.queryText(), sub.conditions()))
                            .toList());
        }
        if (subQueryCount > MAX_CONSULT_SUB_QUERIES) {
            throw new TooManyFaqQuestionsException(MAX_CONSULT_SUB_QUERIES);
        }
    }

    private LlmFollowUpPayload requestFollowUpAnalysis(String reply, WaitingConsult waiting, Long executionId) {
        Set<String> pendingKeys = waiting.pendingKeys();
        LlmRequest request = LlmRequest.builder()
            .executionId(executionId)
            .consultRequestId(waiting.consultRequestId())
            .taskType(TaskType.ROUTING)
            .systemPrompt(RoutingPromptTemplates.FOLLOW_UP_SYSTEM_PROMPT)
            .userPrompt(RoutingPromptTemplates.followUpUserPrompt(pendingKeys, waiting.askedConditions(), reply))
            .format(ResponseFormat.JSON)
            .temperature(0.0)
            .maxTokens(300)
            // 부정하는 답을 값으로, 유심 새로 받기를 재발급으로 읽고, 되물은 질문을 함께 받는 판(TELME-175 4단계)
            .promptVersion("routing-follow-up-v4")
            .build();

        String json = llmClient.generate(request);
        if (json == null || json.isBlank()) {
            throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
        }

        try {
            LlmFollowUpPayload payload = objectMapper.readValue(json, LlmFollowUpPayload.class);
            if (payload == null) {
                throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
            }
            return payload;
        } catch (JsonProcessingException e) {
            throw new GeneralException(IntentErrorCode.LLM_RESPONSE_PARSE_FAILED);
        }
    }

    private WaitingConsult loadWaitingConsult(Long sessionId) {
        return consultRequestRepository
            .findByLatestAskedQuestion(sessionId, ConsultRequest.Status.WAITING_CONDITION)
            .stream()
            .findFirst()
            .map(request -> new WaitingConsult(
                request.getConsultRequestId(), pendingKeysOf(request), askedConditionsOf(request)))
            .orElse(null);
    }

    // 지금 답을 기다리는 조건마다 고객에게 실제로 보낸 질문과 선택지. 복합 질문의 되묻기 메시지는 앞부분에 답변이 있고 질문은 마지막 줄이다
    private Map<String, RoutingPromptTemplates.AskedCondition> askedConditionsOf(ConsultRequest request) {
        Map<String, RoutingPromptTemplates.AskedCondition> asked = new LinkedHashMap<>();
        List<ConsultCondition> conditions = request.getConditions();
        if (conditions == null) {
            return asked;
        }
        for (ConsultCondition condition : conditions) {
            ChatMessage message = condition.getAskedMessage();
            if (condition.getStatus() != ConsultCondition.Status.PENDING || message == null
                    || condition.getAnsweredMessage() != null || condition.getConditionKey() == null
                    || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String question = ConsultationQuestion.extract(ClarificationReask.questionOf(message.getContent()));
            asked.put(condition.getConditionKey(),
                    new RoutingPromptTemplates.AskedCondition(question, optionsOf(message.getFollowUps())));
        }
        return asked;
    }

    private List<String> optionsOf(String followUps) {
        if (followUps == null || followUps.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(followUps,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return List.of();
        }
    }

    // 되묻는 조건을 알 수 없으면 지역으로 본다(현재 DialogueService는 지역만 묻는다)
    // 거절 판정이 이 집합 전체에 적용되므로 KNOWN_CONDITION_KEYS처럼 넓게 잡으면 안 된다
    private Set<String> pendingKeysOf(ConsultRequest request) {
        List<ConsultCondition> conditions = request.getConditions();
        if (conditions == null) {
            return Set.of(FollowUpRouteResponse.LOCATION_KEY);
        }
        Set<String> pending = conditions.stream()
            .filter(condition -> condition.getStatus() == ConsultCondition.Status.PENDING)
            // 한 번에 하나만 묻는다. 아직 질문하지 않은 다음 조건은 이 답의 대상이 아니다.
            .filter(condition -> condition.getAskedMessage() != null)
            .filter(condition -> condition.getAnsweredMessage() == null)
            .map(ConsultCondition::getConditionKey)
            .filter(key -> key != null && !key.isBlank())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return pending.isEmpty() ? Set.of(FollowUpRouteResponse.LOCATION_KEY) : pending;
    }

    // 매장 찾기에서 LLM이 업무를 비우는 경우가 있어(로컬 EXAONE 기준 유심 외 업무), 현재 질문에 업무 표현이 있으면 규칙으로 채운다.
    // 이 규칙은 LLM이 준 업무를 검증할 때와 같다. BOTH는 FAQ 쪽 표현 때문에 매장 업무가 잘못 걸릴 수 있어 채우지 않는다
    private Map<String, String> withRuleServiceType(
            QueryRouting.Intent intent, Map<String, String> extracted, String question) {
        return intent == QueryRouting.Intent.STORE ? withRuleServiceType(extracted, question) : extracted;
    }

    // 업무 조건이 없으면 주어진 원문에서 규칙으로 업무를 채운다.
    private Map<String, String> withRuleServiceType(Map<String, String> extracted, String question) {
        if (extracted.containsKey(FollowUpRouteResponse.SERVICE_TYPE_KEY)) {
            return extracted;
        }
        String serviceType = ruleBasedFallback.serviceTypeOf(question);
        // 업무 표현이 여럿이면 어느 쪽인지 알 수 없다. 모른다는 것과 구분해 되묻지 않도록 표시한다
        if (serviceType == null && !ruleBasedFallback.hasServiceTypeMention(question)) {
            return extracted;
        }
        Map<String, String> filled = new LinkedHashMap<>(extracted);
        filled.put(FollowUpRouteResponse.SERVICE_TYPE_KEY,
                serviceType == null ? FollowUpRouteResponse.SERVICE_TYPE_UNDECIDED : serviceType);
        return filled;
    }

    private ExtractedConditions toExtractedConditions(LlmFollowUpPayload payload, Set<String> pendingKeys) {
        if (payload == null || payload.conditions().isEmpty()) {
            return ExtractedConditions.empty();
        }

        Map<String, String> values = new LinkedHashMap<>();
        Set<String> declinedKeys = new LinkedHashSet<>();

        for (ConditionPayload condition : payload.conditions()) {
            if (condition == null || condition.key() == null || condition.status() == null) {
                continue;
            }

            String key = condition.key().trim();
            // 되묻는 중인 조건은 상담 모듈이 정한다. 매장 밖 조건도 그 목록에 있으면 받는다
            if (!pendingKeys.contains(key)
                    && (!KNOWN_CONDITION_KEYS.contains(key) || askedOnlyOtherConditions(pendingKeys))) {
                log.debug("[후속분석] 되묻지 않은 조건 키를 무시합니다: {}", key);
                continue;
            }

            if (condition.status() == LlmFollowUpPayload.Status.DECLINED) {
                declinedKeys.add(key);
                continue;
            }

            String value = condition.value() != null ? condition.value().trim() : "";
            if (value.isBlank() || value.length() > MAX_CONDITION_VALUE_LENGTH) {
                continue;
            }
            if (FollowUpRouteResponse.LOCATION_KEY.equals(key) && RelativeLocation.isOnlyRelative(value)) {
                log.debug("[후속분석] 기준점만 가리키는 위치 표현은 지역명으로 받지 않습니다");
                continue;
            }
            values.put(key, value);
        }
        return new ExtractedConditions(values, declinedKeys);
    }

    // "미납 요금이 있으신가요?"의 답이 지역일 수는 없는데 "아니요"가 지역으로 들어갔다
    private boolean askedOnlyOtherConditions(Set<String> pendingKeys) {
        return !pendingKeys.isEmpty() && pendingKeys.stream().noneMatch(KNOWN_CONDITION_KEYS::contains);
    }

    private record ExtractedConditions(Map<String, String> values, Set<String> declinedKeys) {

        static ExtractedConditions empty() {
            return new ExtractedConditions(Collections.emptyMap(), Collections.emptySet());
        }

        boolean isEmpty() {
            return values.isEmpty() && declinedKeys.isEmpty();
        }
    }

    private record WaitingConsult(Long consultRequestId, Set<String> pendingKeys,
            Map<String, RoutingPromptTemplates.AskedCondition> askedConditions) {}

    private <T> T runInTransaction(Supplier<T> action) {
        return transactionTemplate.execute(status -> action.get());
    }

    private IntentRouteResponse executeInTransaction(
            ChatMessage userMessage,
            LlmRoutingPayload payload,
            QueryRouting.Method method) {
        try {
            return runInTransaction(() -> saveAndBuildResult(userMessage, payload, method));
        } catch (DataIntegrityViolationException exception) {
            return runInTransaction(() -> queryRoutingRepository
                    .findByMessage_MessageId(userMessage.getMessageId())
                    .map(routing -> {
                        log.info("[라우팅] 동시 저장된 기존 결과를 사용합니다: messageId={}",
                                userMessage.getMessageId());
                        return buildExistingResponse(routing, userMessage.getMessageId());
                    })
                    .orElseThrow(() -> exception));
        }
    }

    private IntentRouteResponse saveAndBuildResult(
            ChatMessage message,
            LlmRoutingPayload payload,
            QueryRouting.Method method) {

        if (payload == null) {
            payload = ruleBasedFallback.classify(message != null ? message.getContent() : "");
        }

        QueryRouting routing = queryRoutingRepository.saveAndFlush(
                intentConverter.toQueryRouting(message, payload, method));

        List<IntentSubQueryResponse> subQueries = new ArrayList<>();
        List<LlmRoutingPayload.SubQueryPayload> subQueryPayloads = payload.subQueries() != null
                ? payload.subQueries()
                : Collections.emptyList();

        // 상위 의도가 있는데 분해 결과가 비면 상담 요청이 하나도 안 만들어져 이후 처리가 통째로 빠진다
        if (subQueryPayloads.isEmpty()) {
            subQueryPayloads = defaultSubQueries(payload, message);
            if (!subQueryPayloads.isEmpty()) {
                log.info("[라우팅] 하위 질의가 비어 기본 하위 질의를 생성합니다: intent={}, 생성 건수={}",
                        payload.intent(), subQueryPayloads.size());
            }
        }

        for (int i = 0; i < subQueryPayloads.size(); i++) {
            LlmRoutingPayload.SubQueryPayload sub = subQueryPayloads.get(i);
            if (sub == null) {
                continue;
            }

            ConsultRequest.Intent subIntent = sub.intent() != null
                ? sub.intent()
                : payload.intent() == QueryRouting.Intent.STORE
                    ? ConsultRequest.Intent.STORE : ConsultRequest.Intent.FAQ;

            short order = (short) (i + 1);
            String queryText = (sub.queryText() != null && !sub.queryText().isBlank())
                ? sub.queryText()
                : (payload.refinedQuery() != null && !payload.refinedQuery().isBlank()
                    ? payload.refinedQuery()
                    : (message != null && message.getContent() != null ? message.getContent() : ""));

            ConsultRequest request = intentConverter.toConsultRequest(
                message, order, subIntent, queryText
            );

            if (sub.conditions() != null) {
                sub.conditions().forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                        ConsultCondition condition = intentConverter.toConsultCondition(request, key, value);
                        if (request.getConditions() != null) {
                            request.getConditions().add(condition);
                        }
                    }
                });
            }

            ConsultRequest savedRequest = consultRequestRepository.save(request);

            subQueries.add(intentConverter.toSubQueryResponse(
                savedRequest,
                order,
                subIntent,
                queryText,
                sub.conditions() != null ? sub.conditions() : Collections.emptyMap()
            ));
        }

        Map<String, String> extractedConditions = payload.extractedConditions() != null
                ? payload.extractedConditions()
                : Collections.emptyMap();

        return intentConverter.toIntentRouteResponse(routing, extractedConditions, subQueries);
    }

    // LLM이 intent만 주고 subQueries를 비워 보내도 상담 요청은 만들어져야 한다
    private List<LlmRoutingPayload.SubQueryPayload> defaultSubQueries(
            LlmRoutingPayload payload, ChatMessage message) {

        String queryText = payload.refinedQuery() != null && !payload.refinedQuery().isBlank()
                ? payload.refinedQuery()
                : (message != null && message.getContent() != null ? message.getContent().trim() : "");

        // UNKNOWN은 상담할 내용이 없고, 질의 문구가 없으면 만들 수 있는 하위 질의도 없다
        if (queryText.isBlank() || payload.intent() == QueryRouting.Intent.UNKNOWN) {
            return Collections.emptyList();
        }

        Map<String, String> conditions = payload.extractedConditions() != null
                ? payload.extractedConditions()
                : Collections.emptyMap();

        return switch (payload.intent()) {
            case FAQ -> List.of(new LlmRoutingPayload.SubQueryPayload(
                    (short) 1, ConsultRequest.Intent.FAQ, queryText, Collections.emptyMap()));
            case STORE -> List.of(new LlmRoutingPayload.SubQueryPayload(
                    (short) 1, ConsultRequest.Intent.STORE, queryText, conditions));
            // BOTH는 한쪽만 만들면 나머지 의도의 상담이 누락된다
            case BOTH -> List.of(
                    new LlmRoutingPayload.SubQueryPayload(
                            (short) 1, ConsultRequest.Intent.FAQ, queryText, Collections.emptyMap()),
                    new LlmRoutingPayload.SubQueryPayload(
                            (short) 2, ConsultRequest.Intent.STORE, queryText, conditions));
            case UNKNOWN -> Collections.emptyList();
        };
    }

    private IntentRouteResponse buildExistingResponse(QueryRouting routing, Long messageId) {
        Map<String, String> extractedConditions = intentConverter.parseConditions(routing.getExtractedConditions());
        List<ConsultRequest> requests = consultRequestRepository.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(messageId);

        List<IntentSubQueryResponse> subQueries = new ArrayList<>();
        for (ConsultRequest req : requests) {
            Map<String, String> condMap = req.getConditions() != null
                ? req.getConditions().stream().collect(Collectors.toMap(
                    ConsultCondition::getConditionKey,
                    ConsultCondition::getConditionValue,
                    (a, b) -> a
                ))
                : Collections.emptyMap();

            subQueries.add(intentConverter.toSubQueryResponse(
                req,
                req.getSubqueryOrder(),
                req.getIntent(),
                req.getQueryText(),
                condMap
            ));
        }

        return intentConverter.toIntentRouteResponse(routing, extractedConditions, subQueries);
    }
}
