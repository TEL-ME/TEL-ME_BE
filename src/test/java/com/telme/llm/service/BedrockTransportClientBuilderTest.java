package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.llm.exception.BedrockTemporaryException;
import com.telme.llm.exception.LlmErrorCode;
import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import javax.net.ssl.SSLHandshakeException;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.utils.AttributeMap;

class BedrockTransportClientBuilderTest {
    private final SdkHttpClient transport = mock(SdkHttpClient.class);
    private final SdkHttpClient.Builder<?> builder = mock(SdkHttpClient.Builder.class);
    private final ExecutableHttpRequest prepared = mock(ExecutableHttpRequest.class);

    @ParameterizedTest
    @MethodSource("provideTemporaryFailures")
    @DisplayName("모델 HTTP 전송 중 확인된 접속·끊김·이름 조회 장애만 대체 가능으로 분류한다")
    void 확인된_통신_장애를_표시한다(IOException failure) throws Exception {
        var request = prepare();
        when(prepared.call()).thenThrow(failure);

        assertThatThrownBy(request::call).isInstanceOfSatisfying(BedrockTemporaryException.class,
                exception -> assertThat(exception.getErrorCode())
                                    .isEqualTo(LlmErrorCode.CONNECTION_FAILED));
    }

    @ParameterizedTest
    @MethodSource("provideOtherFailures")
    @DisplayName("인증서·작업 중단·알 수 없는 입출력 오류는 대체 가능으로 표시하지 않는다")
    void 보안과_불명확한_오류는_우회하지_않는다(IOException failure) throws Exception {
        var request = prepare();
        when(prepared.call()).thenThrow(failure);

        assertThatThrownBy(request::call).isSameAs(failure);
    }

    @Test
    @DisplayName("정상 전송 결과와 요청 중단·클라이언트 종료를 원래 HTTP 연결에 전달한다")
    void 정상_응답과_자원_수명을_보존한다() throws Exception {
        var request = prepare();
        var response = HttpExecuteResponse.builder()
                .response(SdkHttpResponse.builder().statusCode(204).build()).build();
        when(prepared.call()).thenReturn(response);

        assertThat(request.call()).isSameAs(response);
        request.abort();
        createClient().close();

        verify(prepared).abort();
        verify(transport).close();
    }

    private ExecutableHttpRequest prepare() {
        when(transport.prepareRequest(any())).thenReturn(prepared);
        return createClient().prepareRequest(mock(HttpExecuteRequest.class));
    }

    private SdkHttpClient createClient() {
        when(builder.buildWithDefaults(any())).thenReturn(transport);
        return new BedrockTransportClientBuilder(builder).buildWithDefaults(AttributeMap.empty());
    }

    private static Stream<IOException> provideTemporaryFailures() {
        return Stream.of(new ConnectException(), new SocketException(), new SocketTimeoutException(),
                new UnknownHostException(), new EOFException(), new NoHttpResponseException("합성 응답 끊김"),
                new ConnectionClosedException("합성 연결 끊김"));
    }

    private static Stream<IOException> provideOtherFailures() {
        return Stream.of(new SSLHandshakeException("합성 인증서 오류"), new InterruptedIOException(),
                new IOException("합성 입출력 오류"));
    }
}
