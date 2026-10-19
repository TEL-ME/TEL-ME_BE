package com.telme.consult.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 월 통화(분)·문자(건) 사용량 답을 읽는다. 값이 분명하지 않으면 읽지 못한 것으로 두어 되묻게 한다
public final class CallTextReplyParser {

    public enum Kind {
        AMOUNT,
        NONE,
        UNLIMITED,
        UNSURE,
        UNREADABLE
    }

    public record Result(Kind kind, long value, String unit) {
        private static Result of(Kind kind, Channel channel) {
            return new Result(kind, 0, channel.unit);
        }

        public boolean readable() {
            return kind != Kind.UNREADABLE;
        }

        // 조건 값으로 저장하는 글자. 다시 읽어도 같은 결과가 나온다
        public String canonical() {
            return switch (kind) {
                case AMOUNT -> value + unit;
                case NONE -> "거의 없음";
                case UNLIMITED -> "무제한";
                case UNSURE -> "모름";
                case UNREADABLE -> throw new IllegalStateException("읽지 못한 답은 조건 값이 될 수 없습니다.");
            };
        }
    }

    public static final String VOICE_KEY = "monthly_voice_usage";
    public static final String SMS_KEY = "monthly_sms_usage";

    private enum Channel {
        VOICE("분", "시간|분", "건|개|통", Map.of("시간", 60L, "분", 1L)),
        SMS("건", "건|개|통", "시간|분", Map.of("건", 1L, "개", 1L, "통", 1L));

        private final String unit;
        private final Pattern amount;
        private final Pattern otherUnit;
        private final Map<String, Long> factors;

        Channel(String unit, String units, String otherUnits, Map<String, Long> factors) {
            this.unit = unit;
            this.amount = Pattern.compile("(" + NUMBER + ")\\s*(" + units + ")");
            this.otherUnit = Pattern.compile("\\d\\s*(?:" + otherUnits + ")");
            this.factors = factors;
        }
    }

    private static final String NUMBER = "\\d+(?:\\.\\d+)?";
    private static final long MAX_VALUE = 100_000;
    private static final int DAYS_PER_MONTH = 30;
    private static final Pattern NUMBER_TOKEN = Pattern.compile(NUMBER);
    private static final Pattern OTHER_QUANTITY = Pattern.compile(
            "\\d\\s*(?:명|대|살|세|원|%|회선|년|개월|기가|gb|메가|mb|퍼센트|일|인(?![가데지듯]))");
    private static final Pattern NONE = Pattern.compile("거의\\s*(?:안|없)|별로\\s*안|전혀|하나도|조금|안\\s*(?:해|써|쓰|보내|하)");

    private CallTextReplyParser() {}

    public static Result parseVoice(String reply) {
        return parse(reply, Channel.VOICE);
    }

    public static Result parseSms(String reply) {
        return parse(reply, Channel.SMS);
    }

    private static Result parse(String reply, Channel channel) {
        if (reply == null || reply.isBlank()) {
            return Result.of(Kind.UNREADABLE, channel);
        }
        String text = reply.strip().toLowerCase(Locale.ROOT).replaceAll("(?<=\\d),(?=\\d{3})", "");
        boolean daily = DataUsageReplyParser.DAILY.matcher(text).find();
        if (daily && DataUsageReplyParser.MONTHLY.matcher(text).find()) {
            return Result.of(Kind.UNREADABLE, channel);
        }
        BigDecimal amount = explicitAmount(text, channel);
        if (amount == null && !channel.otherUnit.matcher(text).find()) {
            amount = bareAmount(text);
        }
        if (amount != null) {
            return amount(amount, daily, channel);
        }
        if (DataUsageReplyParser.UNLIMITED.matcher(text).find() && !DataUsageReplyParser.NEGATION.matcher(text).find()) {
            return Result.of(Kind.UNLIMITED, channel);
        }
        if (NONE.matcher(text).find()) {
            return Result.of(Kind.NONE, channel);
        }
        return Result.of(DataUsageReplyParser.UNSURE.matcher(text).find() ? Kind.UNSURE : Kind.UNREADABLE, channel);
    }

    private static Result amount(BigDecimal raw, boolean daily, Channel channel) {
        BigDecimal value = (daily ? raw.multiply(BigDecimal.valueOf(DAYS_PER_MONTH)) : raw).setScale(0, RoundingMode.HALF_UP);
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(MAX_VALUE)) > 0) {
            return Result.of(Kind.UNREADABLE, channel);
        }
        return value.signum() == 0 ? Result.of(Kind.NONE, channel) : new Result(Kind.AMOUNT, value.longValueExact(), channel.unit);
    }

    // 단위가 붙은 숫자를 합친다. 같은 단위에 값이 둘이면 어느 쪽인지 알 수 없어 읽지 않는다
    private static BigDecimal explicitAmount(String text, Channel channel) {
        Map<String, Set<BigDecimal>> byUnit = new HashMap<>();
        Matcher found = channel.amount.matcher(text);
        while (found.find()) {
            byUnit.computeIfAbsent(found.group(2), unit -> new HashSet<>()).add(new BigDecimal(found.group(1)));
        }
        if (byUnit.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Set<BigDecimal>> entry : byUnit.entrySet()) {
            if (entry.getValue().size() > 1) {
                return BigDecimal.valueOf(MAX_VALUE + 1);
            }
            total = total.add(entry.getValue().iterator().next().multiply(BigDecimal.valueOf(channel.factors.get(entry.getKey()))));
        }
        return total;
    }

    // 단위 없는 숫자는 숫자가 하나뿐이고 다른 양을 말한 것이 아닐 때만 묻는 단위로 읽는다
    private static BigDecimal bareAmount(String text) {
        if (OTHER_QUANTITY.matcher(text).find()) {
            return null;
        }
        Matcher token = NUMBER_TOKEN.matcher(text);
        if (!token.find()) {
            return null;
        }
        BigDecimal number = new BigDecimal(token.group());
        return token.find() ? null : number;
    }
}
