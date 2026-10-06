package com.telme.member.service;

import com.telme.member.entity.SocialAccount;
import com.telme.member.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SocialLinkStartService {

    private static final String AUTHORIZE_PATH_PREFIX = "/oauth2/authorization/";

    private final CurrentMemberResolver currentMemberResolver;
    private final SocialLinkRequestStore socialLinkRequestStore;
    private final SocialEmailMatchStore socialEmailMatchStore;

    // 클라이언트가 연결 대상 회원을 지정할 수 없다 — 현재 세션의 로그인 회원으로만 고정
    public String start(HttpServletRequest httpRequest, SocialAccount.Provider provider) {
        User currentUser = currentMemberResolver.resolve(httpRequest);
        socialEmailMatchStore.clear(httpRequest);
        String token = socialLinkRequestStore.issue(httpRequest, currentUser.getUserId(), provider);
        return AUTHORIZE_PATH_PREFIX + provider.name().toLowerCase(Locale.ROOT) + "?link_token=" + token;
    }
}
