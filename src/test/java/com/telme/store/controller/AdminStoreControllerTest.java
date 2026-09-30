package com.telme.store.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.global.common.exception.GeneralException;
import com.telme.global.config.SecurityConfig;
import com.telme.member.service.GuestIdentityService;
import com.telme.member.service.KakaoAuthorizationFailureHandler;
import com.telme.member.service.KakaoLinkRequestStore;
import com.telme.member.service.KakaoLoginFailureHandler;
import com.telme.member.service.KakaoLoginSuccessHandler;
import com.telme.member.service.KakaoOAuth2UserService;
import com.telme.store.dto.res.AdminStoreDetailResponse;
import com.telme.store.dto.res.AdminStoreListItemResponse;
import com.telme.store.dto.res.AdminStoreListResponse;
import com.telme.store.dto.res.AdminStoreServiceResponse;
import com.telme.store.exception.StoreErrorCode;
import com.telme.store.service.AdminStoreCommandService;
import com.telme.store.service.AdminStoreQueryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// SecurityConfig를 Import하지 않으면 @WebMvcTest가 기본 보안 설정으로 돌아 ADMIN 제한이 검증되지 않는다
@WebMvcTest(AdminStoreController.class)
@Import(SecurityConfig.class)
class AdminStoreControllerTest {

    private static final String URL = "/api/v1/admin/stores";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminStoreQueryService adminStoreQueryService;
    
    @MockitoBean
    private AdminStoreCommandService adminStoreCommandService;

    @MockitoBean
    private GuestIdentityService guestIdentityService;

    // SecurityConfig가 securityFilterChain 빈에서 요구하는 OAuth2 로그인 의존성 — 웹 슬라이스에는 없어 목으로 채운다
    @MockitoBean
    private KakaoOAuth2UserService kakaoOAuth2UserService;

    @MockitoBean
    private KakaoLoginSuccessHandler kakaoLoginSuccessHandler;

    @MockitoBean
    private KakaoLoginFailureHandler kakaoLoginFailureHandler;

    @MockitoBean
    private KakaoAuthorizationFailureHandler kakaoAuthorizationFailureHandler;

    @MockitoBean
    private KakaoLinkRequestStore kakaoLinkRequestStore;

