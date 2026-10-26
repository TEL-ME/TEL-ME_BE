package com.telme.global.common.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.validation.ConstraintViolationException;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.telme.global.common.CustomResponse;
import com.telme.global.common.code.BaseErrorCode;
import com.telme.global.common.code.CommonErrorCode;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_FORMAT_MESSAGE = "요청 값의 형식이 올바르지 않습니다.";

    // 사람이 고쳐야 하는 서버 오류 코드. 이 코드만 ERROR로 남겨 Sentry로 보내고 나머지 GeneralException은 WARN으로 둔다.
    // LLM502-1 LLM 응답 형식 오류, FAQ500-0 임베딩 응답 오류, FAQ500-1 임베딩 요청 거부, CHAT500-1 답변 안전 검사 오류.
    // 일시 장애(연결 실패·시간 초과·응답 끊김·검색 지연)는 다시 시도하면 풀리고, 외부 서비스(Bedrock·Polly·카카오)는 던지는 곳에서
    // 설정·권한 오류만 이미 ERROR로 남겨 여기서 다시 올리면 같은 오류가 두 번 기록된다. 답변 정책 차단(CHAT500-0)은 정상 동작이다.
    // 공통 패키지가 각 기능의 오류 코드에 의존하지 않도록 코드 문자열로 둔다(GlobalExceptionHandlerTest가 실제 코드와 맞춰 본다)
    static final Set<String> ALERT_CODES = Set.of("LLM502-1", "FAQ500-0", "FAQ500-1", "CHAT500-1");

	// 커스텀 예외 처리
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<CustomResponse<Void>> handleCustomException(GeneralException ex) {

    	BaseErrorCode errorCode = ex.getErrorCode();

    	if (ALERT_CODES.contains(errorCode.getCode())) {
    		// 던진 위치를 알 수 있게 예외를 함께 남긴다. GeneralException은 오류 코드만 담아 사용자 입력이 섞이지 않는다
    		log.error("[CustomException] {} {}", errorCode.getCode(), errorCode.getMessage(), ex);
    	} else {
    		log.warn("[CustomException] {}", errorCode.getMessage());
    	}

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(errorCode.getErrorResponse());
    }

    // 생성자 예외가 BeanInstantiationException으로 감싸진 경우 처리
    @ExceptionHandler(BeanInstantiationException.class)
    public ResponseEntity<CustomResponse<Void>> handleBeanInstantiationException(BeanInstantiationException ex) {
        if (ex.getCause() instanceof GeneralException generalException) {
            return handleCustomException(generalException);
        }
        return handleAllException(ex);
    }

    // @Valid 검증 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CustomResponse<Map<String, String>>> handleValidationException(MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        // 타입 변환 실패(숫자 자리에 글자, 없는 enum 값)의 기본 메시지에는 내부 클래스 이름이 들어 있어 응답에 싣지 않는다
        ex.getBindingResult()
        		.getFieldErrors()
        		.forEach(error ->
        			errors.putIfAbsent(
        					error.getField(),
        					error.isBindingFailure() ? INVALID_FORMAT_MESSAGE : error.getDefaultMessage()
        			)
        );

        return invalidRequestResponse(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CustomResponse<Map<String, String>>> handleConstraintViolationException(
            ConstraintViolationException ex
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            String field = path.substring(path.lastIndexOf('.') + 1);
            errors.putIfAbsent(field, violation.getMessage());
        });

        return invalidRequestResponse(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CustomResponse<Map<String, String>>> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> {
            String field = result.getMethodParameter().getParameterName();
            errors.putIfAbsent(field == null ? "request" : field, error.getDefaultMessage());
        }));

        return invalidRequestResponse(errors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CustomResponse<Map<String, String>>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex
    ) {
        Map<String, String> errors = new HashMap<>();
        errors.put(ex.getName(), INVALID_FORMAT_MESSAGE);

        return invalidRequestResponse(errors);
    }

    // 잘못된 요청 본문(JSON 파싱 실패 등) 처리
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CustomResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {

        // 파싱 오류 메시지에는 좌표 등 요청 값이 포함될 수 있다.
        log.warn("[HttpMessageNotReadableException] {}",
                ex.getMostSpecificCause().getClass().getSimpleName());

        BaseErrorCode errorCode = CommonErrorCode.BAD_REQUEST;

        CustomResponse<Void> errorResponse = CustomResponse.onFailure(
        		errorCode.getCode(),
        		errorCode.getMessage()
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(errorResponse);
    }

    // 처리하지 않은 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CustomResponse<Void>> handleAllException(Exception ex) {

        log.error("[Unhandled Exception]", ex);

        BaseErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;

        CustomResponse<Void> errorResponse = CustomResponse.onFailure(
        		errorCode.getCode(),
        		errorCode.getMessage()
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(errorResponse);
    }

    private ResponseEntity<CustomResponse<Map<String, String>>> invalidRequestResponse(Map<String, String> errors) {
        BaseErrorCode errorCode = CommonErrorCode.NOT_VALID_ERROR;
        CustomResponse<Map<String, String>> errorResponse = CustomResponse.onFailure(
                errorCode.getCode(),
                errorCode.getMessage(),
                errors
        );

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(errorResponse);
    }
}
