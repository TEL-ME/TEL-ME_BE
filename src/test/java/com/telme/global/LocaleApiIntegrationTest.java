package com.telme.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

//Accept-Language에 따라 에러·검증·입력 검사 문구와 매장 번역이 바뀌는지 확인한다. 헤더가 없거나 번역이 없으면 한국어다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocaleApiIntegrationTest {
    
    private static final String EN_NAME = "LG U+ Test Store";
    private static final String EN_ADDRESS = "1 Teheran-ro, Gangnam-gu, Seoul";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MessageSource messageSource;
    
    private long storeId;
    private String koName;
    private String koAddress;
    
    @BeforeEach
    void setUp() {
        Map<String, Object> store = jdbc.queryForMap("""
                SELECT s.store_id, s.name, s.address FROM stores s
                 WHERE s.status = 'OPEN'
                   AND EXISTS (SELECT 1 FROM store_services ss WHERE ss.store_id = s.store_id)
                 ORDER BY s.store_id LIMIT 1
                """);
        storeId = ((Number) store.get("store_id")).longValue();
        koName = (String) store.get("name");
        koAddress = (String) store.get("address");
        jdbc.update("INSERT INTO store_translations (store_id, locale, name, address) VALUES (?, 'en', ?, ?)",
                storeId, EN_NAME, EN_ADDRESS);
    }
    
    @Test
    @DisplayName("에러 메시지는 헤더가 없거나 지원하지 않는 언어면 한국어, en이면 영어다")
    void 에러_메시지를_요청_언어로_보낸다() throws Exception {
        mvc.perform(get("/api/v1/stores/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("매장을 찾을 수 없습니다."));
        mvc.perform(get("/api/v1/stores/999999999").header(HttpHeaders.ACCEPT_LANGUAGE, "fr-FR"))
                .andExpect(jsonPath("$.message").value("매장을 찾을 수 없습니다."));
        mvc.perform(get("/api/v1/stores/999999999").header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9"))
                .andExpect(jsonPath("$.code").value("STORE404-0"))
                .andExpect(jsonPath("$.message").value("Store not found."));
    }
    
    @Test
    @DisplayName("검증 메시지도 요청 언어를 따르고, {value} 같은 제약 값이 채워진다")
    void 검증_메시지를_요청_언어로_보낸다() throws Exception {
        mvc.perform(get("/api/v1/stores/0").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please check the request values."))
                .andExpect(jsonPath("$.result.storeId").value("Store ID must be 1 or greater."));
        mvc.perform(get("/api/v1/stores/0"))
                .andExpect(jsonPath("$.result.storeId").value("매장 ID는 1 이상이어야 합니다."));
        mvc.perform(get("/api/v1/stores/nearby").param("latitude", "37.4979").param("longitude", "127.0276")
                        .param("limit", "999").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.result.limit").value("Number of stores must be 20 or fewer."));
    }
    
    @Test
    @DisplayName("매장 상세는 en이면 번역한 이름·주소와 영어 업무 이름을 주고, 한국어 주소는 따로 준다")
    void 매장_상세를_번역한다() throws Exception {
        mvc.perform(get("/api/v1/stores/{id}", storeId).header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value(EN_NAME))
                .andExpect(jsonPath("$.result.address").value(EN_ADDRESS))
                .andExpect(jsonPath("$.result.addressKo").value(koAddress))
                .andExpect(jsonPath("$.result.services[*].name", not(hasItem(containsString("가")))));

        mvc.perform(get("/api/v1/stores/{id}", storeId))
                .andExpect(jsonPath("$.result.name").value(koName))
                .andExpect(jsonPath("$.result.address").value(koAddress))
                .andExpect(jsonPath("$.result.addressKo").value(koAddress));
    }
    
    @Test
    @DisplayName("번역이 없는 언어는 한국어 원문을 준다")
    void 번역이_없으면_한국어다() throws Exception {
        mvc.perform(get("/api/v1/stores/{id}", storeId).header(HttpHeaders.ACCEPT_LANGUAGE, "ja"))
                .andExpect(jsonPath("$.result.name").value(koName));
    }

    @Test
    @DisplayName("업무 종류 목록은 en이면 영어 이름이다")
    void 업무_종류를_번역한다() throws Exception {
        mvc.perform(get("/api/v1/stores/service-types").header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(jsonPath("$.result.serviceTypes[?(@.code == 'USIM_REISSUE')].name").value("USIM reissue"));
        mvc.perform(get("/api/v1/stores/service-types"))
                .andExpect(jsonPath("$.result.serviceTypes[?(@.code == 'USIM_REISSUE')].name").value("유심재발급"));
    }

    @Test
    @DisplayName("주변 매장 목록에서 번역이 있는 매장만 영어로 바뀐다")
    void 주변_매장_목록을_번역한다() throws Exception {
        Map<String, Object> point = jdbc.queryForMap(
                "SELECT latitude, longitude FROM stores WHERE store_id = ?", storeId);
        mvc.perform(get("/api/v1/stores/nearby")
                        .param("latitude", point.get("latitude").toString())
                        .param("longitude", point.get("longitude").toString())
                        .param("limit", "1")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores[0].storeId").value(storeId))
                .andExpect(jsonPath("$.result.stores[0].name").value(EN_NAME));
    }

    @Test
    @DisplayName("지역 검색은 번역한 이름과 함께, 시·군·구를 뽑을 한국어 주소를 준다")
    void 지역_검색에_한국어_주소를_함께_준다() throws Exception {
        String regionCode = jdbc.queryForObject("SELECT region_code FROM stores WHERE store_id = ?", String.class, storeId);
        mvc.perform(get("/api/v1/stores/region").param("region", regionCode.substring(0, 5)).param("size", "50")
                        .header(HttpHeaders.ACCEPT_LANGUAGE, "en"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stores[?(@.storeId == %d)].name", storeId).value(EN_NAME))
                .andExpect(jsonPath("$.result.stores[?(@.storeId == %d)].addressKo", storeId).value(koAddress));
    }
    
    @Test
    @DisplayName("입력 검사 안내는 인자가 채워지고 언어별로 다르다")
    void 입력_검사_안내_문구를_만든다() {
        Object[] threshold = {3};
        assertThat(messageSource.getMessage("chat.input-guard.WARNED", threshold, Locale.KOREAN)).contains("3회");
        assertThat(messageSource.getMessage("chat.input-guard.WARNED", threshold, Locale.ENGLISH)).contains("3 times");
    }
}
