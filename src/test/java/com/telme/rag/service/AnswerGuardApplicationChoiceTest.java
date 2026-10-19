package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnswerGuardApplicationChoiceTest {
    private static final String CONTEXT = "매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다.";
    private static final String CHOICE = "고객센터 전화, 홈페이지, 또는 매장 방문 중 편하신 방법으로 신청하시면 됩니다.";
    private static final String QUESTION = "인터넷 해지 신청 방법";
    private final AnswerGuard guard = new AnswerGuard();

    @ParameterizedTest
    @ValueSource(strings = {
        CHOICE,
        "매장 방문 또는 홈페이지, 고객센터 전화 중 편하신 방법으로 신청하시면 됩니다.",
        "홈페이지, 매장 방문, 고객센터 전화 가운데 편하신 창구로 신청하세요."
    })
    @DisplayName("근거와 같은 신청 방법을 고르는 안내는 순서와 표현이 달라도 전체 답변 검사에서 보존한다")
    void 근거와_같은_신청_방법_선택을_보존한다(String answer) {
        assertThat(guard.applyEvidencePolicy(answer, CONTEXT, QUESTION)).isEqualTo(answer);
    }

    @Test
    @DisplayName("공통 신청 창구의 선택 안내를 근거 없는 신청 권유로 제거하지 않는다")
    void 신청_검사에서_근거_있는_선택_안내를_보존한다() {
        assertThat(guard.trimUnsupportedApplicationAdvice(CHOICE, CONTEXT)).isEqualTo(CHOICE);
    }

    @Test
    @DisplayName("근거 있는 방법 중 고객이 고르라는 문구를 편의성 비교 주장으로 제거하지 않는다")
    void 비교_검사에서_중립적인_선택_안내를_보존한다() {
        assertThat(guard.trimUngroundedComparisons(CHOICE, CONTEXT, QUESTION)).isEqualTo(CHOICE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "고객센터 전화, 홈페이지, 매장 방문, 앱 중 편하신 방법으로 신청하시면 됩니다.",
        "홈페이지, 매장 방문 중 편하신 방법으로 신청하시면 됩니다.",
        "고객센터 방문, 홈페이지, 매장 전화 중 편하신 방법으로 신청하시면 됩니다.",
        "고객센터 전화, 홈페이지, 매장 방문 중 편하신 방법으로 접수하시면 됩니다.",
        "eSIM은 고객센터 전화, 홈페이지, 매장 방문 중 편하신 방법으로 신청하시면 됩니다.",
        "신분증 없이 고객센터 전화, 홈페이지, 매장 방문 중 편하신 방법으로 신청하시면 됩니다.",
        "대리인도 고객센터 전화, 홈페이지, 매장 방문 중 편하신 방법으로 신청하시면 됩니다.",
        "고객센터 전화, 홈페이지, 매장 방문 중 편하신 방법으로 무료 신청하시면 됩니다.",
        "고객센터 전화, 홈페이지, 매장 방문 중 편하신 방법으로 즉시 신청하시면 됩니다."
    })
    @DisplayName("선택 안내라도 창구·이용 방법·행위·대상·조건을 바꾸거나 추가하면 허용하지 않는다")
    void 선택_안내를_이용한_잘못된_신청_정보를_차단한다(String answer) {
        assertThat(guard.applyEvidencePolicy(answer, CONTEXT, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "로밍은 매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다.",
        "본인만 매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다.",
        "신분증을 지참한 경우에만 매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다.",
        "매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다. 본인 확인이 필요합니다.",
        "매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다. 대리인은 신청할 수 없습니다.",
        "매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다. 로밍은 앱에서 신청하세요.",
        "매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다. 신청은 접수되지 않았습니다.",
        "매장에 가시거나 고객센터에 전화하시거나 홈페이지로 접수하시면 됩니다."
    })
    @DisplayName("근거에 특정 업무·추가 조건·다른 행위가 있으면 대상 없는 공통 선택 안내로 바꾸지 않는다")
    void 조건이_있거나_모호한_근거는_공통_신청으로_확대하지_않는다(String context) {
        assertThat(guard.applyEvidencePolicy(CHOICE, context, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "홈페이지로 신청하는 것이 더 편리합니다.",
        "홈페이지 신청이 더 빠릅니다.",
        "고객센터 전화, 홈페이지, 매장 방문 중 홈페이지가 가장 편리하니 신청하시면 됩니다."
    })
    @DisplayName("고객의 선택 안내와 달리 근거 없는 편의성·속도 우위 주장은 계속 차단한다")
    void 근거_없는_우위_주장은_차단한다(String answer) {
        assertThat(guard.applyEvidencePolicy(answer, CONTEXT, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @ParameterizedTest
    @ValueSource(strings = {CHOICE, "고객센터 전화, 홈페이지, 매장 방문 중 편하신 창구로 신청하시면 됩니다."})
    @DisplayName("고객의 선택을 안내한 근거를 특정 방법이 더 편리하다는 주장의 근거로 사용하지 않는다")
    void 선택_안내는_편의성_우위의_근거가_아니다(String context) {
        assertThat(guard.applyEvidencePolicy("홈페이지로 신청하는 것이 더 편리합니다.", context, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "고객센터에 전화하여, 홈페이지로 신청하시면 됩니다.",
        "고객센터에 전화해서, 홈페이지로 신청하시면 됩니다.",
        "고객센터에 전화하시어, 홈페이지로 신청하시면 됩니다."
    })
    @DisplayName("선행 절차가 있는 근거를 고객이 하나만 고를 수 있는 선택 안내로 바꾸지 않는다")
    void 순서가_있는_절차를_선택지로_바꾸지_않는다(String context) {
        String answer = "고객센터 전화, 홈페이지 중 편하신 방법으로 신청하시면 됩니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "고객센터에 전화하여, 홈페이지로 신청하시면 됩니다.",
        "고객센터에 전화해서, 홈페이지로 신청하시면 됩니다.",
        "고객센터에 전화하시어, 홈페이지로 신청하시면 됩니다."
    })
    @DisplayName("원문 그대로 안내한 순차 신청 절차는 필요한 단계를 포함해 보존한다")
    void 근거의_순차_절차_원문을_보존한다(String context) {
        assertThat(guard.applyEvidencePolicy(context, context, QUESTION)).isEqualTo(context);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "고객센터에 전화하시거나 매장에 방문하여 신청하시면 됩니다.",
        "고객센터에 전화하시거나 매장에 방문해서 신청하시면 됩니다.",
        "고객센터에 전화하시거나 매장에 방문하시어 신청하시면 됩니다."
    })
    @DisplayName("마지막 선택지의 방문 동작을 신청과 연결한 표현은 정상 선택 안내로 유지한다")
    void 마지막_방법과_신청의_연결을_보존한다(String context) {
        String answer = "고객센터 전화, 매장 방문 중 편하신 방법으로 신청하시면 됩니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, QUESTION)).isEqualTo(answer);
    }

    @Test
    @DisplayName("중립 선택 안내 외에 실제 편의성 비교 근거가 따로 있으면 해당 비교는 보존한다")
    void 별도의_명시적_비교_근거를_유지한다() {
        String answer = "홈페이지가 더 편리합니다.";

        assertThat(guard.applyEvidencePolicy(answer, CHOICE + " " + answer, QUESTION)).isEqualTo(answer);
    }

    @Test
    @DisplayName("조건과 함께 원문 그대로 전달된 선택 안내는 조건을 포함해 모두 유지한다")
    void 원문_선택_안내와_추가_조건을_함께_보존한다() {
        String evidence = CHOICE + " 본인 확인이 필요합니다.";

        assertThat(guard.applyEvidencePolicy(evidence, evidence, QUESTION)).isEqualTo(evidence);
    }

    @Test
    @DisplayName("같은 업무의 실제 신청 절차는 준비물과 방문자 조건을 포함한 원문으로 유지한다")
    void 기존_업무_원문_복원과_방문자_조건을_유지한다() {
        String evidence = "대리인은 위임장과 신분증을 지참한 경우에만 매장에서 유심 재발급을 신청하시면 됩니다.";

        assertThat(guard.applyEvidencePolicy("유심 재발급을 신청하세요.", evidence, "유심 재발급 절차"))
                .isEqualTo(evidence);
    }

    @Test
    @DisplayName("다른 업무의 신청 절차를 근거 있는 선택 안내 예외로 가져오지 않는다")
    void 로밍_신청을_이심_신청에_가져오지_않는다() {
        String context = "로밍은 앱에서 신청하세요. eSIM 발급 비용은 2,750원입니다.";

        assertThat(guard.applyEvidencePolicy("eSIM을 신청하세요.", context, "eSIM 신청 방법"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }
}
