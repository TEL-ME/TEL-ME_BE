package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegionCodesTest {

    @Test
    @DisplayName("끝자리가 0인 시군구 코드는 일반구까지 걸리도록 앞 4자리로 찾는다")
    void 일반구가_있는_시() {
        assertThat(RegionCodes.searchPrefix("47110")).isEqualTo("4711");
        assertThat(RegionCodes.searchPrefix("11680")).isEqualTo("1168");
    }

    @Test
    @DisplayName("끝자리가 0이 아닌 시군구와 다른 단위는 그대로 둔다")
    void 그_외는_그대로() {
        assertThat(RegionCodes.searchPrefix("11215")).isEqualTo("11215");
        assertThat(RegionCodes.searchPrefix("47111")).isEqualTo("47111");
        assertThat(RegionCodes.searchPrefix("11")).isEqualTo("11");
        assertThat(RegionCodes.searchPrefix("11680107")).isEqualTo("11680107");
        assertThat(RegionCodes.searchPrefix("1168010700")).isEqualTo("1168010700");
        assertThat(RegionCodes.searchPrefix(null)).isNull();
    }
}
