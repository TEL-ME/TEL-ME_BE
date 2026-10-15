package com.telme.speech.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.global.common.exception.GeneralException;
import com.telme.speech.exception.SpeechErrorCode;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Publisher;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.Alternative;
import software.amazon.awssdk.services.transcribestreaming.model.BadRequestException;
import software.amazon.awssdk.services.transcribestreaming.model.LanguageCode;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponseHandler;

// 실제 Transcribe는 호출하지 않는다
class TranscribeSpeechClientTest {

    private TranscribeStreamingAsyncClient transcribeClient;
    private TranscribeSpeechClient client;

    @BeforeEach
    void setUp() {
        transcribeClient = mock(TranscribeStreamingAsyncClient.class);
        client = new TranscribeSpeechClient(transcribeClient, SpeechTestSupport.sttProperties(5, Duration.ofMinutes(60)));
    }

    @Test
    @DisplayName("한국어·PCM·녹음 표본 주파수로 실시간 인식을 요청한다")
    @SuppressWarnings("unchecked")
    void 요청_형식() {
        when(transcribeClient.startStreamTranscription(any(StartStreamTranscriptionRequest.class), any(Publisher.class),
                any(StartStreamTranscriptionResponseHandler.class))).thenReturn(CompletableFuture.completedFuture(null));

        assertThat(client.transcribe(new byte[3_200], 16_000)).isEmpty();

        ArgumentCaptor<StartStreamTranscriptionRequest> captor = ArgumentCaptor.forClass(StartStreamTranscriptionRequest.class);
        verify(transcribeClient).startStreamTranscription(captor.capture(), any(Publisher.class),
                any(StartStreamTranscriptionResponseHandler.class));
        assertThat(captor.getValue().languageCode()).isEqualTo(LanguageCode.KO_KR);
        assertThat(captor.getValue().mediaEncoding()).isEqualTo(MediaEncoding.PCM);
        assertThat(captor.getValue().mediaSampleRateHertz()).isEqualTo(16_000);
        assertThat(captor.getValue().vocabularyName()).isNull();
    }

    @Test
    @DisplayName("사용자 지정 어휘 이름을 설정하면 요청에 담고, 공백뿐이면 담지 않는다")
    @SuppressWarnings("unchecked")
    void 사용자_지정_어휘() {
        when(transcribeClient.startStreamTranscription(any(StartStreamTranscriptionRequest.class), any(Publisher.class),
                any(StartStreamTranscriptionResponseHandler.class))).thenReturn(CompletableFuture.completedFuture(null));
        ArgumentCaptor<StartStreamTranscriptionRequest> captor = ArgumentCaptor.forClass(StartStreamTranscriptionRequest.class);

        new TranscribeSpeechClient(transcribeClient, SpeechTestSupport.sttProperties(5, Duration.ofMinutes(60), " telme-ko "))
                .transcribe(new byte[3_200], 16_000);
        new TranscribeSpeechClient(transcribeClient, SpeechTestSupport.sttProperties(5, Duration.ofMinutes(60), "  "))
                .transcribe(new byte[3_200], 16_000);

        verify(transcribeClient, times(2)).startStreamTranscription(captor.capture(), any(Publisher.class),
                any(StartStreamTranscriptionResponseHandler.class));
        assertThat(captor.getAllValues().get(0).vocabularyName()).isEqualTo("telme-ko");
        assertThat(captor.getAllValues().get(1).vocabularyName()).isNull();
    }

    @Test
    @DisplayName("Transcribe 오류는 SPEECH503-1로 알린다")
    @SuppressWarnings("unchecked")
    void Transcribe_오류() {
        when(transcribeClient.startStreamTranscription(any(StartStreamTranscriptionRequest.class), any(Publisher.class),
                any(StartStreamTranscriptionResponseHandler.class)))
                .thenReturn(CompletableFuture.failedFuture(BadRequestException.builder()
                        .statusCode(400)
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("BadRequestException").build())
                        .build()));

        assertThatThrownBy(() -> client.transcribe(new byte[3_200], 16_000))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(SpeechErrorCode.TRANSCRIPTION_UNAVAILABLE);
    }

    @Test
    @DisplayName("SDK가 호출을 시작하기 전에 바로 실패해도 서버 오류가 아니라 SPEECH503-1로 알린다")
    @SuppressWarnings("unchecked")
    void 호출_시작_실패() {
        when(transcribeClient.startStreamTranscription(any(StartStreamTranscriptionRequest.class), any(Publisher.class),
                any(StartStreamTranscriptionResponseHandler.class)))
                .thenThrow(SdkClientException.create("Unable to load region"));

        assertThatThrownBy(() -> client.transcribe(new byte[3_200], 16_000))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(SpeechErrorCode.TRANSCRIPTION_UNAVAILABLE);
    }

    @Test
    @DisplayName("제한 시간 안에 끝나지 않으면 호출을 취소하고 SPEECH503-1로 알린다")
    @SuppressWarnings("unchecked")
    void 시간_초과() {
        CompletableFuture<Void> neverEnds = new CompletableFuture<>();
        when(transcribeClient.startStreamTranscription(any(StartStreamTranscriptionRequest.class), any(Publisher.class),
                any(StartStreamTranscriptionResponseHandler.class))).thenReturn(neverEnds);

        assertThatThrownBy(() -> client.transcribe(new byte[3_200], 16_000))
                .isInstanceOf(GeneralException.class)
                .extracting(e -> ((GeneralException) e).getErrorCode())
                .isEqualTo(SpeechErrorCode.TRANSCRIPTION_UNAVAILABLE);
        assertThat(neverEnds).isCancelled();
    }

    @Test
    @DisplayName("중간 결과는 버리고 확정된 결과만 순서대로 공백으로 이어 붙인다")
    void 확정된_결과만_모은다() {
        TranscribeSpeechClient.TranscriptCollector collector = new TranscribeSpeechClient.TranscriptCollector();

        collector.add(List.of(result(true, "요금")));
        collector.add(List.of(result(false, " 요금제 바꾸고 싶어요. "), result(true, "그리고")));
        collector.add(List.of(result(false, "매장도 알려주세요."), result(false, " ")));

        assertThat(collector.text()).isEqualTo("요금제 바꾸고 싶어요. 매장도 알려주세요.");
    }

    private static Result result(boolean partial, String transcript) {
        return Result.builder()
                .isPartial(partial)
                .alternatives(Alternative.builder().transcript(transcript).build())
                .build();
    }
}
