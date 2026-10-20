package com.telme.chat.service;

import java.util.regex.Pattern;

/**
 * 종료 요약에서 바로 앞 문의를 이어받는 고객 발화. 실시간 경로는 "아니 그러니까 정지는 어디서 해요", "카드 말고 계좌로요"를
 * 새 질문으로 접수하고, 요약 모델도 주제를 달리 붙이는 일이 있어 같은 문의가 둘로 나뉜다. 고객 발화의 글자만 본다.
 * 다시 묻기는 같은 문의로 묶기만 하고, 정정은 정정 전 답을 문의별 목록에서 뺀다.
 * 앞 문의를 거두는 말("그건 됐고 …")은 앞 문의를 남은 사항에서 뺀다.
 */
final class ChatEndSummaryFollowUps {
    private ChatEndSummaryFollowUps() {}

    // 머리에 붙는 감탄·사과("아 죄송 카드 말고 계좌로요", "아 헷갈렸다 엄마 아니고 아빠꺼요")
    private static final Pattern FILLER = Pattern.compile(
            "^((아|앗|어|아참|참|잠깐만?|죄송(해요|합니다)?|미안(해요|합니다)?|헷갈렸(다|네요|어요))\\s+)+");
    private static final Pattern REASK = Pattern.compile(
            "^(아니\\s*(그러니까|그니까|그러니깐|그니깐)|내\\s*말은|제\\s*말은|다시\\s*(여쭤|물어)볼게요?)(\\s|$)");
    // "그니까"만으로 시작하면 새 질문일 수도 있다("그니까 아예 차단하는 방법은요?"). 같은 말을 되풀이하는 어미로
    // 끝날 때만 다시 묻기다("그니까 돈 안내면 언제부터 폰 끊기냐구요")
    private static final Pattern SO = Pattern.compile("^(그러니까|그니까|그러니깐|그니깐)(\\s|$)");
    private static final Pattern RESTATE = Pattern.compile(
            "(냐구요|냐고요|냐구|냐고|다구요|다고요|라구요|라고요|거에요|거예요|거죠|말이에요|말이죠|말입니다)[?.!~\\s]*$");
    // 바로 앞 문의를 거두는 말("아 그건 됐고 …", "매장은 됐고 …")
    private static final Pattern WITHDRAW = Pattern.compile(
            "^(그건|그거는?|그\\s*얘기는|\\S+[은는])\\s*(됐고|됐구|됐어요|괜찮고|필요\\s*없고)(\\s|$)");
    // "카드 말고 계좌로요", "1년 아니고 1년 6개월이요", "14세가 아니라 12세요"
    private static final Pattern REPLACE = Pattern.compile("\\S\\s*(말고|아니고|[가이]\\s*아니라)\\s+\\S");
    // "아니 그냥 5만원 이하면 돼요", "아니다 5일이요", "잘못 말했어요 계좌요", "정정할게요 15만원이요".
    // "아니 근데 …"처럼 새 질문을 여는 말은 정정이 아니다
    private static final Pattern DENY = Pattern.compile("^(아니(다|야)?|잘못\\s*말했\\S*|정정(할게요|합니다|이요)?)\\s+"
            + "(?!(근데|그런데|그리고|그럼|혹시|또)(\\s|$))\\S");
    // 정정 문구라도 새로 묻는 말이면("유심 말고 eSIM은 얼마예요") 다른 문의다
    private static final Pattern QUESTION = Pattern.compile("\\?|(나요|까요|니까|얼마|어때요|뭐예요|인가요|은요|는요|되나|하나|있나)\\S{0,2}$");

    static boolean isReask(String content) {
        if (content == null) {
            return false;
        }
        String text = normalize(content);
        return REASK.matcher(text).find() || SO.matcher(text).find() && RESTATE.matcher(text).find();
    }

    static boolean isWithdrawal(String content) {
        return content != null && WITHDRAW.matcher(normalize(content)).find();
    }

    static boolean isCorrection(String content) {
        if (content == null || isReask(content)) {
            return false;
        }
        String text = normalize(content);
        return !QUESTION.matcher(text).find() && (REPLACE.matcher(text).find() || DENY.matcher(text).find());
    }

    static boolean isFollowUp(String content) {
        return isReask(content) || isCorrection(content);
    }

    private static String normalize(String content) {
        String text = content.strip().replaceAll("\\s+", " ").replaceAll("[.!~,ㅠㅜ]+", " ").strip();
        return FILLER.matcher(text).replaceFirst("");
    }
}
