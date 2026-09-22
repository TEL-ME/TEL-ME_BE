package com.telme.member.dto.req;

import com.telme.global.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignUpRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt가 72바이트 초과 시 예외를 던지는데 문자 수(@Size)로는 못 잡아 바이트 기준으로 따로 제한
        @NotBlank @Size(min = 8) @MaxUtf8Bytes(value = 72, message = "비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다.") String password
) {
}
