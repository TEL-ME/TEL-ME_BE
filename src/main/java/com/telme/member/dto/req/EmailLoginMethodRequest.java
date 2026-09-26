package com.telme.member.dto.req;

import com.telme.global.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record EmailLoginMethodRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8) @MaxUtf8Bytes(value = 72, message = "비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다.") String password
) {
    public EmailLoginMethodRequest {
        email = email == null ? null : email.toLowerCase(Locale.ROOT);
    }
}
