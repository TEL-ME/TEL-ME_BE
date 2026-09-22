package com.telme.rag.service;

import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessageSourceRecorder {

    private final MessageSourceWriter messageSourceWriter;

    // 트랜잭션 커밋은 writer 호출이 끝날 때 일어나므로 여기서 감싸야 실패를 붙잡을 수 있다
    public void record(Long answerMessageId, List<AnswerSource> sources) {
        if (answerMessageId == null || sources == null || sources.isEmpty()) {
            return;
        }

        try {
            messageSourceWriter.write(answerMessageId, sources);
        } catch (RuntimeException e) {
            // 근거가 안 남는 것보다 답변이 안 나가는 쪽이 더 나쁘다
            log.warn("[MessageSourceRecorder] 답변 근거 저장 실패 messageId={}", answerMessageId, e);
        }
    }
}
