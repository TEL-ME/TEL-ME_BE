package com.telme.notification.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.notification.dto.NotificationType;
import com.telme.notification.dto.res.NotificationListResponse;
import com.telme.notification.dto.res.NotificationResponse;
import com.telme.notification.repository.NotificationRow;
import com.telme.notification.service.NotificationService.NotificationPage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationConverterTest {

    private final NotificationConverter converter = new NotificationConverter();

    @Test
    @DisplayName("알림 한 줄을 응답으로 바꾸고, 읽은 시각이 있으면 읽음이다")
    void 응답으로_바꾼다() {
        Instant now = Instant.parse("2026-10-19T03:00:00Z");
        NotificationResponse unread = converter.toResponse(row("요금제 상담", "5G 요금제는\n세 가지입니다.", null));
        NotificationResponse read = converter.toResponse(row(null, "답변", now));

        assertThat(unread.notificationId()).isEqualTo(7L);
        assertThat(unread.type()).isEqualTo(NotificationType.ANSWER_ARRIVED);
        assertThat(unread.title()).isEqualTo("요금제 상담");
        assertThat(unread.body()).isEqualTo("5G 요금제는 세 가지입니다.");
        assertThat(unread.sessionId()).isEqualTo(10L);
        assertThat(unread.messageId()).isEqualTo(100L);
        assertThat(unread.read()).isFalse();
        assertThat(read.title()).isNull();
        assertThat(read.read()).isTrue();
    }

    @Test
    @DisplayName("긴 답변은 50자에서 줄이고, 이모지를 반으로 자르지 않는다")
    void 긴_답변은_줄인다() {
        String long49 = "가".repeat(49);

        assertThat(converter.toResponse(row(null, "가".repeat(50), null)).body()).isEqualTo("가".repeat(50));
        assertThat(converter.toResponse(row(null, "가".repeat(51), null)).body()).isEqualTo("가".repeat(50) + "...");
        assertThat(converter.toResponse(row(null, long49 + "😀끝", null)).body()).isEqualTo(long49 + "...");
        assertThat(converter.toResponse(row(null, null, null)).body()).isNull();
    }

    @Test
    @DisplayName("목록 응답에 다음 커서와 다음 페이지 여부를 담는다")
    void 목록_응답() {
        NotificationListResponse response = converter.toListResponse(
                new NotificationPage(List.of(row(null, "답변", null)), 7L, true));

        assertThat(response.notifications()).hasSize(1);
        assertThat(response.nextCursor()).isEqualTo(7L);
        assertThat(response.hasNext()).isTrue();
    }

    private static NotificationRow row(String title, String preview, Instant readAt) {
        return new NotificationRow(7L, "ANSWER_ARRIVED", 10L, 100L, title, preview,
                Instant.parse("2026-10-19T02:00:00Z"), readAt);
    }
}
