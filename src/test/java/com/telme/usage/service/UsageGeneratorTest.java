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

    static final UsageLimits NONE = UsageLimits.unlimited();
    // 알뜰 미니: 1.5GB, 300분, 100건
    static final UsageLimits MINI = new UsageLimits(1536L, 300L, 100L);

    // 5G 프리미엄: 150GB, 통화·문자 무제한
    static final UsageLimits PREMIUM = new UsageLimits(153_600L, null, null);

    final UsageGenerator generator = new UsageGenerator();

    @Test
    void 같은_사용자와_같은_달이면_몇_번을_만들어도_같은_값이다() {
        GeneratedUsage first = generator.generate(7L, OCT, NONE);

        assertThat(generator.generate(7L, OCT, NONE)).isEqualTo(first);
        assertThat(new UsageGenerator().generate(7L, OCT, NONE)).isEqualTo(first);
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
                GeneratedUsage usage = generator.generate(userId, LocalDate.of(2026, month, 1), NONE);

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
                long dataMb = generator.generate(userId, LocalDate.of(2026, month, 1), NONE).dataMb();
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
            dataValues.add(generator.generate(userId, OCT, NONE).dataMb());
        }

        assertThat(dataValues).hasSizeGreaterThan(90);
    }

    @Test
    void 한도가_있는_항목은_한도_대비_유형_비율의_플러스마이너스_20퍼센트_안이다() {
        for (long userId = 1; userId <= 3_000; userId++) {
            UsageProfile profile = generator.profileOf(userId);
            for (int month = 1; month <= 3; month++) {
                GeneratedUsage usage = generator.generate(userId, LocalDate.of(2026, month, 1), MINI);

                assertThat(usage.dataMb()).isBetween((long) (1536 * profile.minRatio() * 0.8) - 1,
                        (long) (1536 * profile.maxRatio() * 1.2) + 1);
                assertThat(usage.voiceMinutes()).isBetween((int) (300 * profile.minRatio() * 0.8) - 1,
                        (int) (300 * profile.maxRatio() * 1.2) + 1);
                assertThat(usage.smsCount()).isBetween((int) (100 * profile.minRatio() * 0.8) - 1,
                        (int) (100 * profile.maxRatio() * 1.2) + 1);
            }
        }
    }

    @Test
    void 한도가_있는_항목만_한도에_맞추고_무제한_항목은_한도가_없을_때와_같다() {
        UsageLimits dataOnly = new UsageLimits(1536L, null, null);
        for (long userId = 1; userId <= 200; userId++) {
            GeneratedUsage limited = generator.generate(userId, OCT, dataOnly);
            GeneratedUsage free = generator.generate(userId, OCT, NONE);

            assertThat(limited.voiceMinutes()).isEqualTo(free.voiceMinutes());
            assertThat(limited.smsCount()).isEqualTo(free.smsCount());
            assertThat(limited.dataMb()).isNotEqualTo(free.dataMb());
        }
    }

    @Test
    void 유형별_초과_비율은_가벼움_0_보통_거의_없음_많이_씀_절반_안팎이다() {
        Map<UsageProfile, int[]> counts = new EnumMap<>(UsageProfile.class);
        for (UsageProfile profile : UsageProfile.values()) {
            counts.put(profile, new int[2]);
        }
        for (long userId = 1; userId <= 20_000; userId++) {
            int[] count = counts.get(generator.profileOf(userId));
            for (int month = 1; month <= 6; month++) {
                GeneratedUsage usage = generator.generate(userId, LocalDate.of(2026, month, 1), MINI);
                count[0]++;
                count[1] += usage.dataMb() > 1536 ? 1 : 0;
            }
        }

        assertThat(counts.get(UsageProfile.LIGHT)[1]).isZero();
        assertThat(counts.get(UsageProfile.NORMAL)[1] / (double) counts.get(UsageProfile.NORMAL)[0]).isLessThan(0.05);
        assertThat(counts.get(UsageProfile.HEAVY)[1] / (double) counts.get(UsageProfile.HEAVY)[0])
                .isBetween(0.3, 0.8);
    }

    @Test
    void 제공량이_다르면_같은_사용자도_다른_값이고_같으면_같은_값이다() {
        UsageLimits larger = new UsageLimits(51_200L, 300L, 100L);

        assertThat(generator.generate(7L, OCT, larger).dataMb()).isNotEqualTo(generator.generate(7L, OCT, MINI).dataMb());
        assertThat(generator.generate(7L, OCT, MINI)).isEqualTo(generator.generate(7L, OCT, MINI));
    }

    @Test
    void 요금제를_올려도_가입_당시_습관이_이어진다() {
        for (long userId = 1; userId <= 300; userId++) {
            long habit = generator.generate(userId, OCT, MINI).dataMb();

            assertThat(generator.generate(userId, OCT, MINI, PREMIUM).dataMb()).isEqualTo(habit);
            assertThat(generator.generate(userId, OCT, MINI, NONE).dataMb()).isEqualTo(habit);
        }
    }

    @Test
    void 요금제를_내리면_바뀐_요금제에서_쓸_만한_양으로_맞춰진다() {
        for (long userId = 1; userId <= 300; userId++) {
            long fitted = generator.generate(userId, OCT, MINI).dataMb();

            assertThat(generator.generate(userId, OCT, PREMIUM, MINI).dataMb()).isEqualTo(fitted);
            assertThat(generator.generate(userId, OCT, NONE, MINI).dataMb()).isEqualTo(fitted);
        }
    }

    @Test
    void 제한이_생긴_항목은_가입_당시_습관과_그_달_요금제_기준_중_작은_값이고_무제한_항목은_습관_그대로이다() {
        UsageLimits dataLimited = new UsageLimits(153_600L, null, null);
        for (long userId = 1; userId <= 500; userId++) {
            GeneratedUsage changed = generator.generate(userId, OCT, MINI, dataLimited);
            GeneratedUsage habit = generator.generate(userId, OCT, MINI);
            GeneratedUsage month = generator.generate(userId, OCT, dataLimited);

            assertThat(changed.dataMb()).isLessThanOrEqualTo(Math.min(habit.dataMb(), month.dataMb()));
            assertThat(changed.voiceMinutes()).isEqualTo(habit.voiceMinutes());
            assertThat(changed.smsCount()).isEqualTo(habit.smsCount());
        }
    }

    @Test
    void 가입_당시도_그_달도_무제한이면_절대_범위로_만든다() {
        for (long userId = 1; userId <= 300; userId++) {
            assertThat(generator.generate(userId, OCT, NONE, NONE)).isEqualTo(generator.generate(userId, OCT, NONE));
        }
    }
}
