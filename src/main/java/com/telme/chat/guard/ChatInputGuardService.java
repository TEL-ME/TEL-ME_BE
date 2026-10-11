package com.telme.chat.guard;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.guard.JdbcInputGuardStore.State;
import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;

/** 소유권 확인 후 접수 트랜잭션에서 실행한다. 정책 거절은 예외로 던지지 않아 기록을 함께 커밋한다. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ChatInputGuardService {
    private final ChatInputInspector inspector;
    private final JdbcInputGuardStore store;
    private final InputGuardProperties properties;
    private final Clock clock;

    public record Turn(
            State state,
            InputInspection inspection,
            InputGuardNotice notice,
            ChatMessageSendResponse cached,
            boolean rejected,
            boolean alreadyRestricted,
            String fingerprint,
            Instant now) {}

    public Turn begin(ChatActor actor, long sessionId, ChatMessageSendRequest request) {
        InputInspection inspection = inspector.inspect(request.content());
        String fingerprint = fingerprint(sessionId, inspection.content(), request);
        State state = store.lock(actor, clock.instant());
        // 잠금 대기 중 만료된 제한이나 지난 집계 구간을 획득 전 시각으로 판단하지 않는다.
        Instant now = clock.instant();
        var cached = store.cached(state, request.requestId());
        if (cached.isPresent()) {
            if (!fingerprint.equals(cached.get().fingerprint())) {
                throw new GeneralException(ChatErrorCode.INPUT_REQUEST_CONFLICT);
            }
            var response = cached.get().response();
            var previous = response.inputGuard();
            if (previous != null && previous.restrictionUntil() != null) {
                response =
                        response.withInputGuard(
                                new InputGuardNotice(
                                        previous.action(),
                                        previous.message(),
                                        previous.violationCount(),
                                        remaining(previous.restrictionUntil(), now),
                                        previous.restrictionStartedAt(),
                                        previous.restrictionUntil(),
                                        previous.detections()));
            }
            return new Turn(state, inspection, null, response, false, false, fingerprint, now);
        }
        state = store.resetExpiredAndInherit(state, now);
        int count = store.count(state, now, now.minus(properties.observationWindow()));
        if (state.restrictionUntil() != null && state.restrictionUntil().isAfter(now)) {
            var notice =
                    notice(
                            "RESTRICTED",
                            "반복된 욕설로 채팅이 일시 제한되었습니다.",
                            count,
                            state.restrictionStartedAt(),
                            state.restrictionUntil(),
                            inspection,
                            now);
            return new Turn(state, inspection, notice, null, true, true, fingerprint, now);
        }
        InputGuardNotice notice = null;
        boolean rejected = false;
        if (inspection.hasProfanity()) {
            count++;
            rejected = true;
            if (count >= properties.threshold()) {
                Instant until = now.plus(properties.restrictionDuration());
                state = store.restrict(state, now, until);
                notice =
                        notice(
                                "RESTRICTED",
                                "반복된 욕설로 채팅이 일시 제한되었습니다.",
                                count,
                                state.restrictionStartedAt(),
                                state.restrictionUntil(),
                                inspection,
                                now);
            } else {
                notice =
                        notice(
                                "WARNED",
                                "원활한 상담을 위해 욕설을 제외하고 질문해 주세요. "
                                        + "최근 집계 기간 내 "
                                        + properties.threshold()
                                        + "회 감지되면 일시 제한됩니다.",
                                count,
                                null,
                                null,
                                inspection,
                                now);
            }
        } else if (!inspection.hasQuestion()) {
            rejected = true;
            notice =
                    notice(
                            "REWRITE_REQUIRED",
                            "민감정보를 제외한 문의 내용을 입력해 주세요.",
                            count,
                            null,
                            null,
                            inspection,
                            now);
        } else if (inspection.wasMasked()) {
            notice = notice("MASKED", "민감정보를 가리고 문의를 처리합니다.", count, null, null, inspection, now);
        }
        return new Turn(state, inspection, notice, null, rejected, false, fingerprint, now);
    }

    public void record(
            Turn turn,
            ChatActor actor,
            long sessionId,
            ChatMessageSendRequest request,
            ChatMessageSendResponse response) {
        if (!turn.inspection().detections().isEmpty() || turn.rejected()) {
            store.record(
                    turn.state(),
                    actor,
                    sessionId,
                    request.requestId(),
                    turn.fingerprint(),
                    turn.inspection(),
                    turn.notice(),
                    inspector.policyVersion(),
                    turn.now(),
                    response);
        }
    }

    private InputGuardNotice notice(
            String action,
            String message,
            int count,
            Instant started,
            Instant until,
            InputInspection inspection,
            Instant now) {
        return new InputGuardNotice(
                action,
                message,
                count,
                remaining(until, now),
                started,
                until,
                inspection.detections());
    }

    private long remaining(Instant until, Instant now) {
        return until == null
                ? 0
                : Math.max(0, (until.toEpochMilli() - now.toEpochMilli() + 999) / 1000);
    }

    private String fingerprint(long sessionId, String safeContent, ChatMessageSendRequest request) {
        // 원문 번호는 해시에 포함하지 않는다. 좌표는 재사용 키의 다른 검색 요청을 구분하기 위한 해시로만 남는다.
        String canonical =
                sessionId
                        + "\n"
                        + safeContent
                        + "\n"
                        + request.latitude()
                        + "\n"
                        + request.longitude();
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("요청 식별 해시를 생성할 수 없습니다.");
        }
    }
}
