package com.telme.chat.service;

import static org.assertj.core.api.Assertions.*;
import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import jakarta.validation.Validation;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatCoordinatesTest {
    @Test
    void validatesPairsRangesAndFiniteValuesAtHttpAndInternalBoundaries() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (Double[] pair : new Double[][]{
                    {37.5, null}, {null, 127.0}, {90.1, 127.0}, {37.5, -180.1},
                    {Double.NaN, 127.0}, {37.5, Double.POSITIVE_INFINITY}}) {
                assertThat(validator.validate(new ChatMessageSendRequest("매장", pair[0], pair[1]))).isNotEmpty();
                assertThatThrownBy(() -> ChatCoordinates.optional(pair[0], pair[1]))
                        .isInstanceOf(com.telme.global.common.exception.GeneralException.class);
            }
            assertThat(validator.validate(new ChatMessageSendRequest("매장", null, null))).isEmpty();
            assertThat(validator.validate(new ChatMessageSendRequest("매장", -90.0, 180.0))).isEmpty();
        }
        assertThat(ChatCoordinates.optional(null, null)).isNull();
    }

    @Test
    void requestCommandAndAnswerInputNeverExposeCoordinatesInToString() {
        var coordinates = new ChatCoordinates(37.456789, 127.987654);
        for (Object value : new Object[]{
                coordinates, new ChatMessageSendRequest("민감한 원문", 37.456789, 127.987654),
                new ChatProcessingCommand(1L, 2L, 3L, "민감한 원문", coordinates),
                new AnswerInput(1, 2, 3, Purpose.NEARBY_STORE, "민감한 원문", "검색", Map.of(), coordinates)}) {
            assertThat(value.toString()).doesNotContain("37.456789", "127.987654", "민감한 원문");
        }
    }
}
