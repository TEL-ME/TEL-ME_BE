package com.telme.chat.dto.res;

import java.util.Objects;
import lombok.Builder;

/** 검색 기준의 의미만 저장하고 사용자 GPS 원본은 이력에 남기지 않는다. */
@Builder
public record ChatStoreSearchContextResponse(Type type, String label, Integer radiusMeters) {
    public enum Type {
        CURRENT_LOCATION, REGION, ADDRESS, PLACE
    }

    public ChatStoreSearchContextResponse {
        Objects.requireNonNull(type, "type");
        if (label == null || label.isBlank() || radiusMeters != null && radiusMeters < 1
                || type == Type.REGION && radiusMeters != null) {
            throw new IllegalArgumentException("올바른 검색 기준이 필요합니다.");
        }
    }
}
