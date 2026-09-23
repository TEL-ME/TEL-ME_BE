package com.telme.rag.service;

import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import java.util.List;
import java.util.Objects;

// 답변 메시지를 저장한 트랜잭션 안에서 발행한다. 저장은 커밋 후에 일어난다
public record AnswerSourcesReady(
        Long answerMessageId,
        List<AnswerSource> sources
) {

    public AnswerSourcesReady {
        Objects.requireNonNull(answerMessageId, "answerMessageId");
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
