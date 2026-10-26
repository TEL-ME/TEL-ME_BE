package com.telme.member.service;

import com.telme.chat.repository.ChatSessionRepository;
import com.telme.feedback.repository.FeedbackStore;
import com.telme.member.entity.User;
import com.telme.member.repository.GuestRepository;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestSuccessionService {

    private final GuestRepository guestRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final Clock clock;
    // FeedbackStore는 telme.feedback.enabled가 꺼져 있으면 빈이 등록되지 않으므로 ObjectProvider로 선택 주입한다
    private final ObjectProvider<FeedbackStore> feedbackStoreProvider;

    @Transactional
    public void succeedGuest(UUID guestId, User user) {
        // merged_user_id가 비어있는 행만 원자적 갱신 — 동시 승계 레이스에서 하나만 통과시켜 채팅·피드백도 그 요청만 이어감
        int updated = guestRepository.succeedGuest(guestId, user, clock.instant());
        if (updated > 0) {
            chatSessionRepository.succeedGuestSessions(guestId, user.getUserId());
            feedbackStoreProvider.ifAvailable(store -> store.succeedGuestFeedback(guestId, user.getUserId()));
        }
    }
}
