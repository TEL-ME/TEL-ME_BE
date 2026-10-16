package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.res.ChatMessageHistoryItemResponse;
import com.telme.chat.dto.res.ChatMessageHistoryResponse;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatInputMaskRepository;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.safety.OutputMaskContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.safety.ChatOutputBlockedException;
import com.telme.global.common.code.BaseErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@RecordApplicationEvents
class ChatExecutionServiceIntegrationTest {
    @MockitoSpyBean private ChatExecutionRepository executions;

    @MockitoSpyBean private ChatInputMaskRepository inputMasks;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatExecutionService chatExecutionService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    private ChatActor actor;
    private Long sessionId;

    @BeforeEach
    void setUpSession() {
        User user = User.builder()
                .email("execution-" + UUID.randomUUID() + "@example.com")
                .name("execution")
                .build();
        entityManager.persist(user);
        entityManager.flush();

        actor = new ChatActor(user.getUserId(), null);
        sessionId = chatSessionService.createSession(actor, new ChatSessionCreateRequest("실행 테스트"))
                .sessionId();
    }

    @ParameterizedTest
    @ValueSource(strings = {"answer", "basic", "clarification", "withoutOutput", "failed", "cancelled"})
    @DisplayName("모든 종료 경로는 시작 시각과 같은 DB 시계로 종료 시각을 남긴다")
    void 모든_종료_경로가_DB_시계로_종료_시각을_남긴다(String path) {
        var question = send("유심 재발급 비용 알려줘");
        Instant databaseTime = findExecution(question.executionId()).getStartedAt().plusSeconds(2);
        doReturn(databaseTime).when(executions).currentDatabaseTime();
        var answer = new ChatAnswer(ChatMessage.MessageType.ANSWER, "답변입니다.", null, null, null);

        switch (path) {
            case "answer" -> chatExecutionService.completeAnswer(question.executionId(), answer);
            case "basic" -> chatExecutionService.completeBasicConversation(question.executionId(), answer);
            case "clarification" -> chatExecutionService.askClarification(question.executionId(), "어느 지역인가요?");
            case "withoutOutput" -> chatExecutionService.completeWithoutOutput(question.executionId());
            case "failed" -> chatExecutionService.fail(question.executionId(),
                    new ChatFailure(ChatMessage.Status.TIMEOUT, "LLM_TIMEOUT"));
            case "cancelled" -> chatExecutionService.fail(question.executionId(),
                    new ChatFailure(ChatMessage.Status.CANCELLED, "CANCELLED"));
            default -> throw new IllegalArgumentException(path);
        }

        var execution = findExecution(question.executionId());
        assertThat(execution.getEndedAt()).isEqualTo(databaseTime);
        if (execution.getOutputMessage() != null) {
            assertThat(execution.getOutputMessage().getCompletedAt()).isEqualTo(databaseTime);
        }
    }

