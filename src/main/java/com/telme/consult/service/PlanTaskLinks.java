package com.telme.consult.service;

import java.util.Map;
import java.util.Optional;

// 처리 화면으로 가는 링크 문구. 응답 형식을 바꾸지 않고 followUps에 담고, 프론트가 이 문구를 보면 채팅으로 보내지 않고 화면으로 이동시킨다.
// 가입·변경은 요금제 고르기(/my/plan/select), 일시 정지·해지는 내 요금제(/my/plan) 화면이다. 문구를 바꾸면 프론트와 함께 바꾼다.
// 대상 요금제가 하나로 정해졌으면 문구에 요금제 이름을 넣어 "5G 스탠다드 가입하러 가기"처럼 보낸다. 프론트는 이름을 요금제로 찾아
// 그 요금제를 고른 채로 요금제 고르기 화면을 연다(찾지 못하면 이름 없는 문구와 같은 화면)
public final class PlanTaskLinks {

    public static final String SUBSCRIBE = "요금제 가입하러 가기";
    public static final String CHANGE = "요금제 변경하러 가기";
    public static final String PAUSE = "일시 정지하러 가기";
    public static final String CANCEL = "해지하러 가기";

    // 프론트가 아직 링크로 그리지 않아 문구가 채팅으로 들어왔을 때의 안내
    private static final String PLAN_SELECT = "마이페이지의 내 요금제에서 요금제 가입·변경 화면으로 들어가 진행해 주세요.";
    private static final String MY_PLAN = "마이페이지의 내 요금제 화면에서 진행해 주세요.";
    private static final Map<String, String> GUIDES = Map.of(
            SUBSCRIBE, PLAN_SELECT, CHANGE, PLAN_SELECT, PAUSE, MY_PLAN, CANCEL, MY_PLAN);

    private static final String SUBSCRIBE_SUFFIX = " 가입하러 가기";
    private static final String CHANGE_SUFFIX = " 변경하러 가기";

    private PlanTaskLinks() {
    }

    static String subscribeTo(String planName) {
        return planName + SUBSCRIBE_SUFFIX;
    }

    static String changeTo(String planName) {
        return planName + CHANGE_SUFFIX;
    }

    // 링크 문구가 그대로 채팅으로 들어왔으면 그 화면으로 가는 길을 글로 알려준다
    static Optional<String> guideFor(String question) {
        if (question == null) {
            return Optional.empty();
        }
        String text = question.strip();
        if (GUIDES.containsKey(text)) {
            return Optional.of(GUIDES.get(text));
        }
        // 요금제 이름이 붙은 링크 문구도 이동하지 않고 채팅으로 들어오면 같은 안내를 준다
        boolean named = text.length() > SUBSCRIBE_SUFFIX.length()
                && (text.endsWith(SUBSCRIBE_SUFFIX) || text.endsWith(CHANGE_SUFFIX));
        return named ? Optional.of(PLAN_SELECT) : Optional.empty();
    }
}
