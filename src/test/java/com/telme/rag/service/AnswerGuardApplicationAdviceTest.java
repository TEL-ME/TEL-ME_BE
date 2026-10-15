package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnswerGuardApplicationAdviceTest {
    private final AnswerGuard guard = new AnswerGuard();
    private static final String VISIT = "신분증을 가지고 매장을 방문하는 절차를 안내했습니다.";
    private static final String RECORD = "이번 상담에서는 재발급 신청을 접수하거나 완료하지 않았습니다.";

    @Test
    @DisplayName("근거 없는 신청 권유만 제거하고 신분증과 방문 절차 및 미처리 기록을 남긴다")
    void 추가_신청_권유를_제거한다() {
        String answer = VISIT + " 매장에서는 재발급 절차를 진행해 드리니 방문하시어 신청하시면 됩니다.";

        assertThat(guard.trimUnsupportedApplicationAdvice(answer, VISIT + " " + RECORD))
                .isEqualTo(VISIT + " " + RECORD);
    }

    @Test
    @DisplayName("같은 문장에 신청 권유가 섞여도 신분증과 방문 근거는 복원한다")
    void 혼합_문장의_방문_근거를_보존한다() {
        assertThat(guard.trimUnsupportedApplicationAdvice(
                "신분증을 가지고 매장을 방문하시어 신청하시면 됩니다.", VISIT + " " + RECORD))
                .isEqualTo(VISIT + " " + RECORD);
    }

    @Test
    @DisplayName("근거에 있는 신청 지침은 방문자 조건까지 그대로 남긴다")
    void 실제_신청_조건은_삭제하지_않는다() {
        String evidence = "대리인은 위임장과 신분증을 지참한 경우에만 매장에서 신청하시면 됩니다.";

        assertThat(guard.trimUnsupportedApplicationAdvice("매장에서 신청하시면 됩니다.", evidence))
                .isEqualTo(evidence);
    }

    @ParameterizedTest
    @ValueSource(strings = {"제가 신청해 드리겠습니다.", "제가 신청을 도와드리겠습니다.", "신청해 보세요."})
    @DisplayName("접수를 대신하겠다는 근거 없는 제안만 있으면 답변 불가로 처리한다")
    void 근거_없는_접수_제안은_차단한다(String answer) {
        assertThat(guard.trimUnsupportedApplicationAdvice(answer, "비용은 7,700원입니다."))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }
}