    @Test
    @DisplayName("로그인하지 않으면 401을 반환한다")
    void 비인증_요청은_401을_반환한다() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN이 아니면 403을 반환한다")
    void 일반_회원은_403을_반환한다() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 200과 목록을 반환한다")
    void 관리자는_200을_반환한다() throws Exception {
        when(adminStoreQueryService.getStores(any())).thenReturn(new AdminStoreListResponse(
                List.of(new AdminStoreListItemResponse(1L, "텔미 강남점", "서울특별시 강남구 테헤란로 123", null,
                        List.of(new AdminStoreServiceResponse("NEW_LINE", "신규가입")), "OPEN", Instant.now())),
                0, 20, 1, 1));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.stores[0].storeId").value(1))
                .andExpect(jsonPath("$.result.stores[0].services[0].name").value("신규가입"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("상태 값이 enum에 없거나 범위를 벗어나면 400과 COMMON400-1을 반환한다")
    void 잘못된_요청은_400을_반환한다() throws Exception {
        String[][] invalid = {{"status", "UNKNOWN"}, {"size", "101"}, {"page", "-1"}, {"keyword", "가".repeat(101)}};
        for (String[] param : invalid) {
            mockMvc.perform(get(URL).param(param[0], param[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON400-1"));
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("없는 매장이면 404와 STORE404-0을 반환한다")
    void 없는_매장은_404를_반환한다() throws Exception {
        when(adminStoreQueryService.getStore(anyLong()))
                .thenThrow(new GeneralException(StoreErrorCode.STORE_NOT_FOUND));

        mockMvc.perform(get(URL + "/{storeId}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("STORE404-0"));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 등록에 201과 만들어진 매장을 반환한다")
    void 관리자는_등록할_수_있다() throws Exception {
        when(adminStoreCommandService.create(any(), any())).thenReturn(detail());
        
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(validBody()))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.result.storeId").value(1));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 수정에 200과 고친 매장을 반환한다")
    void 관리자는_수정할_수_있다() throws Exception {
        when(adminStoreCommandService.update(anyLong(), any(), any())).thenReturn(detail());
        
        mockMvc.perform(put(URL + "/{storeId}", 1).contentType(MediaType.APPLICATION_JSON).content(validBody()))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.result.storeId").value(1));
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("ADMIN이면 삭제와 업무 선택지 조회에 200을 반환한다")
    void 관리자는_삭제와_선택지_조회를_할_수_있다() throws Exception {
        when(adminStoreQueryService.getServiceTypes()).thenReturn(List.of(new AdminStoreServiceResponse("NEW_LINE", "신규가입")));
        
        mockMvc.perform(delete(URL + "/{storeId}", 1)).andExpect(status().isOk());
        mockMvc.perform(get(URL + "/service-types")).andExpect(status().isOk()).andExpect(jsonPath("$.result[0].code").value("NEW_LINE"));
    }
    
    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("ADMIN이 아니면 등록·수정·삭제가 모두 403이다")
    void 일반_회원은_쓰기가_막힌다() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(validBody())).andExpect(status().isForbidden());
        mockMvc.perform(put(URL + "/{storeId}", 1).contentType(MediaType.APPLICATION_JSON).content(validBody())).andExpect(status().isForbidden());
        mockMvc.perform(delete(URL + "/{storeId}", 1)).andExpect(status().isForbidden());
    }
    
    @Test
    @DisplayName("로그인하지 않으면 등록·수정·삭제가 모두 401이다")
    void 비인증_요청은_쓰기가_막힌다() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(validBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(put(URL + "/{storeId}", 1).contentType(MediaType.APPLICATION_JSON).content(validBody())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(URL + "/{storeId}", 1)).andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("입력이 규칙을 어기면 400과 COMMON400-1, 틀린 항목 이름을 반환한다")
    void 잘못된_입력은_틀린_항목과_함께_400이다() throws Exception {
        String[][] cases = {
                // {올바른 요청에서 바꿀 부분, 바꿀 값, 오류에 나와야 할 항목}
                {"\"1168010100\"", "\"SEOUL\"", "regionCode"},
                {"\"latitude\": 37.498095", "\"latitude\": 127.027610", "latitude"},
                {"{\"dayOfWeek\": \"SUNDAY\", \"openTime\": null, \"closeTime\": null, \"closed\": true}",
                 "{\"dayOfWeek\": \"MONDAY\", \"openTime\": null, \"closeTime\": null, \"closed\": true}",
                "weekComplete"},
                {"\"openTime\": null, \"closeTime\": null, \"closed\": true",
                    "\"openTime\": \"10:00\", \"closeTime\": null, \"closed\": true", "hours[6].timeValid"},
                {"[\"NEW_LINE\", \"USIM_REISSUE\"]", "[]", "serviceCodes"},
                {"[\"NEW_LINE\", \"USIM_REISSUE\"]", "[\"NEW_LINE\", \"NEW_LINE\"]", "serviceCodesUnique"}};
        for (String[] c : cases) {
            mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(validBody().replace(c[0], c[1]))).andExpect(status().isBadRequest())
                   .andExpect(jsonPath("$.code").value("COMMON400-1"))
                   .andExpect(jsonPath("$.result['" + c[2] + "']").exists());
        }
    }
    
    // 월~토 10:00~19:00, 일요일 휴무인 올바른 요청
    private static String validBody() {
        StringBuilder hours = new StringBuilder();
        for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")) {
            hours.append("{\"dayOfWeek\": \"").append(day)
                    .append("\", \"openTime\": \"10:00\", \"closeTime\": \"19:00\", \"closed\": false}, ");
        }
        hours.append("{\"dayOfWeek\": \"SUNDAY\", \"openTime\": null, \"closeTime\": null, \"closed\": true}");
        return """
                {
                  "name": "텔미 강남점",
                  "address": "서울특별시 강남구 테헤란로 123",
                  "phone": "02-1234-5678",
                  "regionCode": "1168010100",
                  "latitude": 37.498095,
                  "longitude": 127.027610,
                  "hours": [%s],
                  "serviceCodes": ["NEW_LINE", "USIM_REISSUE"]
                }
                """.formatted(hours);
    }
    
    private static AdminStoreDetailResponse detail() {
        return new AdminStoreDetailResponse(1L, "텔미 강남점", "서울특별시 강남구 테헤란로 123", null, "1168010100", 
                    new BigDecimal("37.498095"), new BigDecimal("127.027610"), "OPEN", List.of(), List.of(), 
                    Instant.now(), Instant.now());
    }
}