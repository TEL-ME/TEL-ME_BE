package com.telme.chat.service;

import com.telme.chat.config.ChatGuestLimitProperties;
import com.telme.chat.dto.res.ChatGuestLimitResponse;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.global.common.exception.GeneralException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 게스트가 오늘 접수한 질문 수를 DB에서 세어 한도를 넘으면 막는다. 서버 대수·재배포와 관계없이 같은 값을 본다.
// 입력 검사에서 막힌 메시지(BLOCKED)와 같은 requestId 재전송은 질문으로 저장되지 않아 세지 않는다.
// 서버 쪽 실패나 취소로 답을 받지 못한 질문도 세지 않는다(ChatMessageRepository.countGuestQuestionsToday)
@Component
@RequiredArgsConstructor
public class GuestDailyChatLimit {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ChatMessageRepository chatMessageRepository;
    private final ChatGuestLimitProperties properties;
    private final Clock clock;

    // 입력 검사가 잡은 actor 잠금 안에서 불러야 같은 게스트의 동시 전송이 한도를 함께 넘지 않는다.
    // 이번 질문을 접수한 뒤 남는 질문 수를 돌려준다. 회원이거나 한도를 끈 경우(0) null
    public Integer check(ChatActor actor) {
        int limit = properties.dailyMessages();
        if (actor.isMember() || limit == 0) {
            return null;
        }
        long used = chatMessageRepository.countGuestQuestionsToday(actor.guestId());
        if (used >= limit) {
            throw new GeneralException(ChatErrorCode.GUEST_DAILY_LIMIT_EXCEEDED);
        }
        return (int) (limit - used - 1);
    }

    // 화면을 열 때 남은 질문 수를 보여 주기 위한 조회. 접수하지 않으므로 잠금 없이 센다
    public ChatGuestLimitResponse status(ChatActor actor) {
        int limit = properties.dailyMessages();
        if (actor.isMember() || limit == 0) {
            return ChatGuestLimitResponse.unlimited();
        }
        long used = chatMessageRepository.countGuestQuestionsToday(actor.guestId());
        return new ChatGuestLimitResponse(true, limit, (int) Math.max(0, limit - used),
                LocalDate.now(clock.withZone(KST)).plusDays(1).atStartOfDay(KST).toInstant());
    }
}
