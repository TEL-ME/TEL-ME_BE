package com.telme.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

//Accept-Language에 따라 에러·검증·입력 검사 문구가 바뀌는지 확인한다. 헤더가 없거나 번역이 없으면 한국어다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LocaleApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MessageSource messageSource;
    
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
    @DisplayName("입력 검사 안내는 인자가 채워지고 언어별로 다르다")
    void 입력_검사_안내_문구를_만든다() {
        Object[] threshold = {3};
        assertThat(messageSource.getMessage("chat.input-guard.WARNED", threshold, Locale.KOREAN)).contains("3회");
        assertThat(messageSource.getMessage("chat.input-guard.WARNED", threshold, Locale.ENGLISH)).contains("3 times");
    }
}
