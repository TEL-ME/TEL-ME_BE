package com.telme.usage.service;

// 날마다 적용 중인 요금제의 월정액(그날의 약정 할인 반영)을 더해 그 달 일수로 나눈 값이며, 개통 전 날은 0원이다. 요금제 변경·약정 만료·개통일로 하루 요금이 달라진 달은 prorated가 true. 지난달에 가입 전이었으면 previousAmount·difference는 null
public record ExpectedFee(int amount, Integer previousAmount, Integer difference, boolean prorated) {
}