    @Test
    @DisplayName("종료 시각은 트랜잭션 시작 시각이 아니라 호출 시점의 DB 시계다")
    void 종료_시각은_호출_시점의_DB_시계다() {
        Instant transactionStart = jdbcTemplate.queryForObject(
                "SELECT CURRENT_TIMESTAMP", (rs, row) -> rs.getTimestamp(1).toInstant());
        jdbcTemplate.execute("SELECT pg_sleep(0.02)");

        assertThat(executions.currentDatabaseTime()).isAfter(transactionStart);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "개 4111111111111111 새끼 유심 문의 | 개 *** 새끼 유심 문의",
        "씨 990101-1234567 발 요금 문의 | 씨 *** 발 요금 문의"
    })
    @DisplayName("실제 접수의 마스크 출처로 되묻기 질문과 선택지를 저장하고 표시 별표를 유지한다")
    void 마스크_인용_되묻기를_저장한다(String original, String visible) {
        var question = send(original);
        var result = chatExecutionService.askClarification(question.executionId(),
                visible + "의 조건을 확인해 주세요.", List.of(visible));

        assertThat(result.status()).isEqualTo(ChatExecution.Status.COMPLETED);
        assertThat(historyItem(2).content()).isEqualTo(visible + "의 조건을 확인해 주세요.");
        entityManager.flush();
        entityManager.clear();
        assertThat(historyItem(2).followUps()).containsExactly(visible);
        assertThat(historyItem(2).content()).doesNotContain("4111", "990101", "1234567", "\uFFFC");
    }

    @Test
    @DisplayName("인사말을 붙인 되묻기의 선택지에만 마스크가 있어도 출처를 한 번 조회하고 보존한다")
    void 인사말과_선택지의_마스크를_함께_처리한다() {
        var question = send("안녕하세요 씨 990101-1234567 발 요금 문의");

        chatExecutionService.askClarification(question.executionId(), "어떤 조건인가요?", List.of("씨 *** 발 요금 문의"));

        verify(inputMasks, times(1)).read(sessionId, 1);
        assertThat(historyItem(2).content()).isEqualTo("안녕하세요! 어떤 조건인가요?");
        assertThat(historyItem(2).followUps()).containsExactly("씨 *** 발 요금 문의");
    }

    @Test
    @DisplayName("다른 세션의 마스크 출처로 같은 별표 출력 검사를 완화하지 않는다")
    void 다른_세션의_마스크를_출력_출처로_사용하지_않는다() {
        var first = send("씨 990101-1234567 발 요금 문의");
        chatExecutionService.completeAnswer(first.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "정상 안내", null, List.of(), null));
        sessionId = chatSessionService.createSession(actor, new ChatSessionCreateRequest("다른 상담")).sessionId();
        var other = send("요금 문의");

        assertThatThrownBy(() -> chatExecutionService.askClarification(other.executionId(),
                "씨 *** 발 요금 문의", List.of())).isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("잘못된 마스크 위치는 안전한 검사 실패로 처리하고 답변 후보를 저장하지 않는다")
    void 저장된_마스크_위치가_틀리면_검사_실패로_처리한다() {
        var question = send("씨 990101-1234567 발 요금 문의");
        jdbcTemplate.update("UPDATE chat_messages SET input_mask_spans='[{\"start\":0,\"end\":3}]'::jsonb"
                + " WHERE message_id=?", question.messageId());

        assertThatThrownBy(() -> chatExecutionService.askClarification(question.executionId(),
                "씨 *** 발 요금 문의", List.of())).isInstanceOf(GeneralException.class)
                .extracting(error -> ((GeneralException) error).getErrorCode())
                .isEqualTo(ChatErrorCode.OUTPUT_CHECK_FAILED);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?",
                Integer.class, sessionId)).isEqualTo(1);
    }

    @Test
    @DisplayName("마스크가 없는 정상 답변과 되묻기는 마스크 이력을 조회하지 않는다")
    void 마스크가_없는_출력은_마스크_조회만_생략한다() {
        var first = send("요금 문의");
        chatExecutionService.completeAnswer(first.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "요금은 7700원이며 약정은 18개월입니다.", null,
                List.of("다른 요금제는?"), null));
        var next = send("변경 문의");
        chatExecutionService.askClarification(next.executionId(), "가입한 달을 알려주세요.", List.of("이번 달", "지난달"));

        verifyNoInteractions(inputMasks);
        assertThat(historyItem(2).content()).contains("7700원", "18개월");
    }

    @Test
    @DisplayName("마스크가 없는 실제 욕설도 조회 생략과 관계없이 차단한다")
    void 마스크_조회_생략으로_일반_욕설_검사를_건너뛰지_않는다() {
        var question = send("요금 문의");

        assertThatThrownBy(() -> chatExecutionService.askClarification(question.executionId(), "씨발", List.of()))
                .isInstanceOf(ChatOutputBlockedException.class);
        verifyNoInteractions(inputMasks);
    }

    @Test
    @DisplayName("본문이 정상이어도 추천 질문에 마스크가 있으면 출처를 한 번 조회해 두 검사에 재사용한다")
    void 추천_질문의_마스크도_한_번_조회한다() {
        var question = send("씨 990101-1234567 발 요금 문의");
        chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "정상 안내", null, List.of("씨 *** 발 요금 문의"), null));

        verify(inputMasks, times(1)).read(sessionId, 1);
        assertThat(historyItem(2).followUps()).containsExactly("씨 *** 발 요금 문의");
    }

    @ParameterizedTest
    @ValueSource(strings = {"씨***발", "개***새끼", "씨 *** 발", "씨******발"})
    @DisplayName("형식 정리에서 별표가 보존돼도 실제 답변 저장 경로는 욕설을 차단한다")
    void 보존된_별표_욕설을_답변으로_저장하지_않는다(String unsafe) {
        var question = send("카드 4111111111111111 납부 문의");
        var inspected = new ChatInputInspector(new ObjectMapper()).inspect("카드 4111111111111111 납부 문의");
        var sources = new OutputMaskContext(List.of(OutputMaskContext.Source.from(inspected)));
        String normalized = ChatSummaryNormalizer.normalize(unsafe, sources);

        assertThat(normalized).isEqualTo(unsafe);
        assertThatThrownBy(() -> chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER, normalized, null, List.of(), null)))
                .isInstanceOf(ChatOutputBlockedException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=?",
                Integer.class, sessionId)).isEqualTo(1);
    }

    @Test
    void persistsSearchContextAndSnapshotsAcrossReload() {
        var request = new ChatMessageSendRequest("가까운 매장", 37.456789, 127.987654);
        var sent = chatSessionService.sendMessage(actor, sessionId, request);
        var command = applicationEvents.stream(ChatProcessingCommand.class).findFirst().orElseThrow();
        assertThat(command.coordinates()).isEqualTo(new ChatCoordinates(37.456789, 127.987654));
        var context = new com.telme.chat.dto.res.ChatStoreSearchContextResponse(
                com.telme.chat.dto.res.ChatStoreSearchContextResponse.Type.CURRENT_LOCATION, "현재 위치", 10000);
        chatExecutionService.completeAnswer(sent.executionId(), new ChatAnswer(
                ChatMessage.MessageType.STORE_RESULT, "현재 위치 기준 매장", null, List.of(),
                List.of(Map.of("storeId", 2, "name", "매장", "latitude", 37.5,
                        "longitude", 127.0, "distanceMeters", 300)), context));
        entityManager.flush();
        entityManager.clear();
        var answer = historyItem(2);
        assertThat(answer.storeSearchContext()).isEqualTo(context);
        assertThat(answer.storeResults().getFirst()).containsEntry("distanceMeters", 300);
        String saved = jdbcTemplate.queryForObject(
                "SELECT store_search_context::text FROM chat_messages WHERE message_id=?",
                String.class, answer.messageId());
        assertThat(saved).doesNotContain("37.456789", "127.987654");
    }

    @Test
    void completesStartedAnswerInPlace() {
        ChatMessageSendResponse question = send("5G 요금제 알려줘");

        ChatExecutionState started = chatExecutionService.startAnswer(question.executionId());
        assertThat(started.outputMessage().sequenceNo()).isEqualTo(2);
        assertThat(started.outputMessage().status()).isEqualTo(ChatMessage.Status.GENERATING);

        ChatExecutionState completed = chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER,
                "5G 요금제는 세 가지입니다.",
                ChatMessage.AnswerBasis.GROUNDED,
                List.of("가장 저렴한 요금제는?", "데이터 무제한은?"),
                null
        ));

        assertThat(completed.outputMessage().messageId()).isEqualTo(started.outputMessage().messageId());
        assertThat(completed.outputMessage().sequenceNo()).isEqualTo(2);
        assertThat(completed.outputMessage().status()).isEqualTo(ChatMessage.Status.COMPLETED);

        ChatExecution execution = findExecution(question.executionId());
        assertThat(execution.getStatus()).isEqualTo(ChatExecution.Status.COMPLETED);
        assertThat(execution.getOutputMessage().getMessageId()).isEqualTo(started.outputMessage().messageId());
        assertThat(execution.getEndedAt()).isNotNull();

        ChatMessageHistoryItemResponse answer = historyItem(2);
        assertThat(answer.role()).isEqualTo("ASSISTANT");
        assertThat(answer.messageType()).isEqualTo("ANSWER");
        assertThat(answer.replyToMessageId()).isEqualTo(question.messageId());
        assertThat(answer.content()).isEqualTo("5G 요금제는 세 가지입니다.");
        assertThat(answer.answerBasis()).isEqualTo("GROUNDED");
        assertThat(answer.followUps()).containsExactly("가장 저렴한 요금제는?", "데이터 무제한은?");
        assertThat(answer.storeResults()).isNull();
        assertThat(answer.completedAt()).isNotNull();
        assertThat(applicationEvents.stream(ChatSummaryRequested.class))
                .containsExactly(new ChatSummaryRequested(question.executionId(), sessionId, 2));
        assertThat(applicationEvents.stream(ChatSessionTitleRequested.class)).isEmpty();
    }

    @Test
    void requestsTitleForSuccessfulUntitledSessionExecutionsUntilTitleIsStored() {
        sessionId = chatSessionService.createSession(actor, new ChatSessionCreateRequest(null)).sessionId();
        ChatMessageSendResponse firstQuestion = send("가족 결합 요금제를 알려줘");

        chatExecutionService.completeAnswer(firstQuestion.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "가족 결합 상품을 안내해 드릴게요.", null, null, null));

        ChatMessageSendResponse secondQuestion = send("할인 금액도 알려줘");
        chatExecutionService.completeAnswer(secondQuestion.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "할인 금액도 안내해 드릴게요.", null, null, null));

        assertThat(applicationEvents.stream(ChatSessionTitleRequested.class))
                .containsExactly(
                        new ChatSessionTitleRequested(
                                firstQuestion.executionId(), sessionId, "가족 결합 요금제를 알려줘"),
                        new ChatSessionTitleRequested(
                                secondQuestion.executionId(), sessionId, "할인 금액도 알려줘"));
    }

    @Test
    void requestsTitleFromNextSuccessfulExecutionWhenFirstExecutionFails() {
        sessionId = chatSessionService.createSession(actor, new ChatSessionCreateRequest(null)).sessionId();
        ChatMessageSendResponse failedQuestion = send("요금제를 알려줘");
        chatExecutionService.fail(
                failedQuestion.executionId(), new ChatFailure(ChatMessage.Status.TIMEOUT, "LLM_TIMEOUT"));

        ChatMessageSendResponse successfulQuestion = send("가족 결합 요금제를 알려줘");
        chatExecutionService.completeAnswer(successfulQuestion.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "가족 결합 상품을 안내해 드릴게요.", null, null, null));

        assertThat(applicationEvents.stream(ChatSessionTitleRequested.class))
                .containsExactly(new ChatSessionTitleRequested(
                        successfulQuestion.executionId(), sessionId, "가족 결합 요금제를 알려줘"));
    }

    @Test
    void generatingAnswerIsVisibleInHistory() {
        ChatMessageSendResponse question = send("가까운 매장 알려줘");
        chatExecutionService.startAnswer(question.executionId());

        ChatMessageHistoryItemResponse generating = historyItem(2);
        assertThat(generating.status()).isEqualTo("GENERATING");
        assertThat(generating.content()).isNull();
        assertThat(generating.completedAt()).isNull();
    }

    @Test
    void completesAnswerWithoutStartByAppendingMessage() {
        ChatMessageSendResponse question = send("강남역 매장 알려줘");

        ChatExecutionState completed = chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                ChatMessage.MessageType.STORE_RESULT,
                "강남역 근처 매장입니다.",
                null,
                null,
                List.of(Map.of("storeId", 1, "name", "강남점"))
        ));

        assertThat(completed.outputMessage().sequenceNo()).isEqualTo(2);
        ChatMessageHistoryItemResponse answer = historyItem(2);
        assertThat(answer.messageType()).isEqualTo("STORE_RESULT");
        assertThat(answer.status()).isEqualTo("COMPLETED");
        assertThat(answer.storeResults()).containsExactly(Map.of("storeId", 1, "name", "강남점"));
    }

    @Test
    void clarificationWaitsForUserAndNextAnswerResumesSession() {
        ChatMessageSendResponse question = send("매장 찾아줘");

        ChatExecutionState clarification = chatExecutionService.askClarification(
                question.executionId(), "어느 지역 매장을 찾으시나요?");

        assertThat(clarification.outputMessage().messageType()).isEqualTo(ChatMessage.MessageType.CLARIFICATION);
        assertThat(clarification.outputMessage().status()).isEqualTo(ChatMessage.Status.COMPLETED);
        assertThat(findSession().getStatus()).isEqualTo(ChatSession.Status.NEED_CLARIFICATION);
        assertThat(findExecution(question.executionId()).getStatus()).isEqualTo(ChatExecution.Status.COMPLETED);
        assertThat(applicationEvents.stream(ChatSummaryRequested.class))
                .contains(new ChatSummaryRequested(question.executionId(), sessionId, 2));

        ChatMessageSendResponse reply = send("강남역이요");
        assertThat(reply.sequenceNo()).isEqualTo(3);

        chatExecutionService.completeAnswer(reply.executionId(), new ChatAnswer(
                ChatMessage.MessageType.STORE_RESULT,
                "강남역 근처 매장입니다.",
                null,
                null,
                List.of(Map.of("storeId", 1, "name", "강남점"))
        ));

        assertThat(findSession().getStatus()).isEqualTo(ChatSession.Status.ACTIVE);
    }

    @Test
    void completesWaitingCorrectionWithoutCreatingAnotherClarification() {
        ChatMessageSendResponse question = send("매장 찾아줘");
        ChatExecutionState clarification = chatExecutionService.askClarification(
                question.executionId(), "어느 지역 매장을 찾으시나요?");
        ChatMessageSendResponse correction = send("업무는 번호이동으로 바꿔줘");

        ChatExecutionState completed = chatExecutionService.completeWithoutOutput(correction.executionId());

        assertThat(completed.status()).isEqualTo(ChatExecution.Status.COMPLETED);
        assertThat(completed.outputMessage()).isNull();
        ChatExecution execution = findExecution(correction.executionId());
        assertThat(execution.getOutputMessage()).isNull();
        assertThat(execution.getEndedAt()).isNotNull();
        assertThat(findSession().getStatus()).isEqualTo(ChatSession.Status.NEED_CLARIFICATION);
        assertThat(applicationEvents.stream(ChatSummaryRequested.class))
                .contains(new ChatSummaryRequested(correction.executionId(), sessionId, 3));

        ChatMessageHistoryResponse history = chatSessionService.getMessages(actor, sessionId, null, 50);
        assertThat(history.messages()).hasSize(3);
        assertThat(history.messages())
                .extracting(ChatMessageHistoryItemResponse::messageId)
                .contains(clarification.outputMessage().messageId());
        assertThat(history.runningExecutionId()).isNull();
        assertThat(send("강남역이요").sequenceNo()).isEqualTo(4);
    }

    @Test
    void rejectsCompletingWithoutOutputAfterAnswerStarted() {
        ChatMessageSendResponse question = send("요금제 알려줘");
        chatExecutionService.startAnswer(question.executionId());

        assertErrorCode(
                () -> chatExecutionService.completeWithoutOutput(question.executionId()),
                ChatErrorCode.ANSWER_ALREADY_STARTED);
    }

    @Test
    void failureWithoutStartedAnswerLeavesErrorMessage() {
        ChatMessageSendResponse question = send("요금제 알려줘");

        ChatExecutionState failed = chatExecutionService.fail(
                question.executionId(), new ChatFailure(ChatMessage.Status.TIMEOUT, "LLM_TIMEOUT"));

        assertThat(failed.outputMessage().sequenceNo()).isEqualTo(2);
        assertThat(failed.outputMessage().messageType()).isEqualTo(ChatMessage.MessageType.ERROR);
        assertThat(failed.outputMessage().status()).isEqualTo(ChatMessage.Status.TIMEOUT);

        ChatExecution execution = findExecution(question.executionId());
        assertThat(execution.getStatus()).isEqualTo(ChatExecution.Status.FAILED);
        assertThat(execution.getErrorCode()).isEqualTo("LLM_TIMEOUT");
        assertThat(execution.getOutputMessage().getMessageId()).isEqualTo(failed.outputMessage().messageId());
        assertThat(applicationEvents.stream(ChatSummaryRequested.class)).isEmpty();
        ChatMessageHistoryItemResponse errorMessage = historyItem(2);
        assertThat(errorMessage.replyToMessageId()).isEqualTo(question.messageId());
        assertThat(errorMessage.completedAt()).isEqualTo(findExecution(question.executionId()).getEndedAt());
    }

    @Test
    void failureAfterStartMarksGeneratingMessage() {
        ChatMessageSendResponse question = send("요금제 알려줘");
        ChatExecutionState started = chatExecutionService.startAnswer(question.executionId());

        ChatExecutionState cancelled = chatExecutionService.fail(
                question.executionId(), new ChatFailure(ChatMessage.Status.CANCELLED, "USER_CANCELLED"));

        assertThat(cancelled.outputMessage().messageId()).isEqualTo(started.outputMessage().messageId());
        assertThat(cancelled.outputMessage().messageType()).isEqualTo(ChatMessage.MessageType.ANSWER);
        assertThat(cancelled.outputMessage().status()).isEqualTo(ChatMessage.Status.CANCELLED);
        ChatExecution execution = findExecution(question.executionId());
        assertThat(execution.getStatus()).isEqualTo(ChatExecution.Status.CANCELLED);
        assertThat(historyItem(2).completedAt()).isEqualTo(findExecution(question.executionId()).getEndedAt());
    }

    @Test
    void clarificationIsVisibleToSqlInSameTransaction() {
        ChatMessageSendResponse question = send("매장 찾아줘");

        ChatExecutionState clarification = chatExecutionService.askClarification(
                question.executionId(), "어느 지역 매장을 찾으시나요?");

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from chat_messages where message_id = ? and session_id = ?"
                        + " and role = 'ASSISTANT' and message_type = 'CLARIFICATION' and status = 'COMPLETED'",
                Integer.class, clarification.outputMessage().messageId(), sessionId))
                .isEqualTo(1);
    }

    @Test
    void closedSessionStillStoresStartedExecutionWithoutReopening() {
        ChatMessageSendResponse question = send("매장 찾아줘");
        chatSessionService.closeSession(actor, sessionId);

        chatExecutionService.askClarification(question.executionId(), "어느 지역인가요?");

        assertThat(findSession().getStatus()).isEqualTo(ChatSession.Status.CLOSED);
        assertThat(historyItem(2).messageType()).isEqualTo("CLARIFICATION");
    }

    @Test
    void rejectsFinishedOrUnknownExecution() {
        ChatMessageSendResponse question = send("요금제 알려줘");
        chatExecutionService.startAnswer(question.executionId());

        assertErrorCode(
                () -> chatExecutionService.startAnswer(question.executionId()),
                ChatErrorCode.ANSWER_ALREADY_STARTED);

        chatExecutionService.fail(question.executionId(), new ChatFailure(ChatMessage.Status.FAILED, "MODEL_ERROR"));

        assertErrorCode(
                () -> chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                        ChatMessage.MessageType.ANSWER, "늦은 답변", null, null, null)),
                ChatErrorCode.EXECUTION_NOT_RUNNING);
        assertErrorCode(
                () -> chatExecutionService.askClarification(Long.MAX_VALUE, "질문"),
                ChatErrorCode.EXECUTION_NOT_FOUND);
    }

    @Test
    void rejectsNewMessageUntilExecutionFinishes() {
        ChatMessageSendResponse question = send("요금제 알려줘");

        assertErrorCode(() -> send("로밍은?"), ChatErrorCode.EXECUTION_IN_PROGRESS);

        chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "답변", null, null, null));

        assertThat(send("로밍은?").sequenceNo()).isEqualTo(3);
    }

    @Test
    void staleRunningExecutionStillBlocksNewMessage() {
        ChatMessageSendResponse question = send("요금제 알려줘");
        entityManager.flush();
        entityManager.createNativeQuery(
                        "update chat_executions set started_at = now() - interval '1 hour' where execution_id = ?")
                .setParameter(1, question.executionId())
                .executeUpdate();
        entityManager.clear();

        assertThat(chatSessionService.getMessages(actor, sessionId, null, 20).runningExecutionId())
                .isEqualTo(question.executionId());
        assertErrorCode(() -> send("다시 질문할게요"), ChatErrorCode.EXECUTION_IN_PROGRESS);
    }

    @Test
    void historyExposesRunningExecutionUntilItFinishes() {
        ChatMessageSendResponse question = send("요금제 알려줘");

        assertThat(chatSessionService.getMessages(actor, sessionId, null, 20).runningExecutionId())
                .isEqualTo(question.executionId());

        chatExecutionService.fail(question.executionId(), new ChatFailure(ChatMessage.Status.FAILED, "MODEL_ERROR"));

        assertThat(chatSessionService.getMessages(actor, sessionId, null, 20).runningExecutionId()).isNull();
    }

    @Test
    void returnsExecutionStateOnlyToOwner() {
        ChatMessageSendResponse question = send("요금제 알려줘");

        ChatExecutionState running = chatSessionService.getExecution(actor, question.executionId());
        assertThat(running.sessionId()).isEqualTo(sessionId);
        assertThat(running.status()).isEqualTo(ChatExecution.Status.RUNNING);
        assertThat(running.outputMessage()).isNull();

        ChatExecutionState answer = chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "답변", null, null, null));

        entityManager.flush();
        entityManager.clear();
        ChatExecutionState completed = chatSessionService.getExecution(actor, question.executionId());
        assertThat(completed.status()).isEqualTo(ChatExecution.Status.COMPLETED);
        assertThat(completed.outputMessage().messageId()).isEqualTo(answer.outputMessage().messageId());
        assertThat(completed.outputMessage().sequenceNo()).isEqualTo(2);
        assertThat(completed.outputMessage().status()).isEqualTo(ChatMessage.Status.COMPLETED);

        User other = User.builder()
                .email("other-" + UUID.randomUUID() + "@example.com")
                .name("other")
                .build();
        entityManager.persist(other);
        assertErrorCode(
                () -> chatSessionService.getExecution(new ChatActor(other.getUserId(), null), question.executionId()),
                ChatErrorCode.EXECUTION_NOT_FOUND);
        assertErrorCode(
                () -> chatSessionService.getExecution(new ChatActor(null, UUID.randomUUID()), question.executionId()),
                ChatErrorCode.EXECUTION_NOT_FOUND);
    }

    private ChatMessageSendResponse send(String content) {
        return chatSessionService.sendMessage(actor, sessionId, new ChatMessageSendRequest(content));
    }

    private ChatMessageHistoryItemResponse historyItem(int sequenceNo) {
        entityManager.flush();
        entityManager.clear();
        ChatMessageHistoryResponse history = chatSessionService.getMessages(actor, sessionId, null, 50);
        return history.messages().stream()
                .filter(message -> message.sequenceNo() == sequenceNo)
                .findFirst()
                .orElseThrow();
    }

    private ChatExecution findExecution(Long executionId) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(ChatExecution.class, executionId);
    }

    private ChatSession findSession() {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(ChatSession.class, sessionId);
    }

    private void assertErrorCode(Runnable call, BaseErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
