package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConditionOptionsGroundingTest {

    private final FaqSearchResponse source = new FaqSearchResponse(65L, null, "test",
            "유심 재발급 서류",
            "본인이 방문하시는 경우에는 신분증만 지참하시면 됩니다. 대리인이 방문할 때는 위임장이 필요합니다.",
            0.68, 1, null, 3, null);

    @Test
    @DisplayName("낱말이 근거에 흩어져 있는 선택지는 버튼으로 남긴다")
    void 낱말이_근거에_있으면_남긴다() {
        assertThat(ConditionGrounding.groundedOptions(
                List.of("본인 방문", "대리인 방문"),
                "본인이 직접 방문하시나요? 아니면 대리인이 방문하시나요?", source))
                .containsExactly("본인 방문", "대리인 방문");
    }

    @Test
    @DisplayName("근거에 없는 낱말이 섞인 선택지는 버린다")
    void 근거에_없는_낱말은_버린다() {
        assertThat(ConditionGrounding.groundedOptions(
                List.of("본인 방문", "법인 방문"), "누가 방문하시나요?", source))
                .containsExactly("본인 방문");
    }
}
