package com.telme.consult.service;

import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ClarificationTextGenerator.ClarificationPrompt;
import com.telme.consult.service.ClarificationTextGenerator.GenerationUnavailableException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DialogueService {
    private static final String LOCATION = "location";
    private static final String FALLBACK = "어느 지역의 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.";
    private final ClarificationTextGenerator generator;

    /** 모델 호출 없이 되물을지 판단한다. */
    public DialogueDecision assess(DialogueInput input) {
        return decide(input, false);
    }

    public DialogueDecision decide(DialogueInput input) {
        return decide(input, true);
    }

    private DialogueDecision decide(DialogueInput input, boolean generateText) {
        Objects.requireNonNull(input, "input");
        var conditions = new HashMap<>(input.previousConditions());
        // 정정·거절은 updates로 전달한다.
        conditions.putAll(input.updates());
        var region = conditions.get(LOCATION);
        boolean hasRegion = region != null && region.status() == ConditionStatus.FILLED;

        // 위치 권한만으로는 검색 지역을 알 수 없다.
        if (input.purpose() == Purpose.GENERAL_FAQ || hasRegion) {
            return new DialogueDecision(
                    input.consultRequestId(),
                    Action.PROCEED,
                    conditions,
                    null,
                    null,
                    MessageOrigin.NONE);
        }
        if (region != null && region.status() == ConditionStatus.DECLINED) {
            return new DialogueDecision(
                    input.consultRequestId(),
                    Action.ALTERNATIVE_GUIDANCE,
                    conditions,
                    null,
                    "검색 지역 없이는 가까운 매장을 안내하기 어려워요. 지역을 알려주실 수 있을 때 매장 찾기를 이어갈 수 있어요.",
                    MessageOrigin.TEMPLATE);
        }

        if (!generateText) {
            return new DialogueDecision(
                    input.consultRequestId(),
                    Action.ASK,
                    conditions,
                    LOCATION,
                    FALLBACK,
                    MessageOrigin.TEMPLATE);
        }

        // 지역을 묻는 데 필요한 정보만 모델에 전달한다.
        var prompt =
                new ClarificationPrompt(
                        "당신은 통신사 매장 찾기 상담의 추가 질문 작성자입니다. "
                                + "사용자가 찾는 것은 통신사 매장으로 이미 정해져 있습니다. "
                                + "검색할 지역 한 가지만 물으세요. 사용자가 역 이름이나 동네를 말하면 됩니다. "
                                + "질문 예시: 어느 지역의 매장을 찾으시나요? 역 이름이나 동네를 알려주세요. "
                                + "예시처럼 짧은 한국어 질문만 작성하세요. 매장 종류나 매장 이름을 묻지 마세요. "
                                + "위치 권한을 다시 요구하거나 지역·매장·재고·요금을 만들어내지 마세요.",
                        "상담 목적: 통신사 매장 찾기\n확인된 정보: 매장 종류=통신사 매장\n"
                                + "부족한 정보: 검색 지역\n현재 사용 가능한 위치 좌표: 없음\n위치 권한 상태: "
                                + (input.locationStatus() == LocationStatus.DECLINED
                                        ? "사용자가 거절함"
                                        : "미제공"),
                        FALLBACK);
        String text = FALLBACK;
        var origin = MessageOrigin.TEMPLATE;
        try {
            String generated = generator.generate(prompt);
            // 빈 응답·긴 응답은 제외한다. 질문 내용의 품질은 별도 검토한다.
            if (generated != null && !generated.isBlank() && generated.strip().length() <= 240) {
                text = generated.strip();
                origin = generator.origin();
            }
        } catch (GenerationUnavailableException ignored) {
            // 모델 오류가 나면 고정 질문으로 이어간다.
        }
        return new DialogueDecision(
                input.consultRequestId(), Action.ASK, conditions, LOCATION, text, origin);
    }
}
