package com.telme.member.service;

import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.member.config.Oauth2Properties;
import com.telme.member.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class KakaoLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final GuestSuccessionService guestSuccessionService;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;
    private final Oauth2Properties oauth2Properties;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        Long userId = ((KakaoOAuth2User) authentication.getPrincipal()).getUserId();
        UUID guestId = readGuestId(request);

        if (guestId != null) {
            try {
                transactionTemplate.executeWithoutResult(status ->
                        guestSuccessionService.succeedGuest(guestId, userRepository.getReferenceById(userId)));
            } catch (RuntimeException exception) {
                logoutHandler.logout(request, response, authentication);
                response.sendRedirect(redirectUri(false, "GUEST_SUCCESSION_FAILED"));
                return;
            }
        }

        HttpSession session = request.getSession();
        if (guestId != null) {
            session.removeAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE);
        }
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);

        response.sendRedirect(redirectUri(true, null));
    }

    private UUID readGuestId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE);
        return value instanceof UUID uuid ? uuid : null;
    }

    private String redirectUri(boolean success, String reason) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(oauth2Properties.frontendUri() + "/oauth/callback")
                .queryParam("success", success);
        if (reason != null) {
            builder.queryParam("reason", reason);
        }
        return builder.build().encode().toUriString();
    }
}
