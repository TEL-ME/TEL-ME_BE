package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatMessage.MessageType;
import com.telme.chat.entity.ChatMessage.Role;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.repository.QueryRoutingRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
    @Mock QueryRoutingRepository routings;
    StoreVisitServiceType finder;
    ChatSession session;

    @BeforeEach
    void setUp() {
        finder = new StoreVisitServiceType(messages, consultRequests, new RuleBasedRoutingFallback(), routings,
                new IntentConverter(new ObjectMapper()));
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

    // 업무 이름 없이 말한 발언("개인 명의로 바꾸려는데")은 규칙으로 업무를 못 찾는다. 그 발언을 라우팅할 때 저장된 업무를 쓴다
    @Test
    void 업무_이름이_없는_발언은_라우팅에_저장된_업무를_쓴다() {
        history(
                user("퇴사하면서 법인폰을 개인 명의로 바꾸려는데 절차가 어떻게 돼요?"),
                bot(MessageType.ANSWER, "양도인(회사 측)과 양수인(본인)이 각각 신분증을 지참하고 매장을 함께 방문하시면 됩니다."));
        given(routings.findByMessage_MessageId(1L)).willReturn(Optional.of(
                QueryRouting.builder().extractedConditions("{\"serviceType\":\"NAME_CHANGE\"}").build()));

        assertThat(finder.of(current())).isEqualTo("NAME_CHANGE");
    }

    @Test
    void 라우팅에_업무가_없으면_발언과_검색어에서_찾는다() {
        history(
                user("명의변경하려면 어떻게 해요?"),
                bot(MessageType.ANSWER, VISIT_ANSWER));
        given(routings.findByMessage_MessageId(1L)).willReturn(Optional.of(
                QueryRouting.builder().extractedConditions("{}").build()));

        assertThat(finder.of(current())).isEqualTo("NAME_CHANGE");
    }

    // 업무를 정하지 않은 매장 정보 질문의 표시값(UNDECIDED)은 업무가 아니다
    @Test
    void 라우팅의_업무_미정_표시는_업무로_쓰지_않는다() {
        history(
                user("매장 영업시간 알려줘"),
                bot(MessageType.ANSWER, "매장에 방문하시기 전에 영업시간을 확인해 주세요."));
        given(routings.findByMessage_MessageId(1L)).willReturn(Optional.of(
                QueryRouting.builder().extractedConditions("{\"serviceType\":\"UNDECIDED\"}").build()));

        assertThat(finder.of(current())).isNull();
    }

    // 방문하지 않아도 된다는 안내는 방문 안내가 아니다(PR #199 리뷰)
    @Test
    void 매장_방문_없이_된다는_안내_뒤에는_업무를_잇지_않는다() {
        history(
                user("번호이동 방법 알려줘"),
                bot(MessageType.ANSWER, "매장 방문 없이 온라인으로 번호이동을 신청할 수 있습니다."));

        assertThat(finder.of(current())).isNull();
    }

    @Test
    void 방문하실_필요_없다는_안내_뒤에도_업무를_잇지_않는다() {
        history(
                user("유심 재발급 어떻게 해요?"),
                bot(MessageType.ANSWER, "택배로 받으시면 매장에 가실 필요 없이 2~3일 안에 받아보실 수 있어요."));

        assertThat(finder.of(current())).isNull();
    }

    // 부정은 방문을 말한 그 문장에서만 본다. 다른 문장의 '않'은 방문 안내를 막지 않는다
    @Test
    void 다른_문장에_부정이_있어도_방문을_안내한_문장이_있으면_업무를_쓴다() {
        history(
                user("명의변경하려면 어떻게 해요?"),
                bot(MessageType.ANSWER, "미납 요금이 있으면 진행되지 않습니다. 두 분이 신분증을 가지고 같이 매장에 오시면 됩니다."));

        assertThat(finder.of(current())).isEqualTo("NAME_CHANGE");
    }

    @Test
    void 온라인과_방문을_함께_안내하면_방문_안내로_본다() {
        history(
                user("유심 재발급 어떻게 해요?"),
                bot(MessageType.ANSWER, "온라인으로 신청하시면 택배로 받을 수 있습니다. 바로 받으시려면 매장에 방문하시면 됩니다."));

        assertThat(finder.of(current())).isEqualTo("USIM_REISSUE");
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
    void 방문과_무관한_되묻기_뒤에도_업무를_쓴다() {
        history(
                user("다른 통신사에서 번호이동 하려면 뭐뭐 준비해야 돼요?"),
                bot(MessageType.CLARIFICATION, "현재 미납 요금이 있으신가요?"));

        assertThat(finder.of(current())).isEqualTo("PORT_IN");
    }

    @Test
    void 되묻기_앞_발언에_업무가_없으면_쓰지_않는다() {
        history(
                user("로밍 요금 알려줘"),
                bot(MessageType.CLARIFICATION, "어느 나라에서 쓰실 건가요?"));

        assertThat(finder.of(current())).isNull();
    }

    @Test
    void 매장이나_온라인에서_신청한다는_안내_뒤에도_업무를_쓴다() {
        history(
                user("유심 재발급 어떻게 해요?"),
                bot(MessageType.ANSWER, "단말이 지원되는지 먼저 확인하시고, 매장이나 온라인에서 신청하시면 됩니다."));

        assertThat(finder.of(current())).isEqualTo("USIM_REISSUE");
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
