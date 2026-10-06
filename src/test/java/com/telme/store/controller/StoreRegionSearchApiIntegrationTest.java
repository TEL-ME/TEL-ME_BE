package com.telme.store.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreRegionSearchApiIntegrationTest {

    static final String URL = "/api/v1/stores/region";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void 로그인_없이_시군구_코드로_찾는다() throws Exception {
        mvc.perform(get(URL).param("region", "11680"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores[*].regionCode", everyItem(startsWith("11680"))))
                .andExpect(jsonPath("$.result.stores[*].storeId", hasItem(101)));
    }

    @Test
    void 폐업_매장은_제외한다() throws Exception {
        mvc.perform(get(URL).param("region", "41150"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores[*].storeId", not(hasItem(157))))
                .andExpect(jsonPath("$.result.stores[*].storeId", not(hasItem(174))))
                .andExpect(jsonPath("$.result.stores").isNotEmpty())
                .andExpect(jsonPath("$.result.stores[*].regionCode", everyItem(startsWith("41150"))));

        List<Long> expected = jdbc.queryForList(
                "SELECT store_id FROM stores WHERE status = 'OPEN' AND region_code LIKE '41150%' ORDER BY store_id",
                Long.class);
        assertThat(storeIds(get(URL).param("region", "41150"))).isEqualTo(expected);
    }

    @Test
    void 가능_업무로_거르면_DB와_같은_매장만_나온다() throws Exception {
        String body = mvc.perform(get(URL).param("region", "11").param("serviceType", "USIM_REISSUE")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<Long> expected = jdbc.queryForList("""
                SELECT s.store_id FROM stores s
                 WHERE s.status = 'OPEN' AND s.region_code LIKE '11%'
                   AND EXISTS (SELECT 1 FROM store_services ss
                                 JOIN store_service_types t ON t.service_type_id = ss.service_type_id
                                WHERE ss.store_id = s.store_id AND t.code = 'USIM_REISSUE')
                 ORDER BY s.store_id
                """, Long.class);

        JsonNode result = objectMapper.readTree(body).path("result");
        List<Long> actual = new ArrayList<>();
        for (JsonNode store : result.path("stores")) {
            actual.add(store.path("storeId").asLong());
            assertThat(store.path("services").findValuesAsText("code")).contains("USIM_REISSUE");
        }
        assertThat(result.path("totalElements").asLong()).isEqualTo(expected.size());
        assertThat(actual).isNotEmpty().isEqualTo(expected.subList(0, Math.min(50, expected.size())));
    }

    @Test
    void 업무를_필터해도_매장의_업무_목록은_전부_내려온다() throws Exception {
        mvc.perform(get(URL).param("region", "1168010700").param("serviceType", "PORT_IN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores[?(@.storeId == 101)].services[*].code",
                        contains("NEW_LINE", "PORT_IN", "NAME_CHANGE", "USIM_REISSUE")));
    }

    @Test
    void 페이지로_나눠서_내려준다() throws Exception {
        List<Long> expected = jdbc.queryForList(
                "SELECT store_id FROM stores WHERE status = 'OPEN' AND region_code LIKE '11%' ORDER BY store_id",
                Long.class);

        mvc.perform(get(URL).param("region", "11").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores.length()").value(10))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(10))
                .andExpect(jsonPath("$.result.totalElements").value(expected.size()))
                .andExpect(jsonPath("$.result.totalPages").value((expected.size() + 9) / 10));

        assertThat(storeIds(get(URL).param("region", "11").param("page", "0").param("size", "10")))
                .isEqualTo(expected.subList(0, 10));
        assertThat(storeIds(get(URL).param("region", "11").param("page", "1").param("size", "10")))
                .isEqualTo(expected.subList(10, 20));
    }

    @Test
    void 페이지_크기_기본값은_20() throws Exception {
        mvc.perform(get(URL).param("region", "11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores.length()").value(20))
                .andExpect(jsonPath("$.result.size").value(20));
    }

    @Test
    void 결과가_없으면_빈_목록() throws Exception {
        mvc.perform(get(URL).param("region", "9999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores.length()").value(0))
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @Test
    void 잘못된_업무_코드는_내부_정보_없이_400() throws Exception {
        for (String serviceType : List.of("UNKNOWN", "usim_reissue")) {
            mvc.perform(get(URL).param("region", "11680").param("serviceType", serviceType))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.result.serviceType").value("허용되지 않는 업무 코드입니다."))
                    .andExpect(content().string(not(containsString("com.telme"))));
        }
    }

    @Test
    void 법정동코드_단위가_아닌_자릿수는_400() throws Exception {
        for (String region : List.of("116", "116801", "1168010", "116801070", "11680107000")) {
            mvc.perform(get(URL).param("region", region))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.result.region").exists());
        }
    }

    @Test
    void 법정동코드_단위_자릿수는_모두_허용한다() throws Exception {
        for (String region : List.of("11", "1168", "11680", "11680107", "1168010700")) {
            mvc.perform(get(URL).param("region", region))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.stores[*].regionCode", everyItem(startsWith(region))));
        }
    }

    @Test
    void 일반구가_있는_시_코드로_찾으면_일반구_매장도_나온다() throws Exception {
        List<Long> expected = jdbc.queryForList(
                "SELECT store_id FROM stores WHERE status = 'OPEN' AND region_code LIKE '4711%' ORDER BY store_id",
                Long.class);

        assertThat(expected).isNotEmpty();
        assertThat(storeIds(get(URL).param("region", "47110"))).isEqualTo(expected);
    }

    @Test
    void 잘못된_요청값은_400() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("region", "gangnam")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("region", "11").param("size", "51")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("region", "11").param("page", "-1")).andExpect(status().isBadRequest());
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
