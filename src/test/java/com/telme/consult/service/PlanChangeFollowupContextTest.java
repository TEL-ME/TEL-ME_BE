package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static com.telme.consult.dto.PlanChangeConditions.*;

import com.telme.intent.service.RuleBasedRoutingFallback;
import com.telme.intent.dto.res.LlmFollowUpPayload.ResponseType;
import com.telme.intent.dto.res.LlmFollowUpPayload.Status;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

class PlanChangeFollowupContextTest {
    static Stream<Arguments> replyStates() {
        return Stream.of(
            Arguments.of("친구는 가입한 달을 모르겠어요", JOINED, Map.of(), Set.of()),
            Arguments.of("친구는 가입한 달을 모르겠어요. 잘 모르겠어요", JOINED, Map.of(), Set.of()),
            Arguments.of("저는 지난달에 가입했고 친구는 이번 달 변경 이력을 모르겠어요", JOINED, Map.of(JOINED, NO), Set.of()),
            Arguments.of("친구는 가입한 달을 모르겠어요. 저는 지난달에 가입했어요", JOINED, Map.of(JOINED, NO), Set.of()),
            Arguments.of("만약 가입한 달을 모르면 어떻게 돼요?", JOINED, Map.of(), Set.of()),
            Arguments.of("가입한 달은 모르겠어요. 아니, 지난달에 가입했어요", JOINED, Map.of(JOINED, NO), Set.of()),
            Arguments.of("가입한 달은 모르겠어요. 아니, 지난달이에요", CHANGED, Map.of(JOINED, NO), Set.of()),
            Arguments.of("지난달에 가입했어요. 정정할게요. 가입한 달은 모르겠어요", CHANGED, Map.of(), Set.of(JOINED)),
            Arguments.of("지난달에 가입했어요. 이번 달 변경 이력은 모르겠어요", JOINED, Map.of(JOINED, NO), Set.of(CHANGED)),
            Arguments.of("알려주고 싶지 않아요", CHANGED, Map.of(), Set.of(CHANGED)));
    }

    @ParameterizedTest @MethodSource("replyStates")
    void filledAndUnavailableUseSameSubjectAndLatestReplyOrder(String reply, String waiting,
            Map<String,String> expectedValues, Set<String> expectedUnavailable) {
        assertThat(extract(reply, Set.of(waiting))).isEqualTo(expectedValues);
        assertThat(unavailableKeys(reply, Set.of(waiting))).isEqualTo(expectedUnavailable);
        var result = new RuleBasedRoutingFallback().classifyFollowUp(reply, Set.of(waiting));
        assertThat(result.conditions().stream().filter(c -> c.status() == Status.DECLINED)
                .map(c -> c.key()).collect(java.util.stream.Collectors.toSet())).isEqualTo(expectedUnavailable);
        assertThat(result.conditions().stream().filter(c -> c.status() == Status.FILLED)
                .collect(java.util.stream.Collectors.toMap(c -> c.key(), c -> c.value()))).isEqualTo(expectedValues);
    }

    static Stream<Arguments> explicitReplies() {
        return Stream.of(
            Arguments.of("네", JOINED, Map.of(JOINED, YES)),
            Arguments.of("아니요", CHANGED, Map.of(CHANGED, NO)),
            Arguments.of("지난달이요", JOINED, Map.of(JOINED, NO)),
            Arguments.of("지난달이요", CHANGED, Map.of()),
            Arguments.of("네, 고마워요", JOINED, Map.of(JOINED, YES)),
            Arguments.of("네 고마워요", JOINED, Map.of(JOINED, YES)),
            Arguments.of("지난달에 가입했고, 이번 달에는 아직 요금제를 안 바꿨어요.", JOINED, Map.of(JOINED, NO, CHANGED, NO)),
            Arguments.of("아니, 이번 달에 가입했어요", CHANGED, Map.of(JOINED, YES)),
            Arguments.of("이번 달 가입했어요. 아니, 지난달이에요", JOINED, Map.of(JOINED, NO)),
            Arguments.of("이번 달인지 지난달인지 모르겠어요", JOINED, Map.of()),
            Arguments.of("바꾼 것 같기도 하고 아닌 것 같기도 해요", CHANGED, Map.of()),
            Arguments.of("이번 달에 가입한 것 같아요", JOINED, Map.of()),
            Arguments.of("이번 달에 가입했어요. 지난달에 가입했어요", JOINED, Map.of()),
            Arguments.of("이번 달에 가입했어요, 지난달에 가입했어요", JOINED, Map.of()),
            Arguments.of("이번 달에 가입했다면 어떻게 돼요?", JOINED, Map.of()),
            Arguments.of("친구는 이번 달에 가입했어요", JOINED, Map.of()),
            Arguments.of("저는 지난달에 가입했고 친구는 이번 달이에요", JOINED, Map.of(JOINED, NO)));
    }
    @ParameterizedTest @MethodSource("explicitReplies")
    void extractsOnlyUnambiguousOwnConditions(String reply, String waiting, Map<String,String> expected) {
        assertThat(extract(reply, Set.of(waiting))).isEqualTo(expected);
    }
    @Test
    void acknowledgementWithoutAWaitingKeyHasNoCondition() {
        assertThat(extract("네", Set.of())).isEmpty();
        assertThat(extract("아니요", Set.of())).isEmpty();
    }
    @Test
    void mixedNewRequestDoesNotFillOldConditionUnderExistingSingleConsultPolicy() {
        var result = new RuleBasedRoutingFallback().classifyFollowUp("네, 유심 비용도 알려주세요", Set.of(JOINED));
        assertThat(result.responseType()).isEqualTo(ResponseType.NEW_QUESTION);
        assertThat(result.conditions()).isEmpty();
    }
    @Test
    void requestForClarificationOfWaitingQuestionDoesNotBecomeNewConsultation() {
        var result = new RuleBasedRoutingFallback().classifyFollowUp("가입한 달이 무슨 뜻이에요?", Set.of(JOINED));
        assertThat(result.responseType()).isEqualTo(ResponseType.DEFERRED);
        assertThat(result.conditions()).isEmpty();
        assertThat(isDeferred("가입한 달이 무슨 뜻이에요?")).isTrue();
    }
    @Test
    void unavailableOtherConditionIsNotLostWhenOneValueIsProvided() {
        var result = new RuleBasedRoutingFallback().classifyFollowUp("가입한 달은 모르겠어요. 이번 달에는 아직 안 바꿨어요", Set.of(CHANGED));
        assertThat(result.conditions()).anySatisfy(c -> { assertThat(c.key()).isEqualTo(JOINED); assertThat(c.status()).isEqualTo(Status.DECLINED); });
        assertThat(result.conditions()).anySatisfy(c -> { assertThat(c.key()).isEqualTo(CHANGED); assertThat(c.value()).isEqualTo(NO); });
    }
}
