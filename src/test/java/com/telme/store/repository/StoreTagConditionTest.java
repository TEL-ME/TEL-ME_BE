package com.telme.store.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.store.entity.StoreServiceType;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoreTagConditionTest {

    @Test
    @DisplayName("모든 업무 코드에 서로 다른 태그 번호가 있다")
    void 모든_업무에_태그가_있다() {
        assertThat(Arrays.stream(StoreServiceType.Code.values()).map(StoreTag::of).map(StoreTag::id))
                .doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(id).isPositive());
    }

    @Test
    @DisplayName("고른 태그를 정렬해 SQL에 배열 상수로 넣는다")
    void 태그를_상수로_넣는다() {
        StoreTagCondition condition = StoreTagCondition.of(List.of(StoreTag.USIM_REISSUE, StoreTag.PORT_IN));

        assertThat(condition.toSql()).isEqualTo("s.tags @> '{2,4}'::int[]");
        assertThat(condition.parameters()).isEmpty();
        assertThat(condition.backedBySpatialIndex()).isTrue();
    }

    @Test
    @DisplayName("같은 태그를 여러 번 골라도 한 번만 넣는다")
    void 중복_태그는_한_번만() {
        StoreTagCondition condition = StoreTagCondition.of(List.of(StoreTag.PORT_IN, StoreTag.PORT_IN));

        assertThat(condition.toSql()).isEqualTo("s.tags @> '{2}'::int[]");
    }

    @Test
    @DisplayName("태그가 없거나 번호가 1보다 작으면 조건을 만들지 않는다")
    void 잘못된_태그는_거부한다() {
        assertThatThrownBy(() -> StoreTagCondition.of(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoreTagCondition(new TreeSet<>(List.of(0, 2))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StoreTagCondition(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
