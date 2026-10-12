package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SearchTopicPolicyTest {

    // 같은 대상의 다른 표현이나 원문에 있던 대상만 쓴 문장은 허용한다.
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "유심을 잃어버렸어요 | 유심 분실 처리 안내",
            "유심을잃어버렸어요 | 유심 분실 처리 안내",
            "다른 통신사로 번호를 옮기고 싶어요 | 번호이동 신청 방법",
            "해외에서 데이터가 안 돼요 | 해외 데이터 이용 불가 처리 안내",
            "미납 요금이 있는데 번호이동하고 싶어요 | 미납 요금이 있는 경우 번호이동 안내",
            "다음 달에 입대합니다. 약정이 남았는데 해지하면 얼마를 물어야 하나요 | 약정 기간 남은 상태에서 해지 위약금 금액",
            "아버지 회선을 제 이름으로 돌리려는데 돈이 드는지 알고 싶습니다 | 아버지 회선 명의변경 비용",
            "요금 몇 천원 밀린 게 있는데 그대로 다른 통신사로 옮겨도 되나요 | 미납 요금이 있는 상태에서 번호이동 가능 여부",
            "시니어 요금제로 바꾸면 기존 약정 할인은 그대로 유지되나요? | 시니어 요금제로 변경 시 기존 약정 할인 유지되나요?",
            "번호이동하고싶어요 | 번호이동 방법 및 필요 절차",
            "유심 재발급 비용과 배송비를 알려주세요 | 유심 재발급 요금"
    })
    void sameTopicsAreAllowed(String original, String candidate) {
        assertThat(SearchTopicPolicy.introducesNewTopic(original, candidate)).isFalse();
    }

    // 원문에 없는 업무 대상을 추측해 넣은 문장은 거절한다.
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "지난달에 한 번 바꿨는데 이달에 또 바꿔도 되는지 궁금합니다 | 동일 기간 내 유심 재발급 가능 여부",
            "여행 갈 때 아이패드도 들고 가는데 폰만 신청하면 같이 되는 거 아닌가요 | 여행 중 폰 개통 시 아이패드와 데이터 공유 가능 여부",
            "해지는 원하지 않고 요금만 줄이고 싶어요 | 요금제 변경 방법",
            "얼마예요? | 유심 재발급 비용"
    })
    void inventedTopicsAreRejected(String original, String candidate) {
        assertThat(SearchTopicPolicy.introducesNewTopic(original, candidate)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "요금제 변경 | PLAN",
            "요금 납부 | BILLING",
            "칩 크기 | USIM"
    })
    void planIsNotBillingAndChipIsUsim(String text, SearchTopicPolicy.Topic topic) {
        assertThat(SearchTopicPolicy.topics(text)).containsExactly(topic);
    }
}
