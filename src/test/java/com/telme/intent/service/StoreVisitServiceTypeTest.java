package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatMessage.MessageType;
import com.telme.chat.entity.ChatMessage.Role;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StoreVisitServiceTypeTest {

    private static final String VISIT_ANSWER = "두 분이 각각 신분증을 지참하고 매장에 함께 오시면 됩니다.";

    @Mock ChatMessageRepository messages;
    @Mock ConsultRequestRepository consultRequests;
    StoreVisitServiceType finder;
    ChatSession session;

    @BeforeEach
    void setUp() {
        finder = new StoreVisitServiceType(messages, consultRequests, new RuleBasedRoutingFallback());
        session = ChatSession.builder().sessionId(7L).build();
    }

    @Test
    void 매장_방문을_안내한_상담의_업무를_쓴다() {
        history(
                user("명의변경하려면 어떻게 해요?"),
                bot(MessageType.CLARIFICATION, "두 분 모두 매장에 오실 수 있나요?"),
                user("예"),
                bot(MessageType.CLARIFICATION, "현재 미납 요금이 있으신가요?"),
                user("아니요"),
                bot(MessageType.ANSWER, VISIT_ANSWER));

        assertThat(finder.of(current())).isEqualTo("NAME_CHANGE");
    }

    @Test
    void 매장_방문을_묻는_되묻기_뒤에도_업무를_쓴다() {
        history(
                user("번호이동 하려면 어떻게 해요?"),
                bot(MessageType.CLARIFICATION, "매장에 방문하실 수 있나요?"));

        assertThat(finder.of(current())).isEqualTo("PORT_IN");
    }

    // 원문에 업무 이름이 없어도 그 발언으로 만든 FAQ 상담의 검색어에 있으면 쓴다
    @Test
    void 원문에_업무_이름이_없으면_상담_검색어에서_찾는다() {
        history(
                user("명의 넘기려면 어떻게 해요"),
                bot(MessageType.ANSWER, VISIT_ANSWER));
        given(consultRequests.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(1L)).willReturn(List.of(
                ConsultRequest.builder().intent(ConsultRequest.Intent.FAQ).queryText("명의변경 방법").build()));

        assertThat(finder.of(current())).isEqualTo("NAME_CHANGE");
    }

    @Test
    void 고객_발언에_업무가_없으면_안내_답변의_업무를_쓴다() {
        history(
                user("명의 넘기려면 어떻게 해요"),
                bot(MessageType.CLARIFICATION, "두 분이 함께 매장에 오실 수 있나요?"),
                user("네 둘다 갈수있어요"),
                bot(MessageType.ANSWER, "두 분이 각각 신분증을 지참하고 매장에 함께 방문하시면 명의를 변경하실 수 있습니다."));

        assertThat(finder.of(current())).isEqualTo("NAME_CHANGE");
    }

    @Test
    void 고객이_말한_업무가_안내_답변보다_우선한다() {
        history(
                user("번호이동 하려면 어떻게 해요?"),
                bot(MessageType.ANSWER, "매장에 방문하시면 명의 확인 후 번호이동을 도와드립니다. 명의변경이 필요하면 함께 처리할 수 있습니다."));

        assertThat(finder.of(current())).isEqualTo("PORT_IN");
    }

    @Test
    void 직전_답변이_매장_방문_안내가_아니면_쓰지_않는다() {
        history(
                user("명의변경하려면 어떻게 해요?"),
                bot(MessageType.ANSWER, "고객센터 앱에서 신청하실 수 있습니다."));

        assertThat(finder.of(current())).isNull();
    }

    @Test
    void 앞_상담의_업무는_쓰지_않는다() {
        history(
                user("번호이동 하려면 어떻게 해요?"),
                bot(MessageType.ANSWER, "가입하실 통신사에서 신청하시면 됩니다."),
                user("요금 미납이 있으면 어떻게 해요?"),
                bot(MessageType.ANSWER, "미납 요금은 매장에 방문하셔서 납부하실 수 있습니다."));

        assertThat(finder.of(current())).isNull();
    }

    @Test
    void 업무가_둘이면_쓰지_않는다() {
        history(
                user("명의변경하려면 어떻게 해요?"),
                bot(MessageType.CLARIFICATION, "두 분 모두 매장에 오실 수 있나요?"),
                user("번호이동도 같이 할 건데요"),
                bot(MessageType.ANSWER, VISIT_ANSWER));

        assertThat(finder.of(current())).isNull();
    }

    @Test
    void 앞_대화가_없으면_쓰지_않는다() {
        history();

        assertThat(finder.of(current())).isNull();
    }

    private ChatMessage current() {
        return ChatMessage.builder().messageId(100L).session(session).sequenceNo(100)
                .role(Role.USER).messageType(MessageType.QUESTION).content("매장이 어디있는데요?").build();
    }

    // 오래된 것부터 받아 저장소처럼 최근 것부터 돌려준다
    private void history(ChatMessage... oldestFirst) {
        List<ChatMessage> newestFirst = new ArrayList<>();
        for (int index = 0; index < oldestFirst.length; index++) {
            newestFirst.add(withSequence(oldestFirst[index], index + 1));
        }
        Collections.reverse(newestFirst);
        given(messages.findCompletedContextMessagesBefore(
                anyLong(), anyInt(), eq(100), any(), any(), any())).willReturn(newestFirst);
    }

    private ChatMessage withSequence(ChatMessage message, int sequenceNo) {
        return ChatMessage.builder().messageId((long) sequenceNo).session(session).sequenceNo(sequenceNo)
                .role(message.getRole()).messageType(message.getMessageType()).content(message.getContent()).build();
    }

    private static ChatMessage user(String content) {
        return ChatMessage.builder().role(Role.USER).messageType(MessageType.QUESTION).content(content).build();
    }

    private static ChatMessage bot(MessageType type, String content) {
        return ChatMessage.builder().role(Role.ASSISTANT).messageType(type).content(content).build();
    }
}
