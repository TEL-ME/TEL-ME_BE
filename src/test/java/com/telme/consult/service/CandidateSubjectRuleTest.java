package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CandidateSubjectRuleTest {

    @Test
    @DisplayName("하위 질문의 대상이 FAQ에 없으면 막는다: eSIM 비용에 유심 재발급 비용 FAQ")
    void 질문_대상이_FAQ에_없으면_막는다() {
        assertThat(CandidateSubjectRule.passes("그 김에 이심으로 바꾸면 비용은 얼마예요?",
                "새 폰 할부로 사려는데 몇 개월 할부 돼요? 그 김에 이심으로 바꾸면 비용은 얼마예요?",
                "유심 새로 받는 데 얼마 들어요", "7,700원입니다. 매장 가시면 바로 되고, 택배는 2~3 영업일 걸립니다.")).isFalse();
    }

    @Test
    @DisplayName("하위 질문에 대상이 없으면 그 문장과 앞 문장의 대상으로 본다: 부가서비스 해지에 회선 해지 FAQ")
    void 조각이면_가까운_문장의_대상으로_본다() {
        String message = "부가서비스 하나 해지하려는데 이번 달 요금은 빠지나요? 해지하면 바로 못 쓰게 되는 거예요?";
        assertThat(CandidateSubjectRule.passes("이번 달 요금은 빠지나요", message,
                "해지하는 달 요금은 한 달 치 다 내요?", "아닙니다. 해지한 달의 요금은 일할 계산됩니다.")).isFalse();
        assertThat(CandidateSubjectRule.passes("해지하면 바로 못 쓰게 되는 거예요", message,
                "해지하려면 어디로 가야 해요", "매장에 가시거나 고객센터에 전화하시면 됩니다. 신청하시면 바로 해지됩니다."))
                .isFalse();
        assertThat(CandidateSubjectRule.passes("이번 달 요금은 빠지나요", message,
                "부가서비스 해지하면 그 달 요금은 안 나가나요?", "해지를 신청하셔도 해당 월 말일까지는 이용하실 수 있습니다."))
                .isTrue();
    }

    @Test
    @DisplayName("FAQ 제목의 대상이 질문 쪽에 없으면 막는다: 번호이동 질문에 명의변경 FAQ")
    void FAQ_제목_대상이_질문에_없으면_막는다() {
        assertThat(CandidateSubjectRule.passes("요금이 밀려 있어도 옮길 수 있나요",
                "다른 통신사로 번호 옮기려는데 요금이 밀려 있어도 옮길 수 있나요",
                "명의변경 하려는데 미납 요금이 있으면 안 되나요?", "미납 요금을 완납하셔야 명의변경이 가능합니다.")).isFalse();
    }

    @Test
    @DisplayName("같은 대상이면 통과한다. 대상 낱말이 하나도 없으면 판단하지 않고 통과시킨다")
    void 같은_대상이거나_대상이_없으면_통과한다() {
        assertThat(CandidateSubjectRule.passes("알뜰요금제가 정확히 뭐예요?", "알뜰요금제가 정확히 뭐예요? 종류는 몇 개?",
                "알뜰 요금제라는 게 뭐예요", "세이브랑 미니, 두 가지가 있는 저렴한 요금제입니다.")).isTrue();
        assertThat(CandidateSubjectRule.passes("아빠 폰을 제 명의로 바꾸려면 서류 뭐 필요해요?",
                "아빠 폰을 제 명의로 바꾸려면 서류 뭐 필요해요? 그리고 아빠가 잃어버린 유심 재발급을 제가 대신 받으러 가면 뭐 챙겨야 하는지도요",
                "명의를 아버지한테서 저한테 바꾸려면 뭐가 필요해요?",
                "양도인과 양수인의 신분증이 각각 필요하고, 원칙적으로 두 분이 함께 매장을 방문하셔야 합니다.")).isTrue();
        assertThat(CandidateSubjectRule.passes("언제까지 내야 되는지도요", null,
                "요금 며칠까지 내야 해요", "매월 25일까지입니다.")).isTrue();
    }

    @Test
    @DisplayName("가까운 문장은 하위 질문이 있는 문장과 바로 앞 문장이다")
    void 가까운_문장은_하위_질문_문장과_앞_문장이다() {
        String message = "첫 질문입니다. eSIM은 아무 폰에서나 되는 거예요? 그리고 신청하면 실제로 활성화되기까지 얼마나 걸려요";
        assertThat(CandidateSubjectRule.localContext("그리고 신청하면 실제로 활성화되기까지 얼마나 걸려요", message))
                .isEqualTo("eSIM은아무폰에서나되는거예요?그리고신청하면실제로활성화되기까지얼마나걸려요");
        assertThat(CandidateSubjectRule.localContext("못 찾는 문장", message)).isEqualTo(message);
        assertThat(CandidateSubjectRule.localContext("질문만", null)).isEqualTo("질문만");
    }
}
