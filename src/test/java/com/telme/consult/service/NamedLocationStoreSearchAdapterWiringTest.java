package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class NamedLocationStoreSearchAdapterWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("명시 지역 검색 구현이 미연결 기본 구현을 대신해 채팅 매장 검색에 연결된다")
    void 명시_지역_구현이_연결된다() {
        assertThat(context.getBeansOfType(NamedLocationStoreSearchPort.class)).hasSize(1);
        assertThat(context.getBean(NamedLocationStoreSearchPort.class))
                .isInstanceOf(NamedLocationStoreSearchAdapter.class);
    }
}
