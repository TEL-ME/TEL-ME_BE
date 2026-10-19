package com.telme.consult.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 월 데이터 사용량 답을 MB로 읽는다. 값이 분명하지 않으면 읽지 못한 것으로 두어 되묻게 한다
public final class DataUsageReplyParser {

    public enum Kind {
        AMOUNT,
        UNLIMITED,
        UNSURE,
        UNREADABLE
    }

    public record Result(Kind kind, long dataMb) {
        private static final Result UNLIMITED = new Result(Kind.UNLIMITED, 0);
        private static final Result UNSURE = new Result(Kind.UNSURE, 0);
        private static final Result UNREADABLE = new Result(Kind.UNREADABLE, 0);

        public boolean readable() {
            return kind != Kind.UNREADABLE;
        }
    }

    private static final long MB_PER_GB = 1024;
    private static final long MAX_GB = 10_000;
    private static final int DAYS_PER_MONTH = 30;

    private static final String NUMBER = "\\d+(?:\\.\\d+)?";
    private static final String GB_UNIT = "기가바이트|기가|지비|gb";
    private static final String MB_UNIT = "메가바이트|메가|엠비|mb";
    private static final String UNIT = "(?:" + GB_UNIT + "|" + MB_UNIT + ")";

    // 숫자 하나 또는 범위(앞 숫자에 단위가 없으면 뒤 숫자의 단위를 따른다)
    private static final Pattern QUANTITY = Pattern.compile(
            "(" + NUMBER + ")\\s*(" + UNIT + ")?\\s*(?:(?:~|-|에서|부터)\\s*(" + NUMBER + ")\\s*(" + UNIT + ")?)?");
    private static final Pattern KOREAN_QUANTITY = Pattern.compile("([일이삼사오육칠팔구십백천]+)\\s*(" + UNIT + ")");
    private static final Pattern GB_ONLY = Pattern.compile("^\\s*(" + NUMBER + ")\\s*g\\s*$");
    private static final Pattern OTHER_QUANTITY = Pattern.compile(
            "\\d\\s*(?:명|대|살|세|개|원|%|회선|년|개월|시간|분|건|번|배|퍼센트|일|인(?![가데지듯]))");
    private static final Pattern DAILY = Pattern.compile("하루|매일|일\\s*평균|일평균|(?<!\\d)1일");
    private static final Pattern MONTHLY = Pattern.compile("한\\s*달|매달|달에|월\\s*평균|월간|1개월");
    private static final Pattern UNLIMITED = Pattern.compile("무제한|언리미티드|제한\\s*없");
    private static final Pattern NEGATION = Pattern.compile("안|않|아니|필요\\s*없|싫");
    private static final Pattern UNSURE = Pattern.compile("모르|몰라|모름|글쎄|기억(?:이)?\\s*(?:안|잘)|감이\\s*안|확실하지");
    private static final Pattern GB_WORD = Pattern.compile("^(?:" + GB_UNIT + ")$");

    private DataUsageReplyParser() {}

    public static Result parse(String reply) {
        if (reply == null || reply.isBlank()) {
            return Result.UNREADABLE;
        }
        String text = reply.strip().toLowerCase(Locale.ROOT).replaceAll("(?<=\\d),(?=\\d{3})", "");
        boolean daily = DAILY.matcher(text).find();
        if (daily && MONTHLY.matcher(text).find()) {
            return Result.UNREADABLE;
        }
        Set<BigDecimal> amounts = explicitAmounts(text);
        if (amounts.isEmpty()) {
            BigDecimal bare = bareAmount(text);
            if (bare != null) {
                amounts.add(bare);
            }
        }
        if (amounts.size() > 1) {
            return Result.UNREADABLE;
        }
        if (amounts.size() == 1) {
            return amount(amounts.iterator().next(), daily);
        }
        if (UNLIMITED.matcher(text).find()) {
            return NEGATION.matcher(text).find() ? Result.UNREADABLE : Result.UNLIMITED;
        }
        return UNSURE.matcher(text).find() ? Result.UNSURE : Result.UNREADABLE;
    }

    private static Result amount(BigDecimal mb, boolean daily) {
        BigDecimal monthly = (daily ? mb.multiply(BigDecimal.valueOf(DAYS_PER_MONTH)) : mb).setScale(0, RoundingMode.HALF_UP);
        if (monthly.signum() <= 0 || monthly.compareTo(BigDecimal.valueOf(MAX_GB * MB_PER_GB)) > 0) {
            return Result.UNREADABLE;
        }
        return new Result(Kind.AMOUNT, monthly.longValueExact());
    }

    // 단위가 붙은 숫자만 읽는다. 범위는 큰 쪽을 쓴다
    private static Set<BigDecimal> explicitAmounts(String text) {
        Set<BigDecimal> amounts = new LinkedHashSet<>();
        Matcher quantity = QUANTITY.matcher(text);
        while (quantity.find()) {
            String unit = quantity.group(4) != null ? quantity.group(4) : quantity.group(2);
            if (unit == null) {
                continue;
            }
            BigDecimal first = new BigDecimal(quantity.group(1));
            BigDecimal second = quantity.group(3) == null ? first : new BigDecimal(quantity.group(3));
            amounts.add(toMb(first.max(second), unit));
        }
        Matcher korean = KOREAN_QUANTITY.matcher(text);
        while (korean.find()) {
            long number = koreanNumber(korean.group(1));
            if (number > 0) {
                amounts.add(toMb(BigDecimal.valueOf(number), korean.group(2)));
            }
        }
        return amounts;
    }

    // 단위 없는 숫자는 숫자가 하나뿐이고 다른 양을 말한 것이 아닐 때만 GB로 읽는다. 5G 같은 망 이름은 읽지 않는다
    private static BigDecimal bareAmount(String text) {
        Matcher gbOnly = GB_ONLY.matcher(text);
        if (gbOnly.matches()) {
            return toMb(new BigDecimal(gbOnly.group(1)), "gb");
        }
        if (OTHER_QUANTITY.matcher(text).find()) {
            return null;
        }
        List<BigDecimal> bare = new ArrayList<>();
        Matcher quantity = QUANTITY.matcher(text);
        while (quantity.find()) {
            if (text.startsWith("g", quantity.end(1)) || text.startsWith("g", quantity.end())) {
                return null;
            }
            BigDecimal first = new BigDecimal(quantity.group(1));
            BigDecimal second = quantity.group(3) == null ? first : new BigDecimal(quantity.group(3));
            bare.add(toMb(first.max(second), "gb"));
        }
        return bare.size() == 1 ? bare.get(0) : null;
    }

    private static BigDecimal toMb(BigDecimal value, String unit) {
        return GB_WORD.matcher(unit).matches() ? value.multiply(BigDecimal.valueOf(MB_PER_GB)) : value;
    }

    // 십·백·천을 쓰는 한자어 수 읽기
    private static long koreanNumber(String text) {
        String digits = "영일이삼사오육칠팔구";
        long total = 0;
        long current = 0;
        for (char letter : text.toCharArray()) {
            int digit = digits.indexOf(letter);
            if (digit > 0) {
                current = digit;
            } else if (letter == '십' || letter == '백' || letter == '천') {
                long place = letter == '십' ? 10 : letter == '백' ? 100 : 1000;
                total += (current == 0 ? 1 : current) * place;
                current = 0;
            }
        }
        return total + current;
    }
}
