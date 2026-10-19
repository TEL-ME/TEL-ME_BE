package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.repository.ChatEndSummaryInquiryRepository.Classification;
import com.telme.chat.safety.OutputMaskContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChatEndSummaryGeneralContinuationTest {
    @ParameterizedTest
    @ValueSource(strings = {"BILLING", "PLAN", "ROAMING", "DEVICE", "NAME_CHANGE",
            "PORTING", "SERVICE", "SUBSCRIBE", "TERMINATE"})
    @DisplayName("유심 외 분야도 명시적으로 같은 문의를 이어 물으면 같은 묶음으로 연결한다")
    void 일반_분야의_명시적_이어묻기를_연결한다(String category) {
        var roots = ChatEndSummaryInquiryLinker.resolveRoots(input(category, "제 회선의 처리 방법을 알려주세요.",
                "아까 제 회선과 같은 문의를 이어서 여쭤볼게요. 그 안내의 조건도 알려주세요."));

        assertThat(roots.get(3L)).isEqualTo(roots.get(1L));
    }

    @ParameterizedTest
    @CsvSource({
            "제 A회선 요금제 변경 문의, 아까 어머니 A회선 요금제 변경 문의의 조건은요?",
            "제 A회선 요금제 변경 문의, 아까 제 B회선 요금제 변경 문의의 조건은요?",
            "제 요금제 변경 문의, 아까 제 요금제 추천 문의의 조건은요?",
            "제 요금제 변경 문의, 아까 그건 제 요금제 추천 문의의 조건은요?",
            "제 요금제 변경 문의, 아까와 다른 요금제로 변경하는 문의의 조건은요?",
            "형의 요금제 변경 문의, 아까 동료의 요금제 변경 문의의 조건은요?",
            "본인과 어머니 요금제 변경 문의, 아까 본인 요금제 변경 문의의 조건은요?"
    })
    @DisplayName("이어묻기 표현이 있어도 사람·회선·상품·업무가 다르면 분리한다")
    void 다른_대상과_업무를_합치지_않는다(String first, String next) {
        var roots = ChatEndSummaryInquiryLinker.resolveRoots(input("PLAN", first, next));

        assertThat(roots.get(3L)).isNotEqualTo(roots.get(1L));
    }

    @ParameterizedTest
    @CsvSource({
            "제 납부 방법 문의입니다., 아까 그건 제 환불 방법 문의입니다.",
            "제 환불 방법 문의입니다., 아까 그건 제 납부 방법 문의입니다."
    })
    @DisplayName("같은 청구 분야의 지시어라도 납부와 환불 업무를 합치지 않는다")
    void 같은_분야의_다른_처리_업무를_구분한다(String first, String next) {
        var roots = ChatEndSummaryInquiryLinker.resolveRoots(input("BILLING", first, next));

        assertThat(roots.get(3L)).isNotEqualTo(roots.get(1L));
    }

    @ParameterizedTest
    @CsvSource({
            "앞서 제 A회선 명의 양도 문의의 그 안내를 다시 확인합니다., true",
            "앞서 제 B회선 명의 양도 문의의 그 안내를 다시 확인합니다., false",
            "앞서 동료 A회선 명의 양도 문의의 그 안내를 다시 확인합니다., false"
    })
    @DisplayName("명의 양도 당사자 중 본인의 명시적 같은 회선 참조만 원래 업무에 연결한다")
    void 명의_양도_두_당사자와_다른_문의를_구분한다(String next, boolean linked) {
        var roots = ChatEndSummaryInquiryLinker.resolveRoots(input("NAME_CHANGE",
                "제 A회선 문의입니다. 명의를 아버지한테서 저한테 바꾸려면 뭐가 필요해요?", next));

        assertThat(roots.get(3L).equals(roots.get(1L))).isEqualTo(linked);
    }

    @ParameterizedTest
    @CsvSource({"C,C,true", "C,D,false", "Z,Z,true", "1,1,true", "1,2,false"})
    @DisplayName("명의 양도 회선의 영문 한 글자와 숫자 식별자를 정확히 구분한다")
    void 확장된_회선_식별자의_같음과_다름을_확인한다(String first, String next, boolean linked) {
        var roots = ChatEndSummaryInquiryLinker.resolveRoots(input("NAME_CHANGE",
                "제 " + first + "회선 명의를 어머니한테서 저한테 바꾸는 문의입니다.",
                "앞서 제 " + next + "회선 명의 양도의 그 안내를 다시 확인합니다."));

        assertThat(roots.get(3L).equals(roots.get(1L))).isEqualTo(linked);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eSIM회선", "LTE회선", "5G회선"})
    @DisplayName("상품 이름의 끝 글자를 임의의 회선 식별자로 사용하지 않는다")
    void 상품_이름을_회선_식별자로_오인하지_않는다(String product) {
        var roots = ChatEndSummaryInquiryLinker.resolveRoots(input("USIM",
                "제 " + product.replace("회선", "") + " 유심 교체 문의입니다.",
                "앞서 제 " + product + " 유심 교체의 같은 문의입니다."));

        assertThat(roots.get(3L)).isEqualTo(roots.get(1L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"BILLING", "PLAN", "ROAMING"})
    @DisplayName("같은 분야라도 연결 표현이 없는 질문은 임의로 합치지 않는다")
    void 연결_근거_없는_동일_분야를_분리한다(String category) {
        var roots = ChatEndSummaryInquiryLinker.resolveRoots(input(category,
                "제 회선의 처리 방법을 알려주세요.", "제 회선의 처리 비용을 알려주세요."));

        assertThat(roots.get(3L)).isNotEqualTo(roots.get(1L));
    }

    private ChatEndSummaryData.Input input(String category, String first, String next) {
        var classification = new Classification("FAQ", List.of(category));
        return new ChatEndSummaryData.Input(1, 4, 1L, List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", first, null, "COMPLETED"),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "ANSWER", "안내 원문입니다.",
                        "GROUNDED", "COMPLETED"),
                new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION", next, null, "COMPLETED"),
                new ChatEndSummaryData.Message(4, 4, 3L, "ASSISTANT", "ANSWER", "추가 안내 원문입니다.",
                        "GROUNDED", "COMPLETED")), List.of(), Set.of(), List.of(), OutputMaskContext.empty(),
                Map.of(1L, classification, 2L, classification, 3L, classification, 4L, classification));
    }
}
