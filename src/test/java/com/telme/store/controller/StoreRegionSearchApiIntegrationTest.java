package com.telme.store.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StoreRegionSearchApiIntegrationTest {

    static final String URL = "/api/v1/stores";

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
                .andExpect(jsonPath("$.result.stores[*].storeId", not(hasItem(174))));
    }

    @Test
    void 가능_업무로_거르면_DB와_같은_매장만_나온다() throws Exception {
        String body = mvc.perform(get(URL).param("region", "11").param("serviceType", "USIM_REISSUE"))
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

        JsonNode stores = objectMapper.readTree(body).path("result").path("stores");
        List<Long> actual = new ArrayList<>();
        for (JsonNode store : stores) {
            actual.add(store.path("storeId").asLong());
            assertThat(store.path("services").findValuesAsText("code")).contains("USIM_REISSUE");
        }
        assertThat(actual).isNotEmpty().isEqualTo(expected);
    }

    @Test
    void 업무를_필터해도_매장의_업무_목록은_전부_내려온다() throws Exception {
        mvc.perform(get(URL).param("region", "1168010700").param("serviceType", "PORT_IN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores[?(@.storeId == 101)].services[*].code",
                        contains("NEW_LINE", "PORT_IN", "NAME_CHANGE", "USIM_REISSUE")));
    }

    @Test
    void 결과가_없으면_빈_목록() throws Exception {
        mvc.perform(get(URL).param("region", "9999999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores.length()").value(0));
    }

    @Test
    void 잘못된_요청값은_400() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("region", "gangnam")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("region", "11680").param("serviceType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }
}
