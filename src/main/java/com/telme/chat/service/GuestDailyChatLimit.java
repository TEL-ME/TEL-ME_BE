package com.telme.chat.service;

import com.telme.chat.config.ChatGuestLimitProperties;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.global.common.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 게스트가 오늘 접수한 질문 수를 DB에서 세어 한도를 넘으면 막는다. 서버 대수·재배포와 관계없이 같은 값을 본다.
// 입력 검사에서 막힌 메시지(BLOCKED)와 같은 requestId 재전송은 질문으로 저장되지 않아 세지 않는다
@Component
@RequiredArgsConstructor
public class GuestDailyChatLimit {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatGuestLimitProperties properties;

    // 입력 검사가 잡은 actor 잠금 안에서 불러야 같은 게스트의 동시 전송이 한도를 함께 넘지 않는다
    public void check(ChatActor actor) {
        int limit = properties.dailyMessages();
        if (actor.isMember() || limit == 0) {
            return;
        }
        if (chatMessageRepository.countGuestQuestionsToday(actor.guestId()) >= limit) {
            throw new GeneralException(ChatErrorCode.GUEST_DAILY_LIMIT_EXCEEDED);
        }
    }
}
