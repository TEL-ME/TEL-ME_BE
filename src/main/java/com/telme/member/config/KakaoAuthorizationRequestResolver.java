package com.telme.member.config;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.service.KakaoLinkRequestStore;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

@RequiredArgsConstructor
public class KakaoAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;
    private final KakaoLinkRequestStore linkRequestStore;

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return bindLinkRequest(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return bindLinkRequest(request, delegate.resolve(request, clientRegistrationId));
    }

    private OAuth2AuthorizationRequest bindLinkRequest(
            HttpServletRequest request, OAuth2AuthorizationRequest authorizationRequest) {
        if (authorizationRequest == null) {
            return null;
        }
        String token = request.getParameter("link_token");
        if (token == null) {
            linkRequestStore.clear(request);
        } else {
            try {
                linkRequestStore.bind(request, token, authorizationRequest.getState());
            } catch (GeneralException exception) {
                throw new OAuth2AuthenticationException(
                        new OAuth2Error(exception.getErrorCode().getCode()), exception);
            }
        }
        return authorizationRequest;
    }
}
