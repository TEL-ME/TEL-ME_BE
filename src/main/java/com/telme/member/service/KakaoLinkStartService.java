package com.telme.member.service;

import com.telme.member.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KakaoLinkStartService {

    private static final String KAKAO_AUTHORIZE_PATH = "/oauth2/authorization/kakao";

    private final CurrentMemberResolver currentMemberResolver;
    private final KakaoLinkRequestStore kakaoLinkRequestStore;
    private final KakaoEmailMatchStore kakaoEmailMatchStore;

    // 클라이언트가 연결 대상 회원을 지정할 수 없다 — 현재 세션의 로그인 회원으로만 고정
    public String start(HttpServletRequest httpRequest) {
        User currentUser = currentMemberResolver.resolve(httpRequest);
        kakaoEmailMatchStore.clear(httpRequest);
        kakaoLinkRequestStore.issue(httpRequest, currentUser.getUserId());
        return KAKAO_AUTHORIZE_PATH;
    }
}
