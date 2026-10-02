package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static com.telme.consult.dto.PlanChangeConditions.*;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.consult.service.FollowupContextService.Context;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PlanChangeQuestionExplanationTest {
    private Context context(String text, String field, String intent) {
        return new Context(1, 3, text, List.of(new Candidate(2, field, 1, KEYS.contains(field) ? question(field) : "어느 지역인가요?",
                "제가 지금 요금제를 바꿀 수 있나요?", "요금제 변경", intent)));
    }

    @ParameterizedTest
    @ValueSource(strings={"가입한 달이 무슨 뜻이에요?", "가입월이 뭔가요?", "가입 여부 설명해 주세요", "가입한 달의 의미를 모르겠어요", "무슨 뜻이에요?", "이 질문이 무슨 말인가요?", "설명해 주세요", "이 질문이 이해가 안 돼요", "질문이 무슨 뜻이에요?", "이 질문 설명 좀 해주세요"})
    void explainsCalendarMonthWithoutMakingPersonalDecision(String reply) {
        var answer = PlanChangeQuestionExplanation.answer(context(reply, JOINED, "FAQ")).orElseThrow();
        assertThat(answer.content()).contains("달력상의 달", question(JOINED));
        assertThat(answer.content()).doesNotContain("변경할 수 있습니다", "변경할 수 없습니다", "30일", "다음 달부터");
        assertThat(answer.answerBasis()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings={"변경 이력이 무슨 뜻이에요?", "변경 여부 설명해 주세요", "무슨 뜻이에요?", "그 질문 뜻이 뭐예요?"})
    void explainsHistoryAndKeepsCurrentQuestion(String reply) {
        assertThat(PlanChangeQuestionExplanation.answer(context(reply, CHANGED, "FAQ")).orElseThrow().content())
                .contains("이번 달에 이미 요금제를 바꾼 적", question(CHANGED));
    }

    @ParameterizedTest
    @ValueSource(strings={"네", "아니요", "잘 모르겠어요", "알려주고 싶지 않아요", "나중에요", "요금제 변경 기준이 뭐예요?", "유심 비용 설명해 주세요", "지난달에 가입했어요. 변경 이력이 무슨 뜻이에요?"})
    void leavesConditionRepliesAndNewQuestionsToExistingAnalysis(String reply) {
        assertThat(PlanChangeQuestionExplanation.answer(context(reply, JOINED, "FAQ"))).isEmpty();
    }

    @Test
    void hasNoExplanationWithoutCurrentPlanConsultationOrWithAmbiguousTarget() {
        assertThat(PlanChangeQuestionExplanation.answer(new Context(1,3,"무슨 뜻이에요?",List.of()))).isEmpty();
        assertThat(PlanChangeQuestionExplanation.answer(context("무슨 뜻이에요?", "location", "STORE"))).isEmpty();
        var candidate = context("무슨 뜻이에요?", JOINED, "FAQ").candidates().getFirst();
        assertThat(PlanChangeQuestionExplanation.answer(new Context(1,3,"무슨 뜻이에요?",List.of(candidate,candidate)))).isEmpty();
    }
}
