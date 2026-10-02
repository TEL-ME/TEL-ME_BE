package com.telme.store.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.res.KakaoAddressSearchResponse;
import com.telme.store.dto.res.KakaoKeywordSearchResponse;
import com.telme.store.exception.StoreErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 사용자가 말한 위치(지명·주소·장소 이름)를 좌표로 바꾼다.
// 주소 검색이 행정구역·주소를 정확히 맞추므로 먼저 보고, 못 찾으면 키워드 검색으로 장소 이름을 찾는다.
// 둘 다 없으면 빈 결과다. 0건을 어떻게 안내할지(되묻기 등)는 호출자가 정한다
@Service
@RequiredArgsConstructor
public class LocationLookupService {

    private static final String REGION_ADDRESS_TYPE = "REGION";
    private static final Pattern LEGAL_DONG_CODE = Pattern.compile("\\d{10}");

    private final KakaoLocalClient kakaoLocalClient;

    public Optional<LocationLookupResult> lookup(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        String keyword = query.strip();
        List<KakaoAddressSearchResponse.Document> addresses = kakaoLocalClient.searchAddress(keyword);
        if (!addresses.isEmpty()) {
            return Optional.of(toResult(addresses.get(0)));
        }
        return kakaoLocalClient.searchKeyword(keyword).stream().findFirst().map(this::toResult);
    }

    private LocationLookupResult toResult(KakaoAddressSearchResponse.Document document) {
        LocationLookupResult.Type type = REGION_ADDRESS_TYPE.equals(document.addressType())
                ? LocationLookupResult.Type.REGION
                : LocationLookupResult.Type.ADDRESS;
        String legalDongCode = document.address() == null ? null : document.address().bCode();
        return new LocationLookupResult(document.addressName(), type,
                parseCoordinate(document.y()), parseCoordinate(document.x()), toRegionCode(legalDongCode));
    }

    private LocationLookupResult toResult(KakaoKeywordSearchResponse.Document document) {
        return new LocationLookupResult(document.placeName(), LocationLookupResult.Type.PLACE,
                parseCoordinate(document.y()), parseCoordinate(document.x()), null);
    }

    // 법정동코드는 하위 단위가 없으면 0으로 채운다(서울 강남구 = 1168000000). 지역 검색은 앞자리로 찾으므로
    // 0으로 채운 자리를 떼야 강남구 아래 동(1168010700 등)이 모두 걸린다
    static String toRegionCode(String legalDongCode) {
        if (legalDongCode == null || !LEGAL_DONG_CODE.matcher(legalDongCode).matches()) {
            return null;
        }
        if (legalDongCode.endsWith("00000000")) {
            return legalDongCode.substring(0, 2);
        }
        if (legalDongCode.endsWith("00000")) {
            return legalDongCode.substring(0, 5);
        }
        if (legalDongCode.endsWith("00")) {
            return legalDongCode.substring(0, 8);
        }
        return legalDongCode;
    }

    // 카카오는 좌표를 문자열로 준다. 숫자가 아니면 응답이 깨진 것이라 조회 실패로 본다
    private double parseCoordinate(String value) {
        if (value == null) {
            throw new GeneralException(StoreErrorCode.LOCATION_LOOKUP_UNAVAILABLE);
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new GeneralException(StoreErrorCode.LOCATION_LOOKUP_UNAVAILABLE);
        }
    }
}
