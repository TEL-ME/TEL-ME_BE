package com.telme.chat.service;

import jakarta.servlet.http.HttpServletRequest;

public interface ChatActorProvider {

    ChatActor getCurrentActor(HttpServletRequest request);
}
