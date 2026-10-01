package com.telme.store.repository;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

// 번호를 태그 배열로 묶어 필터 한번에 처리
// 태그 번호는 파라미터가 아니라 SQL에 상수로 넣는다.
// 옵티마이저가 값을 보고 흔한 태그(영업 인덱스 + 필터)와 드문 태그(태그 인덱스)를 고르게 하기 위해서다.
public record StoreTagCondition(SortedSet<Integer> tagIds) implements StoreSearchCondition {

    public StoreTagCondition {
        if (tagIds == null || tagIds.isEmpty()) {
            throw new IllegalArgumentException("태그 조건은 한 개 이상이어야 합니다.");
        }
        if (tagIds.stream().anyMatch(id -> id == null || id < 1)) {
            throw new IllegalArgumentException("태그 번호는 1 이상이어야 합니다.");
        }
        tagIds = Collections.unmodifiableSortedSet(new TreeSet<>(tagIds));
    }

    public static StoreTagCondition of(Collection<StoreTag> tags) {
        return new StoreTagCondition(tags.stream().map(StoreTag::id).collect(Collectors.toCollection(TreeSet::new)));
    }

    @Override
    public String toSql() {
        return tagIds.stream().map(String::valueOf).collect(Collectors.joining(",", "s.tags @> '{", "}'::int[]"));
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of();
    }

    @Override
    public boolean backedBySpatialIndex() {
        return true;
    }
}
