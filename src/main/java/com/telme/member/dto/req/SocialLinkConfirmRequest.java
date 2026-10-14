package com.telme.member.dto.req;

import com.telme.global.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;

// 연결 대상 계정은 세션의 pending 정보(matchedUserId)로 고정된다 — 클라이언트가 이메일을 임의로 지정할 수 없다
public record SocialLinkConfirmRequest(
        @NotBlank(message = "{member.password.required}")
        @MaxUtf8Bytes(value = 72, message = "{member.password.max-bytes}") String password
) {
}
