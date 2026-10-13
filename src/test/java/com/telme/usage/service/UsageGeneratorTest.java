package com.telme.usage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UsageGeneratorTest {

    static final LocalDate OCT = LocalDate.of(2026, 10, 1);

    final UsageGenerator generator = new UsageGenerator();

    @Test
    void 같은_사용자와_같은_달이면_몇_번을_만들어도_같은_값이다() {
        GeneratedUsage first = generator.generate(7L, OCT);

        assertThat(generator.generate(7L, OCT)).isEqualTo(first);
        assertThat(new UsageGenerator().generate(7L, OCT)).isEqualTo(first);
        assertThat(generator.profileOf(7L)).isEqualTo(new UsageGenerator().profileOf(7L));
    }

    @Test
    void 유형_비율은_가벼움_30_보통_50_많이_씀_20_퍼센트에_가깝다() {
        Map<UsageProfile, Integer> counts = new EnumMap<>(UsageProfile.class);
        int users = 20_000;
        for (long userId = 1; userId <= users; userId++) {
            counts.merge(generator.profileOf(userId), 1, Integer::sum);
        }

        assertThat(counts.get(UsageProfile.LIGHT) / (double) users).isCloseTo(0.30, within(0.02));
        assertThat(counts.get(UsageProfile.NORMAL) / (double) users).isCloseTo(0.50, within(0.02));
        assertThat(counts.get(UsageProfile.HEAVY) / (double) users).isCloseTo(0.20, within(0.02));
    }

    @Test
    void 연속된_사용자_ID가_같은_유형으로_몰리지_않는다() {
        Set<UsageProfile> firstTen = new HashSet<>();
        for (long userId = 1; userId <= 10; userId++) {
            firstTen.add(generator.profileOf(userId));
        }

        assertThat(firstTen).hasSizeGreaterThan(1);
    }

    @Test
    void 월_사용량은_유형_범위의_플러스마이너스_20퍼센트_안이다() {
        for (long userId = 1; userId <= 3_000; userId++) {
            UsageProfile profile = generator.profileOf(userId);
            for (int month = 1; month <= 3; month++) {
                GeneratedUsage usage = generator.generate(userId, LocalDate.of(2026, month, 1));

                assertThat(usage.dataMb()).isBetween((long) (profile.minDataGb() * 0.8 * 1024) - 1,
                        (long) (profile.maxDataGb() * 1.2 * 1024) + 1);
                assertThat(usage.voiceMinutes()).isBetween((int) (profile.minVoiceMinutes() * 0.8) - 1,
                        (int) (profile.maxVoiceMinutes() * 1.2) + 1);
                assertThat(usage.smsCount()).isBetween((int) (profile.minSmsCount() * 0.8) - 1,
                        (int) (profile.maxSmsCount() * 1.2) + 1);
            }
        }
    }

    @Test
    void 한_사용자의_달별_사용량은_조금씩_변하지만_평소_수준에서_크게_벗어나지_않는다() {
        for (long userId : new long[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}) {
            long min = Long.MAX_VALUE;
            long max = Long.MIN_VALUE;
            Set<Long> distinct = new HashSet<>();
            for (int month = 1; month <= 12; month++) {
                long dataMb = generator.generate(userId, LocalDate.of(2026, month, 1)).dataMb();
                min = Math.min(min, dataMb);
                max = Math.max(max, dataMb);
                distinct.add(dataMb);
            }

            assertThat(distinct).hasSizeGreaterThan(1);
            // 평소 수준 x 0.8 ~ 1.2 이므로 최대/최소는 1.5배를 넘지 않는다
            assertThat(max / (double) min).isLessThanOrEqualTo(1.5 + 0.01);
        }
    }

    @Test
    void 사용자가_다르면_사용량도_다르다() {
        Set<Long> dataValues = new HashSet<>();
        for (long userId = 1; userId <= 100; userId++) {
            dataValues.add(generator.generate(userId, OCT).dataMb());
        }

        assertThat(dataValues).hasSizeGreaterThan(90);
    }
}
