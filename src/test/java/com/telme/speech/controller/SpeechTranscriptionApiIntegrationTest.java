package com.telme.speech.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.global.common.exception.GeneralException;
import com.telme.speech.WavFixtures;
import com.telme.speech.exception.SpeechErrorCode;
import com.telme.speech.service.TranscribeSpeechClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// Transcribe 호출은 대신하고 본문 읽기·형식 검사·오류 응답을 실제 MVC로 확인한다
@SpringBootTest
@AutoConfigureMockMvc
class SpeechTranscriptionApiIntegrationTest {

    private static final String URL = "/api/v1/chat/speech/transcriptions";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TranscribeSpeechClient transcribeSpeechClient;

    @Test
    void WAV를_보내면_인식한_문장을_돌려준다() throws Exception {
        when(transcribeSpeechClient.transcribe(any(byte[].class), eq(16_000))).thenReturn(" 요금제 바꾸고 싶어요 ");

        mockMvc.perform(post(URL).session(guestSession()).contentType("audio/wav")
                        .content(WavFixtures.monoPcm16(16_000, 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.text").value("요금제 바꾸고 싶어요"));
        verify(transcribeSpeechClient).transcribe(any(byte[].class), eq(16_000));
    }

    @Test
    void WAV가_아니면_SPEECH400_1() throws Exception {
        mockMvc.perform(post(URL).session(guestSession()).contentType("audio/wav")
                        .content(WavFixtures.wav(1, 2, 16_000, 16, new byte[64_000])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPEECH400-1"));
        mockMvc.perform(post(URL).session(guestSession()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPEECH400-1"));
        verifyNoInteractions(transcribeSpeechClient);
    }

    @Test
    void 너무_짧거나_긴_녹음은_SPEECH400_2() throws Exception {
        mockMvc.perform(post(URL).session(guestSession()).contentType("audio/wav")
                        .content(WavFixtures.monoPcm16(16_000, 0.2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPEECH400-2"));
        mockMvc.perform(post(URL).session(guestSession()).contentType("audio/wav")
                        .content(WavFixtures.monoPcm16(16_000, 31)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPEECH400-2"));
        mockMvc.perform(post(URL).session(guestSession()).contentType("audio/wav")
                        .content(new byte[3_000_000]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPEECH400-2"));
        verifyNoInteractions(transcribeSpeechClient);
    }

    @Test
    void 인식된_말이_없으면_SPEECH422_0() throws Exception {
        when(transcribeSpeechClient.transcribe(any(byte[].class), eq(16_000))).thenReturn("");

        mockMvc.perform(post(URL).session(guestSession()).contentType("audio/wav")
                        .content(WavFixtures.monoPcm16(16_000, 1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SPEECH422-0"));
    }

    @Test
    void Transcribe_실패는_SPEECH503_1() throws Exception {
        when(transcribeSpeechClient.transcribe(any(byte[].class), eq(16_000)))
                .thenThrow(new GeneralException(SpeechErrorCode.TRANSCRIPTION_UNAVAILABLE));

        mockMvc.perform(post(URL).session(guestSession()).contentType("audio/wav")
                        .content(WavFixtures.monoPcm16(16_000, 1)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SPEECH503-1"));
    }

    @Test
    void 같은_사용자가_1분에_5번을_넘기면_SPEECH429_0() throws Exception {
        when(transcribeSpeechClient.transcribe(any(byte[].class), eq(16_000))).thenReturn("안녕하세요");
        MockHttpSession session = guestSession();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(URL).session(session).contentType("audio/wav")
                            .content(WavFixtures.monoPcm16(16_000, 1)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post(URL).session(session).contentType("audio/wav")
                        .content(WavFixtures.monoPcm16(16_000, 1)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SPEECH429-0"));
    }

    private MockHttpSession guestSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, UUID.randomUUID());
        return session;
    }
}
