package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MessageSourceRecorderTest {

    private final RecordingWriter writer = new RecordingWriter();
    private final MessageSourceRecorder recorder = new MessageSourceRecorder(writer);

    @Test
    @DisplayName("근거 목록을 그대로 저장에 넘긴다")
    void 근거를_저장에_넘긴다() {
        recorder.record(10L, List.of(source(1L), source(2L)));

        assertThat(writer.calls).hasSize(1);
        assertThat(writer.calls.getFirst().messageId()).isEqualTo(10L);
        assertThat(writer.calls.getFirst().sources()).hasSize(2);
    }

    @Test
    @DisplayName("근거가 없으면 저장하지 않는다")
    void 근거가_없으면_저장하지_않는다() {
        recorder.record(10L, List.of());

        assertThat(writer.calls).isEmpty();
    }

    @Test
    @DisplayName("답변 메시지가 없으면 저장하지 않는다")
    void messageId가_없으면_저장하지_않는다() {
        recorder.record(null, List.of(source(1L)));

        assertThat(writer.calls).isEmpty();
    }

    @Test
    @DisplayName("근거 목록이 null이어도 예외를 내보내지 않는다")
    void 근거가_null이어도_안전하다() {
        assertThatCode(() -> recorder.record(10L, null)).doesNotThrowAnyException();

        assertThat(writer.calls).isEmpty();
    }

    @Test
    @DisplayName("저장이 실패해도 예외를 밖으로 내보내지 않는다")
    void 저장_실패를_삼킨다() {
        writer.failure = new IllegalStateException("저장 실패");

        assertThatCode(() -> recorder.record(10L, List.of(source(1L))))
                .doesNotThrowAnyException();
    }

    private AnswerSource source(long faqId) {
        return AnswerSource.builder()
                .faqId(faqId)
                .titleSnapshot("질문" + faqId)
                .faqVersion(1)
                .faqUpdatedAt(LocalDate.of(2026, 9, 17))
                .searchRank((short) faqId)
                .score(new BigDecimal("0.9123"))
                .build();
    }

    private record Call(Long messageId, List<AnswerSource> sources) {
    }

    private static final class RecordingWriter extends MessageSourceWriter {

        private final List<Call> calls = new ArrayList<>();
        private RuntimeException failure;

        private RecordingWriter() {
            super(null, null);
        }

        @Override
        public void write(Long answerMessageId, List<AnswerSource> sources) {
            if (failure != null) {
                throw failure;
            }
            calls.add(new Call(answerMessageId, sources));
        }
    }
}
