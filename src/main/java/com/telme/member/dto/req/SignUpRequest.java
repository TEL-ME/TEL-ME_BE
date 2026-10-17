package com.telme.member.dto.req;

import com.telme.global.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record SignUpRequest(
        @NotBlank(message = "{member.name.required}")
        @Size(max = 50, message = "{member.name.size}") String name,
        @NotBlank(message = "{member.email.required}")
        @Email(message = "{member.email.format}")
        @Size(max = 255, message = "{member.email.size}") String email,
        // BCrypt가 72바이트 초과 시 예외를 던지는데 문자 수(@Size)로는 못 잡아 바이트 기준으로 따로 제한
        @NotBlank(message = "{member.password.required}")
        @Size(min = 8, message = "{member.password.min}")
        @MaxUtf8Bytes(value = 72, message = "{member.password.max-bytes}") String password
) {
    // 대소문자만 다른 이메일이 별개 계정이 되지 않도록 진입 시점에 한 번만 정규화. Locale.ROOT 고정 -> 서버 기본 Locale에 따라 대소문자 변환 결과가 달라지는 것 방지
    public SignUpRequest {
        name = name == null ? null : name.trim();
        email = email == null ? null : email.toLowerCase(Locale.ROOT);
    }
}
