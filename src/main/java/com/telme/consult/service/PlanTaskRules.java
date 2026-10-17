package com.telme.consult.service;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 채팅에서 요금제 업무를 맡기는 말인지와 어떤 업무인지 정한다. 계약 화면으로 안내하는 일이라 모델 판단에 맡기지 않고 규칙이 분명할 때만 받는다
public final class PlanTaskRules {

    // 맡기는 말(바꿔줘, 신청해 주세요, 갈아탈래요, 해지할게요). 바꾸면 얼마인지처럼 가정하는 말은 넣지 않는다
    private static final Pattern REQUEST = Pattern.compile("(해|꿔|켜|타)(줘|주세요|주실래요|줄래|주라)|(할|탈|꿀)(게|께|래)|부탁");
    private static final Pattern WISH = Pattern.compile("(하|타|꾸)고싶");
    // "5G 라이트로 할게요"처럼 대상만 말하고 맡기는 말
    private static final Pattern CHOOSE = Pattern.compile("(으로|로)(할게|할께|할래|해줘|해주세요)");

    // "정해지는지"처럼 낱말 안에 든 "해지"는 잡지 않도록 해지 뒤에 맡기는 말이 이어질 때만 본다
    private static final Pattern CANCEL = Pattern.compile("해지(해|할|하|신청|진행|부탁|좀)");
    private static final Pattern PAUSE = Pattern.compile("정지");
    private static final Pattern PAUSE_WORD = Pattern.compile("일시정지");
    // 정지를 푸는 말은 정지 신청이 아니다
    private static final Pattern RESUME = Pattern.compile("해제|풀어|풀기|재개|다시쓰");
    private static final Pattern CHANGE = Pattern.compile("변경|바꾸|바꿔|바꿀|갈아|전환");
    private static final Pattern SUBSCRIBE = Pattern.compile("신청|가입|개통");

    // 요금제 이름 조각이나 "그걸로"처럼 대상 요금제를 가리키는 말
    private static final Pattern TARGET = Pattern.compile(
            "(?i)5g|lte|알뜰|라이트|스탠다드|프리미엄|언리미티드|베이직|맥스|플러스|미니|세이브|시니어|청소년|오지|엘티이"
                    + "|[그이저](걸|거|것)으?로|추천(해준|한|받은)(걸|거|것|요금제)");
    private static final Pattern PLAN_WORD = Pattern.compile("요금제|플랜");
    // 알뜰폰으로 옮기기, 기기·번호·유심·명의 변경, 분실 정지, 부가서비스·OTT 구독, 회원 탈퇴는 요금제 업무가 아니라 FAQ가 답한다.
    // "디즈니 플러스", "유튜브 프리미엄"처럼 요금제 이름 조각이 든 서비스 이름도 여기서 거른다
    private static final Pattern NOT_PLAN_TASK = Pattern.compile(
            "(?i)ott|넷플릭스|유튜브|디즈니|티빙|웨이브|왓챠|쿠팡|멜론|지니|스포티파이|알뜰폰|휴대폰|핸드폰|스마트폰|기기|단말|번호|유심|이심|통신사|명의|주소|카드|결제|비밀번호|분실|도난|부가|자동이체|탈퇴|회원");
    // 어떤 요금제로 할지 아직 고르는 중이면 추천 경로가 답한다
    private static final Pattern STILL_CHOOSING = Pattern.compile(
            "(뭐|뭘|어떤|어느|무슨)(가|로|요금제|걸|거)|좋을까|나을까|좋은지|추천해(줘|주세요|주실|줄래)|추천좀");

    private static final Pattern DAYS = Pattern.compile("(?<![0-9월])([0-9]{1,3})일");
    private static final Pattern WEEKS = Pattern.compile("([0-9]{1,2})주");
    private static final Pattern MONTHS = Pattern.compile("([0-9]{1,2})(개월|달)");

    private PlanTaskRules() {
    }

