package com.telme.usage.service;

// 사용 습관 유형. 월 사용량 범위는 데이터(GB), 통화(분), 문자(건)이고 요금제와 무관하다
public enum UsageProfile {

    LIGHT(2, 8, 50, 150, 20, 80),
    NORMAL(8, 30, 150, 400, 80, 250),
    HEAVY(30, 120, 400, 900, 250, 600);

    private final int minDataGb;
    private final int maxDataGb;
    private final int minVoiceMinutes;
    private final int maxVoiceMinutes;
    private final int minSmsCount;
    private final int maxSmsCount;

    UsageProfile(int minDataGb, int maxDataGb, int minVoiceMinutes, int maxVoiceMinutes,
            int minSmsCount, int maxSmsCount) {
        this.minDataGb = minDataGb;
        this.maxDataGb = maxDataGb;
        this.minVoiceMinutes = minVoiceMinutes;
        this.maxVoiceMinutes = maxVoiceMinutes;
        this.minSmsCount = minSmsCount;
        this.maxSmsCount = maxSmsCount;
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
}
