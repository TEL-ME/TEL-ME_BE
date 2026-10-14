package com.telme.usage.service;

// 사용 습관 유형. 범위는 한도가 없는 항목의 월 사용량(데이터 GB, 통화 분, 문자 건)이고, 한도가 있으면 한도 대비 비율을 쓴다
public enum UsageProfile {

    LIGHT(2, 8, 50, 150, 20, 80, 0.2, 0.5),
    NORMAL(8, 30, 150, 400, 80, 250, 0.5, 0.9),
    HEAVY(30, 120, 400, 900, 250, 600, 0.9, 1.2);

    private final int minDataGb;
    private final int maxDataGb;
    private final int minVoiceMinutes;
    private final int maxVoiceMinutes;
    private final int minSmsCount;
    private final int maxSmsCount;
    private final double minRatio;
    private final double maxRatio;

    UsageProfile(int minDataGb, int maxDataGb, int minVoiceMinutes, int maxVoiceMinutes,
            int minSmsCount, int maxSmsCount, double minRatio, double maxRatio) {
        this.minDataGb = minDataGb;
        this.maxDataGb = maxDataGb;
        this.minVoiceMinutes = minVoiceMinutes;
        this.maxVoiceMinutes = maxVoiceMinutes;
        this.minSmsCount = minSmsCount;
        this.maxSmsCount = maxSmsCount;
        this.minRatio = minRatio;
        this.maxRatio = maxRatio;
    }

    public int minDataGb() {
        return minDataGb;
    }

    public int maxDataGb() {
        return maxDataGb;
    }

    public int minVoiceMinutes() {
        return minVoiceMinutes;
    }

    public int maxVoiceMinutes() {
        return maxVoiceMinutes;
    }

    public int minSmsCount() {
        return minSmsCount;
    }

    public int maxSmsCount() {
        return maxSmsCount;
    }

    public double minRatio() {
        return minRatio;
    }

    public double maxRatio() {
        return maxRatio;
    }
}
