package com.telme.faq.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// 검색 임계값 설정
// similarityThreshold는 질문+답변 벡터(Q_A) 기준, dualVector는 질문만 벡터(QUESTION_ONLY)를 함께 검색할 때 쓴다
// 두 벡터는 점수 분포가 달라 임계값을 따로 둔다
// grayZone은 채팅 답변용 검색에서 점수 구간 일부를 판정기로 확인할 때 쓴다(TELME-160)
@ConfigurationProperties(prefix = "search")
public record SearchProperties(
        Double similarityThreshold,
        @DefaultValue DualVector dualVector,
        @DefaultValue ScoreRecording scoreRecording,
        @DefaultValue GrayZone grayZone
) {

    public SearchProperties {
        // 기존 @Value 주입처럼 값이 없으면 기동에 실패하게 한다. primitive로 받으면 0으로 조용히 채워진다
        if (similarityThreshold == null) {
            throw new IllegalArgumentException("search.similarity-threshold가 설정되지 않았습니다");
        }
        requireScore("search.similarity-threshold", similarityThreshold);
    }

    public record DualVector(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("0.88") double questionThreshold
    ) {

        public DualVector {
            requireScore("search.dual-vector.question-threshold", questionThreshold);
        }
    }
    
    // 관리자 검색 점수 화면용 기록. 테스트는 REQUIRES_NEW로 롤백을 빠져나가 DB에 쌓이므로 끈다.
    // retention이 지난 기록은 FaqSearchScoreCleanupScheduler가 매일 지운다
    public record ScoreRecording(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("90d") Duration retention
            ) {
    }

    // 점수가 pass 이상이면 판정 없이 통과, floor 이상 pass 미만이면 판정기가 통과시켜야 근거로 쓰고, floor 미만은 버린다
    // 꺼져 있으면 similarityThreshold·dualVector.questionThreshold만 쓰는 기존 검색과 같다
    // 기본 선은 측정으로 고른 값이다(docs/GRAY_ZONE_UPPER_BAND.md: Q_A 0.69 ~ 0.80, 질문 0.80 ~ 0.88)
    public record GrayZone(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("0.80") double qaPass,
            @DefaultValue("0.69") double qaFloor,
            @DefaultValue("0.88") double questionPass,
            @DefaultValue("0.80") double questionFloor
    ) {

        public static final GrayZone DISABLED = new GrayZone(false, 0.80, 0.69, 0.88, 0.80);

        public GrayZone {
            requireScore("search.gray-zone.qa-pass", qaPass);
            requireScore("search.gray-zone.qa-floor", qaFloor);
            requireScore("search.gray-zone.question-pass", questionPass);
            requireScore("search.gray-zone.question-floor", questionFloor);
            if (qaFloor > qaPass || questionFloor > questionPass) {
                throw new IllegalArgumentException("search.gray-zone의 floor는 pass보다 클 수 없습니다");
            }
        }
    }

    // 측정 때는 임계값을 0으로 풀어 쓰므로 0은 허용한다
    private static void requireScore(String key, double value) {
        if (!(value >= 0 && value <= 1)) {
            throw new IllegalArgumentException(key + "는 0~1이어야 합니다: " + value);
        }
    }
}
