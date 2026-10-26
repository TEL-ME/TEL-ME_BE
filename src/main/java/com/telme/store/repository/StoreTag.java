package com.telme.store.repository;

import com.telme.store.entity.StoreServiceType;

// 정적 조건(업무 종류와 앞으로 더할 매장 속성) 하나하나를 매장 태그 번호로 나타낸다.(V16_store_tags()와 동기화 필요)
public enum StoreTag {

    NEW_LINE(1),
    PORT_IN(2),
    NAME_CHANGE(3),
    USIM_REISSUE(4);

    private final int id;

    StoreTag(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static StoreTag of(StoreServiceType.Code code) {
        return switch (code) {
            case NEW_LINE -> NEW_LINE;
            case PORT_IN -> PORT_IN;
            case NAME_CHANGE -> NAME_CHANGE;
            case USIM_REISSUE -> USIM_REISSUE;
        };
    }
}
