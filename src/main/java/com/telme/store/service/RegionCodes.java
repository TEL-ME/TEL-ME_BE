package com.telme.store.service;

public final class RegionCodes {

    private RegionCodes() {
    }

    // 일반구가 있는 시(포항 47110)의 매장은 일반구 코드(47111, 47113)로 저장돼 있어 시 코드로는 앞 4자리까지만 비교한다
    public static String searchPrefix(String regionCode) {
        if (regionCode != null && regionCode.length() == 5 && regionCode.endsWith("0")) {
            return regionCode.substring(0, 4);
        }
        return regionCode;
    }
}
