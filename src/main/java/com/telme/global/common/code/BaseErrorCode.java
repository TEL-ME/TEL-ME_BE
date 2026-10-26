package com.telme.global.common.code;

import org.springframework.http.HttpStatus;

import com.telme.global.common.CustomResponse;

public interface BaseErrorCode {

	HttpStatus getStatus();
	String getCode();
	String getMessage();

	default CustomResponse<Void> getErrorResponse() {
		return CustomResponse.onFailure(getCode(), getMessage());
	}
}
