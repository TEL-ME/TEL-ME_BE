package com.telme.global;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

// 로컬 시드(dev-migration)가 운영에 적용되지 않는지 확인
class ProdFlywayLocationsTest {

    @Test
    @DisplayName("prod 프로파일은 로컬 개발용 시드(dev-migration)를 포함하지 않는다")
    void prod_프로파일은_dev_시드를_포함하지_않는다() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application-prod", new ClassPathResource("application-prod.yml"));

        Object locations = sources.stream()
                .map(source -> source.getProperty("spring.flyway.locations"))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);

        assertThat(locations)
                .as("application-prod.yml 이 spring.flyway.locations 를 덮어써야 한다. "
                        + "값이 없으면 베이스 설정의 dev-migration 이 운영에 그대로 적용된다")
                .isNotNull();

        assertThat(locations.toString())
                .as("운영에는 스키마 마이그레이션만 적용해야 한다")
                .doesNotContain("dev-migration")
                .contains("db/migration");
    }
}
