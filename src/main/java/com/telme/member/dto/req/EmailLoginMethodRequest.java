package com.telme.member.dto.req;

import com.telme.global.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record EmailLoginMethodRequest(
        @NotBlank(message = "{member.email.required}")
        @Email(message = "{member.email.format}")
        @Size(max = 255, message = "{member.email.size}") String email,
        @NotBlank(message = "{member.password.required}")
        @Size(min = 8, message = "{member.password.min}")
        @MaxUtf8Bytes(value = 72, message = "{member.password.max-bytes}") String password
) {
    public EmailLoginMethodRequest {
        email = email == null ? null : email.toLowerCase(Locale.ROOT);
    }
}
