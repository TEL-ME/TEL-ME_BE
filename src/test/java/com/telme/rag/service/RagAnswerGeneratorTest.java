package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.entity.ChatMessage.AnswerBasis;
import com.telme.chat.service.ExecutionTrace;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.entity.LlmGeneration.Status;
import com.telme.llm.repository.LlmGenerationRepository;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.converter.AnswerContextConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.rag.config.EvidenceCheckProperties;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RagAnswerGeneratorTest {

    private final RecordingHandler handler = new RecordingHandler();
    private final SpyRecorder recorder = new SpyRecorder();

    @Test
    @DisplayName("검색 결과가 없으면 LLM을 부르지 않고 안내 문구를 반환한다")
    void 근거가_없으면_LLM을_부르지_않는다() {
        StubClient client = new StubClient(List.of("쓰이지", "않음"));
        RagAnswerGenerator generator = generator(client);

        AnswerResult result = generator.generate(request(List.of()), handler);

        assertThat(client.called).isFalse();
        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(result.sources()).isEmpty();
        assertThat(handler.tokens).containsExactly(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(handler.completed).isTrue();
        assertThat(recorder.statuses).containsExactly(Status.NO_EVIDENCE);
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.NO_EVIDENCE);
    }

    @Test
    @DisplayName("검색 결과 없음의 guard 기록은 호출 전 단계가 남기므로 생성기는 다시 기록하지 않는다")
    void 근거가_없어도_guard_단계를_중복_기록하지_않는다() {
        List<String> stages = new ArrayList<>();
        RagAnswerGenerator generator = generator(new StubClient(List.of("쓰이지 않음")), new ExecutionTrace() {
            @Override
            public void stage(Long executionId, String stage, Object value) {
                stages.add(stage);
            }

            @Override
            public void append(Long executionId, String stage, Object value) {}
        });

        generator.generate(request(List.of()), handler);

        assertThat(stages).doesNotContain("guard");
    }

    @Test
    @DisplayName("모은 토큰을 Guard로 검증한 뒤 최종 답변을 handler에 전달한다")
    void 토큰을_모아_답변을_만든다() {
        RagAnswerGenerator generator = generator(new StubClient(List.of("요금제는 ", "한 달에 한 번 ", "변경됩니다.")));

        AnswerResult result = generator.generate(request(List.of(faq(1L))), handler);

        assertThat(result.answer()).isEqualTo("요금제는 한 달에 한 번 변경됩니다.");
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.GROUNDED);
        assertThat(handler.tokens).containsExactly(result.answer());
        assertThat(handler.completed).isTrue();
    }

    @Test
    @DisplayName("근거를 줘도 모델이 답변 불가 문구를 내놓으면 NO_EVIDENCE로 표시한다")
    void 모델이_답변_불가면_NO_EVIDENCE() {
        RagAnswerGenerator generator =
                generator(new StubClient(List.of(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)));

        AnswerResult result = generator.generate(request(List.of(faq(1L))), handler);

        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.NO_EVIDENCE);
        assertThat(result.sources()).hasSize(1);
    }

    @Test
    @DisplayName("조건 키를 읽기 쉬운 말로 바꿔 프롬프트에 넣는다")
    void 조건_키를_한글로_바꾼다() {
        StubClient client = new StubClient(List.of("답변"));
        Map<String, String> conditions = new HashMap<>();
        conditions.put("location", "강남");
        conditions.put("serviceType", "NAME_CHANGE");

        AnswerRequest request = AnswerRequest.builder()
                .userQuery("명의변경하고 싶어요")
                .conditions(conditions)
                .searchResults(List.of(faq(1L)))
                .build();

        generator(client).generate(request, handler);

        assertThat(client.received.userPrompt())
                .contains("- 지역: 강남")
                .contains("- 업무 유형: NAME_CHANGE");
    }

    @Test
    @DisplayName("전달한 검색 결과를 근거로 담아 반환한다")
    void 검색_결과를_근거로_담는다() {
        RagAnswerGenerator generator = generator(new StubClient(List.of("답변")));

        AnswerResult result = generator.generate(request(List.of(faq(1L), faq(2L))), handler);

        assertThat(result.sources()).hasSize(2);
        assertThat(result.sources()).extracting(AnswerResult.AnswerSource::faqId)
                .containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("RAG_ANSWER 작업 유형과 executionId를 담아 호출한다")
    void 요청을_조립한다() {
        StubClient client = new StubClient(List.of("답변"));
        RagAnswerGenerator generator = generator(client);

        generator.generate(request(List.of(faq(1L))), handler);

        LlmRequest sent = client.received;
        assertThat(sent.taskType()).isEqualTo(TaskType.RAG_ANSWER);
        assertThat(sent.executionId()).isEqualTo(42L);
        assertThat(sent.systemPrompt()).isEqualTo(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT);
        assertThat(sent.userPrompt()).contains("[1] Q: 질문1").contains("강남").contains("요금제 바꾸고 싶어요");
        assertThat(sent.contextCount()).isEqualTo(1);
        assertThat(sent.promptVersion()).isEqualTo(AnswerPromptTemplates.PROMPT_VERSION);
    }

    @Test
    @DisplayName("클라이언트가 실패를 알리면 완료로 끝내지 않고 예외를 던진다")
    void 실패하면_완료로_끝내지_않는다() {
        RagAnswerGenerator generator = generator(
                new StubClient(new GeneralException(LlmErrorCode.INVALID_RESPONSE)));

        assertThatThrownBy(() -> generator.generate(request(List.of(faq(1L))), handler))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode())
                        .isEqualTo(LlmErrorCode.INVALID_RESPONSE));

        // SSE가 완료로 끝내지 않도록 onComplete 대신 onError만 받아야 한다
        assertThat(handler.completed).isFalse();
        assertThat(handler.error).isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("handler가 없으면 호출할 수 없다")
    void handler가_없으면_거부한다() {
        RagAnswerGenerator generator = generator(new StubClient(List.of("답변")));

        assertThatThrownBy(() -> generator.generate(request(List.of(faq(1L))), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("handler");
    }

    @Test
    @DisplayName("사용자 질문이 비어 있으면 요청을 만들 수 없다")
    void 빈_질문은_거부한다() {
        assertThatThrownBy(() -> AnswerRequest.builder()
                .userQuery(" ")
                .searchResults(List.of(faq(1L)))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("생성에 실패하면 onError로 알리고 예외를 던진다")
    void 실패하면_예외를_던진다() {
        RagAnswerGenerator generator = generator(
                new StubClient(new GeneralException(LlmErrorCode.TIMEOUT)));

        assertThatThrownBy(() -> generator.generate(request(List.of(faq(1L))), handler))
                .isInstanceOf(GeneralException.class);

        assertThat(handler.error).isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("재시도하면 앞서 모은 토큰을 버린다")
    void 재시도하면_토큰을_버린다() {
        RagAnswerGenerator generator = generator(new RetryingStubClient());

        AnswerResult result = generator.generate(request(List.of(faq(1L))), handler);

        assertThat(result.answer()).isEqualTo("정상 답변");
        assertThat(handler.retries).isEqualTo(1);
    }

    @Test
    @DisplayName("사용자 질문의 금액을 근거 없이 확정한 답변 대신 안전 안내를 반환한다")
    void 질문의_금액을_사실로_확정하면_안전_안내를_반환한다() {
        RagAnswerGenerator generator = generator(
                new StubClient(List.of("네, 5일 로밍 요금은 총 84,700원입니다.")));
        AnswerRequest request = request(
                "5일 로밍 요금이 84,700원 맞나요?",
                List.of(faq(1L, "데이터 무제한 로밍은 하루 12,100원입니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.NO_EVIDENCE);
        assertThat(handler.tokens).containsExactly(result.answer());
        assertThat(handler.error).isNull();

        assertThat(handler.completed).isTrue();
    }

    @Test
    @DisplayName("사용자 입력값을 사실 확정 없이 되받은 답변은 생성 완료한다")
    void 사용자_입력값을_되받은_답변은_생성_완료한다() {
        String answer = "말씀하신 5일 일정은 안내된 정보에 없습니다.";
        RagAnswerGenerator generator = generator(new StubClient(List.of(answer)));
        AnswerRequest request = request(
                "5일 여행인데 언제 로밍을 신청하나요?",
                List.of(faq(1L, "로밍은 출국 전에 신청하는 것이 좋습니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(answer);
        assertThat(handler.completed).isTrue();
    }

    @Test
    @DisplayName("FAQ 질문에만 있는 금액은 답변 근거로 인정하지 않는다")
    void FAQ_질문의_금액은_답변_근거가_아니다() {
        RagAnswerGenerator generator = generator(
                new StubClient(List.of("5일 로밍 요금은 총 84,700원입니다.")));
        AnswerRequest request = request(
                "5일 로밍 요금은 얼마인가요?",
                List.of(faq(
                        1L,
                        "5일 로밍 요금이 84,700원인가요?",
                        "데이터 무제한 로밍은 하루 12,100원입니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.NO_EVIDENCE);
        assertThat(handler.tokens).containsExactly(result.answer());
        assertThat(handler.error).isNull();
    }

    @Test
    @DisplayName("FAQ 답변의 상한을 넘는 사용자 기간을 범위 밖으로 안내하면 생성 완료한다")
    void FAQ_답변의_상한을_넘는_사용자_기간은_범위_밖으로_안내한다() {
        String answer = "요금납부확인서는 최근 3년분까지만 발급 가능하여 4년 전 기록은 제공되지 않습니다.";
        RagAnswerGenerator generator = generator(new StubClient(List.of(answer)));
        AnswerRequest request = request(
                "4년 전 요금 납부 기록을 발급받을 수 있나요?",
                List.of(faq(
                        1L,
                        "4년 전 요금 낸 기록이 필요한데 안 나와요",
                        "요금납부확인서는 최근 3년분까지만 발급됩니다. 그 이전 기록은 발급 범위 밖입니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(answer);
        assertThat(handler.completed).isTrue();
    }

    @Test
    @DisplayName("FAQ 근거와 의미가 같은 비교 표현은 단어가 달라도 생성 완료한다")
    void FAQ_근거와_같은_비교_표현은_단어가_달라도_생성_완료한다() {
        String answer = "제일 싼 알뜰 요금제는 월 15,000원이며 데이터는 1.5GB 제공됩니다.";
        RagAnswerGenerator generator = generator(new StubClient(List.of(answer)));
        AnswerRequest request = request(
                "제일 싼 알뜰 상품의 가격과 데이터를 알려주세요.",
                List.of(faq(
                        1L,
                        "가장 저렴한 요금제가 무엇인가요?",
                        "알뜰 미니는 월 15,000원으로 가장 저렴하며 데이터 1.5GB를 제공합니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(answer);
        assertThat(handler.completed).isTrue();
    }

    @Test
    @DisplayName("근거에 없는 부가세 포함 답변은 근거 없음으로 처리한다")
    void 근거에_없는_부가세_포함을_제거한다() {
        RagAnswerGenerator generator = generator(
                new StubClient(List.of("eSIM 발급 비용 2,750원은 부가세가 포함된 금액입니다.")));
        AnswerRequest request = request(
                "부가세 포함인가요?",
                List.of(faq(1L, "eSIM 발급 비용은 2,750원입니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.NO_EVIDENCE);
    }

    @Test
    @DisplayName("근거에 없는 카드와 현금 결제 답변은 근거 없음으로 처리한다")
    void 근거에_없는_결제_수단을_제거한다() {
        RagAnswerGenerator generator = generator(
                new StubClient(List.of("유심 재발급 비용은 현금이나 카드로 결제가 가능합니다.")));
        AnswerRequest request = request(
                "카드 결제가 되나요?",
                List.of(faq(1L, "유심 재발급 비용은 7,700원입니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("근거에 없는 현금영수증과 뒤따르는 절차 안내를 함께 제거한다")
    void 근거에_없는_현금영수증과_절차_안내를_제거한다() {
        String answer = "현금영수증 발급이 가능합니다. 발급 시 관련 절차를 요청하시면 됩니다.";
        RagAnswerGenerator generator = generator(new StubClient(List.of(answer)));
        AnswerRequest request = request(
                "현금영수증 발급이 되나요?",
                List.of(faq(1L, "유심 재발급 비용은 7,700원입니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("근거에 없는 정책 문장만 제거하고 근거 문장은 유지한다")
    void 근거에_없는_택배비와_위약금_문장만_제거한다() {
        String answer = "재발급 비용은 7,700원입니다. 택배비는 이 금액에 포함됩니다. 위약금은 없습니다.";
        RagAnswerGenerator generator = generator(new StubClient(List.of(answer)));
        AnswerRequest request = request(
                "추가 비용이 있나요?",
                List.of(faq(1L, "재발급 비용은 7,700원이며 택배로 2~3 영업일이 걸립니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo("재발급 비용은 7,700원입니다.");
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.GROUNDED);
    }

    @Test
    @DisplayName("질문한 정책이 근거에 없다고 명시한 답변은 유지한다")
    void 정책_근거_없음_안내는_유지한다() {
        String answer = "제공된 정보에는 카드 할부에 대한 내용이 포함되어 있지 않습니다."
                + " 할부 관련 사항은 매장에 문의해 주세요.";
        RagAnswerGenerator generator = generator(new StubClient(List.of(answer)));
        AnswerRequest request = request(
                "카드 할부도 되나요?",
                List.of(faq(1L, "유심 재발급 비용은 7,700원입니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer())
                .isEqualTo("제공된 정보에는 카드 할부에 대한 내용이 포함되어 있지 않습니다.");
    }

    @Test
    @DisplayName("근거에 명시된 카드 결제 정책은 유지한다")
    void 근거에_있는_정책은_유지한다() {
        String answer = "신용카드로 납부할 수 있습니다.";
        RagAnswerGenerator generator = generator(new StubClient(List.of(answer)));
        AnswerRequest request = request(
                "카드 납부가 되나요?",
                List.of(faq(1L, "요금은 계좌이체와 신용카드로 납부할 수 있습니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(answer);
    }

    @Test
    @DisplayName("RAG 완료 시 근거와 반대인 수수료 문장만 제거한다")
    void 근거와_반대인_수수료_문장만_제거한다() {
        String answer = "가족 간에도 명의변경 수수료는 면제되지 않습니다. "
                + "모든 명의변경에 대해 수수료가 발생하지 않습니다.";
        RagAnswerGenerator generator = generator(new StubClient(List.of(answer)));
        AnswerRequest request = request(
                "가족 간에는 명의변경 수수료가 면제되나요?",
                List.of(faq(1L, "가족 여부와 상관없이 명의변경 수수료는 없습니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo("모든 명의변경에 대해 수수료가 발생하지 않습니다.");
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.GROUNDED);
    }

    @Test
    @DisplayName("RAG 완료 시 근거에 없는 기능 미지원 문장만 제거한다")
    void 근거에_없는_기능_미지원_문장만_제거한다() {
        String answer = "로밍은 하루 9,900원입니다. 로밍 요금제 변경 기능은 제공하지 않습니다.";
        RagAnswerGenerator generator = generator(new StubClient(List.of(answer)));
        AnswerRequest request = request(
                "로밍 요금제 얼마고 현지에서 끊을 수 있어요?",
                List.of(faq(1L, "일 단위 로밍 요금제는 9,900원입니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo("로밍은 하루 9,900원입니다.");
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.GROUNDED);
    }

    @Test
    @DisplayName("RAG 완료 시 근거 없는 마감 시점과 종속 설명을 함께 제거한다")
    void 근거에_없는_마감과_종속_설명을_함께_제거한다() {
        String answer = "최소한 출국 당일 아침까지는 로밍 신청을 완료해야 합니다. "
                + "이는 신청 후 처리 시간을 고려한 것입니다.";
        RagAnswerGenerator generator = generator(new StubClient(List.of(answer)));
        AnswerRequest request = request(
                "로밍은 출국 며칠 전까지 신청해야 하나요?",
                List.of(faq(1L, "출국 전 신청을 권장하지만 현지 도착 후에도 신청할 수 있습니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.NO_EVIDENCE);
    }

    @Test
    @DisplayName("RAG 완료 시 다른 정책의 상한을 근거로 사용하면 차단한다")
    void 다른_정책의_상한을_근거로_사용하면_차단한다() {
        RagAnswerGenerator generator = generator(
                new StubClient(List.of("요금납부확인서는 4년 전 기록은 제공되지 않습니다.")));
        AnswerRequest request = request(
                "4년 전 요금납부확인서를 발급할 수 있나요?",
                List.of(faq(1L, "통화기록은 최근 3년분까지만 조회할 수 있습니다.")));

        AnswerResult result = generator.generate(request, handler);

        assertThat(result.answer()).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(result.answerBasis()).isEqualTo(AnswerBasis.NO_EVIDENCE);
        assertThat(handler.tokens).containsExactly(result.answer());
        assertThat(handler.error).isNull();
    }

    private RagAnswerGenerator generator(LlmClient client) {
        return generator(client, ExecutionTrace.noop());
    }

    private RagAnswerGenerator generator(LlmClient client, ExecutionTrace trace) {
        // 판정은 꺼진 상태가 기본이라 항상 통과한다
        EvidenceRelevanceChecker checker = new EvidenceRelevanceChecker(
                client, new ObjectMapper(), new EvidenceCheckProperties(false, 300));
        return new RagAnswerGenerator(
                client, new AnswerContextConverter(), new AnswerGuard(), checker, recorder, trace);
    }

    private AnswerRequest request(List<FaqSearchResponse> searchResults) {
        return request("요금제 바꾸고 싶어요", searchResults);
    }

    private AnswerRequest request(String userQuery, List<FaqSearchResponse> searchResults) {
        return AnswerRequest.builder()
                .executionId(42L)
                .userQuery(userQuery)
                .conditions(Map.of("location", "강남"))
                .searchResults(searchResults)
                .build();
    }

    private FaqSearchResponse faq(Long faqId) {
        return faq(faqId, "답변" + faqId);
    }

    private FaqSearchResponse faq(Long faqId, String answer) {
        return faq(faqId, "질문" + faqId, answer);
    }

    private FaqSearchResponse faq(Long faqId, String question, String answer) {
        return new FaqSearchResponse(
                faqId, null, "BILLING", question, answer,
                0.9, 1, LocalDate.of(2026, 9, 17), faqId.intValue(), null);
    }

    private static final class StubClient implements LlmClient {
        private final List<String> tokens;
        private final RuntimeException failure;
        private boolean called;
        private LlmRequest received;

        private StubClient(List<String> tokens) {
            this(tokens, null);
        }

        private StubClient(RuntimeException failure) {
            this(List.of(), failure);
        }

        private StubClient(List<String> tokens, RuntimeException failure) {
            this.tokens = tokens;
            this.failure = failure;
        }

        @Override
        public String generate(LlmRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            called = true;
            received = request;
            if (failure != null) {
                handler.onError(failure);
                return;
            }
            tokens.forEach(handler::onToken);
            handler.onComplete();
        }
    }

    // 첫 시도에서 토큰을 흘린 뒤 재시도하는 상황
    private static final class RetryingStubClient implements LlmClient {
        @Override
        public String generate(LlmRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            handler.onToken("버려질 ");
            handler.onRetry(2, new GeneralException(LlmErrorCode.TIMEOUT));
            handler.onToken("정상 답변");
            handler.onComplete();
        }
    }

    private static final class SpyRecorder extends LlmGenerationRecorder {
        private final List<Status> statuses = new ArrayList<>();

        private SpyRecorder() {
            super((LlmGenerationRepository) null, null, null, null);
        }

        @Override
        public void record(LlmRequest request, String model, Result result) {
            statuses.add(result.status());
        }
    }

    private static final class RecordingHandler implements LlmStreamHandler {
        private final List<String> tokens = new ArrayList<>();
        private boolean completed;
        private Throwable error;
        private int retries;

        @Override
        public void onToken(String token) {
            tokens.add(token);
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }

        @Override
        public void onRetry(int attempt, Throwable cause) {
            retries++;
        }
    }
}
