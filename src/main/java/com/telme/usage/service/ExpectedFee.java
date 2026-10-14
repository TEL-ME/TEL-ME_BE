package com.telme.usage.service;

// 예상 요금 = 월정액 - 약정 할인(약정 진행 중일 때만). 지난달에 가입 전이었으면 previousAmount와 difference는 null
public record ExpectedFee(int amount, Integer previousAmount, Integer difference) {
}
