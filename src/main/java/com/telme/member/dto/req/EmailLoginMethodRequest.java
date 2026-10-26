package com.telme.member.dto.req;

import com.telme.global.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record EmailLoginMethodRequest(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식으로 입력해 주세요.")
        @Size(max = 255, message = "이메일은 255자 이하여야 합니다.") String email,
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @MaxUtf8Bytes(value = 72, message = "비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다.") String password
) {
    public EmailLoginMethodRequest {
        email = email == null ? null : email.toLowerCase(Locale.ROOT);
    }
}
