package com.telme.faq.repository;

import com.telme.faq.entity.FaqEmbedding;

// findNearest()의 순위(ORDER BY)와 이후 score 계산이 같은 distance 값에서 나오도록 묶어서 반환하는 projection
public record FaqNearestMatch(FaqEmbedding embedding, double distance) {
}
