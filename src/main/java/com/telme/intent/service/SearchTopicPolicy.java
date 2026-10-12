package com.telme.intent.service;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 재검색 문장이 원문에 없는 업무 대상을 새로 만들었는지 판정한다.
 * 모델이 "지난달에 한 번 바꿨는데"를 "유심 재발급"으로 채우는 것처럼, 대상이 없는 질문에 대상을 추측하면 엉뚱한 FAQ로 답하게 된다.
 * 같은 대상의 다른 표현("번호를 옮기고" -> "번호이동")은 같은 대상으로 본다. 띄어쓰기는 무시한다.
 * 여기 없는 표현은 판정하지 않고 의미 검증에 맡긴다. 잘못 거절해도 재검색을 하지 않을 뿐이다.
 */
final class SearchTopicPolicy {

    enum Topic {
        USIM("유심|usim|심카드|칩|이심|e심|esim"),
        PORTING("번호이동|번호를?옮|통신사를?옮|통신사로옮|통신사를?(바꾸|바꿔|변경)|다른통신사"),
        NAME_CHANGE("명의|양도|승계|이름으로(돌리|돌려|바꾸|바꿔|변경|옮)"),
        TERMINATE("해지|해약|위약금"),
        CONTRACT("약정"),
        ROAMING("로밍|해외|출국|외국"),
        PLAN("요금제|무제한"),
        DATA("데이터|인터넷|쉐어링|테더링"),
        // "요금"만으로는 고르지 않는다. "비용", "얼마"를 "요금"으로 바꿔 쓰는 재작성이 흔하다.
        BILLING("청구|납부|고지서|미납|연체|자동이체|밀린|밀렸"),
        DEVICE("기기|단말|폰|액정|할부"),
        NEW_LINE("개통|신규"),
        LOSS("분실|잃어버|잃었|도난"),
        SUSPEND("정지"),
        DISCOUNT("할인|결합|묶");

        private final Pattern pattern;

        Topic(String regex) {
            this.pattern = Pattern.compile(regex);
        }
    }

    private SearchTopicPolicy() {
    }

    /** 후보 문장에만 있는 업무 대상이 하나라도 있으면 true다. */
    static boolean introducesNewTopic(String original, String candidate) {
        Set<Topic> added = topics(candidate);
        added.removeAll(topics(original));
        return !added.isEmpty();
    }

    static Set<Topic> topics(String text) {
        Set<Topic> found = EnumSet.noneOf(Topic.class);
        if (text == null) {
            return found;
        }
        String compact = text.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        for (Topic topic : Topic.values()) {
            if (topic.pattern.matcher(compact).find()) {
                found.add(topic);
            }
        }
        return found;
    }
}
