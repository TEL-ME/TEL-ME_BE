package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.res.KakaoAddressSearchResponse;
import com.telme.store.dto.res.KakaoKeywordSearchResponse;
import com.telme.store.exception.StoreErrorCode;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LocationLookupServiceTest {

    private KakaoLocalClient client;
    private LocationLookupService service;

    @BeforeEach
    void setUp() {
        client = mock(KakaoLocalClient.class);
        service = new LocationLookupService(client);
    }

    @Test
    @DisplayName("행정구역 지명은 주소 검색 결과의 좌표와 지역 검색용 법정동코드 앞자리를 돌려준다")
    void 행정구역_지명() {
        when(client.searchAddress("서울 강남구")).thenReturn(List.of(
                address("서울 강남구", "REGION", "127.047377", "37.517331", "1168000000")));

        assertThat(service.lookup("서울 강남구")).contains(new LocationLookupResult(
                "서울 강남구", LocationLookupResult.Type.REGION, 37.517331, 127.047377, "11680"));
        verify(client, never()).searchKeyword(anyString());
    }

    @Test
    @DisplayName("도로명 주소는 ADDRESS로 돌려준다")
    void 도로명_주소() {
        when(client.searchAddress("테헤란로 152")).thenReturn(List.of(
                address("서울 강남구 테헤란로 152", "ROAD_ADDR", "127.036", "37.500", "1168010100")));

        assertThat(service.lookup("테헤란로 152")).hasValueSatisfying(result -> {
            assertThat(result.type()).isEqualTo(LocationLookupResult.Type.ADDRESS);
            assertThat(result.regionCode()).isEqualTo("11680101");
        });
    }

    @Test
    @DisplayName("주소로 못 찾으면 키워드 검색으로 장소 이름을 찾고, 행정구역이 아니라 지역코드는 없다")
    void 장소_이름은_키워드_검색() {
        when(client.searchAddress("강남역")).thenReturn(List.of());
        when(client.searchKeyword("강남역")).thenReturn(List.of(
                new KakaoKeywordSearchResponse.Document("강남역 2호선", "127.028001", "37.498086")));

        assertThat(service.lookup("강남역")).contains(new LocationLookupResult(
                "강남역 2호선", LocationLookupResult.Type.PLACE, 37.498086, 127.028001, null));
    }

    @Test
    @DisplayName("주소와 키워드 모두 없으면 빈 결과다")
    void 둘_다_없으면_빈_결과() {
        when(client.searchAddress("없는곳")).thenReturn(List.of());
        when(client.searchKeyword("없는곳")).thenReturn(List.of());

        assertThat(service.lookup("없는곳")).isEmpty();
    }

    @Test
    @DisplayName("검색어 앞뒤 공백은 지우고 찾는다")
    void 앞뒤_공백() {
        when(client.searchAddress("강남구")).thenReturn(List.of());
        when(client.searchKeyword("강남구")).thenReturn(List.of());

        service.lookup("  강남구 ");

        verify(client).searchAddress("강남구");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("검색어가 비어 있으면 카카오를 호출하지 않고 빈 결과다")
    void 빈_검색어(String query) {
        assertThat(service.lookup(query)).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("응답 좌표가 숫자가 아니면 위치 조회 불가 오류다")
    void 깨진_좌표() {
        when(client.searchAddress("강남구")).thenReturn(List.of(address("강남구", "REGION", "abc", null, null)));

        assertThatThrownBy(() -> service.lookup("강남구"))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.LOCATION_LOOKUP_UNAVAILABLE);
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(value = {
            "1100000000, 11",
            "1168000000, 11680",
            "1168010700, 11680107",
            "4111112121, 4111112121",
            "116800000, null",
            "abcdefghij, null",
            "null, null"
    }, nullValues = "null")
    @DisplayName("법정동코드에서 0으로 채운 하위 단위를 떼어 지역 검색 단위(2·5·8·10자리)로 바꾼다")
    void 지역코드_앞자리(String legalDongCode, String expected) {
        assertThat(LocationLookupService.toRegionCode(legalDongCode)).isEqualTo(expected);
    }

    private KakaoAddressSearchResponse.Document address(String name, String type, String x, String y, String bCode) {
        return new KakaoAddressSearchResponse.Document(name, type, x, y, new KakaoAddressSearchResponse.Address(bCode));
    }
}
