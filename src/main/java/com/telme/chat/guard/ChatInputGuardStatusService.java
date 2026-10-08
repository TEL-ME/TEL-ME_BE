package com.telme.chat.guard;

import com.telme.chat.dto.res.ChatInputGuardStatusResponse;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatInputGuardStatusService {

    private final JdbcInputGuardStatusStore store;
    private final Clock clock;

    public ChatInputGuardStatusResponse getStatus(ChatActor actor) {
        if (actor.userId() == null && actor.guestId() == null) {
            throw new GeneralException(ChatErrorCode.UNAUTHENTICATED);
        }

        var ends = store.findRestrictionEnds(actor);
        // DB 조회가 지연되더라도 조회 전에 읽은 시각으로 만료된 제한을 반환하지 않는다.
        Instant now = clock.instant();
        // 기존 입력 검사와 같이 회원 본인의 유효한 제한을 우선한다.
        // 본인 제한이 없거나 만료됐으면 승계된 게스트 중 종료가 가장 늦은 유효한 제한을 사용한다.
        var active = ends.stream().filter(until -> until.isAfter(now)).findFirst();
        if (active.isEmpty()) {
            return new ChatInputGuardStatusResponse(false, null, 0, now);
        }

        Instant until = active.get();
        Duration remaining = Duration.between(now, until);
        long seconds = remaining.getSeconds() + (remaining.getNano() == 0 ? 0 : 1);
        return new ChatInputGuardStatusResponse(true, until, seconds, now);
    }
}
