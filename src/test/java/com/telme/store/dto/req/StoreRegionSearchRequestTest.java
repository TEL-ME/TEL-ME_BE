package com.telme.store.dto.req;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.store.entity.StoreServiceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreRegionSearchRequestTest {

    @Test
    @DisplayName("업무 코드 문자열을 enum으로 바꾸고, 비어 있으면 필터 없음")
    void 업무_코드_변환() {
        assertThat(new StoreRegionSearchRequest("11680", "PORT_IN", null, null).serviceTypeCode())
                .isEqualTo(StoreServiceType.Code.PORT_IN);
        assertThat(new StoreRegionSearchRequest("11680", " ", null, null).serviceTypeCode()).isNull();
        assertThat(new StoreRegionSearchRequest("11680", null, null, null).serviceTypeCode()).isNull();
    }

    @Test
    @DisplayName("페이지 값을 비우면 0페이지 20건")
    void 페이지_기본값() {
        StoreRegionSearchRequest request = new StoreRegionSearchRequest("11680", null, null, null);

        assertThat(request.page()).isZero();
        assertThat(request.size()).isEqualTo(20);
    }
}
