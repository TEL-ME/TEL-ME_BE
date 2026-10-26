package com.telme.chat.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.dto.res.ChatMessageHistoryItemResponse;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import org.junit.jupiter.api.Test;

class ChatMessageConverterTest {

    private final ChatMessageConverter converter = new ChatMessageConverter(new ObjectMapper());

    @Test
    void convertsJsonColumnsToJsonResponseValues() {
        ChatSession session = ChatSession.builder()
                .sessionId(10L)
                .userId(7L)
                .build();
        ChatMessage message = ChatMessage.builder()
                .messageId(20L)
                .session(session)
                .sequenceNo(2)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.STORE_RESULT)
                .content("가까운 매장을 안내합니다.")
                .status(ChatMessage.Status.COMPLETED)
                .answerBasis(ChatMessage.AnswerBasis.GROUNDED)
                .followUps("[\"다른 매장도 보여줘\"]")
                .storeResults("[{\"storeId\":3,\"name\":\"텔미 강남점\"}]")
                .build();

        ChatMessageHistoryItemResponse response = converter.toHistoryItemResponse(message, false, null);

        assertThat(response.followUps()).containsExactly("다른 매장도 보여줘");
        assertThat(response.storeResults()).singleElement().satisfies(store -> {
            assertThat(((Number) store.get("storeId")).longValue()).isEqualTo(3L);
            assertThat(store).containsEntry("name", "텔미 강남점");
        });
    }

    @Test
    void returnsNullOnlyForMalformedJsonFields() {
        ChatSession session = ChatSession.builder()
                .sessionId(10L)
                .userId(7L)
                .build();
        ChatMessage message = ChatMessage.builder()
                .messageId(20L)
                .session(session)
                .sequenceNo(2)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.ANSWER)
                .content("응답 본문")
                .status(ChatMessage.Status.COMPLETED)
                .followUps("{\"items\":[\"잘못된 형태\"]}")
                .storeResults("[1,2,3]")
                .build();

        ChatMessageHistoryItemResponse response = converter.toHistoryItemResponse(message, false, null);

        assertThat(response.content()).isEqualTo("응답 본문");
        assertThat(response.followUps()).isNull();
        assertThat(response.storeResults()).isNull();
    }

    @Test
    void rejectsScalarCoercionInJsonArrays() {
        ChatSession session = ChatSession.builder()
                .sessionId(10L)
                .userId(7L)
                .build();
        ChatMessage message = ChatMessage.builder()
                .messageId(20L)
                .session(session)
                .sequenceNo(2)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.ANSWER)
                .content("응답 본문")
                .status(ChatMessage.Status.COMPLETED)
                .followUps("[1,true]")
                .storeResults("[[1,2]]")
                .build();

        ChatMessageHistoryItemResponse response = converter.toHistoryItemResponse(message, false, null);

        assertThat(response.followUps()).isNull();
        assertThat(response.storeResults()).isNull();
    }
}
