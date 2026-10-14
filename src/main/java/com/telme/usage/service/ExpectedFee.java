package com.telme.usage.service;

// 날마다 적용 중인 요금제의 월정액(약정 할인 반영)을 일할로 합산하며 그 달에 요금제가 바뀌었으면 prorated가 true. 지난달에 가입 전이었으면 previousAmount·difference는 null
public record ExpectedFee(int amount, Integer previousAmount, Integer difference, boolean prorated) {
}
