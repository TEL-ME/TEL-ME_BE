package com.telme.consult.service;

import java.util.regex.Pattern;

// 채팅에서 요금제를 실제로 바꿔 달라는 말인지 정한다. 실제 계약을 바꾸는 일이라 모델 판단에 맡기지 않고 규칙이 분명할 때만 받는다
public final class PlanChangeRequestRules {

    // 요금제 변경을 맡기는 말(바꿔줘, 신청할게요, 갈아탈래요). 바꾸면 얼마인지처럼 가정하는 말은 넣지 않는다
    private static final Pattern CHANGE_REQUEST = Pattern.compile(
            "(바꿔|변경해|신청해|전환해)(줘|주세요|주실래요|줄래|주라)|갈아타(줘|게해줘)"
                    + "|(바꿀|변경할|신청할|갈아탈|전환할)(게|께|래)|(으로|로)(할게|할께|할래|해줘|해주세요)");
    private static final Pattern CHANGE_WISH = Pattern.compile("(바꾸|변경하|신청하|갈아타|전환하)고싶");
    // 요금제 이름 조각이나 "그걸로"처럼 바꿀 대상을 가리키는 말
    private static final Pattern CHANGE_TARGET = Pattern.compile(
            "(?i)5g|lte|알뜰|라이트|스탠다드|프리미엄|언리미티드|베이직|맥스|플러스|미니|세이브|오지|엘티이"
                    + "|[그이저](걸|거|것)으?로|추천(해준|한|받은)(걸|거|것|요금제)");
    private static final Pattern PLAN_WORD = Pattern.compile("요금제|플랜");
    // 알뜰폰으로 옮기기, 기기·번호·유심·명의 변경은 요금제 변경이 아니라 FAQ가 답한다
    private static final Pattern NOT_PLAN_CHANGE = Pattern.compile(
            "알뜰폰|휴대폰|핸드폰|스마트폰|기기|단말|번호|유심|이심|통신사|명의|주소|카드|결제|비밀번호");
    // 어떤 요금제로 바꿀지 아직 고르는 중이면 추천 경로가 답한다
    private static final Pattern STILL_CHOOSING = Pattern.compile(
            "(뭐|뭘|어떤|어느|무슨)(가|로|요금제|걸|거)|좋을까|나을까|좋은지|추천해(줘|주세요|주실|줄래)|추천좀");

    private PlanChangeRequestRules() {
    }

    // 요금제 이름이나 "그걸로"와 함께 바꿔 달라는 말, "요금제 바꿔줘"처럼 분명히 맡기는 말만 변경 요청이고 방법·가능 여부·비교 질문은 FAQ가 답한다
    public static boolean requestsPlanChange(String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String text = question.replaceAll("\\s+", "");
        // 방법·가능 여부·비교 판단은 요금제 답변 규칙(PlanQuestionRules)을 그대로 따른다. 요금제 목록 질문이 아니라 NONE을 넘긴다
        if (PlanQuestionRules.asksOtherService(text) || PlanQuestionRules.asksProcedure(PlanQuestionType.NONE, text)
                || STILL_CHOOSING.matcher(text).find() || NOT_PLAN_CHANGE.matcher(text).find()) {
            return false;
        }
        boolean request = CHANGE_REQUEST.matcher(text).find();
        boolean wish = CHANGE_WISH.matcher(text).find();
        if (CHANGE_TARGET.matcher(text).find()) {
            return request || wish;
        }
        return request && PLAN_WORD.matcher(text).find();
    }
}
