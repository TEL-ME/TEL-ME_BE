package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.dto.res.ChatStoreResponse;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse;
import com.telme.consult.service.NamedLocationStoreSearchPort;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 모델·외부 검색만 대체하고 메시지 전송부터 실제 상담 저장·종료·요약 조회까지 연결한다.
// 모든 입력을 주제 판정하는 기존 선택 모드에서 표현별 주제 확인 요청을 재현한다.
@SpringBootTest(properties = {"llm.provider=ollama", "rag.evidence-check.enabled=false",
        "chat.question-resolution.mode=LLM_ALL",
        "chat.end-summary.enabled=true", "chat.end-summary.dispatch-enabled=false",
        "chat.end-summary.scheduler-enabled=false", "chat.summary.trigger-messages=1000",
        "chat.summary.trigger-tokens=100000"})
@AutoConfigureMockMvc
class ChatEndSummaryClarificationFlowIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ChatSessionEndSummaryService summaries;
    @MockitoBean private FaqSearchService search;
    @MockitoBean private NamedLocationStoreSearchPort storeSearch;
    @MockitoBean(name = "baseLlmClient", enforceOverride = true) private LlmClient model;
    private long userId;
    private long sessionId;
    private long faqId;
    private MockHttpSession identity;
    private String faqAnswer;
    private List<String> generalQuestions;
    private boolean restartStoreOnInvalid;
    private String restartMessage;

    @BeforeEach
    void 실제_상담_경로와_외부_응답_대역을_준비한다() {
        faqAnswer = "유심 재발급 비용은 7,700원입니다.";
        generalQuestions = List.of("요금 알려줘", "요금이요", "번호이동 알려줘", "가입 문의드려요");
        restartStoreOnInvalid = false;
        restartMessage = "잘못 입력했어요";
        userId = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'요약 흐름 검증') RETURNING user_id",
                Long.class, "summary-flow-" + UUID.randomUUID() + "@example.com");
        sessionId = jdbc.queryForObject("INSERT INTO chat_sessions(user_id,title) VALUES (?,?) RETURNING session_id",
                Long.class, userId, "요약 흐름 검증");
        faqId = jdbc.queryForObject("INSERT INTO faqs(category,question,answer) VALUES ('USIM',?,?) RETURNING faq_id",
                Long.class, "요약 흐름 검증 " + sessionId, "유심 재발급 비용은 7,700원입니다.");
        identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        when(search.search(any())).thenReturn(List.of(new FaqSearchResponse(faqId, null, "USIM",
                "유심 재발급 비용", "유심 재발급 비용은 7,700원입니다.", 0.99, 1, null, 1, null)));
        when(storeSearch.search(any(), any())).thenReturn(new NamedLocationStoreSearchPort.SearchResult(
                NamedLocationStoreSearchPort.Status.SUCCESS,
                List.of(ChatStoreResponse.builder().storeId(1L).name("검증 매장").address("서울 강남구 검증로 1")
                        .latitude(BigDecimal.valueOf(37.5)).longitude(BigDecimal.valueOf(127)).build()),
                new ChatStoreSearchContextResponse(ChatStoreSearchContextResponse.Type.REGION, "강남역", null)));
        when(model.generate(any())).thenAnswer(call -> modelResponse(call.getArgument(0)));
        doAnswer(call -> {
            LlmStreamHandler handler = call.getArgument(1);
            handler.onToken(faqAnswer);
            handler.onComplete();
            return null;
        }).when(model).stream(any(), any());
    }

    @AfterEach
    void 이번_검증의_합성_자료만_정리한다() {
        jdbc.update("DELETE FROM consult_requests WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM chat_sessions WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
        jdbc.update("DELETE FROM faqs WHERE faq_id=?", faqId);
    }

    @Test
    @DisplayName("매장 찾기에서 지역과 업무를 답한 뒤 매장 목록까지 받으면 이전 확인 항목이 남지 않는다")
    void 지역과_업무_응답_뒤_실제_매장_결과로_해결한다() throws Exception {
        send("가까운 매장 알아보기");
        assertThat(lastAnswer().path("messageType").asText()).isEqualTo("CLARIFICATION");
        send("강남역");
        send("유심 재발급");
        assertThat(lastAnswer().path("messageType").asText()).isEqualTo("STORE_RESULT");
        assertThat(lastAnswer().path("answerBasis").isNull()).isTrue();
        assertThat(lastAnswer().path("storeResults")).hasSize(1);

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).isEmpty();
        assertThat(summary.path("inquiries").toString()).contains("검증 매장", "서울 강남구 검증로 1");
    }

    @Test
    @DisplayName("요금 주제 확인 뒤 구체적인 요금제 안내까지 받으면 첫 질문의 확인 요청을 제외한다")
    void 주제를_구체화한_요금제_답변으로_해결한다() throws Exception {
        send("요금 알려줘");
        assertThat(lastAnswer().path("content").asText()).contains("상담 주제");
        send("5G 요금제 알려줘");
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");
        assertThat(lastAnswer().path("content").asText()).contains("5G");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).isEmpty();
        assertThat(summary.path("inquiries").toString()).contains("5G");
        assertThat(summary.path("inquiries").toString()).doesNotContain("상담 주제를 조금 더");
    }

    @Test
    @DisplayName("요금 주제 확인 뒤 다른 유심 문의가 해결돼도 원래 요금 확인 요청은 유지한다")
    void 다른_업무의_정상_답변으로_요금_확인_요청을_지우지_않는다() throws Exception {
        send("요금 알려줘");
        send("유심 재발급 비용 알려줘");
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).hasSize(1);
        assertThat(summary.path("remaining").toString()).contains("요금 알려줘");
        assertThat(summary.path("inquiries").toString()).contains("7,700원");
    }

    @Test
    @DisplayName("매장 검색에 실패하면 요청 완료 표시만으로 지역 문의를 해결 처리하지 않는다")
    void 매장_검색_실패는_남은_사항에서_유지한다() throws Exception {
        when(storeSearch.search(any(), any())).thenReturn(NamedLocationStoreSearchPort.SearchResult.failed());
        send("가까운 매장 알아보기");
        send("강남역");
        send("유심 재발급");
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("NO_EVIDENCE");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).isNotEmpty();
        assertThat(summary.path("remaining").toString()).contains("UNANSWERED");
    }

    @Test
    @DisplayName("매장 결과 유형만 있고 저장된 목록이 비어 있으면 해결을 인정하지 않는다")
    void 빈_매장_목록은_정상_결과로_인정하지_않는다() throws Exception {
        send("가까운 매장 알아보기");
        send("강남역");
        send("유심 재발급");
        long answerId = lastAnswer().path("messageId").asLong();
        jdbc.update("UPDATE chat_messages SET store_results='[]'::jsonb WHERE message_id=?", answerId);

        assertThat(closeAndRead().path("remaining")).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"어머니 5G 요금제 알려줘", "다른 회선 5G 요금제 알려줘",
        "부모님 5G 요금제 알려줘", "친구 5G 요금제 알려줘", "가족 5G 요금제 알려줘",
        "형 5G 요금제 알려줘", "직장 동료 5G 요금제 알려줘", "그럼 형 5G 요금제 알려줘"})
    @DisplayName("주제 확인 뒤 다른 사람이나 회선의 요금제 답변은 원래 문의의 해결 근거로 쓰지 않는다")
    void 다른_대상의_요금제_답변으로_해결하지_않는다(String followup) throws Exception {
        send("요금 알려줘");
        send(followup);
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");

        assertThat(closeAndRead().path("remaining").toString()).contains("요금 알려줘");
    }

    @Test
    @DisplayName("다른 문의가 끼어들면 나중의 같은 주제 답변으로 앞선 확인 요청을 소급 해결하지 않는다")
    void 중간의_다른_문의를_건너뛰어_연결하지_않는다() throws Exception {
        send("요금 알려줘");
        send("유심 재발급 비용 알려줘");
        send("5G 요금제 알려줘");
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");

        assertThat(closeAndRead().path("remaining").toString()).contains("요금 알려줘");
    }

    @Test
    @DisplayName("같은 요금 문의를 두 번 확인한 뒤 정상 답변을 받으면 앞선 확인 항목을 모두 제외한다")
    void 연속_되묻기의_완료를_실제_종료_요약까지_연결한다() throws Exception {
        sendRepeatedFeeQuestions();
        send("5G 요금제 알려줘");
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).isEmpty();
        assertThat(summary.path("inquiries")).hasSize(1);
        assertThat(summary.path("inquiries").toString()).contains("5G").doesNotContain("상담 주제를 조금 더");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "가입 문의드려요 | 신규 가입이요 | 신규 가입 서류 알려줘 | SUBSCRIBE",
        "휴대폰이요 | 단말 문의요 | 기기변경 할부 조건 알려줘 | DEVICE",
        "청구 문의요 | 청구서이요 | 청구서 납부 방법 알려줘 | BILLING"
    })
    @DisplayName("저장된 대화에서 같은 주제의 별칭과 구체화를 거쳐 답변받으면 종료 요약의 확인 항목을 제외한다")
    void 별칭_연속_확인의_실제_저장과_종료_요약을_연결한다(String first, String middle,
            String last, String category) throws Exception {
        generalQuestions = List.of(first, middle);
        prepareFaq(category, "해당 문의의 서류와 절차를 안내받았습니다.");
        send(first);
        assertTopicClarification();
        send(middle);
        assertTopicClarification();
        send(last);
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).isEmpty();
        assertThat(summary.path("inquiries").toString()).contains("서류와 절차");
    }

    @ParameterizedTest
    @ValueSource(strings = {"다른 회선", "가족", "형", "직장 동료"})
    @DisplayName("주제 별칭으로 이어졌어도 다른 대상이나 회선의 최종 답변으로 앞선 문의를 해결하지 않는다")
    void 별칭_연속_확인에서도_다른_대상을_구분한다(String subject) throws Exception {
        generalQuestions = List.of("가입 문의드려요", "신규 가입이요");
        prepareFaq("SUBSCRIBE", "신규 가입 서류와 절차를 안내받았습니다.");
        send("가입 문의드려요");
        assertTopicClarification();
        send("신규 가입이요");
        assertTopicClarification();
        send(subject + " 신규 가입 서류 알려줘");
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");

        assertThat(closeAndRead().path("remaining").toString()).contains("가입 문의드려요", "신규 가입이요");
    }

    @Test
    @DisplayName("별칭 확인을 해결한 뒤에도 별도 매장 문의의 미확인 지역은 남은 사항으로 보존한다")
    void 별칭_해결과_다른_문의의_미해결_조건을_구분한다() throws Exception {
        generalQuestions = List.of("가입 문의드려요", "신규 가입이요");
        prepareFaq("SUBSCRIBE", "신규 가입 서류와 절차를 안내받았습니다.");
        send("가입 문의드려요");
        send("신규 가입이요");
        send("신규 가입 서류 알려줘");
        send("가까운 매장 알아보기");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).hasSize(1);
        assertThat(summary.path("remaining").toString()).contains("지역").doesNotContain("가입 문의드려요");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "가족 유심 재발급 비용은요? | 다른 가족 유심 재발급 비용은요? | 2",
        "큰형 유심 재발급 비용은요? | 작은형 유심 재발급 비용은요? | 2",
        "형 유심 재발급 비용은요? | 저의 유심 재발급 비용은요? | 2",
        "가족 유심 재발급 비용은요? | 그 유심은 본인 신분증이 필요한가요? | 1",
        "형 유심 재발급 비용은요? | 그 유심은 본인 신분증이 필요한가요? | 1",
        "직장 동료 유심 재발급 비용은요? | 그 유심은 본인 신분증이 필요한가요? | 1",
        "가족 유심 재발급 비용은요? | 내 실물 유심 재발급 비용은요? | 2",
        "형 유심 재발급 비용은요? | 저의 분실 유심 재발급 비용은요? | 2",
        "직장 동료 유심 재발급 비용은요? | 나의 물리 유심 재발급 비용은요? | 2",
        "가족 유심 재발급 비용은요? | 그 유심은 본인이 매장에 방문할 때 준비물이 뭔가요? | 1",
        "형 유심 재발급 비용은요? | 그 유심은 본인이 매장에 방문할 때 준비물이 뭔가요? | 1",
        "직장 동료 유심 재발급 비용은요? | 그 유심은 본인이 매장에 방문할 때 준비물이 뭔가요? | 1"
    })
    @DisplayName("메시지 전송부터 종료 요약까지 다른 소유자는 나누고 같은 유심의 준비물은 같은 문단에 남긴다")
    void 실제_유심_상담의_소유자와_준비물을_구분한다(String first, String second, int expected) throws Exception {
        send(first);
        prepareFaq("USIM", "해당 유심 명의자의 신분증을 지참합니다.");
        send(second);
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");

        var summary = closeAndRead();

        assertThat(summary.path("inquiries")).hasSize(expected);
        assertThat(summary.path("inquiries").toString()).contains("7,700원", "신분증");
        assertThat(summary.path("remaining")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "큰형과 작은형 유심 준비물은요? | 1", "작은형과 큰형 유심 준비물은요? | 1",
        "작은형 유심 준비물은요? | 2", "그 유심 준비물은요? | 2"
    })
    @DisplayName("실제 종료 요약에서도 복수 대상 제목을 보존하고 같은 대상 집합에만 이어묻기를 연결한다")
    void 복수_대상의_실제_저장과_종료_요약을_확인한다(String followup, int expected) throws Exception {
        send("큰형과 작은형 유심 재발급 비용은요?");
        prepareFaq("USIM", "해당 유심 명의자의 신분증을 지참합니다.");
        send(followup);
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");

        var summary = closeAndRead();

        assertThat(summary.path("inquiries")).hasSize(expected);
        assertThat(summary.path("inquiries").get(0).path("title").asText()).startsWith("큰형·작은형 ");
        assertThat(summary.path("inquiries").get(0).path("text").asText()).contains("7,700원");
        if (expected == 2) {
            assertThat(summary.path("inquiries").get(0).path("text").asText()).doesNotContain("신분증");
            assertThat(summary.path("inquiries").get(1).path("text").asText())
                    .contains("신분증").doesNotContain("7,700원");
        } else {
            assertThat(summary.path("inquiries").get(0).path("text").asText()).contains("신분증");
        }
        assertThat(summary.path("remaining")).isEmpty();
    }

    @Test
    @DisplayName("여러 사람의 안내를 정상 종료해도 별도 매장의 미확인 지역을 남은 사항에서 삭제하지 않는다")
    void 복수_대상_안내와_별도_미해결_지역을_분리한다() throws Exception {
        send("큰형과 작은형 유심 재발급 비용은요?");
        send("가까운 매장 알아보기");

        var summary = closeAndRead();

        assertThat(summary.path("inquiries").get(0).path("title").asText()).startsWith("큰형·작은형 ");
        assertThat(summary.path("remaining")).hasSize(1);
        assertThat(summary.path("remaining").toString()).contains("지역");
    }

    @Test
    @DisplayName("연속 되묻기를 해결해도 별도로 미확인 상태인 매장 지역 조건을 삭제하지 않는다")
    void 연속_되묻기와_별도_미해결_조건을_구분한다() throws Exception {
        sendRepeatedFeeQuestions();
        send("5G 요금제 알려줘");
        send("가까운 매장 알아보기");
        assertThat(lastAnswer().path("messageType").asText()).isEqualTo("CLARIFICATION");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).hasSize(1);
        assertThat(summary.path("remaining").toString()).contains("지역").doesNotContain("요금이요");
    }

    @Test
    @DisplayName("연속 확인 뒤 최종 검색이 실패하면 앞선 주제 확인을 해결 처리하지 않는다")
    void 연속_되묻기_뒤_실패를_남은_사항에서_보존한다() throws Exception {
        sendRepeatedFeeQuestions();
        when(search.search(any())).thenReturn(List.of());
        send("요금제 변경 조건 알려줘");
        assertThat(lastAnswer().path("answerBasis").asText()).isNotEqualTo("GROUNDED");

        assertThat(closeAndRead().path("remaining").toString()).contains("요금이요");
    }

    @Test
    @DisplayName("연속 확인 중 다른 업무가 끼면 뒤의 요금제 답변으로 앞선 문의를 소급 해결하지 않는다")
    void 연속_되묻기_중간의_다른_업무를_건너뛰지_않는다() throws Exception {
        sendRepeatedFeeQuestions();
        send("유심 재발급 비용 알려줘");
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");
        send("5G 요금제 알려줘");
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");

        assertThat(closeAndRead().path("remaining").toString()).contains("요금이요");
    }

    @Test
    @DisplayName("요금 확인을 해결한 뒤 별도 매장 문의의 지역 조건은 남은 사항에 보존한다")
    void 해결된_주제와_새로운_미확인_조건을_분리한다() throws Exception {
        send("요금 알려줘");
        send("5G 요금제 알려줘");
        send("가까운 매장 알아보기");
        assertThat(lastAnswer().path("messageType").asText()).isEqualTo("CLARIFICATION");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).hasSize(1);
        assertThat(summary.path("remaining").toString()).contains("지역").doesNotContain("요금 알려줘");
    }

    @Test
    @DisplayName("같은 요금 주제로 구체화해도 정상 답변을 받지 못하면 주제 확인을 해결 처리하지 않는다")
    void 구체화한_문의의_검색_실패로_해결을_단정하지_않는다() throws Exception {
        send("요금 알려줘");
        when(search.search(any())).thenReturn(List.of());
        send("요금제 변경 조건 알려줘");
        assertThat(lastAnswer().path("answerBasis").asText()).isNotEqualTo("GROUNDED");

        assertThat(closeAndRead().path("remaining").toString()).contains("요금 알려줘");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "번호이동 알려줘 | 번호이동 준비 서류 알려줘 | PORTING | 번호이동에는 신분증이 필요합니다.",
        "가입 문의드려요 | 신규 가입 서류 알려줘 | SUBSCRIBE | 신규 가입에는 신분증이 필요합니다.",
        "요금이요 | 5G 요금제 알려줘 | PLAN | 5G 요금제 안내입니다."
    })
    @DisplayName("확장한 주제 표현도 실제 메시지 전송과 상담 종료 경로에서 해결된 확인 항목을 제외한다")
    void 새_주제_표현의_정상_답변을_종료_요약까지_연결한다(String original, String followup,
            String category, String answer) throws Exception {
        faqAnswer = answer;
        jdbc.update("UPDATE faqs SET category=?, answer=? WHERE faq_id=?", category, answer, faqId);
        when(search.search(any())).thenReturn(List.of(new FaqSearchResponse(faqId, null, category,
                followup, answer, 0.99, 1, null, 1, null)));
        send(original);
        assertThat(lastAnswer().path("content").asText()).contains("상담 주제");
        send(followup);
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).isEmpty();
        assertThat(summary.path("inquiries")).isNotEmpty();
    }

    @Test
    @DisplayName("실수 입력으로 요청이 취소된 뒤 같은 매장 문의가 다시 완료되면 이전 지역 확인을 제외한다")
    void 실수_입력_뒤_재시작한_매장_문의를_종료_요약에서_해결한다() throws Exception {
        restartStoreOnInvalid = true;
        send("가까운 매장 알아보기");
        send("잘못 입력했어요");
        assertThat(lastAnswer().path("messageType").asText()).isEqualTo("CLARIFICATION");
        send("강남역");
        send("유심 재발급");
        assertThat(lastAnswer().path("messageType").asText()).isEqualTo("STORE_RESULT");
        assertThat(jdbc.queryForList("SELECT status FROM consult_requests WHERE session_id=? "
                + "ORDER BY origin_message_id", String.class, sessionId)).containsExactly("CANCELLED", "DONE");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).isEmpty();
        assertThat(summary.path("inquiries").toString()).contains("검증 매장");
    }

    @ParameterizedTest
    @ValueSource(strings = {"어머니 매장 다시 알아보기", "다른 회선 매장 다시 알아보기", "별도 문의로 매장 다시 알아보기",
        "가족 매장 다시 알아보기", "형 매장 다시 알아보기", "직장 동료 매장 다시 알아보기",
        "그리고 형 매장 다시 알아보기"})
    @DisplayName("재시작 검색어가 같게 저장돼도 실제 사용자 발화의 다른 대상과 새 문의는 이전 해결로 보지 않는다")
    void 재시작_매장_검색의_다른_대상과_문의를_보존한다(String message) throws Exception {
        restartStoreOnInvalid = true;
        restartMessage = message;
        send("가까운 매장 알아보기");
        send(message);
        send("강남역");
        send("유심 재발급");
        assertThat(lastAnswer().path("messageType").asText()).isEqualTo("STORE_RESULT");
        assertThat(jdbc.queryForList("SELECT status FROM consult_requests WHERE session_id=? "
                + "ORDER BY origin_message_id", String.class, sessionId)).containsExactly("CANCELLED", "DONE");

        var summary = closeAndRead();

        assertThat(summary.path("remaining")).isNotEmpty();
        assertThat(summary.path("remaining").toString()).contains("가까운 매장 알아보기");
    }

    @Test
    @DisplayName("준비물 단정을 보정한 실제 저장 답변과 종료 요약에 방문자 조건이 유지된다")
    void 준비물_보정_뒤_저장과_종료_요약에도_조건을_유지한다() throws Exception {
        String evidence = "유심 재발급 비용은 7,700원입니다. "
                + "명의자 본인은 신분증을 지참하고 매장을 방문해야 합니다. "
                + "대리인은 위임장과 신분증을 가지고 매장을 방문해야 합니다. "
                + "이번 상담에서는 재발급 신청을 접수하거나 완료하지 않았습니다.";
        prepareFaq("USIM", evidence);
        faqAnswer = "유심 재발급 비용은 7,700원입니다. 필요한 준비물은 신분증만 준비하시면 됩니다.";

        send("유심 재발급 준비물과 방문 절차를 알려주세요.");

        assertThat(lastAnswer().path("content").asText()).isEqualTo(evidence).doesNotContain("신분증만");
        var summary = closeAndRead();
        assertThat(summary.path("inquiries")).hasSize(1);
        assertThat(summary.path("inquiries").toString())
                .contains("7,700원", "명의자 본인", "신분증", "매장을 방문", "대리인", "위임장", "접수하거나 완료하지")
                .doesNotContain("신분증만");
        assertThat(summary.path("remaining")).isEmpty();
    }

    @Test
    @DisplayName("여러 줄 FAQ의 준비물과 방문자 조건 및 미접수 상태가 저장과 종료 요약에 모두 남는다")
    void 줄바꿈_뒤_조건도_저장과_요약에_보존한다() throws Exception {
        String evidence = "유심 재발급 비용은 7,700원입니다.\n"
                + "명의자 본인은 신분증을 지참하고 매장을 방문해야 합니다.\n"
                + "대리인은 위임장도 지참해야 합니다.\n"
                + "이번 상담에서는 재발급 신청을 접수하거나 완료하지 않았습니다.";
        prepareFaq("USIM", evidence);
        faqAnswer = "유심 재발급 비용은 7,700원이며 신분증만 준비하면 됩니다.";

        send("유심 재발급 준비물과 방문 절차를 알려주세요.");

        assertThat(lastAnswer().path("content").asText()).isEqualTo(evidence.replace("\n", " "));
        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("GROUNDED");
        var summary = closeAndRead();
        assertThat(summary.path("inquiries")).hasSize(1);
        assertThat(summary.path("inquiries").toString())
                .contains("7,700원", "명의자 본인", "신분증", "매장을 방문", "대리인", "위임장", "접수하거나 완료하지")
                .doesNotContain("신분증만");
        assertThat(summary.path("remaining")).isEmpty();
    }

    @Test
    @DisplayName("다른 사람의 준비물로 답변하지 못한 문의는 종료 요약에서도 해결로 바뀌지 않는다")
    void 다른_사람_근거로_원래_문의를_해결하지_않는다() throws Exception {
        prepareFaq("USIM", "지수의 유심 재발급은 신분증과 위임장을 지참하고 매장을 방문해야 합니다.");
        faqAnswer = "민수의 유심 재발급은 신분증만 준비하면 됩니다.";

        send("민수의 유심 재발급 준비물을 알려주세요.");

        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("NO_EVIDENCE");
        assertThat(lastAnswer().path("content").asText()).doesNotContain("지수", "위임장");
        var summary = closeAndRead();
        assertThat(summary.path("inquiries")).isEmpty();
        assertThat(summary.path("remaining").toString()).contains("UNANSWERED", "민수").doesNotContain("지수");
    }

    @Test
    @DisplayName("명의자 서류를 가진 대리인의 절차를 본인 방문 문의의 해결 근거로 쓰지 않는다")
    void 서류_소유자와_다른_방문자의_절차로_해결하지_않는다() throws Exception {
        prepareFaq("USIM", "대리인은 유심 재발급 시 명의자 본인 명의의 신분증과 위임장을 지참하고 매장을 방문해야 합니다.");
        faqAnswer = "명의자 본인은 유심 재발급 시 신분증만 준비하면 됩니다.";

        send("명의자 본인 유심 재발급 준비물을 알려주세요.");

        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("NO_EVIDENCE");
        var summary = closeAndRead();
        assertThat(summary.path("inquiries")).isEmpty();
        assertThat(summary.path("remaining").toString()).contains("UNANSWERED", "명의자 본인");
    }

    @Test
    @DisplayName("준비물 복원 전 수치 검사에서 차단한 답변은 종료 요약에서도 정상 안내로 표시하지 않는다")
    void 수치_차단은_저장과_종료_요약에서도_유지한다() throws Exception {
        prepareFaq("USIM", "유심 재발급 비용은 7,700원입니다.\n신분증을 지참하고 매장을 방문해야 합니다.");
        faqAnswer = "유심 재발급 비용은 8,800원이며 신분증만 준비하면 됩니다.";

        send("유심 재발급 준비물과 비용을 알려주세요.");

        assertThat(lastAnswer().path("answerBasis").asText()).isEqualTo("NO_EVIDENCE");
        assertThat(lastAnswer().path("content").asText()).doesNotContain("8,800원", "7,700원");
        var summary = closeAndRead();
        assertThat(summary.path("inquiries")).isEmpty();
        assertThat(summary.path("remaining").toString()).contains("UNANSWERED");
    }

    private void sendRepeatedFeeQuestions() throws Exception {
        send("요금이요");
        assertThat(lastAnswer().path("content").asText()).contains("상담 주제");
        send("요금이요");
        assertThat(lastAnswer().path("content").asText()).contains("상담 주제");
    }

    private String modelResponse(LlmRequest request) throws Exception {
        if (request.responseSchema() != null && request.responseSchema().toString().contains("PLAN_CATALOG")) {
            return "{\"type\":\"PLAN_CATALOG\"}";
        }
        if (request.taskType() == TaskType.SUMMARY) {
            var result = mapper.createObjectNode();
            var topics = result.putObject("topics");
            mapper.valueToTree(request.responseSchema()).path("properties").path("topics").path("required")
                    .forEach(id -> topics.put(id.asText(), "상담 문의"));
            var selected = result.putArray("selectedSentenceIds");
            mapper.readTree(request.userPrompt()).path("guidanceCandidates")
                    .forEach(candidate -> selected.add(candidate.path("sentenceId").asInt()));
            result.putArray("pendingItems");
            return result.toString();
        }
        if (request.promptVersion() != null && request.promptVersion().contains("-reference")) {
            String question = mapper.readTree(request.userPrompt()).path("currentQuestion").asText();
            return mapper.writeValueAsString(Map.of("reference",
                    generalQuestions.contains(question)
                            ? "OMITTED" : "EXPLICIT"));
        }
        if (request.promptVersion() != null && request.promptVersion().startsWith("routing-follow-up-v")) {
            if (restartStoreOnInvalid && request.userPrompt().contains(restartMessage)) {
                return "{\"responseType\":\"NEW_QUESTION\",\"conditions\":[]}";
            }
            return "{\"responseType\":\"CONDITION_RESPONSE\",\"conditions\":["
                    + "{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"강남역\"}]}";
        }
        if (request.taskType() == TaskType.CLARIFICATION) {
            return "어느 지역의 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.";
        }
        if ("routing-request-inventory-v4".equals(request.promptVersion())) {
            return "{\"decision\":\"SINGLE\",\"requestCount\":1}";
        }
        String question = request.userPrompt();
        String marker = "[현재 질문]\n";
        int start = question.lastIndexOf(marker);
        question = start < 0 ? question : question.substring(start + marker.length());
        if (restartStoreOnInvalid && question.replaceAll("\\s+", "")
                .contains(restartMessage.replaceAll("\\s+", ""))) {
            question = "가까운 매장 알아보기";
        }
        boolean procedure = question.contains("유심") && (question.contains("준비물") || question.contains("신분증"));
        boolean store = question.contains("매장") && !procedure;
        String intent = store ? "STORE" : "FAQ";
        return mapper.writeValueAsString(Map.of("intent", intent, "confidence", 0.99, "refinedQuery", question,
                "extractedConditions", Map.of(), "subQueries",
                List.of(Map.of("order", 1, "intent", intent, "queryText", question, "conditions", Map.of()))));
    }

    private void send(String content) throws Exception {
        var response = mvc.perform(post("/api/v1/chat/sessions/{id}/messages", sessionId).session(identity)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isCreated()).andReturn();
        long execution = mapper.readTree(response.getResponse().getContentAsByteArray()).path("result")
                .path("executionId").asLong();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_executions WHERE execution_id=?", String.class, execution))
                .isEqualTo("COMPLETED"));
    }

    private void prepareFaq(String category, String answer) {
        faqAnswer = answer;
        jdbc.update("UPDATE faqs SET category=?,answer=? WHERE faq_id=?", category, answer, faqId);
        when(search.search(any())).thenReturn(List.of(new FaqSearchResponse(faqId, null, category,
                "요약 흐름 검증", answer, 0.99, 1, null, 1, null)));
    }

    private void assertTopicClarification() throws Exception {
        var answer = lastAnswer();
        // 라우팅에서 고정 확인 문구를 ANSWER로 저장하는 경로도 요약 입력에서는 확인 요청으로 읽는다.
        assertThat(answer.path("content").asText()).isEqualTo(ChatTopicClarification.MESSAGE);
        assertThat(answer.path("answerBasis").isNull()).isTrue();
    }

    private JsonNode lastAnswer() throws Exception {
        var response = mvc.perform(get("/api/v1/chat/sessions/{id}/messages", sessionId).session(identity))
                .andExpect(status().isOk()).andReturn();
        var messages = mapper.readTree(response.getResponse().getContentAsByteArray()).path("result").path("messages");
        return messages.get(messages.size() - 1);
    }

    private JsonNode closeAndRead() throws Exception {
        mvc.perform(patch("/api/v1/chat/sessions/{id}/close", sessionId).session(identity)).andExpect(status().isOk());
        summaries.process(sessionId);
        var response = mvc.perform(get("/api/v1/chat/sessions/{id}/summary", sessionId).session(identity))
                .andExpect(status().isOk()).andReturn();
        var result = mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
        assertThat(result.path("summaryStatus").asText()).isEqualTo("COMPLETED");
        return result.path("summary");
    }
}
