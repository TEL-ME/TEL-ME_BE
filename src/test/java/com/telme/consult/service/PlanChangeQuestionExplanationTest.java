package com.telme.consult.service;

import static com.telme.consult.dto.PlanChangeConditions.CHANGED;
import static com.telme.consult.dto.PlanChangeConditions.JOINED;
import static com.telme.consult.dto.PlanChangeConditions.KEYS;
import static com.telme.consult.dto.PlanChangeConditions.question;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.consult.service.FollowupContextService.Context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

class PlanChangeQuestionExplanationTest {
    private Context context(String text, String field, String intent) {
        return new Context(
                1,
                3,
                text,
                List.of(
                        new Candidate(
                                2,
                                field,
                                1,
                                KEYS.contains(field) ? question(field) : "어느 지역인가요?",
                                "제가 지금 요금제를 바꿀 수 있나요?",
                                "요금제 변경",
                                intent)));
    }

    @ParameterizedTest
    @DisplayName("가입월의 뜻을 설명하고 개인별 변경 가능 여부는 판단하지 않는다")
    @ValueSource(
            strings = {
                "가입한 달이 무슨 뜻이에요?",
                "가입월이 뭔가요?",
                "가입 여부 설명해 주세요",
                "가입한 달의 의미를 모르겠어요",
                "무슨 뜻이에요?",
                "이 질문이 무슨 말인가요?",
                "설명해 주세요",
                "이 질문이 이해가 안 돼요",
                "질문이 무슨 뜻이에요?",
                "이 질문 설명 좀 해주세요"
            })
    void 가입월의_뜻을_설명하고_개인별_변경_가능_여부는_판단하지_않는다(String reply) {
        var answer =
                PlanChangeQuestionExplanation.answer(context(reply, JOINED, "FAQ")).orElseThrow();
        assertThat(answer.content()).contains("달력상의 달", question(JOINED));
        assertThat(answer.content()).doesNotContain("변경할 수 있습니다", "변경할 수 없습니다", "30일", "다음 달부터");
        assertThat(answer.answerBasis()).isNull();
    }

    @ParameterizedTest
    @DisplayName("변경 이력을 설명하고 현재 대기 질문을 유지한다")
    @ValueSource(strings = {"변경 이력이 무슨 뜻이에요?", "변경 여부 설명해 주세요", "무슨 뜻이에요?", "그 질문 뜻이 뭐예요?"})
    void 변경_이력을_설명하고_현재_대기_질문을_유지한다(String reply) {
        assertThat(
                        PlanChangeQuestionExplanation.answer(context(reply, CHANGED, "FAQ"))
                                .orElseThrow()
                                .content())
                .contains("이번 달에 이미 요금제를 바꾼 적", question(CHANGED));
    }

    @ParameterizedTest
    @DisplayName("조건 응답과 새 질문은 기존 후속 입력 처리에 맡긴다")
    @ValueSource(
            strings = {
                "네",
                "아니요",
                "잘 모르겠어요",
                "알려주고 싶지 않아요",
                "나중에요",
                "요금제 변경 기준이 뭐예요?",
                "유심 비용 설명해 주세요",
                "지난달에 가입했어요. 변경 이력이 무슨 뜻이에요?"
            })
    void 조건_응답과_새_질문은_기존_후속_입력_처리에_맡긴다(String reply) {
        assertThat(PlanChangeQuestionExplanation.answer(context(reply, JOINED, "FAQ"))).isEmpty();
    }

    @Test
    @DisplayName("대기 중인 요금제 상담이 없거나 대상이 여러 개이면 설명하지 않는다")
    void 대기_중인_요금제_상담이_없거나_대상이_여러_개이면_설명하지_않는다() {
        assertThat(PlanChangeQuestionExplanation.answer(new Context(1, 3, "무슨 뜻이에요?", List.of())))
                .isEmpty();
        assertThat(PlanChangeQuestionExplanation.answer(context("무슨 뜻이에요?", "location", "STORE")))
                .isEmpty();
        var candidate = context("무슨 뜻이에요?", JOINED, "FAQ").candidates().getFirst();
        assertThat(
                        PlanChangeQuestionExplanation.answer(
                                new Context(1, 3, "무슨 뜻이에요?", List.of(candidate, candidate))))
                .isEmpty();
    }
}
