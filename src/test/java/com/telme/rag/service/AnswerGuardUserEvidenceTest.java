package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.rag.exception.AnswerGuardException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnswerGuardUserEvidenceTest {

    private final AnswerGuard guard = new AnswerGuard();

    @Test
    @DisplayName("고객 질문의 금액을 근거 없이 사실로 확정하면 차단한다")
    void 질문의_금액을_사실로_확정하면_차단한다() {
        String context = "데이터 무제한 로밍은 하루 12,100원입니다.";
        String answer = "네, 5일 로밍 요금은 총 84,700원입니다.";

        assertThatThrownBy(() -> guard.verifyAmounts(
                answer, context, "5일 로밍 요금이 84,700원 맞나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("84700");
    }

    @Test
    @DisplayName("고객 질문의 금액을 언급하고 근거 없음을 밝혀야 통과시킨다")
    void 질문의_금액을_근거_없음으로_안내하면_통과한다() {
        String answer = "말씀하신 84,700원은 안내된 정보에 없습니다.";
        String context = "데이터 무제한 로밍은 하루 12,100원입니다.";

        assertThatCode(() -> guard.verifyAmounts(
                answer, context, "5일 로밍 요금이 84,700원 맞나요?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("고객 질문의 금액을 확인할 수 없다고 안내하면 통과시킨다")
    void 질문의_금액을_확인할_수_없다고_하면_통과한다() {
        String answer = "말씀하신 84,700원은 확인할 수 없습니다.";
        String context = "데이터 무제한 로밍은 하루 12,100원입니다.";

        assertThatCode(() -> guard.verifyAmounts(
                answer, context, "5일 로밍 요금이 84,700원 맞나요?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("근거 없음의 부정 표현으로 금액을 확정하면 차단한다")
    void 질문의_금액을_없지_않다고_하면_차단한다() {
        String answer = "말씀하신 84,700원은 안내된 정보에 없지 않습니다.";
        String context = "데이터 무제한 로밍은 하루 12,100원입니다.";

        assertThatThrownBy(() -> guard.verifyAmounts(
                answer, context, "5일 로밍 요금이 84,700원 맞나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("84700");
    }

    @Test
    @DisplayName("근거 없음 문구를 뒤에 붙여 잘못된 금액을 확정해도 차단한다")
    void 질문의_금액을_확정한_뒤_근거_없음_문구를_붙여도_차단한다() {
        String answer = "말씀하신 84,700원이 맞고 다른 내용은 안내된 정보에 없습니다.";
        String context = "데이터 무제한 로밍은 하루 12,100원입니다.";

        assertThatThrownBy(() -> guard.verifyAmounts(
                answer, context, "5일 로밍 요금이 84,700원 맞나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("84700");
    }

    @Test
    @DisplayName("근거 없음 뒤에 고객 질문의 금액을 확정해도 차단한다")
    void 근거_없음_뒤에_질문의_금액을_확정해도_차단한다() {
        String answer = "말씀하신 금액은 근거에는 없지만 84,700원입니다.";
        String context = "데이터 무제한 로밍은 하루 12,100원입니다.";

        assertThatThrownBy(() -> guard.verifyAmounts(
                answer, context, "5일 로밍 요금이 84,700원 맞나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("84700");
    }

    @Test
    @DisplayName("말씀하신이라는 표현으로 잘못된 금액을 확정해도 차단한다")
    void 질문의_금액을_말씀하신으로_확정하면_차단한다() {
        String answer = "말씀하신 84,700원이 맞습니다.";
        String context = "데이터 무제한 로밍은 하루 12,100원입니다.";

        assertThatThrownBy(() -> guard.verifyAmounts(
                answer, context, "5일 로밍 요금이 84,700원 맞나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("84700");
    }

    @Test
    @DisplayName("고객 질문의 창구를 근거 없이 실제 안내처로 확정하면 제거한다")
    void 질문의_창구를_안내처로_확정하면_제거한다() {
        String answer = "고객센터로 문의하시면 확인됩니다.";
        String context = "문의는 어떻게 하나요? 담당 부서에서 확인해 드립니다.";

        assertThat(guard.trimUngroundedChannels(answer, context, "고객센터로 물어봐야 하나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("말씀하신이라는 표현으로 질문의 창구를 안내처로 확정해도 제거한다")
    void 질문의_창구를_말씀하신으로_확정해도_제거한다() {
        String answer = "말씀하신 고객센터로 문의하시면 됩니다.";
        String context = "문의는 어떻게 하나요? 담당 부서에서 확인해 드립니다.";

        assertThat(guard.trimUngroundedChannels(answer, context, "고객센터로 물어봐야 하나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("근거 없음 문구를 뒤에 붙여 질문의 창구를 안내해도 제거한다")
    void 질문의_창구를_안내한_뒤_근거_없음_문구를_붙여도_제거한다() {
        String answer = "말씀하신 고객센터로 문의하시면 되고 다른 내용은 안내된 정보에 없습니다.";
        String context = "문의는 어떻게 하나요? 담당 부서에서 확인해 드립니다.";

        assertThat(guard.trimUngroundedChannels(answer, context, "고객센터로 물어봐야 하나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("근거 없음 뒤에 질문의 창구를 안내해도 제거한다")
    void 근거_없음_뒤에_질문의_창구를_안내해도_제거한다() {
        String answer = "말씀하신 고객센터는 근거에는 없지만 그곳으로 문의하세요.";
        String context = "문의는 어떻게 하나요? 담당 부서에서 확인해 드립니다.";

        assertThat(guard.trimUngroundedChannels(answer, context, "고객센터로 물어봐야 하나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("줄바꿈으로 나뉜 답변에서는 근거 없는 문장만 제거한다")
    void 줄바꿈_답변에서_근거_없는_문장만_제거한다() {
        String answer = "유심 재발급 비용은 7,700원입니다\n자세한 내용은 이벤트 페이지를 확인해 주세요";
        String context = "유심 재발급 비용은 7,700원입니다.";

        assertThat(guard.trimUngroundedChannels(answer, context, "유심 얼마예요?"))
                .isEqualTo("유심 재발급 비용은 7,700원입니다");
    }

    @Test
    @DisplayName("고객 질문의 기간을 근거 없이 사실로 확정하면 차단한다")
    void 질문의_기간을_사실로_확정하면_차단한다() {
        String answer = "네, 배송에는 5영업일이 걸립니다.";
        String context = "배송에는 3영업일이 걸립니다.";

        assertThatThrownBy(() -> guard.verifyMeasures(answer, context, "배송이 5영업일 걸리나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("5영업일");
    }

    @Test
    @DisplayName("고객 질문의 기간을 사용자 입력으로 되받으면 통과시킨다")
    void 질문의_기간을_되받으면_통과한다() {
        String answer = "말씀하신 5일 일정은 안내된 정보에 없습니다.";
        String context = "로밍은 출국 전에 신청하는 것이 좋습니다.";

        assertThatCode(() -> guard.verifyMeasures(answer, context, "5일 여행인데 언제 신청하나요?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("말씀하신이라는 표현으로 질문의 기간을 확정해도 차단한다")
    void 질문의_기간을_말씀하신으로_확정하면_차단한다() {
        String answer = "말씀하신 5영업일이 맞습니다.";
        String context = "배송에는 3영업일이 걸립니다.";

        assertThatThrownBy(() -> guard.verifyMeasures(answer, context, "배송이 5영업일 걸리나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("5영업일");
    }

    @Test
    @DisplayName("질문에만 나온 속도를 사실로 확정하면 차단한다")
    void 질문에만_나온_속도를_영상_시청_가능_근거로_쓰지_않는다() {
        String answer = "데이터 소진 후에도 400kbps 속도로 영상 시청이 가능합니다.";
        String context = "속도 제한 상태라 고화질 영상은 어렵습니다. 언리미티드 요금제는 제한이 없습니다.";

        assertThatThrownBy(() -> guard.applyEvidencePolicy(
                answer, context, "데이터 소진 후 400kbps에서 영상 시청이 되나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("400kbps");
    }

    @Test
    @DisplayName("근거에 명시된 속도는 답변에서 사용할 수 있다")
    void 근거에_있는_속도는_답변에서_사용할_수_있다() {
        String answer = "소진 후 400kbps 속도로 이용할 수 있습니다.";
        String context = "데이터 소진 후 400kbps 속도로 이용할 수 있습니다.";

        assertThatCode(() -> guard.verifyMeasures(answer, context, "데이터 소진 후 속도는 얼마인가요?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("근거의 상한을 넘는 사용자 기간이 범위 밖이라는 답변은 통과시킨다")
    void 근거의_상한을_넘는_사용자_기간을_범위_밖으로_안내하면_통과한다() {
        String answer = "요금납부확인서는 최근 3년분까지만 발급 가능하여 4년 전 기록은 제공되지 않습니다.";
        String context = "요금납부확인서는 최근 3년분까지만 발급됩니다. 그 이전 기록은 발급 범위 밖입니다.";

        assertThatCode(() -> guard.verifyMeasures(answer, context, "4년 전 기록을 발급할 수 있나요?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("상한 안의 사용자 기간을 근거 없이 불가하다고 하면 차단한다")
    void 근거의_상한_안인_사용자_기간을_불가로_안내하면_차단한다() {
        String answer = "2년 전 기록은 제공되지 않습니다.";
        String context = "요금납부확인서는 최근 3년분까지만 발급됩니다.";

        assertThatThrownBy(() -> guard.verifyMeasures(answer, context, "2년 전 기록을 발급할 수 있나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("2년");
    }

    @Test
    @DisplayName("고객 질문의 비교 표현을 근거 없이 결론으로 확정하면 제거한다")
    void 질문의_비교를_결론으로_확정하면_제거한다() {
        String answer = "이메일 청구서가 더 저렴하고 편리합니다.";
        String context = "우편 청구서는 월 500원이고 이메일 청구서는 무료입니다.";

        assertThat(guard.trimUngroundedComparisons(answer, context, "이메일이 더 저렴하고 편리한가요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("근거에 없는 절차 난이도 평가를 제거한다")
    void 근거에_없는_간단하다는_평가를_제거한다() {
        String answer = "절차는 비교적 간단하지만, 정확한 안내는 매장 직원에게 문의하세요.";
        String context = "매장에서 기기변경을 신청하고 기존 할부금을 완납하거나 승계할 수 있습니다.";

        assertThat(guard.trimUngroundedComparisons(answer, context, "기기변경 절차를 알려주세요."))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("근거에 절차가 간단하다고 명시되어 있으면 유지한다")
    void 근거에_있는_간단하다는_평가를_유지한다() {
        String answer = "기기변경 절차는 간단합니다.";
        String context = "기기변경은 신분증을 가지고 매장에 방문하면 되어 절차가 간단합니다.";

        assertThat(guard.trimUngroundedComparisons(answer, context, "기기변경 절차를 알려주세요."))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("근거 없음 뒤에 카드 결제가 가능하다고 확정하면 제거한다")
    void 근거_없음_뒤의_정책_확정을_제거한다() {
        String answer = "카드 결제는 근거에는 없지만 할 수 있습니다.";
        String context = "유심 재발급 비용은 7,700원입니다.";

        assertThat(guard.trimUngroundedPolicyAttributes(
                answer, context, "유심 재발급 비용은 카드로 결제할 수 있나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("다른 정책의 같은 기간 단위를 상한 근거로 사용하지 않는다")
    void 관련_없는_기간_상한을_근거로_사용하지_않는다() {
        String answer = "요금납부확인서는 4년 전 기록은 제공되지 않습니다.";
        String context = "통화기록은 최근 3년분까지만 조회할 수 있습니다.";

        assertThatThrownBy(() -> guard.verifyMeasures(
                answer, context, "4년 전 요금납부확인서를 발급할 수 있나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("4년");
    }

    @Test
    @DisplayName("근거가 수수료 없음인데 면제되지 않는다고 하면 반대 문장만 제거한다")
    void 근거와_반대인_수수료_주장을_제거한다() {
        String answer = "가족 간에도 명의변경 수수료는 면제되지 않습니다. "
                + "모든 명의변경에 대해 수수료가 발생하지 않습니다.";
        String context = "가족 여부와 상관없이 명의변경 수수료는 없습니다.";

        assertThat(guard.trimContradictedChargeClaims(answer, context))
                .isEqualTo("모든 명의변경에 대해 수수료가 발생하지 않습니다.");
    }

    @Test
    @DisplayName("다른 비용 항목의 반대 극성을 현재 정책의 근거로 사용하지 않는다")
    void 비용_항목별로_극성을_구분한다() {
        String answer = "명의변경 수수료는 없습니다.";
        String context = "명의변경 수수료는 없습니다. 중도 해지 위약금은 부과됩니다.";

        assertThat(guard.trimContradictedChargeClaims(answer, context)).isEqualTo(answer);
    }

    @Test
    @DisplayName("한 문장에 비용 항목이 함께 있어도 각 항목의 극성을 구분한다")
    void 한_문장에서도_비용_항목별_극성을_구분한다() {
        String answer = "명의변경 수수료는 없습니다.";
        String context = "명의변경 수수료는 없고 중도 해지 위약금은 부과됩니다.";

        assertThat(guard.trimContradictedChargeClaims(answer, context)).isEqualTo(answer);
    }

    @Test
    @DisplayName("근거에 없는 달력일 기준 단정을 제거한다")
    void 근거에_없는_달력일_단정을_제거한다() {
        String answer = "철회 기간 14일은 달력상의 일수 기준입니다.";
        String context = "개통 후 14일 이내로 안내됩니다.";

        assertThat(guard.trimUnsupportedPolicyClaims(answer, context))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("날짜 경과 기준이라는 우회 표현도 근거에 없으면 제거한다")
    void 날짜_경과_기준이라는_우회_단정을_제거한다() {
        String answer = "영업일 여부에 따른 구분은 명시되어 있지 않습니다. "
                + "따라서 일반적으로 날짜 경과를 기준으로 적용됩니다.";
        String context = "개통 후 14일 이내로 안내됩니다.";

        assertThat(guard.trimUnsupportedPolicyClaims(answer, context))
                .isEqualTo("영업일 여부에 따른 구분은 명시되어 있지 않습니다.");
    }

    @Test
    @DisplayName("근거에 없는 신청 마감 시점을 제거한다")
    void 근거에_없는_마감_시점을_제거한다() {
        String answer = "최소한 출국 당일 아침까지는 로밍 신청을 완료해야 합니다.";
        String context = "출국 전 신청을 권장하지만 현지 도착 후에도 신청할 수 있습니다.";

        assertThat(guard.trimUnsupportedPolicyClaims(answer, context))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("제거된 정책 단정에 이어지는 종속 설명도 함께 제거한다")
    void 제거된_정책_단정의_종속_설명도_제거한다() {
        String answer = "최소한 출국 당일 아침까지는 로밍 신청을 완료해야 합니다. "
                + "이는 신청 후 처리 시간을 고려한 것입니다.";
        String context = "출국 전 신청을 권장하지만 현지 도착 후에도 신청할 수 있습니다.";

        assertThat(guard.trimUnsupportedPolicyClaims(answer, context))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("잘못된 정책 문장 뒤라도 근거가 있는 독립 결론은 유지한다")
    void 제거된_정책_단정_뒤의_근거_있는_결론은_유지한다() {
        String answer = "최소한 출국 당일 아침까지는 로밍 신청을 완료해야 합니다. "
                + "따라서 로밍 요금은 하루 9,900원입니다.";
        String context = "현지 도착 후에도 신청할 수 있습니다. 로밍 요금은 하루 9,900원입니다.";

        assertThat(guard.trimUnsupportedPolicyClaims(answer, context))
                .isEqualTo("따라서 로밍 요금은 하루 9,900원입니다.");
    }

    @Test
    @DisplayName("근거에 없는 기능 미지원 단정만 제거한다")
    void 근거에_없는_기능_미지원_단정을_제거한다() {
        String answer = "로밍은 하루 9,900원입니다. 로밍 요금제 변경 기능은 제공하지 않습니다.";
        String context = "일 단위 로밍 요금제는 9,900원입니다.";

        assertThat(guard.trimUnsupportedPolicyClaims(answer, context))
                .isEqualTo("로밍은 하루 9,900원입니다.");
    }

    @Test
    @DisplayName("카드 결제 불가 근거로 카드 결제 가능 답변을 통과시키지 않는다")
    void 정책_속성의_긍정과_부정을_구분한다() {
        String answer = "유심 재발급 비용은 카드로 결제할 수 있습니다.";
        String context = "유심 재발급 비용은 카드로 결제할 수 없습니다.";

        assertThat(guard.trimUngroundedPolicyAttributes(
                answer, context, "유심 재발급 비용은 카드로 결제할 수 있나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("카드 결제 가능 근거와 같은 답변은 유지한다")
    void 정책_속성의_같은_극성은_유지한다() {
        String answer = "유심 재발급 비용은 카드로 결제할 수 있습니다.";
        String context = "유심 재발급 비용은 카드로 결제할 수 있습니다.";

        assertThat(guard.trimUngroundedPolicyAttributes(
                answer, context, "유심 재발급 비용은 카드로 결제할 수 있나요?"))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("다른 기능의 미지원 근거를 현재 기능의 근거로 사용하지 않는다")
    void 기능_미지원_정책은_대상_기능까지_일치해야_한다() {
        String answer = "eSIM 발급 기능은 지원하지 않습니다.";
        String context = "로밍 요금제 변경 기능은 지원하지 않습니다.";

        assertThat(guard.trimUnsupportedPolicyClaims(answer, context))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("같은 기능의 미지원 근거는 유지한다")
    void 같은_기능의_미지원_정책은_유지한다() {
        String answer = "eSIM 발급 기능은 지원하지 않습니다.";
        String context = "eSIM 발급 기능은 지원하지 않습니다.";

        assertThat(guard.trimUnsupportedPolicyClaims(answer, context)).isEqualTo(answer);
    }

    @Test
    @DisplayName("카드 발급 안내만으로 카드 결제 가능 주장을 근거화하지 않는다")
    void 카드_발급_근거로_카드_결제_주장을_통과시키지_않는다() {
        String answer = "카드 결제가 가능합니다.";
        String context = "신용카드 발급 절차는 앱에서 확인할 수 있습니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "카드 결제가 되나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("할부금이 없는 명의변경 안내는 정책 속성 가드가 유지한다")
    void 명의변경_조건부_할부_안내는_유지한다() {
        String answer = "네, 할부금이 없는 경우 신용 심사 없이 바로 명의변경이 가능합니다.";
        String context = "단말 할부금이 없으면 신용 심사 없이 진행됩니다. "
                + "할부금이 없으면 별도 심사 없이 진행되지만, 할부금이 남아 있으면 양수인의 신용 심사를 거쳐야 합니다.";

        assertThat(guard.trimUngroundedPolicyAttributes(answer, context, "할부금이 없으면 신용 심사 없이 명의변경이 되나요?"))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("다른 항목의 수수료 근거를 명의변경 수수료 판정에 섞지 않는다")
    void 다른_항목의_수수료_근거가_명의변경_수수료를_허용하지_않는다() {
        String answer = "명의변경 수수료가 발생합니다.";
        String context = "명의변경 수수료는 없습니다. 유심 재발급 수수료는 발생합니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "명의변경 수수료가 얼마인가요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("로밍 변경 미지원 근거를 로밍 해지 미지원 주장에 재사용하지 않는다")
    void 로밍_변경_근거로_로밍_해지_미지원_주장을_통과시키지_않는다() {
        String answer = "로밍 해지 기능은 제공하지 않습니다.";
        String context = "로밍 변경 기능은 제공하지 않습니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "로밍 해지 기능을 지원하나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("현지에서 변경 가능하다는 근거를 해지 가능 주장에 재사용하지 않는다")
    void 로밍_변경_가능_근거로_현지_해지_가능_주장을_통과시키지_않는다() {
        String answer = "현지에서 로밍 요금제 해지가 가능합니다.";
        String context = "현지에서 로밍 요금제 변경이 가능합니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "현지에서 로밍을 해지할 수 있나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("현지에서 변경 불가라는 근거와 반대되는 변경 가능 주장을 차단한다")
    void 현지_변경_불가_근거와_반대인_변경_가능_주장을_통과시키지_않는다() {
        String answer = "현지에서 로밍 요금제 변경이 가능합니다.";
        String context = "현지에서 로밍 요금제 변경은 지원하지 않습니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "현지에서 변경할 수 있나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("복수 동작 주장에는 각 동작을 뒷받침하는 근거가 필요하다")
    void 로밍_변경_미지원_근거만으로_변경과_해지_모두_미지원이라_하지_않는다() {
        String answer = "로밍 변경과 해지는 모두 지원하지 않습니다.";
        String context = "로밍 변경 기능은 지원하지 않습니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "로밍 변경과 해지가 안 되나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("변경과 해지 미지원 근거가 각각 있으면 복수 동작 답변을 유지한다")
    void 로밍_변경과_해지_근거가_각각_있으면_복수_동작_답변을_유지한다() {
        String answer = "로밍 변경과 해지는 모두 지원하지 않습니다.";
        String context = "현지에서 로밍 변경 기능은 지원하지 않습니다. "
                + "현지에서 로밍 해지 기능은 지원하지 않습니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "로밍 변경과 해지가 안 되나요?"))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("변경 가능과 해지 불가가 한 문장에 있어도 동작별 극성을 구분한다")
    void 로밍_복수_동작의_서로_다른_극성을_구분한다() {
        String answer = "현지에서 로밍 변경은 가능하지만 해지는 지원하지 않습니다.";
        String context = "현지에서 로밍 변경이 가능합니다. 현지에서 로밍 해지는 지원하지 않습니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "현지에서 변경과 해지가 가능한가요?"))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("근거에 있는 정책 단정은 유지한다")
    void 근거에_있는_정책_단정은_유지한다() {
        String answer = "번호이동은 반드시 20시 전에 신청해야 합니다.";
        String context = "번호이동은 반드시 20시 전에 신청해야 합니다.";

        assertThat(guard.trimUnsupportedPolicyClaims(answer, context)).isEqualTo(answer);
    }

    @Test
    @DisplayName("비용이 없다는 근거와 이중 부정으로 반대하는 수수료 주장은 제거한다")
    void 수수료_이중_부정은_근거와_반대로_판정한다() {
        String answer = "모든 명의변경에 대해 수수료가 부과되지 않는 것은 아닙니다.";
        String context = "가족 여부와 상관없이 명의변경 수수료는 없습니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "가족 간에는 명의변경 수수료가 면제되나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("달력 날짜 기준이라는 근거 밖 표현을 제거하고 확인 불가 안내는 유지한다")
    void 달력_날짜_기준_표현을_근거_없이_단정하지_않는다() {
        String answer = "영업일 여부에 대한 구분은 명시되어 있지 않습니다. "
                + "정확한 기간 준수를 위해 달력 날짜를 기준으로 확인해 주시기 바랍니다.";
        String context = "철회는 개통 후 14일 이내로 안내됩니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "철회 기간 14일은 영업일 기준인가요?"))
                .isEqualTo("영업일 여부에 대한 구분은 명시되어 있지 않습니다.");
    }

    @Test
    @DisplayName("요금 근거만으로 현지 변경·결제와 귀국 후 처리 정책을 만들지 않는다")
    void 요금_근거만으로_현지_변경_정책을_추가하지_않는다() {
        String answer = "로밍 요금제는 하루 기준으로 기본 요금제가 9,900원이며, "
                + "데이터 무제한 요금제는 12,100원입니다. "
                + "현지에서 요금제 변경이나 결제는 지원하지 않습니다. "
                + "요금제 변경은 주로 귀국 후에 진행하시는 것이 일반적입니다.";
        String context = "일 단위 로밍 요금제는 9,900원입니다. 데이터 무제한은 하루 12,100원입니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "로밍 요금제 얼마고 현지에서 끊을 수 있어요?"))
                .isEqualTo("로밍 요금제는 하루 기준으로 기본 요금제가 9,900원이며, "
                        + "데이터 무제한 요금제는 12,100원입니다.");
    }

    @Test
    @DisplayName("근거에 있는 현지 변경 제한은 유지한다")
    void 근거에_있는_현지_변경_제한은_유지한다() {
        String answer = "현지에서 요금제 변경은 지원하지 않습니다.";
        String context = "현지에서 요금제 변경은 지원하지 않습니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "현지에서 요금제 변경이 가능한가요?"))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("근거 없는 가입 비교 이점만 제거하고 처리 시간 안내는 유지한다")
    void 근거_없는_가입_비교_이점만_제거한다() {
        String answer = "번호이동은 보통 2시간 이내에 처리됩니다. "
                + "새로 가입하는 것보다 번호 유지 측면에서 이점이 있습니다.";
        String context = "번호이동은 보통 2시간 이내에 처리됩니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "번호이동은 새로 가입하는 것보다 빠른가요?"))
                .isEqualTo("번호이동은 보통 2시간 이내에 처리됩니다.");
    }

    @Test
    @DisplayName("근거에 없는 시간 절약 주장은 제거하고 근거에 있는 주장은 유지한다")
    void 근거_없는_시간_절약_주장을_제거한다() {
        String answer = "전화로 진행하시면 시간을 절약하실 수 있습니다.";
        String context = "매장과 전화 모두 바로 처리됩니다. 전화가 편하시면 전화로 하세요.";

        assertThat(guard.applyEvidencePolicy(answer, context, "전화와 매장 중 어느 쪽이 더 빠른가요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);

        String supportedContext = "전화로 신청하면 매장 방문보다 시간을 절약할 수 있습니다.";
        assertThat(guard.applyEvidencePolicy(answer, supportedContext, "전화 신청 방법을 알려주세요."))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("근거에 있는 비교 이점은 유지한다")
    void 근거에_있는_가입_비교_이점은_유지한다() {
        String answer = "번호이동은 번호를 유지할 수 있다는 이점이 있습니다.";
        String context = "번호이동은 기존 번호를 유지할 수 있다는 이점이 있습니다.";

        assertThat(guard.trimUngroundedComparisons(answer, context, "번호이동의 이점이 있나요?"))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("개별 비용만으로 배송비를 포함한 총액을 단정하지 않는다")
    void 배송비_포함_근거없이_총액을_단정하지_않는다() {
        String answer = "총 비용은 7,700원입니다.";
        String context = "유심 재발급 비용은 7,700원이며 택배로 2~3 영업일이 걸립니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "유심 재발급 총 비용이 얼마인가요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("FAQ가 배송비 포함 총액을 명시하면 총액 답변을 유지한다")
    void 배송비_포함_총액이_근거에_있으면_유지한다() {
        String answer = "총 비용은 7,700원입니다.";
        String context = "유심 재발급 총 비용은 7,700원이며 배송비가 포함됩니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "유심 재발급 총 비용이 얼마인가요?"))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("다른 상품의 총액과 유심 배송비 근거를 섞어 유심 총액을 만들지 않는다")
    void 다른_상품의_총액으로_유심_재발급_총액을_근거화하지_않는다() {
        String answer = "유심 재발급 총 비용은 7,700원입니다.";
        String context = "휴대폰 케이스 총 비용은 7,700원입니다. 유심 재발급 배송비는 별도입니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "유심 재발급 총 비용이 얼마인가요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("대상이 생략된 총액 답변은 질문 대상과 근거 대상이 일치해야 한다")
    void 대상이_생략된_총액도_다른_상품의_근거로_통과시키지_않는다() {
        String answer = "총 비용은 7,700원입니다.";
        String context = "휴대폰 케이스 총 비용은 7,700원입니다. 유심 재발급 배송비는 별도입니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "유심 재발급 총 비용이 얼마인가요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("명의변경 처리 시간 근거에 없는 예외 조건을 제거한다")
    void 명의변경_처리시간에_근거없는_예외조건을_추가하지_않는다() {
        String answer = "명의변경은 매장에서 30분 이내에 처리됩니다. "
                + "특별한 경우가 아니라면 며칠이 걸리지 않습니다.";
        String context = "명의변경은 매장에서 30분 이내에 처리됩니다. "
                + "미납 요금이 있으면 먼저 완납해야 진행됩니다.";

        assertThat(guard.applyEvidencePolicy(answer, context, "명의변경 하는 데 며칠이나 걸려요?"))
                .isEqualTo("명의변경은 매장에서 30분 이내에 처리됩니다.");
    }

    @Test
    @DisplayName("근거에 명시된 예외 조건은 유지한다")
    void 근거에_있는_예외조건은_유지한다() {
        String answer = "특별한 경우가 아니라면 명의변경은 매장에서 30분 이내에 처리됩니다.";
        String context = "특별한 경우가 아니라면 명의변경은 매장에서 30분 이내에 처리됩니다.";

        assertThat(guard.trimUnsupportedPolicyClaims(answer, context)).isEqualTo(answer);
    }
}
