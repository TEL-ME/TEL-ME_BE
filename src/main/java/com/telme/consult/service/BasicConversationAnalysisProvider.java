package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisProvider;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisResult;
import com.telme.consult.service.FollowupContextService.Context;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 정의한 기본 대화 전체 발화만 처리하고, 상담 질문과 조건 응답은 기존 분석기에 위임한다. */
public final class BasicConversationAnalysisProvider implements AnalysisProvider {
    private static final Set<String> GREETINGS = Set.of("안녕", "안녕하세요", "안녕하십니까", "반가워요", "반갑습니다");
    private static final Set<String> THANKS =
            Set.of("감사합니다", "감사해요", "감사", "고마워", "고마워요", "고맙습니다", "고맙네요");
    private static final Set<String> FAREWELLS =
            Set.of("안녕히계세요", "안녕히가세요", "잘가요", "잘가", "다음에또올게요", "수고하세요", "수고하셨습니다");
    private static final Set<String> HELP_REQUESTS =
            Set.of(
                    "도움말",
                    "도와주세요",
                    "도와줘",
                    "뭐물어볼수있어요",
                    "무엇을물어볼수있나요",
                    "어떤질문을할수있나요",
                    "뭘할수있어요",
                    "어떤상담을할수있나요");

    private final AnalysisProvider delegate;

    public BasicConversationAnalysisProvider(AnalysisProvider delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public AnalysisResult analyze(Context context) {
        Objects.requireNonNull(context, "context");
        // 대기 중에는 짧은 동의·감사도 조건 응답일 수 있어 기존 후속 처리를 우선한다.
        if (!context.candidates().isEmpty()) {
            return delegate.analyze(context);
        }
        String normalized =
                context.message().strip().replaceAll("(?U)\\s+", "").replaceAll("[.!?~…]+$", "");
        String response;
        if (GREETINGS.contains(normalized)) {
            response = "안녕하세요. 무엇을 도와드릴까요?";
        } else if (THANKS.contains(normalized)) {
            response = "천만에요. 더 궁금한 점이 있으면 말씀해 주세요.";
        } else if (FAREWELLS.contains(normalized)) {
            response = "이용해 주셔서 감사합니다. 궁금한 점이 생기면 다시 말씀해 주세요.";
        } else if (HELP_REQUESTS.contains(normalized)) {
            response = "요금제, 청구·납부, 유심, 로밍 등 통신 서비스와 매장 관련 질문을 도와드려요. 궁금한 내용을 말씀해 주세요.";
        } else {
            return delegate.analyze(context);
        }
        // 고정 응답은 FAQ 근거 판정이 아니며, 작별 인사도 세션 종료 요청으로 해석하지 않는다.
        return AnalysisResult.direct(
                new ChatAnswer(ChatMessage.MessageType.ANSWER, response, null, List.of(), null));
    }
}
