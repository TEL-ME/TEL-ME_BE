package com.telme.faq.dto.req;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.faq.exception.FaqErrorCode;
import com.telme.global.common.exception.GeneralException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaqSearchRequestTest {

    @Test
    @DisplayName("topK가 null이면 기본값 3을 사용한다")
    void topK가_null이면_기본값을_사용한다() {
        var request = new FaqSearchRequest("질문", null);

        assertThat(request.topK()).isEqualTo(3);
    }

    @Test
    @DisplayName("topK가 0이면 예외를 던진다")
    void topK가_0이면_예외를_던진다() {
        assertThatThrownBy(() -> new FaqSearchRequest("질문", 0))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.INVALID_TOP_K);
    }

    @Test
    @DisplayName("topK가 음수면 예외를 던진다")
    void topK가_음수면_예외를_던진다() {
        assertThatThrownBy(() -> new FaqSearchRequest("질문", -1))
                .isInstanceOf(GeneralException.class)
                .extracting(exception -> ((GeneralException) exception).getErrorCode())
                .isEqualTo(FaqErrorCode.INVALID_TOP_K);
    }

    @Test
    @DisplayName("topK가 양수면 그대로 사용한다")
    void topK가_양수면_그대로_사용한다() {
        var request = new FaqSearchRequest("질문", 5);

        assertThat(request.topK()).isEqualTo(5);
    }
}
