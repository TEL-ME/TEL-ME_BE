package com.telme.llm.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(OutputCaptureExtension.class)
class BedrockCredentialsCheckTest {

    @Test
    @DisplayName("Bedrock API 키가 없으면 시작할 때 경고하고 키 값은 남기지 않는다")
    void 키가_없으면_경고한다(CapturedOutput output) {
        new BedrockCredentialsCheck(new MockEnvironment()).warnIfTokenMissing();

        assertThat(output).contains("AWS_BEARER_TOKEN_BEDROCK", "엑사원으로 동작한다");
    }

    @Test
    @DisplayName("키가 비어 있어도 없는 것으로 본다")
    void 빈_키도_경고한다(CapturedOutput output) {
        new BedrockCredentialsCheck(new MockEnvironment().withProperty(BedrockCredentialsCheck.TOKEN_ENV, " "))
                .warnIfTokenMissing();

        assertThat(output).contains("엑사원으로 동작한다");
    }

    @Test
    @DisplayName("키가 있으면 경고하지 않는다")
    void 키가_있으면_경고하지_않는다(CapturedOutput output) {
        new BedrockCredentialsCheck(new MockEnvironment().withProperty(BedrockCredentialsCheck.TOKEN_ENV, "test-value"))
                .warnIfTokenMissing();

        assertThat(output).doesNotContain("엑사원으로 동작한다").doesNotContain("test-value");
    }
}
