package com.telme.speech.service;

import com.telme.speech.entity.SpeechDailyUsage;
import com.telme.speech.repository.SpeechDailyUsageRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 서비스 전체 하루 음성 사용량을 DB에서 차감한다. 재배포와 서버 대수에 상관없이 하루 한도가 유지된다
@Service
@RequiredArgsConstructor
public class SpeechDailyUsageService {

    private final SpeechDailyUsageRepository speechDailyUsageRepository;

    // 호출한 쪽 트랜잭션이 롤백돼도 Polly·Transcribe 호출 전에 차감한 사용량은 남아야 하고, 행 잠금도 이 짧은 트랜잭션 안에서만 잡기 위해 새로 연다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryCharge(LocalDate day, SpeechDailyUsage.Kind kind, long amount, long dailyLimit) {
        return speechDailyUsageRepository.chargeIfWithinLimit(day, kind.name(), amount, dailyLimit) > 0;
    }
}
