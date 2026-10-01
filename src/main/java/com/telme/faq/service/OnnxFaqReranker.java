package com.telme.faq.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.telme.faq.config.SearchRerankProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "search.rerank", name = "enabled", havingValue = "true")
public class OnnxFaqReranker implements FaqReranker, DisposableBean {

    // XLM-RoBERTa 계열 토크나이저의 <pad>
    private static final long PAD_ID = 1L;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final HuggingFaceTokenizer tokenizer;
    private final int batchSize;
    private final boolean needsTokenTypeIds;

    public OnnxFaqReranker(SearchRerankProperties properties) {
        try {
            this.environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            options.setIntraOpNumThreads(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
            this.session = environment.createSession(properties.modelPath(), options);
            this.needsTokenTypeIds = session.getInputNames().contains("token_type_ids");
            this.tokenizer = HuggingFaceTokenizer.newInstance(
                    Path.of(properties.tokenizerPath()),
                    Map.of("maxLength", String.valueOf(properties.maxLength()),
                            "truncation", "true",
                            "padding", "false"));
            this.batchSize = properties.batchSize();
        } catch (OrtException e) {
            throw new IllegalStateException("리랭커 모델을 불러오지 못했습니다: " + properties.modelPath(), e);
        } catch (IOException e) {
            throw new UncheckedIOException("리랭커 토크나이저를 불러오지 못했습니다: " + properties.tokenizerPath(), e);
        }
        log.info("[OnnxFaqReranker] 리랭커 로드 완료 model={}", properties.modelPath());
    }

    @Override
    public double[] score(String query, List<String> documents) {
        double[] scores = new double[documents.size()];
        for (int start = 0; start < documents.size(); start += batchSize) {
            int end = Math.min(start + batchSize, documents.size());
            double[] batch = scoreBatch(query, documents.subList(start, end));
            System.arraycopy(batch, 0, scores, start, batch.length);
        }
        return scores;
    }

    private double[] scoreBatch(String query, List<String> documents) {
        Encoding[] encodings = new Encoding[documents.size()];
        int maxLength = 0;
        for (int i = 0; i < documents.size(); i++) {
            encodings[i] = tokenizer.encode(query, documents.get(i));
            maxLength = Math.max(maxLength, encodings[i].getIds().length);
        }

        long[][] inputIds = new long[documents.size()][maxLength];
        long[][] attentionMask = new long[documents.size()][maxLength];
        for (int i = 0; i < encodings.length; i++) {
            long[] ids = encodings[i].getIds();
            for (int j = 0; j < maxLength; j++) {
                boolean real = j < ids.length;
                inputIds[i][j] = real ? ids[j] : PAD_ID;
                attentionMask[i][j] = real ? 1L : 0L;
            }
        }

        try (OnnxTensor ids = OnnxTensor.createTensor(environment, inputIds);
             OnnxTensor mask = OnnxTensor.createTensor(environment, attentionMask);
             OnnxTensor types = needsTokenTypeIds
                     ? OnnxTensor.createTensor(environment, new long[documents.size()][maxLength])
                     : null) {
            Map<String, OnnxTensor> inputs = types == null
                    ? Map.of("input_ids", ids, "attention_mask", mask)
                    : Map.of("input_ids", ids, "attention_mask", mask, "token_type_ids", types);
            try (OrtSession.Result result = session.run(inputs)) {
                float[][] logits = (float[][]) result.get(0).getValue();
                double[] scores = new double[logits.length];
                for (int i = 0; i < logits.length; i++) {
                    scores[i] = 1.0 / (1.0 + Math.exp(-logits[i][0]));
                }
                return scores;
            }
        } catch (OrtException e) {
            throw new IllegalStateException("리랭커 추론에 실패했습니다.", e);
        }
    }

    @Override
    public void destroy() throws OrtException {
        tokenizer.close();
        session.close();
    }
}
