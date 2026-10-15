package com.telme.chat.safety;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.guard.MaskSpan;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.service.ChatAnswer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

class ChatOutputGuardTest {

    private final ChatOutputGuard guard = new ChatOutputGuard(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {
            "씨발", "씨 팔", "씨.발", "씨123발", "씨\u200B발", "씨발ㅋㅋ", "씨발ㅠㅠ",
            "병신아", "병신이잖아", "ㅅㅂ", "ㅂㅅㅠㅠ", "서비스ㅂㅅ같아", "이거씨발",
            "너병신", "씨발요금 왜 이래", "'병신'이라고 하셨네요", "씨발은 너야",
            "시발역 안내. 씨발", "씨발아\n\n납부 내역 안내"
    })
    @DisplayName("최종 출력의 명확한 욕설·초성·검증한 결합과 원문 인용을 차단한다")
    void 욕설이_포함된_최종_출력을_차단한다(String content) {
        ChatOutputBlockedException error = assertThrows(
                ChatOutputBlockedException.class, () -> guard.verify(answer(content)));
        assertThat(error.getErrorCode()).isEqualTo(ChatErrorCode.OUTPUT_POLICY_BLOCKED);
        assertThat(error.policyVersion()).isEqualTo("chat-output-guard-v1");
        assertThat(error.field()).isEqualTo("content");
        assertThat(error.ruleIds()).isNotEmpty();
        assertThat(error.toString()).doesNotContain(content);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "유심 다시 발급받고 싶어요", "택배는 몇 시 발송돼요?", "오후 3시 발송인가요",
            "새 요금제 출시 발표 언제예요?", "개통 시 발신 제한이 있나요?", "병 신청은 어디서 해요",
            "아저씨 발 사이즈", "유심 다시발급 안내", "출시발표 안내", "병신청 안내",
            "다시 발급ㅋㅋ", "출시 발표ㅠㅠ", "시발점 안내", "시발역에서 출발합니다",
            "시발열차 시간", "입대할 병 신임 교육 안내", "ㅂㅅ역 근처 매장",
            "재발급 비용은 7,700원입니다.", "가입한 달에는 변경할 수 없습니다.", "안녕하세요. 질문해 주세요."
    })
    @DisplayName("정상 단어·금액·조건·기본 대화는 바꾸거나 차단하지 않는다")
    void 정상_문구와_근거_안내는_보존한다(String content) {
        ChatAnswer answer = answer(content);
        assertThatCode(() -> guard.verify(answer)).doesNotThrowAnyException();
        assertThat(answer.content()).isEqualTo(content);
    }

    @Test
    @DisplayName("본문이 정상이더라도 추천 질문의 욕설을 차단한다")
    void 추천_질문도_검사한다() {
        ChatAnswer answer = new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "정상 안내", ChatMessage.AnswerBasis.GROUNDED,
                List.of("씨발 비용은?"), null);
        ChatOutputBlockedException error = assertThrows(
                ChatOutputBlockedException.class, () -> guard.verify(answer));
        assertThat(error.field()).isEqualTo("followUps");
    }

    @Test
    @DisplayName("되묻기 질문과 선택지에도 같은 출력 정책을 적용한다")
    void 되묻기와_선택지를_검사한다() {
        assertThatThrownBy(() -> guard.verifyClarification("씨발 가입하셨나요?", List.of()))
                .isInstanceOf(ChatOutputBlockedException.class);
        ChatOutputBlockedException error = assertThrows(ChatOutputBlockedException.class,
                () -> guard.verifyClarification("어떻게 받으시나요?", List.of("이메일", "병신")));
        assertThat(error.field()).isEqualTo("clarificationOptions");
    }

    @Test
    @DisplayName("매장 스냅샷·검색 기준의 표시 문구도 검사하고 원본 데이터는 수정하지 않는다")
    void 매장_표시_문구를_검사한다() {
        Map<String, Object> store = Map.of("storeId", 4, "name", "씨발");
        ChatAnswer answer = new ChatAnswer(
                ChatMessage.MessageType.STORE_RESULT, null, null, List.of(), List.of(store));
        assertThatThrownBy(() -> guard.verify(answer)).isInstanceOf(ChatOutputBlockedException.class);
        assertThat(store).containsEntry("storeId", 4).containsEntry("name", "씨발");
        ChatAnswer unsafeLabel = new ChatAnswer(
                ChatMessage.MessageType.STORE_RESULT, "정상 안내", null, List.of(),
                List.of(Map.of("storeId", 4, "name", "정상 매장")),
                new ChatStoreSearchContextResponse(ChatStoreSearchContextResponse.Type.PLACE, "씨발", null));
        assertThatThrownBy(() -> guard.verify(unsafeLabel)).isInstanceOf(ChatOutputBlockedException.class);
    }

    @Test
    @DisplayName("검사할 수 없는 순환 메타데이터를 그대로 통과시키거나 무한 재귀하지 않는다")
    void 잘못된_중첩_자료는_검사를_종료한다() {
        Map<String, Object> cycle = new HashMap<>();
        cycle.put("self", cycle);
        ChatAnswer answer = new ChatAnswer(
                ChatMessage.MessageType.STORE_RESULT, "정상 안내", null, List.of(), List.of(cycle));
        assertThatThrownBy(() -> guard.verify(answer)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "개 4111111111111111 새끼 유심 문의 | 개 *** 새끼 유심 문의",
        "씨 990101-1234567 발 요금 문의 | 씨 *** 발 요금 문의"
    })
    @DisplayName("서버 마스크 인용은 모든 출력 경로에서 허용하지만 출처 없는 같은 별표는 차단한다")
    void 실제_마스크_인용만_출력_경계로_검사한다(String original, String visible) throws Exception {
        var inspection = new ChatInputInspector(new ObjectMapper()).inspect(original);
        var masks = new OutputMaskContext(List.of(OutputMaskContext.Source.from(inspection)));
        var mapper = new ObjectMapper();
        String strings = mapper.writeValueAsString(List.of(visible));
        var stores = List.<Map<String, Object>>of(Map.of("name", visible));
        var context = new ChatStoreSearchContextResponse(ChatStoreSearchContextResponse.Type.PLACE, visible, null);
        var content = answer(visible);
        var followUp = new ChatAnswer(ChatMessage.MessageType.ANSWER, "정상 안내", null, List.of(visible), null);
        var store = new ChatAnswer(ChatMessage.MessageType.STORE_RESULT, "정상 안내", null, List.of(), stores);
        var label = new ChatAnswer(ChatMessage.MessageType.STORE_RESULT, "정상 안내", null,
                List.of(), List.of(Map.of("name", "정상 매장")), context);
        List<Runnable> paths = List.of(
                () -> guard.verify(content, masks), () -> guard.verify(followUp, masks),
                () -> guard.verify(store, masks), () -> guard.verify(label, masks),
                () -> guard.verifyClarification(visible, List.of(), masks),
                () -> guard.verifyClarification("정상 질문", List.of(visible), masks),
                () -> guard.verifySummary(strings, masks),
                () -> guard.verifySerialized(visible, null, null, null, masks),
                () -> guard.verifySerialized("정상 안내", strings, null, null, masks),
                () -> guard.verifySerialized("정상 안내", null, strings, null, masks),
                () -> guard.verifySerialized("정상 안내", null, null, strings, masks),
                () -> guard.verifySerializedClarification(visible, null, masks),
                () -> guard.verifySerializedClarification("정상 질문", strings, masks));
        paths.forEach(path -> assertThatCode(path::run).doesNotThrowAnyException());
        assertThat(content.content()).isEqualTo(visible);
        assertThat(masks.toString()).doesNotContain(visible);
        assertThatThrownBy(() -> guard.verify(content)).isInstanceOf(ChatOutputBlockedException.class);
        assertThatThrownBy(() -> guard.verifyClarification(visible, List.of()))
                .isInstanceOf(ChatOutputBlockedException.class);
        assertThatThrownBy(() -> guard.verifySummary(strings)).isInstanceOf(ChatOutputBlockedException.class);
        assertThatThrownBy(() -> guard.verify(answer(visible + " 씨***발"), masks))
                .isInstanceOf(ChatOutputBlockedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "시4111111111111111발", "씨990101-1234567발", "개4111111111111111새끼", "ㅅ4111111111111111ㅂ",
        "씨*4111111111111111*발", "'시4111111111111111발'이라고 들었어요"
    })
    @DisplayName("번호를 끼운 욕설은 서버 마스크 출처나 입력의 피해 인용 예외로 출력 검사를 우회하지 못한다")
    void 번호를_끼운_욕설_출력을_차단한다(String original) throws Exception {
        var inspection = new ChatInputInspector(new ObjectMapper()).inspect(original);
        var masks = new OutputMaskContext(List.of(OutputMaskContext.Source.from(inspection)));
        var visible = inspection.content();
        var strings = new ObjectMapper().writeValueAsString(List.of(visible));

        assertThatThrownBy(() -> guard.verify(answer(visible), masks))
                .isInstanceOf(ChatOutputBlockedException.class);
        assertThatThrownBy(() -> guard.verifySerialized(visible, null, null, null, masks))
                .isInstanceOf(ChatOutputBlockedException.class);
        assertThatThrownBy(() -> guard.verifyClarification(visible, List.of(), masks))
                .isInstanceOf(ChatOutputBlockedException.class);
        assertThatThrownBy(() -> guard.verifySummary(strings, masks))
                .isInstanceOf(ChatOutputBlockedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "다시4111111111111111발급 문의", "출시4111111111111111발표 안내", "시4111111111111111발점 안내"
    })
    @DisplayName("공백 없는 정상 단어의 마스크 인용은 기존 출력 단어 경계로 허용한다")
    void 번호_사이_정상_단어_출력을_유지한다(String original) {
        var inspection = new ChatInputInspector(new ObjectMapper()).inspect(original);
        var masks = new OutputMaskContext(List.of(OutputMaskContext.Source.from(inspection)));

        assertThatCode(() -> guard.verify(answer(inspection.content()), masks)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"씨***발", "개***새끼", "씨 *** 발", "개 *** 새끼", "씨**발", "씨******발"})
    @DisplayName("다른 정상 마스크가 있어도 새로 생성한 별표 욕설을 허용하지 않는다")
    void 다른_문맥의_마스크로_별표_욕설을_우회하지_못한다(String unsafe) {
        var inspection = new ChatInputInspector(new ObjectMapper()).inspect("카드 4111111111111111 문의");
        var masks = new OutputMaskContext(List.of(OutputMaskContext.Source.from(inspection)));
        assertThatThrownBy(() -> guard.verify(answer(unsafe), masks)).isInstanceOf(ChatOutputBlockedException.class);
    }

    @Test
    @DisplayName("원문에 없던 단어를 마스크 양옆에 붙이면 신뢰한 인용으로 취급하지 않는다")
    void 마스크_양옆_단어와_경계를_확인한다() {
        var inspector = new ChatInputInspector(new ObjectMapper());
        var masks = new OutputMaskContext(List.of(OutputMaskContext.Source.from(
                inspector.inspect("씨 990101-1234567 발 요금 문의"))));
        assertThatThrownBy(() -> guard.verify(answer("씨 *** 발아"), masks))
                .isInstanceOf(ChatOutputBlockedException.class);
        var leading = new OutputMaskContext(List.of(OutputMaskContext.Source.from(
                inspector.inspect("990101-1234567 발 요금 문의"))));
        assertThatThrownBy(() -> guard.verify(answer("씨 *** 발 요금 문의"), leading))
                .isInstanceOf(ChatOutputBlockedException.class);
    }

    @Test
    @DisplayName("문구와 다르거나 겹치는 마스크 위치를 검사 출처로 받지 않는다")
    void 잘못된_마스크_위치로_검사를_완화하지_않는다() {
        assertThatThrownBy(() -> new OutputMaskContext.Source("씨 *** 발", List.of(new MaskSpan(0, 3))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OutputMaskContext.Source("******",
                List.of(new MaskSpan(0, 3), new MaskSpan(2, 5))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MaskSpan(Integer.MAX_VALUE, Integer.MIN_VALUE + 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ChatAnswer answer(String content) {
        return new ChatAnswer(
                ChatMessage.MessageType.ANSWER, content, ChatMessage.AnswerBasis.GROUNDED, List.of(), null);
    }
}
