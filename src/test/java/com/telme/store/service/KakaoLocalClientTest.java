package com.telme.store.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import com.telme.global.common.exception.GeneralException;
import com.telme.store.dto.res.KakaoAddressSearchResponse;
import com.telme.store.dto.res.KakaoKeywordSearchResponse;
import com.telme.store.exception.StoreErrorCode;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// 실제 카카오 API는 호출하지 않는다. 응답 예시는 카카오 로컬 API 문서의 형식을 따른다
class KakaoLocalClientTest {

    private static final String BASE_URL = "http://kakao.test";

    private MockRestServiceServer server;
    private KakaoLocalClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "KakaoAK test-key");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoLocalClient(builder.build());
    }

    @Test
    @DisplayName("주소 검색은 검색어를 인코딩해 보내고 지명·좌표·법정동코드를 읽는다")
    void 주소_검색() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/local/search/address.json")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-key"))
                .andExpect(queryParam("query", encoded("서울 강남구")))
                .andExpect(queryParam("size", "1"))
                .andRespond(withSuccess("""
                        {"meta":{"total_count":1},
                         "documents":[{"address_name":"서울 강남구","address_type":"REGION",
                                       "x":"127.047377408384","y":"37.517331925853",
                                       "address":{"address_name":"서울 강남구","b_code":"1168000000"},
                                       "road_address":null}]}
                        """, MediaType.APPLICATION_JSON));

        List<KakaoAddressSearchResponse.Document> documents = client.searchAddress("서울 강남구");

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.addressName()).isEqualTo("서울 강남구");
            assertThat(document.addressType()).isEqualTo("REGION");
            assertThat(document.x()).isEqualTo("127.047377408384");
            assertThat(document.y()).isEqualTo("37.517331925853");
            assertThat(document.address().bCode()).isEqualTo("1168000000");
        });
        server.verify();
    }

    @Test
    @DisplayName("키워드 검색은 장소 이름과 좌표를 읽는다")
    void 키워드_검색() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/local/search/keyword.json")))
                .andExpect(queryParam("query", encoded("강남역")))
                .andRespond(withSuccess("""
                        {"meta":{"total_count":45},
                         "documents":[{"place_name":"강남역 2호선","category_group_code":"SW8",
                                       "x":"127.02800140627488","y":"37.49808633653005"}]}
                        """, MediaType.APPLICATION_JSON));

        List<KakaoKeywordSearchResponse.Document> documents = client.searchKeyword("강남역");

        assertThat(documents).containsExactly(
                new KakaoKeywordSearchResponse.Document("강남역 2호선", "127.02800140627488", "37.49808633653005"));
    }

    @Test
    @DisplayName("결과가 없으면 빈 목록을 반환한다")
    void 결과_없음() {
        server.expect(requestTo(startsWith(BASE_URL + "/v2/local/search/address.json")))
                .andRespond(withSuccess("{\"meta\":{\"total_count\":0},\"documents\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.searchAddress("없는곳")).isEmpty();
    }

    @Test
    @DisplayName("키 오류(401)·호출 한도 초과(429)·카카오 장애(5xx)는 위치 조회 불가 오류로 바꾼다")
    void 응답_오류() {
        server.expect(requestTo(startsWith(BASE_URL))).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(startsWith(BASE_URL))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(startsWith(BASE_URL))).andRespond(withServerError());

        assertLookupUnavailable(() -> client.searchAddress("강남구"));
        assertLookupUnavailable(() -> client.searchKeyword("강남역"));
        assertLookupUnavailable(() -> client.searchAddress("강남구"));
    }

    @Test
    @DisplayName("타임아웃도 위치 조회 불가 오류로 바꾼다")
    void 타임아웃() {
        server.expect(requestTo(startsWith(BASE_URL))).andRespond(withException(new SocketTimeoutException()));

        assertLookupUnavailable(() -> client.searchKeyword("강남역"));
    }

    private void assertLookupUnavailable(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(StoreErrorCode.LOCATION_LOOKUP_UNAVAILABLE);
    }

    private String encoded(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
