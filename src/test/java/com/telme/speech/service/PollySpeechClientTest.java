package com.telme.speech.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.speech.exception.SpeechErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.PollyException;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechResponse;
import software.amazon.awssdk.services.polly.model.TextType;
import software.amazon.awssdk.services.polly.model.VoiceId;

// 실제 Polly는 호출하지 않는다
class PollySpeechClientTest {

    private PollyClient pollyClient;
    private PollySpeechClient client;

    @BeforeEach
    void setUp() {
        pollyClient = mock(PollyClient.class);
        client = new PollySpeechClient(pollyClient, SpeechTestSupport.properties(10, 1000, 1000));
    }

    @Test
    @DisplayName("설정한 목소리·엔진으로 일반 텍스트를 mp3로 요청한다")
    void 요청_형식() {
        when(pollyClient.synthesizeSpeechAsBytes(any(SynthesizeSpeechRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(SynthesizeSpeechResponse.builder().build(), new byte[] {7, 8}));

        assertThat(client.synthesize("안녕하세요 <speak>")).containsExactly(7, 8);

        ArgumentCaptor<SynthesizeSpeechRequest> captor = ArgumentCaptor.forClass(SynthesizeSpeechRequest.class);
        verify(pollyClient).synthesizeSpeechAsBytes(captor.capture());
        SynthesizeSpeechRequest request = captor.getValue();
        assertThat(request.text()).isEqualTo("안녕하세요 <speak>");
        assertThat(request.textType()).isEqualTo(TextType.TEXT);
        assertThat(request.voiceId()).isEqualTo(VoiceId.SEOYEON);
        assertThat(request.engine()).isEqualTo(Engine.NEURAL);
        assertThat(request.outputFormat()).isEqualTo(OutputFormat.MP3);
    }

    @Test
    @DisplayName("권한 오류 같은 AWS 응답 오류는 SPEECH503-0으로 알린다")
    void AWS_응답_오류() {
        when(pollyClient.synthesizeSpeechAsBytes(any(SynthesizeSpeechRequest.class)))
                .thenThrow(PollyException.builder()
                        .statusCode(403)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDeniedException").build())
                        .build());

        assertThatThrownBy(() -> client.synthesize("안녕하세요"))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(SpeechErrorCode.SYNTHESIS_UNAVAILABLE);
    }

    @Test
    @DisplayName("키가 없거나 연결이 끊긴 경우도 SPEECH503-0으로 알린다")
    void 연결_실패() {
        when(pollyClient.synthesizeSpeechAsBytes(any(SynthesizeSpeechRequest.class)))
                .thenThrow(SdkClientException.create("Unable to load credentials"));

        assertThatThrownBy(() -> client.synthesize("안녕하세요"))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(SpeechErrorCode.SYNTHESIS_UNAVAILABLE);
    }
}
