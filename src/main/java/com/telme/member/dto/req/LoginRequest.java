package com.telme.member.dto.req;

import com.telme.global.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // 가입 때와 같은 상한 — 없으면 72바이트 넘는 값도 그대로 조회·BCrypt 비교까지 감
        @NotBlank @MaxUtf8Bytes(value = 72, message = "비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다.") String password
) {
    // 가입 시 소문자로 저장되므로 조회도 같은 규칙으로 정규화. Locale.ROOT 고정 -> 서버 기본 Locale에 따라 결과가 달라지는 것 방지
    public LoginRequest {
        email = email == null ? null : email.toLowerCase(Locale.ROOT);
    }
}
