package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatMessage;
import com.telme.rag.service.AnswerPromptTemplates;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatPersonaTest {
    @Test
    void isolatedMiddleGreetingIsAddedOnceWithoutTreatingLiteralAsGreeting() {
        assertThat(ChatPersona.prependGreeting("유심 비용, 안녕 바라야, 알려줘", "7,700원입니다."))
                .isEqualTo("안녕하세요! 7,700원입니다.");
        assertThat(ChatPersona.prependGreeting("'안녕' 문자를 보내면 얼마야?", "문자 요금을 확인해 주세요."))
                .isEqualTo("문자 요금을 확인해 주세요.");
    }

    @Test
    void decorationPreservesBasisAndSourceMetadataAndDoesNotRepeatGreeting() {
        var original = new ChatAnswer(ChatMessage.MessageType.ANSWER,
                AnswerPromptTemplates.NO_EVIDENCE_ANSWER, ChatMessage.AnswerBasis.NO_EVIDENCE,
                List.of("로밍 신청"), null);
        var decorated = ChatPersona.decorate("안녕 바라야 로밍 신청 알려줘", original);

        assertThat(decorated.content()).isEqualTo("안녕하세요! " + original.content());
        assertThat(decorated.answerBasis()).isEqualTo(ChatMessage.AnswerBasis.NO_EVIDENCE);
        assertThat(decorated.followUps()).isEqualTo(original.followUps());
        assertThat(ChatPersona.decorate("안녕 바라야 로밍 신청 알려줘", decorated)).isEqualTo(decorated);
        assertThat(ChatPersona.decorate("로밍 신청 알려줘", original)).isEqualTo(original);
    }

    @Test
    void greetingDoesNotDropStorePayloadOrAddEmojiToMixedAnswers() {
        var stores = List.<Map<String, Object>>of(Map.of("name", "강남 매장"));
        var original = new ChatAnswer(ChatMessage.MessageType.STORE_RESULT, null, null, List.of(), stores);

        var decorated = ChatPersona.decorate("바라야 안녕 매장 찾아줘", original);

        assertThat(decorated.content()).isEqualTo("안녕하세요!");
        assertThat(decorated.storeResults()).isEqualTo(stores);
        assertThat(decorated.messageType()).isEqualTo(ChatMessage.MessageType.STORE_RESULT);
    }
}
