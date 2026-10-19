package com.telme.llm.service;

import com.telme.llm.exception.BedrockTemporaryException;
import com.telme.llm.exception.LlmErrorCode;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.NoHttpResponseException;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.utils.AttributeMap;

public record BedrockTransportClientBuilder(SdkHttpClient.Builder<?> delegate)
        implements SdkHttpClient.Builder<BedrockTransportClientBuilder> {
    @Override
    public SdkHttpClient buildWithDefaults(AttributeMap defaults) {
        return new TransportClient(delegate.buildWithDefaults(defaults));
    }

    private record TransportClient(SdkHttpClient delegate) implements SdkHttpClient {
        @Override
        public ExecutableHttpRequest prepareRequest(HttpExecuteRequest request) {
            var prepared = delegate.prepareRequest(request);
            return new ExecutableHttpRequest() {
                @Override
                public HttpExecuteResponse call() throws IOException {
                    try {
                        return watchResponseBody(prepared.call());
                    } catch (IOException exception) {
                        // 인증 정보 조회 중 발생한 통신 실패와 모델 HTTP 연결 실패를 섞지 않는다.
                        if (isTemporaryConnection(exception)) {
                            throw new BedrockTemporaryException(LlmErrorCode.CONNECTION_FAILED);
                        }
                        throw exception;
                    }
                }

                @Override
                public void abort() {
                    prepared.abort();
                }
            };
        }

        @Override
        public String clientName() {
            return delegate.clientName();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static HttpExecuteResponse watchResponseBody(HttpExecuteResponse response) {
        // 인증·권한 오류 응답은 본문 전송이 끊겨도 접속 장애로 바꾸지 않는다.
        if (response.httpResponse().statusCode() < 200 || response.httpResponse().statusCode() >= 300
                || response.responseBody().isEmpty()) {
            return response;
        }
        var original = response.responseBody().orElseThrow();
        var watched = AbortableInputStream.create(new WatchedBody(original), original::abort);
        return HttpExecuteResponse.builder().response(response.httpResponse()).responseBody(watched).build();
    }

    private static final class WatchedBody extends FilterInputStream {
        private WatchedBody(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            try {
                return in.read();
            } catch (IOException exception) {
                throwIfTemporary(exception);
                throw exception;
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            try {
                return in.read(buffer, offset, length);
            } catch (IOException exception) {
                throwIfTemporary(exception);
                throw exception;
            }
        }

        private void throwIfTemporary(IOException exception) {
            if (isTemporaryConnection(exception)) {
                throw new BedrockTemporaryException(LlmErrorCode.CONNECTION_FAILED);
            }
        }
    }

    private static boolean isTemporaryConnection(IOException exception) {
        // 인증서·보안 연결 오류는 접속 장애로 간주해 우회하지 않는다.
        if (exception instanceof SSLException) {
            return false;
        }
        return exception instanceof SocketException || exception instanceof SocketTimeoutException
                || exception instanceof UnknownHostException || exception instanceof EOFException
                || exception instanceof NoHttpResponseException || exception instanceof ConnectionClosedException;
    }
}
