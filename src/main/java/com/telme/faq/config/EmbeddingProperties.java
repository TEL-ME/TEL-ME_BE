package com.telme.faq.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

// provider: ollama(/api/embed) 또는 tei(Hugging Face text-embeddings-inference, /embed). 주소는 embedding.url
@ConfigurationProperties(prefix = "embedding")
public record EmbeddingProperties(
        String model,
        int dimension,
        Duration connectTimeout,
        Duration searchReadTimeout,
        Duration batchReadTimeout,
        @DefaultValue("ollama") String provider
) {

    public static final String TEI = "tei";

    @ConstructorBinding
    public EmbeddingProperties {
    }

    public EmbeddingProperties(String model, int dimension, Duration connectTimeout, Duration searchReadTimeout,
            Duration batchReadTimeout) {
        this(model, dimension, connectTimeout, searchReadTimeout, batchReadTimeout, "ollama");
    }

    public boolean tei() {
        return TEI.equals(provider);
    }
}
