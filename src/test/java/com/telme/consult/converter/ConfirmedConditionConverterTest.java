package com.telme.consult.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.consult.dto.DialogueInput.Condition;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ConfirmedConditionConverterTest {
    private final ConfirmedConditionConverter converter = new ConfirmedConditionConverter();

    @Test
    void onlyFilledConditionsArePassedToAnswerGeneration() {
        var result =
                converter.convert(
                        Map.of(
                                "location",
                                Condition.filled("역삼역"),
                                "serviceType",
                                Condition.filled("USIM_REISSUE"),
                                "waiting",
                                Condition.pending(),
                                "declined",
                                Condition.declined()));

        assertThat(result)
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("location", "역삼역", "serviceType", "USIM_REISSUE"));
    }

    @Test
    void convertedConditionsCannotBeChanged() {
        var result = converter.convert(Map.of("location", Condition.filled("역삼역")));

        assertThatThrownBy(() -> result.put("location", "다른 지역"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
