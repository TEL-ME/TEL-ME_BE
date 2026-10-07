package com.telme.consult.service;

import com.telme.chat.dto.res.ChatStoreResponse;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse;
import com.telme.store.entity.StoreServiceType;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 명시 지역 경로는 검색만 담당하고 답변 저장·SSE 완료는 공통 처리기에 맡긴다. */
public interface NamedLocationStoreSearchPort {
    SearchResult search(String location, Set<StoreServiceType.Code> serviceTypes);

    enum Status {
        SUCCESS, LOCATION_NOT_FOUND, FAILED
    }

    record SearchResult(
            Status status, List<ChatStoreResponse> stores, ChatStoreSearchContextResponse context) {
        public SearchResult {
            Objects.requireNonNull(status, "status");
            stores = List.copyOf(Objects.requireNonNull(stores, "stores"));
            if (status == Status.SUCCESS && context == null
                    || status != Status.SUCCESS && !stores.isEmpty()) {
                throw new IllegalArgumentException("검색 상태와 결과가 일치해야 합니다.");
            }
        }

        public static SearchResult failed() {
            return new SearchResult(Status.FAILED, List.of(), null);
        }
    }
}
