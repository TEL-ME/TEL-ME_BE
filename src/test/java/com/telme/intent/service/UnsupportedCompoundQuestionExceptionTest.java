package com.telme.intent.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.consult.entity.ConsultRequest;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.Map;

class UnsupportedCompoundQuestionExceptionTest {

    // 하위 질문은 직렬화하지 않으므로 직렬화를 거치면 빈 목록이 된다(null이 아니다)
    @Test
    void partsAreEmptyAfterSerialization() throws Exception {
        var exception = new UnsupportedCompoundQuestionException(List.of(
                new UnsupportedCompoundQuestionException.Part(ConsultRequest.Intent.FAQ, "5G 요금제", Map.of())));
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(exception);
        }

        UnsupportedCompoundQuestionException restored;
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (UnsupportedCompoundQuestionException) in.readObject();
        }

        assertThat(exception.parts()).hasSize(1);
        assertThat(restored.parts()).isEmpty();
    }
}
