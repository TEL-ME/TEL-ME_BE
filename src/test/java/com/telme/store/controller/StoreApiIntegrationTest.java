package com.telme.store.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// 시드 매장(dev-migration)으로 사용자 매장 API를 확인한다. 기준점은 강남역 좌표다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreApiIntegrationTest {

    static final String NEARBY_URL = "/api/v1/stores/nearby";
    static final String LATITUDE = "37.4979";
    static final String LONGITUDE = "127.0276";
    static final String POINT = "ST_SetSRID(ST_MakePoint(127.0276, 37.4979), 4326)::geography";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void 로그인_없이_가까운_순으로_기본_5곳과_기본_반경을_내려준다() throws Exception {
        mvc.perform(nearby())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores.length()").value(5))
                .andExpect(jsonPath("$.result.radiusMeters").value(10000))
                .andExpect(jsonPath("$.result.stores[0].distanceMeters").isNumber());

        assertThat(storeIds(nearby())).isEqualTo(nearestOpenStoreIds(5, null));
    }

    @Test
    void 반경이_10km를_넘으면_10km로_줄여_검색하고_적용_반경을_알린다() throws Exception {
        mvc.perform(nearby().param("radiusMeters", "50000").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.radiusMeters").value(10000));

        List<Long> expected = jdbc.queryForList("""
                SELECT store_id FROM stores
                 WHERE status = 'OPEN' AND ST_DWithin(geog, %1$s, 10000, false)
                 ORDER BY geog <-> %1$s, store_id
                 LIMIT 20
                """.formatted(POINT), Long.class);
        assertThat(storeIds(nearby().param("radiusMeters", "50000").param("limit", "20"))).isEqualTo(expected);
    }

    @Test
    void 업무를_여러_개_고르면_모두_가능한_매장만_내려준다() throws Exception {
        List<Long> actual = storeIds(nearby()
                .param("serviceTypes", "USIM_REISSUE")
                .param("serviceTypes", "PORT_IN")
                .param("limit", "20"));

        assertThat(actual).isNotEmpty().isEqualTo(nearestOpenStoreIds(20, List.of("USIM_REISSUE", "PORT_IN")));
    }

    @Test
    void 국내_서비스_지역_밖이면_빈_목록() throws Exception {
        mvc.perform(get(NEARBY_URL).param("latitude", "40.7128").param("longitude", "-74.0060"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores.length()").value(0));
    }

    @Test
    void 업무_종류에_빈_값이_섞여도_선택_안_함으로_보고_검색한다() throws Exception {
        assertThat(storeIds(nearby().param("serviceTypes", "", "PORT_IN").param("limit", "20")))
                .isNotEmpty()
                .isEqualTo(nearestOpenStoreIds(20, List.of("PORT_IN")));
    }

    @Test
    void 검색_조건이_범위를_벗어나면_COMMON400_1로_거부한다() throws Exception {
        mvc.perform(get(NEARBY_URL).param("longitude", LONGITUDE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"))
                .andExpect(jsonPath("$.result.latitude").value("위도를 입력해 주세요."));
        mvc.perform(get(NEARBY_URL).param("latitude", "90.000001").param("longitude", LONGITUDE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.latitude").value("위도는 90 이하여야 합니다."));
        mvc.perform(get(NEARBY_URL).param("latitude", "NaN").param("longitude", LONGITUDE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
        mvc.perform(get(NEARBY_URL).param("latitude", LATITUDE).param("longitude", "Infinity"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
        mvc.perform(nearby().param("radiusMeters", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.radiusMeters").value("검색 반경은 1m 이상이어야 합니다."));
        mvc.perform(nearby().param("limit", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.limit").value("매장 개수는 20 이하여야 합니다."));
    }

    @Test
    void 영업_중_조건은_STORE400_4로_거부한다() throws Exception {
        mvc.perform(nearby().param("openNow", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STORE400-4"));
    }

    @Test
    void 형식이_잘못된_값은_내부_정보_없이_COMMON400_1() throws Exception {
        mvc.perform(nearby().param("serviceTypes", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"))
                .andExpect(jsonPath("$.result.serviceTypes").value("요청 값의 형식이 올바르지 않습니다."))
                .andExpect(content().string(not(containsString("com.telme"))));
        mvc.perform(get(NEARBY_URL).param("latitude", "abc").param("longitude", LONGITUDE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"))
                .andExpect(jsonPath("$.result.latitude").value("요청 값의 형식이 올바르지 않습니다."));
    }

    @Test
    void 매장_상세는_요일별_영업시간과_가능_업무를_내려준다() throws Exception {
        mvc.perform(get("/api/v1/stores/{storeId}", 101))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.storeId").value(101))
                .andExpect(jsonPath("$.result.name").value("텔미 강남구1호점"))
                .andExpect(jsonPath("$.result.phone").doesNotExist())
                .andExpect(jsonPath("$.result.hours.length()").value(7))
                .andExpect(jsonPath("$.result.hours[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.result.hours[0].openTime").value("10:00:00"))
                .andExpect(jsonPath("$.result.hours[0].closeTime").value("19:00:00"))
                .andExpect(jsonPath("$.result.hours[6].dayOfWeek").value("SUNDAY"))
                .andExpect(jsonPath("$.result.hours[6].closed").value(true))
                .andExpect(jsonPath("$.result.hours[6].openTime").isEmpty())
                .andExpect(jsonPath("$.result.services[*].code")
                        .value(contains("NEW_LINE", "PORT_IN", "NAME_CHANGE", "USIM_REISSUE")))
                .andExpect(jsonPath("$.result.regionCode").doesNotExist())
                .andExpect(jsonPath("$.result.status").doesNotExist());
    }

    @Test
    void 폐점이거나_없는_매장은_404() throws Exception {
        assertThat(jdbc.queryForObject("SELECT status FROM stores WHERE store_id = 157", String.class))
                .isEqualTo("CLOSED_DOWN");

        mvc.perform(get("/api/v1/stores/{storeId}", 157))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE404-0"));
        mvc.perform(get("/api/v1/stores/{storeId}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE404-0"));
    }

    @Test
    void 매장_ID_형식이_잘못되면_400() throws Exception {
        mvc.perform(get("/api/v1/stores/{storeId}", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
    }

    @Test
    void 업무_종류_목록을_업무_ID_순으로_내려준다() throws Exception {
        List<String> expected = jdbc.queryForList(
                "SELECT code FROM store_service_types ORDER BY service_type_id", String.class);

        String body = mvc.perform(get("/api/v1/stores/service-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.serviceTypes[0].name").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body).path("result").path("serviceTypes").findValuesAsText("code"))
                .isEqualTo(expected);
    }

    private MockHttpServletRequestBuilder nearby() {
        return get(NEARBY_URL).param("latitude", LATITUDE).param("longitude", LONGITUDE);
    }

    private List<Long> nearestOpenStoreIds(int limit, List<String> serviceTypes) {
        String serviceFilter = serviceTypes == null ? "" : """
                AND (SELECT count(DISTINCT t.code) FROM store_services ss
                       JOIN store_service_types t ON t.service_type_id = ss.service_type_id
                      WHERE ss.store_id = s.store_id AND t.code IN (%s)) = %d
                """.formatted("'" + String.join("','", serviceTypes) + "'", serviceTypes.size());
        return jdbc.queryForList("""
                SELECT s.store_id FROM stores s
                 WHERE s.status = 'OPEN' AND ST_DWithin(s.geog, %1$s, 10000, false)
                """.formatted(POINT) + serviceFilter + """
                 ORDER BY s.geog <-> %1$s, s.store_id
                 LIMIT %2$d
                """.formatted(POINT, limit), Long.class);
    }

    private List<Long> storeIds(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Long> ids = new ArrayList<>();
        for (JsonNode store : objectMapper.readTree(body).path("result").path("stores")) {
            ids.add(store.path("storeId").asLong());
        }
        return ids;
    }
}
