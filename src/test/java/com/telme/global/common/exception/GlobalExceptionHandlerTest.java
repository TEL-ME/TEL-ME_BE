package com.telme.global.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.global.common.CustomResponse;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("타입 변환 실패는 내부 클래스 이름이 든 기본 메시지 대신 형식 오류 문구로 응답한다")
    void 타입_변환_실패는_내부_정보를_숨긴다() throws Exception {
        String springMessage = "Failed to convert from type [java.lang.String] "
                + "to type [com.telme.store.entity.StoreServiceType$Code]";
        MethodArgumentNotValidException ex = exception(new FieldError("request", "serviceTypes", "FOO", true,
                new String[] {"typeMismatch"}, null, springMessage));

        ResponseEntity<CustomResponse<Map<String, String>>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().getCode()).isEqualTo("COMMON400-1");
        assertThat(response.getBody().getResult()).containsEntry("serviceTypes", "요청 값의 형식이 올바르지 않습니다.");
    }

    @Test
    @DisplayName("검증 어노테이션 위반은 어노테이션에 적은 메시지를 그대로 응답한다")
    void 검증_실패는_메시지를_그대로_쓴다() throws Exception {
        MethodArgumentNotValidException ex = exception(
                new FieldError("request", "region", "116", false, null, null, "지역 코드는 숫자여야 합니다."));

        ResponseEntity<CustomResponse<Map<String, String>>> response = handler.handleValidationException(ex);

        assertThat(response.getBody().getResult()).containsEntry("region", "지역 코드는 숫자여야 합니다.");
    }

    private MethodArgumentNotValidException exception(FieldError error) throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(error);
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("exception", FieldError.class), 0);
        return new MethodArgumentNotValidException(parameter, bindingResult);
    }
}