    // 해지 > 일시 정지 > 변경 > 신청 순서로 본다. "변경 신청해줘"는 변경, "요금제 신청해줘"는 신청이다.
    // 방법·가능 여부·비교 질문("해지하면 위약금 얼마예요?", "바꿀 수 있어요?")은 업무 요청이 아니라 FAQ가 답한다
    public static Optional<PlanTask> detect(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String text = question.replaceAll("\\s+", "");
        // 방법·가능 여부·비교 판단은 요금제 답변 규칙(PlanQuestionRules)을 그대로 따른다. 요금제 목록 질문이 아니라 NONE을 넘긴다
        if (PlanQuestionRules.asksOtherService(text) || PlanQuestionRules.asksProcedure(PlanQuestionType.NONE, text)
                || STILL_CHOOSING.matcher(text).find() || NOT_PLAN_TASK.matcher(text).find()) {
            return Optional.empty();
        }
        boolean choose = CHOOSE.matcher(text).find();
        boolean request = choose || REQUEST.matcher(text).find();
        boolean wish = WISH.matcher(text).find();
        if (!request && !wish) {
            return Optional.empty();
        }
        boolean planWord = PLAN_WORD.matcher(text).find();
        // 대상 없이 "해지하고 싶어요"처럼 바라기만 하는 말은 FAQ 질문일 수 있어 요금제를 함께 말할 때만 받는다
        boolean entrusted = request || planWord;
        if (CANCEL.matcher(text).find()) {
            return entrusted ? Optional.of(PlanTask.CANCEL) : Optional.empty();
        }
        if (PAUSE.matcher(text).find()) {
            // "15일 정지해줘"처럼 기간을 함께 말해도 요금제 정지로 본다
            boolean pause = !RESUME.matcher(text).find()
                    && (PAUSE_WORD.matcher(text).find() || planWord || pauseDays(text).isPresent());
            return pause && entrusted ? Optional.of(PlanTask.PAUSE) : Optional.empty();
        }
        boolean target = TARGET.matcher(text).find();
        // 대상 요금제를 말하면 바라는 말도 받고, 말하지 않으면 "요금제"와 함께 분명히 맡길 때만 받는다
        boolean named = target ? request || wish : request && planWord;
        if (CHANGE.matcher(text).find() || (choose && !SUBSCRIBE.matcher(text).find())) {
            return named ? Optional.of(PlanTask.CHANGE) : Optional.empty();
        }
        if (SUBSCRIBE.matcher(text).find()) {
            return named ? Optional.of(PlanTask.SUBSCRIBE) : Optional.empty();
        }
        return Optional.empty();
    }

    // "7일", "일주일", "2주", "보름", "한 달"을 일수로 읽는다. 없으면 비어 있다
    static OptionalInt pauseDays(String question) {
        if (question == null) {
            return OptionalInt.empty();
        }
        String text = question.replaceAll("\\s+", "");
        Matcher days = DAYS.matcher(text);
        if (days.find()) {
            return OptionalInt.of(Integer.parseInt(days.group(1)));
        }
        if (text.contains("일주일") || text.contains("한주")) {
            return OptionalInt.of(7);
        }
        Matcher weeks = WEEKS.matcher(text);
        if (weeks.find()) {
            return OptionalInt.of(Integer.parseInt(weeks.group(1)) * 7);
        }
        if (text.contains("이주")) {
            return OptionalInt.of(14);
        }
        if (text.contains("보름")) {
            return OptionalInt.of(15);
        }
        if (text.contains("한달") || text.contains("한개월")) {
            return OptionalInt.of(30);
        }
        if (text.contains("두달")) {
            return OptionalInt.of(60);
        }
        Matcher months = MONTHS.matcher(text);
        if (months.find()) {
            return OptionalInt.of(Integer.parseInt(months.group(1)) * 30);
        }
        return OptionalInt.empty();
    }
}
