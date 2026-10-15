package com.telme.speech.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.speech.WavFixtures;
import com.telme.speech.service.TranscribeSpeechClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// MockMvc는 실제 소켓을 쓰지 않아 남은 본문 때문에 연결이 끊기는 경우를 재현하지 못해 실제 서버로 확인한다
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpeechTranscriptionOversizeServerTest {

    @LocalServerPort
    private int port;

    @MockitoBean
    private TranscribeSpeechClient transcribeSpeechClient;

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Test
    void 상한을_조금_넘는_본문도_연결이_끊기지_않고_SPEECH400_2_JSON을_받는다() throws Exception {
        assertRejectedAsJson(WavFixtures.monoPcm16(48_000, 31));
    }

    @Test
    void 상한의_두_배인_본문도_연결이_끊기지_않고_SPEECH400_2_JSON을_받는다() throws Exception {
        assertRejectedAsJson(WavFixtures.monoPcm16(48_000, 60));
    }

    @Test
    void 길이_상한을_넘은_16kHz_녹음도_연결이_끊기지_않고_SPEECH400_2_JSON을_받는다() throws Exception {
        assertRejectedAsJson(WavFixtures.monoPcm16(16_000, 120));
    }

    private void assertRejectedAsJson(byte[] wav) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/chat/speech/transcriptions"))
                .header("Content-Type", "audio/wav")
                .POST(HttpRequest.BodyPublishers.ofByteArray(wav))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("\"code\":\"SPEECH400-2\"");
    }
}
