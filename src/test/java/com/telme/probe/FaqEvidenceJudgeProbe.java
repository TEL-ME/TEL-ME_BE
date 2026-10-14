package com.telme.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.llm.config.BedrockProperties;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.service.BedrockClient;
import com.telme.rag.service.FaqEvidenceJudge;
import com.telme.rag.service.FaqEvidenceJudge.Verdict;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

// 운영 판정기로 실제 Bedrock 판정 2건을 돌린다(1원 미만). 측정 때 3회 모두 같은 결과가 나온 쌍이다
// TELME_PROBE=true이고 AWS_BEARER_TOKEN_BEDROCK 또는 AWS 기본 자격 증명이 있을 때만 돈다. CI에서는 돌지 않는다
@EnabledIfEnvironmentVariable(named = "TELME_PROBE", matches = "true")
class FaqEvidenceJudgeProbe {

    @Test
    void 측정과_같은_판정이_나온다() {
        BedrockProperties properties = new BedrockProperties(
                true, "us-east-1", "openai.gpt-oss-120b-1:0", Duration.ofSeconds(15), "low", 1000);
        try (BedrockRuntimeClient runtime = BedrockRuntimeClient.builder()
                .region(Region.of(properties.region()))
                .build()) {
            FaqEvidenceJudge judge = new FaqEvidenceJudge(
                    new BedrockClient(runtime, new BedrockRequestConverter(properties)), new ObjectMapper());

            // EVAL-121 | USIM-0003: FAQ에 없는 세부 사항(부가세)을 묻는다
            Verdict vat = judge.judge(null, null, "eSIM 발급 비용 2,750원은 부가세가 포함된 금액인가요?",
                    "eSIM은 얼마예요?", "eSIM 발급 비용은 2,750원입니다.");
            // EVAL-005 | DEVICE-0064: 정답 FAQ
            Verdict exchange = judge.judge(null, null, "한 번 교환한 단말을 다시 교환할 수 있나요?",
                    "이미 한 번 교환했는데 다시 교환할 수 있을까요?",
                    "반품·교환은 1회에 한해서만 가능해, 이미 한 번 교환하셨다면 추가 교환은 어렵습니다.");
            System.out.printf("[FaqEvidenceJudgeProbe] 부가세 %s / 재교환 %s%n", vat, exchange);

            assertThat(vat).isEqualTo(Verdict.NOT_ANSWERABLE);
            assertThat(exchange).isEqualTo(Verdict.ANSWERABLE);
        }
    }
}
