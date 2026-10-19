package com.telme.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telme.chat.dto.res.ChatSessionEndSummaryResponse.Content;
import com.telme.chat.dto.res.ChatSessionEndSummaryResponse.Inquiry;
import com.telme.chat.dto.res.ChatSessionEndSummaryResponse.Remaining;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.safety.ChatOutputGuard;
import com.telme.chat.safety.OutputMaskContext;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 안내 재작성에 따른 의미 변경을 줄이기 위해 원문과 저장 상태로 문의별 결과를 조립한다. */
@Component
@RequiredArgsConstructor
final class ChatEndSummaryGroundedGenerator {
    static final String VERSION = "consultation-end-inquiry-grounded-v1";

    record Result(ChatEndSummaryData.Result summary, List<ChatEndSummaryInquiryGrouper.Inquiry> inquiries,
            List<ChatEndSummaryPendingResolver.Pending> pending) {}

    private final ObjectMapper mapper;
    private final ChatInputInspector inspector;
    private final ChatOutputGuard guard;

    Result generate(ChatEndSummaryData.Input input, ChatEndSummaryData.Result extractive) {
        try {
            var original = mapper.readTree(extractive.json());
            var groups = ChatEndSummaryInquiryGrouper.group(input, original);
            var pending = ChatEndSummaryPendingResolver.resolve(input, input.pendingIds());
            var root = mapper.createObjectNode();
            var topics = root.putArray("topics");
            var guidance = root.putArray("guidance");
            var remaining = root.putArray("pendingItems");
            List<OutputMaskContext.Source> masks = new ArrayList<>(extractive.maskContext().sources());
            List<Inquiry> inquiryDetails = new ArrayList<>();
            List<Remaining> remainingDetails = new ArrayList<>();
            for (var group : groups) {
                add(topics.addObject(), group.title(), group.questionMessageIds(), masks);
                String text = formatPeriodSentence(group.periodContext()) + convertToRecordTone(group.sourceText());
                var stored = guidance.addObject();
                add(stored, text, group.sourceMessageIds(), masks);
                String title = topics.get(topics.size() - 1).path("text").asText();
                stored.put("title", title);
                inquiryDetails.add(Inquiry.builder()
                        .title(title)
                        .text(stored.path("text").asText())
                        .build());
            }
            for (var period : original.path("topics")) {
                boolean quote = period.path("text").asText().startsWith("고객이 언급한 기간(원문): ");
                boolean absorbed = groups.stream().anyMatch(group -> !group.periodContext().isBlank()
                        && group.sourceMessageIds().containsAll(extractSourceIds(period)));
                if (quote && !absorbed) {
                    topics.add(period);
                }
            }
            List<ChatEndSummaryPendingResolver.Pending> safePending = new ArrayList<>();
            for (var item : pending) {
                add(topics.addObject(), item.title(), List.of(item.originMessageId()), masks);
                var stored = remaining.addObject();
                add(stored, item.text(), List.of(item.originMessageId()), masks);
                String title = topics.get(topics.size() - 1).path("text").asText();
                stored.put("title", title);
                stored.put("kind", item.kind().name());
                remainingDetails.add(Remaining.builder()
                        .title(title)
                        .text(stored.path("text").asText())
                        .kind(item.kind().name())
                        .build());
                safePending.add(new ChatEndSummaryPendingResolver.Pending(item.key(), item.originMessageId(),
                        inspector.inspect(item.title()).content(), stored.path("text").asText(), item.kind()));
            }
            String json = mapper.writeValueAsString(root);
            var context = new OutputMaskContext(masks);
            guard.verifySummary(json, context);
            var content = Content.builder()
                    .topics(extractTexts(root.path("topics")))
                    .guidance(extractTexts(root.path("guidance")))
                    .pendingItems(extractTexts(root.path("pendingItems")))
                    .inquiries(inquiryDetails)
                    .remaining(remainingDetails)
                    .build();
            return new Result(new ChatEndSummaryData.Result(json, content, context), groups, List.copyOf(safePending));
        } catch (java.io.IOException failure) {
            throw new IllegalArgumentException("GROUNDED_INVALID_JSON", failure);
        }
    }

    private void add(ObjectNode item, String text, List<Long> sources, List<OutputMaskContext.Source> masks) {
        var inspected = inspector.inspect(text);
        if (!inspected.maskSpans().isEmpty()) {
            masks.add(OutputMaskContext.Source.from(inspected));
        }
        item.put("text", inspected.content());
        var ids = item.putArray("sourceMessageIds");
        sources.forEach(ids::add);
    }

    private List<String> extractTexts(com.fasterxml.jackson.databind.JsonNode items) {
        List<String> result = new ArrayList<>();
        items.forEach(item -> result.add(item.path("text").asText()));
        return List.copyOf(result);
    }

    private List<Long> extractSourceIds(com.fasterxml.jackson.databind.JsonNode item) {
        List<Long> result = new ArrayList<>();
        item.path("sourceMessageIds").forEach(value -> result.add(value.asLong()));
        return List.copyOf(result);
    }

    private String convertToRecordTone(String text) {
        return text.replace("방문해 주시기 바랍니다.", "방문하는 절차를 안내받았습니다.")
                .replace("방문해 주세요.", "방문하는 절차를 안내받았습니다.");
    }

    private String formatPeriodSentence(String context) {
        if (context.isBlank()) {
            return "";
        }
        String compact = context.replaceAll("\\s+", "");
        var self = java.util.regex.Pattern.compile("(?:저는|본인).*?([0-9]+(?:개월|년))").matcher(compact);
        var family = java.util.regex.Pattern.compile("어머니.*?([0-9]+(?:개월|년))").matcher(compact);
        if (self.find() && family.find() && compact.contains("가입")
                && ChatEndSummaryPeriodContext.canStatePeriod(context)) {
            return "고객이 말한 가입 기간은 본인 " + self.group(1) + ", 어머니 " + family.group(1) + "입니다. ";
        }
        var periods = java.util.regex.Pattern.compile("[0-9]+(?:개월|년)").matcher(compact).results()
                .map(java.util.regex.MatchResult::group).toList();
        boolean scopedSubject = java.util.regex.Pattern.compile(
                "어머니|엄마|아버지|아빠|배우자|자녀|남편|아내|회선|명의자|대리인").matcher(compact).find();
        if (periods.size() == 1 && compact.contains("가입") && !scopedSubject
                && ChatEndSummaryPeriodContext.canStatePeriod(context)) {
            return "고객은 가입 기간이 " + periods.getFirst() + "이라고 말씀하셨습니다. ";
        }
        // 대상이나 정정 관계를 단순 규칙으로 확정할 수 없으면 원문을 그대로 남긴다.
        return "고객이 기간에 관해 말한 내용은 ‘" + context + "’입니다. ";
    }

}
