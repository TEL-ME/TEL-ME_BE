package com.telme.usage.service;

import java.time.LocalDate;

// 한 달 전체 사용량. 개통한 달은 개통 이후 일수만큼만 반영
public record MonthlyUsage(LocalDate month, long dataMb, int voiceMinutes, int smsCount) {
}
