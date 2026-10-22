package com.telme.intent.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.repository.QueryRoutingRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

// 업무 질문에 매장 방문을 안내받은 뒤 "매장 어디예요?"처럼 업무 없이 매장을 찾으면, 그 상담의 업무를 쓴다.
// 문맥 해석은 이런 질문을 앞 대화와 무관한 질문으로 보고 문맥을 넘기지 않아, 저장된 메시지를 직접 읽는다
@Component
@RequiredArgsConstructor
public class StoreVisitServiceType {

    // 직전 답변과 그 상담의 고객 발언을 찾기에 충분한 개수. 되묻기가 몇 번 오가도 이 안에 든다
    private static final int LOOKBACK = 12;
    private static final Pattern STORE = Pattern.compile("매장|대리점|지점");
    private static final Pattern VISIT = Pattern.compile("방문|오시|오셔|오실|가시|가셔|가실|들르|들러");
    // 방문하지 않아도 된다는 안내("매장 방문 없이 온라인으로 신청할 수 있습니다", PR #199 리뷰)
    private static final Pattern NO_VISIT = Pattern.compile(
            "(?:방문|오시|오실|가시|가실)(?:하지|하실|할|지)?\\s*(?:없이|않|안\\s|필요\\s*(?:없|가\\s*없)|대신)"
                    + "|안\\s*(?:오셔|오시|가셔|가시|방문)");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.?!])\\s+|\\n");
    private static final String NONE = "";
    private static final Set<String> SERVICE_TYPES = Set.of("NEW_LINE", "PORT_IN", "NAME_CHANGE", "USIM_REISSUE");

    private final ChatMessageRepository messages;
    private final ConsultRequestRepository consultRequests;
    private final RuleBasedRoutingFallback rules;
    private final QueryRoutingRepository routings;
    private final IntentConverter converter;

    // 직전 답변이 매장 방문 안내가 아니거나 업무가 하나로 정해지지 않으면 null
    public String of(ChatMessage current) {
        if (current == null || current.getSession() == null || current.getSequenceNo() == null) {
            return null;
        }
        List<ChatMessage> recent = messages.findCompletedContextMessagesBefore(
                current.getSession().getSessionId(), 0, current.getSequenceNo(), ChatMessage.Status.COMPLETED,
                Set.of(ChatMessage.MessageType.ERROR, ChatMessage.MessageType.BLOCKED), PageRequest.of(0, LOOKBACK));
        if (recent.isEmpty() || !guidesStoreVisit(recent.getFirst())) {
            return null;
        }
        String asked = askedServiceType(recent);
        if (!NONE.equals(asked)) {
            return asked;
        }
        // 고객이 업무 이름을 쓰지 않았으면 안내 답변이 말한 업무("명의를 변경하실 수 있습니다")를 쓴다
        String guide = recent.getFirst().getContent();
        return rules.hasServiceTypeMention(guide) ? rules.serviceTypeOf(guide) : null;
    }

    // 고객 발언에서 찾은 업무. 업무 표현이 없으면 NONE, 여러 업무가 섞였으면 null
    private String askedServiceType(List<ChatMessage> recent) {
        Set<String> codes = new HashSet<>();
        for (ChatMessage message : recent.subList(1, recent.size())) {
            // 앞 상담의 답이나 매장 결과가 나오면 그 앞은 다른 상담이다
            if (message.getRole() == ChatMessage.Role.ASSISTANT
                    && (message.getMessageType() == ChatMessage.MessageType.ANSWER
                        || message.getMessageType() == ChatMessage.MessageType.STORE_RESULT)) {
                break;
            }
            if (message.getRole() != ChatMessage.Role.USER) {
                continue;
            }
            // 그 발언을 라우팅할 때 모델이 읽고 검증을 통과한 업무가 있으면 쓴다.
            // "개인 명의로 바꾸려는데"처럼 업무 이름이 없는 발언은 아래 규칙으로는 업무를 찾지 못한다(TELME-214 측정 BS-D06)
            String routed = routedServiceType(message);
            if (routed != null) {
                codes.add(routed);
                continue;
            }
            for (String text : texts(message)) {
                if (!rules.hasServiceTypeMention(text)) {
                    continue;
                }
                String code = rules.serviceTypeOf(text);
                if (code == null) {
                    return null;
                }
                codes.add(code);
            }
        }
        if (codes.isEmpty()) {
            return NONE;
        }
        return codes.size() == 1 ? codes.iterator().next() : null;
    }

    // 발언의 라우팅 기록에 저장된 업무. 저장된 조건은 업무 검증(질문에 적힌 업무와 같은지)을 거친 값이다
    private String routedServiceType(ChatMessage message) {
        if (message.getMessageId() == null) {
            return null;
        }
        return routings.findByMessage_MessageId(message.getMessageId())
                .map(routing -> {
                    try {
                        return converter.parseConditions(routing.getExtractedConditions())
                                .get(FollowUpRouteResponse.SERVICE_TYPE_KEY);
                    } catch (IllegalArgumentException unreadable) {
                        return null;
                    }
                })
                .filter(SERVICE_TYPES::contains)
                .orElse(null);
    }

    // 고객 원문과, 그 발언으로 만든 FAQ 상담의 검색어. 업무 이름이 없는 원문도 검색어에서는 업무 이름으로 정리되기도 한다
    private List<String> texts(ChatMessage message) {
        List<String> texts = new ArrayList<>();
        if (message.getContent() != null) {
            texts.add(message.getContent());
        }
        if (message.getMessageId() != null) {
            consultRequests.findByOriginMessage_MessageIdOrderBySubqueryOrderAsc(message.getMessageId()).stream()
                    .filter(request -> request.getIntent() == ConsultRequest.Intent.FAQ && request.getQueryText() != null)
                    .forEach(request -> texts.add(request.getQueryText()));
        }
        return texts;
    }

    // 되묻기("두 분 모두 매장에 오실 수 있나요?")도 매장 방문을 전제로 하므로 함께 본다.
    // 매장과 방문이 한 문장에 있고 그 방문을 부정하지 않는 문장이 있어야 한다. 다른 문장의 부정은 보지 않는다
    private static boolean guidesStoreVisit(ChatMessage message) {
        String content = message.getContent();
        if (message.getRole() != ChatMessage.Role.ASSISTANT
                || (message.getMessageType() != ChatMessage.MessageType.ANSWER
                    && message.getMessageType() != ChatMessage.MessageType.CLARIFICATION)
                || content == null) {
            return false;
        }
        for (String sentence : SENTENCE_END.split(content)) {
            if (STORE.matcher(sentence).find() && VISIT.matcher(sentence).find() && !NO_VISIT.matcher(sentence).find()) {
                return true;
            }
        }
        return false;
    }
}
