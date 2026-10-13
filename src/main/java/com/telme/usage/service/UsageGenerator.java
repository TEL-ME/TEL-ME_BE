package com.telme.usage.service;

import java.time.LocalDate;
import java.util.SplittableRandom;
import org.springframework.stereotype.Component;

// 실제 통신망이 없어 사용자별 월 사용량을 만든다. 같은 사용자·같은 달이면 언제 실행해도 같은 값이다.
// 유형(30/50/20%)과 평소 사용 수준은 사용자 ID로 정해지고, 달마다 그 수준의 ±20% 안에서 변한다
@Component
public class UsageGenerator {

    private static final long PROFILE_SALT = 0x51ED270B6A1F3C5DL;
    private static final long HABIT_SALT = 0x2C1B3C6D7E4F9A01L;
    private static final long MONTH_SALT = 0x7A3E5B9C1D2F4863L;
    private static final int LIGHT_UNDER = 30;
    private static final int NORMAL_UNDER = 80;
    private static final double JITTER = 0.2;
    private static final int MB_PER_GB = 1024;

    public UsageProfile profileOf(long userId) {
        long bucket = Math.floorMod(mix(userId ^ PROFILE_SALT), 100L);
        if (bucket < LIGHT_UNDER) {
            return UsageProfile.LIGHT;
        }
        return bucket < NORMAL_UNDER ? UsageProfile.NORMAL : UsageProfile.HEAVY;
    }

    public GeneratedUsage generate(long userId, LocalDate month) {
        UsageProfile profile = profileOf(userId);
        SplittableRandom habit = new SplittableRandom(mix(userId ^ HABIT_SALT));
        double dataGb = between(habit, profile.minDataGb(), profile.maxDataGb());
        double voice = between(habit, profile.minVoiceMinutes(), profile.maxVoiceMinutes());
        double sms = between(habit, profile.minSmsCount(), profile.maxSmsCount());

        SplittableRandom monthly = new SplittableRandom(mix(userId ^ MONTH_SALT ^ mix(month.toEpochDay())));
        return new GeneratedUsage(
                Math.round(dataGb * jitter(monthly) * MB_PER_GB),
                (int) Math.round(voice * jitter(monthly)),
                (int) Math.round(sms * jitter(monthly)));
    }

    private static double between(SplittableRandom random, int min, int max) {
        return min + (max - min) * random.nextDouble();
    }

    private static double jitter(SplittableRandom random) {
        return 1 - JITTER + 2 * JITTER * random.nextDouble();
    }

    // splitmix64: 가까운 입력(연속된 사용자 ID 등)도 고르게 흩어 준다
    private static long mix(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
