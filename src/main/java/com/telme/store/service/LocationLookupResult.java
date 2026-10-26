package com.telme.store.service;

// 검색어를 바꾼 위치. regionCode는 주소 검색으로 찾았을 때만 있고, 지역 검색(region 파라미터)에 그대로 넣을 수 있는
// 법정동코드 앞자리다(시도 2, 시군구 5, 읍면동 8, 리 10자리)
public record LocationLookupResult(String name, Type type, double latitude, double longitude, String regionCode) {

    public enum Type {
        REGION,  // 행정구역 지명(예: 서울 강남구, 역삼동)
        ADDRESS, // 도로명·지번 주소
        PLACE    // 장소 이름(예: 강남역). 행정구역이 아니라 regionCode가 없다
    }
}
