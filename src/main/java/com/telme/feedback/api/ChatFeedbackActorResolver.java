package com.telme.feedback.api;

import com.telme.chat.service.ChatActorProvider;
import com.telme.feedback.dto.FeedbackModels.Actor;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChatFeedbackActorResolver implements VerifiedFeedbackActorResolver {
    private final ChatActorProvider chatActorProvider;

    @Override
    public Actor resolve(HttpServletRequest request) {
        // Chat API에서 확인한 신원을 그대로 사용한다.
        var actor = chatActorProvider.getCurrentActor(request);
        if (actor == null) {
            return null;
        }
        // 로그인 후 남은 게스트 ID는 작성자 식별에 사용하지 않는다.
        return actor.isMember() ? new Actor(actor.userId(), null) : new Actor(null, actor.guestId());
    }
}
