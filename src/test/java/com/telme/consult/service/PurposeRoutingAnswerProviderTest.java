package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.telme.chat.entity.ChatMessage;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;

import org.junit.jupiter.api.Test;

import java.util.Map;

class PurposeRoutingAnswerProviderTest {
    @Test
    void nearbyStoreDoesNotReachFaqProvider() {
        AnswerProvider faq = mock(AnswerProvider.class);
        var provider = new PurposeRoutingAnswerProvider(faq);

        var result = provider.generate(
                new AnswerInput(
                        1L,
                        2L,
                        3L,
                        Purpose.NEARBY_STORE,
                        "강남역이요",
                        "가까운 매장",
                        Map.of("location", "강남역")));

        verifyNoInteractions(faq);
        assertThat(result.sources()).isEmpty();
        assertThat(result.answer().messageType()).isEqualTo(ChatMessage.MessageType.ANSWER);
        assertThat(result.answer().answerBasis()).isEqualTo(ChatMessage.AnswerBasis.NO_EVIDENCE);
        assertThat(result.answer().content()).contains("매장 정보를 바로 확인하기 어려워요");
    }
}
