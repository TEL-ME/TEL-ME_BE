package com.telme.global;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// 엔티티 매핑과 실제 스키마 일치하는지 확인(길이까지는 검사X, 타입만)
@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class SchemaValidationTest {

    @Test
    @DisplayName("엔티티 매핑이 실제 DB 스키마와 일치한다")
    void 엔티티_매핑이_실제_스키마와_일치한다() {
        // 컨텍스트 로딩 자체가 검증
        // 불일치하면 SchemaManagementException 으로 컨텍스트 생성이 실패
    }
}
