package com.telme.usage.service;

// 한 달 전체 기준 평균 사용량. months는 평균에 쓴 달 수
public record UsageDemand(long dataMb, long voiceMinutes, long smsCount, int months) {
}
