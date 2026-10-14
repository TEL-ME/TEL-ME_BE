package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaqEvidencePromptTemplatesTest {

    private static final Path EXPERIMENT = Path.of("scripts/gray_zone_upper_band_bedrock.py");
    private static final String START = "B3_SYSTEM_PROMPT = \"\"\"";

    @Test
    @DisplayName("시스템 프롬프트가 측정에 쓴 B3와 글자까지 같다")
    void 측정에_쓴_B3와_같다() throws IOException {
        String source = Files.readString(EXPERIMENT);
        int start = source.indexOf(START) + START.length();
        String experiment = source.substring(start, source.indexOf("\"\"\"", start));

        assertThat(FaqEvidencePromptTemplates.SYSTEM_PROMPT).isEqualTo(experiment);
    }

    @Test
    @DisplayName("FAQ 하나와 고객 질문을 측정과 같은 형식으로 담는다")
    void 측정과_같은_입력_형식() {
        assertThat(FaqEvidencePromptTemplates.userPrompt("eSIM은 얼마예요?", "eSIM 발급 비용은 2,750원입니다.",
                "부가세 포함인가요?"))
                .isEqualTo("[FAQ]\n[1] Q: eSIM은 얼마예요?\nA: eSIM 발급 비용은 2,750원입니다.\n\n[고객 질문]\n부가세 포함인가요?");
    }
}
