package com.telme.global.common.exception;

import java.util.HashMap;
import java.util.Map;

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

	// 커스텀 예외 처리
    @ExceptionHandler(GeneralException.class)
    public ResponseEntity<CustomResponse<Void>> handleCustomException(GeneralException ex) {

    	BaseErrorCode errorCode = ex.getErrorCode();

    	log.warn("[CustomException] {}", errorCode.getMessage());

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

        ex.getBindingResult()
        		.getFieldErrors()
        		.forEach(error ->
        			errors.putIfAbsent(
        					error.getField(),
        					error.getDefaultMessage()
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
        errors.put(ex.getName(), "요청 값의 형식이 올바르지 않습니다.");

        return invalidRequestResponse(errors);
    }

    // 잘못된 요청 본문(JSON 파싱 실패 등) 처리
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CustomResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {

        log.warn("[HttpMessageNotReadableException] {}", ex.getMessage());

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
