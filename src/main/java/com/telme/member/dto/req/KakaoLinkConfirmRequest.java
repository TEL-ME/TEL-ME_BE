package com.telme.member.dto.req;

import com.telme.global.common.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;

// 연결 대상 계정은 세션의 pending 정보(matchedUserId)로 고정된다 — 클라이언트가 이메일을 임의로 지정할 수 없다
public record KakaoLinkConfirmRequest(
        @NotBlank @MaxUtf8Bytes(value = 72, message = "비밀번호는 UTF-8 기준 72바이트를 넘을 수 없습니다.") String password
) {
}
