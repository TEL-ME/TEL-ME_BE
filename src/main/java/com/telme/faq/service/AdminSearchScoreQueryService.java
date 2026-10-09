package com.telme.faq.service;

import com.telme.faq.config.SearchProperties;
import com.telme.faq.dto.req.AdminSearchScoreRequest;
import com.telme.faq.dto.res.AdminSearchScoreResponse;
import com.telme.faq.repository.FaqSearchScoreRepository;
import com.telme.faq.repository.FaqSearchScoreRepository.BucketCount;
import com.telme.faq.repository.FaqSearchScoreRepository.Summary;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminSearchScoreQueryService {

    private final FaqSearchScoreRepository repository;
    private final SearchProperties searchProperties;
    
    public AdminSearchScoreResponse getScores(AdminSearchScoreRequest request) {
        Instant from = request.fromOrMin();
        Instant to = request.toOrMax();
        double threshold = searchProperties.similarityThreshold();
        Summary summary = repository.findSummary(from, to);
        return new AdminSearchScoreResponse(threshold, summary.total(), summary.scored(), summary.passed(), 
                summary.refinedPassed(), summary.aboveThreshold(), 
                toBuckets(repository.findBuckets(from, to)));
    }
    
    // 검색이 없던 칸도 0으로 채워 그래프 막대가 항상 같은 자리에 오게 한다
    private List<AdminSearchScoreResponse.Bucket> toBuckets(List<BucketCount> counts) {
        Map<Integer, Long> byBucket = counts.stream().collect(Collectors.toMap(BucketCount::bucket, BucketCount::count));
        int n = FaqSearchScoreRepository.BUCKET_COUNT;
        return IntStream.range(0, n).mapToObj(i -> new AdminSearchScoreResponse.Bucket(
                (double) i / n, (double) (i + 1) / n, byBucket.getOrDefault(i, 0L))).toList();
    }
}
