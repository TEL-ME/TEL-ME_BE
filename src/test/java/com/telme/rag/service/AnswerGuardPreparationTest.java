package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.rag.exception.AnswerGuardException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AnswerGuardPreparationTest {
    private static final String QUESTION = "유심 재발급 준비물과 방문 절차를 알려주세요.";
    private static final String COST = "유심 재발급 비용은 7,700원입니다.";
    private static final String VISIT = "명의자 본인은 신분증을 지참하고 매장을 방문해야 합니다.";
    private static final String DELEGATE = "대리인은 위임장과 신분증을 가지고 매장을 방문해야 합니다.";
    private final AnswerGuard guard = new AnswerGuard();

    @ParameterizedTest
    @ValueSource(strings = {
            "필요한 준비물은 신분증만 준비하시면 됩니다.",
            "신분증 하나만 가지고 매장을 방문하시면 됩니다.",
            "신분증 한 장만 지참하면 됩니다.",
            "신분증은 하나만 준비하시면 됩니다.",
            "오직 신분증을 지참하면 됩니다.",
            "준비물은 신분증뿐입니다.",
            "신분증만으로 재발급이 가능합니다.",
            "신분증이면 충분합니다.",
            "신분증으로 충분합니다.",
            "다른 서류는 필요 없습니다."
    })
    @DisplayName("근거 없는 준비물 충분성 단정을 조건을 포함한 원문으로 복원한다")
    void 준비물_단정을_원문_조건으로_복원한다(String generated) {
        String evidence = COST + " " + VISIT + " " + DELEGATE;

        assertThat(guard.applyEvidencePolicy(COST + " " + generated, evidence, QUESTION))
                .isEqualTo(evidence)
                .contains("명의자 본인", "신분증", "매장을 방문", "대리인", "위임장");
    }

    @Test
    @DisplayName("실제 채팅의 비용과 미접수 상태를 유지하며 신분증만 단정을 제거한다")
    void 실제_채팅의_정상_사실과_미접수_상태를_보존한다() {
        String condition = "기기 불량으로 확인된 경우에만 무료입니다.";
        String pending = "이번 상담에서는 재발급 신청을 접수하거나 완료하지 않았습니다.";
        String evidence = COST + " " + condition + " " + VISIT + " " + pending;
        String answer = COST + " " + condition + " " + VISIT
                + " 필요한 준비물은 신분증만 준비하시면 됩니다. " + pending;

        assertThat(guard.applyEvidencePolicy(answer, evidence, QUESTION))
                .isEqualTo(evidence).doesNotContain("신분증만");
    }

    @ParameterizedTest
    @CsvSource({
            "eSIM 발급, 유심 재발급",
            "유심 해지, 유심 재발급",
            "어머니 유심 재발급, 아버지 유심 재발급",
            "민수 씨 유심 재발급, 지수 씨 유심 재발급",
            "첫 번째 회선 유심 재발급, 두 번째 회선 유심 재발급",
            "A 회선 유심 재발급, B 회선 유심 재발급",
            "내 회선 유심 재발급, 다른 회선 유심 재발급",
            "5G 요금제 변경, LTE 요금제 변경"
    })
    @DisplayName("다른 상품과 업무와 사람과 회선의 준비물을 가져오지 않는다")
    void 다른_대상의_준비물을_복원하지_않는다(String subject, String other) {
        String answer = subject + " 시 신분증만 가지고 매장을 방문하세요.";
        String evidence = other + " 시 위임장과 신분증을 가지고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(answer, evidence, subject + " 준비물"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("대리인 안내를 명의자 본인 방문 근거로 임의 변경하지 않는다")
    void 다른_방문자의_조건을_복원하지_않는다() {
        String answer = "대리인은 유심 재발급 시 신분증만 가지고 매장을 방문하세요.";
        String evidence = "명의자 본인은 유심 재발급 시 신분증을 가지고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(answer, evidence, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("명의자 신분증의 소유자를 대리인 방문 절차의 방문자로 혼동하지 않는다")
    void 신분증_소유자와_실제_방문자를_혼동하지_않는다() {
        String answer = "명의자 본인은 유심 재발급 시 신분증만 가지고 매장을 방문하세요.";
        String evidence = "대리인은 유심 재발급 시 명의자 본인의 신분증과 위임장을 지참하고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(answer, evidence, "명의자 본인 유심 재발급 준비물"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("서로 다른 FAQ의 준비물이 모호하면 한 원문을 임의로 선택하지 않는다")
    void 여러_준비물_근거를_임의로_합치지_않는다() {
        String evidence = "유심 재발급은 신분증을 가지고 매장을 방문해야 합니다.\n"
                + "유심 재발급은 위임장과 신분증을 가지고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy("신분증만 준비하시면 됩니다.", evidence, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("신분증만 필요하다는 근거도 동일한 방문자 조건 전체를 보존한다")
    void 근거에_있는_충분성_조건은_원문대로_유지한다() {
        String evidence = "유심 재발급은 명의자 본인이 방문할 때 신분증만 지참하면 됩니다.";

        assertThat(guard.applyEvidencePolicy("유심 재발급은 신분증만 준비하시면 됩니다.", evidence, QUESTION))
                .isEqualTo(evidence);
    }

    @Test
    @DisplayName("신분증만으로 부족하다는 부정과 위임장 조건을 함께 유지한다")
    void 근거의_부정과_추가_서류_조건을_함께_유지한다() {
        String evidence = "대리인의 유심 재발급은 신분증만으로는 불가능합니다. "
                + "위임장과 신분증을 지참하고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy("대리인은 유심 재발급에 신분증만 필요합니다.", evidence, QUESTION))
                .isEqualTo(evidence);
    }

    @ParameterizedTest
    @ValueSource(strings = {"위임장은 필요 없습니다.", "위임장 없이 방문하면 됩니다.",
            "위임장은 지참하지 않아도 됩니다.", "위임장은 불필요합니다."})
    @DisplayName("대리인 위임장 조건을 면제하는 생성문은 방문자와 준비물 원문으로 복원한다")
    void 근거에_없는_위임장_면제를_복원한다(String generated) {
        String evidence = "대리인의 유심 재발급은 위임장과 신분증을 지참하고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(generated, evidence, "대리인 유심 재발급 준비물"))
                .isEqualTo(evidence);
    }

    @Test
    @DisplayName("고객 질문에 있는 신분증만 표현을 정책 근거로 사용하지 않는다")
    void 질문의_준비물_단정을_근거로_쓰지_않는다() {
        assertThat(guard.applyEvidencePolicy("신분증만 필요합니다.", "유심 재발급을 안내합니다.",
                "유심 재발급은 신분증만 있으면 되나요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("충분성 확인 불가 안내와 정상 무료 조건을 잘못 제거하지 않는다")
    void 단정이_아닌_안내는_유지한다() {
        String answer = "신분증만으로 가능한지는 근거에서 확인할 수 없습니다. "
                + "기기 불량으로 확인된 경우에만 무료입니다.";

        assertThat(guard.applyEvidencePolicy(answer, answer, QUESTION)).isEqualTo(answer);
    }

    @Test
    @DisplayName("로밍 근거와 이심 비용이 섞여도 이심에 로밍 준비물을 복원하지 않는다")
    void 여러_업무의_준비물을_섞지_않는다() {
        String cost = "eSIM 발급 비용은 2,750원입니다.";
        String evidence = "로밍 변경은 신분증과 위임장을 지참하고 매장을 방문해야 합니다.\n" + cost;

        assertThat(guard.applyEvidencePolicy(cost + " 신분증만 준비하시면 됩니다.", evidence, "eSIM 발급 준비물"))
                .isEqualTo(cost);
    }

    @Test
    @DisplayName("준비물을 복원해도 근거 없는 금액은 계속 차단한다")
    void 준비물_복원으로_잘못된_금액을_우회하지_않는다() {
        assertThatThrownBy(() -> guard.applyEvidencePolicy(
                "유심 재발급 비용은 8,800원입니다. 신분증만 준비하시면 됩니다.", COST + " " + VISIT, QUESTION))
                .isInstanceOf(AnswerGuardException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"신분증을 가지고 매장을 방문하세요.", "신분증을 지참해 주세요.",
            "매장을 방문하면 됩니다.", "신분증 외에 추가 서류와 확인 절차가 필요할 수 있습니다."})
    @DisplayName("단정 단어가 없어도 단일 준비물 근거의 명의자와 대리인 조건을 유지한다")
    void 단일_준비물_문의의_조건_전체를_복원한다(String generated) {
        String evidence = COST + " " + VISIT + " " + DELEGATE;

        assertThat(guard.applyEvidencePolicy(generated, evidence, QUESTION)).isEqualTo(evidence);
    }

    @Test
    @DisplayName("이심 근거에 없는 준비물과 위임장 면제 문장을 모두 제거한다")
    void 다른_업무의_준비물_추가와_면제를_함께_차단한다() {
        String cost = "eSIM 발급 비용은 2,750원입니다.";
        String evidence = "로밍 변경은 위임장과 신분증을 지참하고 매장을 방문해야 합니다.\n" + cost;
        String answer = cost + " 준비물로는 신분증이 필요합니다. 추가적인 위임장은 eSIM 발급에 요구되지 않습니다.";

        assertThat(guard.applyEvidencePolicy(answer, evidence, "eSIM 발급 준비물")).isEqualTo(cost);
    }

    @ParameterizedTest
    @ValueSource(strings = {"대리인은 위임장도 지참해야 합니다.", "명의자 본인만 매장을 방문할 수 있습니다."})
    @DisplayName("FAQ 경계를 알 수 없는 여러 줄의 준비물을 일부만 복원하지 않는다")
    void 경계가_없는_준비물은_불완전하게_복원하지_않는다(String condition) {
        String evidence = "유심 재발급은 신분증을 지참하고 매장을 방문해야 합니다.\n" + condition;

        assertThat(guard.applyEvidencePolicy("유심 재발급은 신분증만 준비하면 됩니다.", evidence, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"명의의", "원본", "실물", "유효한"})
    @DisplayName("신분증 앞 수식어가 있어도 서류 소유자를 실제 방문자로 읽지 않는다")
    void 수식어가_있는_서류_소유자를_방문자로_읽지_않는다(String modifier) {
        String evidence = "대리인은 유심 재발급 시 명의자 본인 " + modifier
                + " 신분증과 위임장을 지참하고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy("명의자 본인은 유심 재발급 시 신분증만 지참하면 됩니다.",
                evidence, "명의자 본인 유심 재발급 준비물"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @ParameterizedTest
    @CsvSource({"민수의 유심, 지수의 유심", "민수의 실물 유심, 지수의 실물 유심",
            "민수의 휴대폰, 지수의 휴대폰", "민수의 회선, 지수의 회선"})
    @DisplayName("호칭 없이 이름의 소유격을 써도 다른 사람의 준비물을 복원하지 않는다")
    void 이름_소유격으로_다른_사람을_구분한다(String owner, String other) {
        String evidence = other + " 재발급은 위임장과 신분증을 지참하고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(owner + " 재발급은 신분증만 지참하면 됩니다.",
                evidence, owner + " 재발급 준비물"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("생성 답변이 사람을 바꿔도 질문과 다른 사람의 준비물을 복원하지 않는다")
    void 생성된_다른_사람을_질문의_대신으로_쓰지_않는다() {
        String evidence = "지수의 유심 재발급은 위임장과 신분증을 지참하고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy("지수의 유심 재발급은 신분증만 준비하면 됩니다.",
                evidence, "민수의 유심 재발급 준비물"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("같은 사람 이름의 소유격과 대리인 준비물은 원문대로 복원한다")
    void 같은_사람의_대리인_준비물은_보존한다() {
        String evidence = "대리인은 민수의 유심 재발급 시 명의자 본인 명의의 신분증과 위임장을 지참하고"
                + " 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy("대리인은 민수의 유심 재발급에 신분증만 준비하면 됩니다.",
                evidence, "대리인 민수의 유심 재발급 준비물"))
                .isEqualTo(evidence);
    }

    @ParameterizedTest
    @ValueSource(strings = {"유심 재발급 비용은 8,800원이며 신분증만 지참하면 됩니다.",
            "유심 재발급은 3일 안에 신분증만 지참하면 됩니다."})
    @DisplayName("여러 근거의 준비물을 복원하기 전에도 근거 없는 수치를 차단한다")
    void 복수_근거에서도_복원_전_수치를_검사한다(String generated) {
        String evidence = COST + " 유심 재발급은 신분증을 지참하고 매장을 방문해야 합니다.\n로밍 서비스를 안내합니다.";

        assertThatThrownBy(() -> guard.applyEvidencePolicy(generated, evidence, QUESTION))
                .isInstanceOf(AnswerGuardException.class);
    }

    @Test
    @DisplayName("방문 허용을 새로 주장하지 않은 공통 신분증 소유 안내의 전체 원문은 유지한다")
    void 공통_서류_소유_안내의_전체_인용은_유지한다() {
        String evidence = "해당 유심 명의자의 신분증을 지참합니다.";

        assertThat(guard.applyEvidencePolicy(evidence, evidence,
                "가족 유심은 본인이 매장에 방문할 때 준비물이 뭔가요?", "", List.of(evidence)))
                .isEqualTo(evidence);
    }

    @Test
    @DisplayName("공통 서류 소유 근거에 없는 본인 방문 충분성 주장은 인용 예외로 통과시키지 않는다")
    void 전체_인용_예외로_방문_조건을_추가하지_않는다() {
        String evidence = "해당 유심 명의자의 신분증을 지참합니다.";

        assertThat(guard.applyEvidencePolicy("본인은 유심 재발급 시 신분증만 준비하면 됩니다.", evidence,
                "본인 유심 재발급 준비물", "", List.of(evidence)))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("다른 사람의 전체 원문도 질문과 대상이 다르면 인용 예외로 남기지 않는다")
    void 다른_사람의_원문_인용도_제거한다() {
        String evidence = "지수의 유심 재발급은 신분증을 지참하고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(evidence, evidence, "민수의 유심 재발급 준비물", "",
                List.of(evidence))).isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @ParameterizedTest
    @CsvSource({"엄마, 어머니", "어머니, 엄마", "아빠, 아버지", "아버지, 아빠"})
    @DisplayName("같은 가족 호칭의 소유격은 이름을 추가해도 다른 사람으로 바뀌지 않는다")
    void 가족_별칭의_소유격을_같은_사람으로_본다(String owner, String alias) {
        String evidence = alias + "의 유심 재발급은 신분증과 위임장을 지참하고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(owner + "의 유심 재발급은 신분증만 준비하면 됩니다.",
                evidence, owner + "의 유심 재발급 준비물", "", List.of(evidence))).isEqualTo(evidence);
    }

    @ParameterizedTest
    @CsvSource({"엄마, 아버지", "아빠, 어머니"})
    @DisplayName("가족 별칭을 맞추더라도 서로 다른 부모의 준비물은 섞지 않는다")
    void 가족_별칭을_맞춰도_다른_부모는_구분한다(String owner, String other) {
        String evidence = other + "의 유심 재발급은 신분증과 위임장을 지참하고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(owner + "의 유심 재발급은 신분증만 준비하면 됩니다.",
                evidence, owner + "의 유심 재발급 준비물", "", List.of(evidence)))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }
}
