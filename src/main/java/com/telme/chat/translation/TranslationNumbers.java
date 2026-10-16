package com.telme.chat.translation;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//번역이 숫자를 바꾸지 않았는지 본다. 같은 값을 여러 번 쓰는 것은 괜찮고, 값이 빠지거나 새로 생기면 다르다고 본다
final class TranslationNumbers {

    // "30만", "1억 5000만", "2억"을 값으로 푼다. 영어 번역은 300,000처럼 풀어 쓰기 때문이다. "3만큼"의 만은 단위가 아니다
    private static final Pattern KOREAN_UNIT = Pattern.compile(
            "(?:(\\d[\\d,]*)\\s*억\\s*)?(\\d[\\d,]*)\\s*만(?!큼)|(\\d[\\d,]*)\\s*억");
    private static final BigInteger TEN_THOUSAND = BigInteger.valueOf(10_000L);
    private static final BigInteger HUNDRED_MILLION = BigInteger.valueOf(100_000_000L);
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    // "3월 5일"을 "March 5"로 옮기면 3이 글자가 된다. 조동사 may와 헷갈리지 않게 대문자로 시작할 때만 본다
    private static final List<String> MONTHS = List.of("January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December");
    private static final Pattern MONTH = Pattern.compile(
            "\\b(January|February|March|April|May|June|July|August|September|October|November|December"
                    + "|Jan|Feb|Mar|Apr|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)\\b");
    
    private TranslationNumbers() {
    }
    
    static boolean same(String korean, String english) {
        return koreanNumbers(korean).equals(englishNumbers(english));
    }
    
    static Set<String> koreanNumbers(String text) {
        Set<String> numbers = new HashSet<>();
        StringBuilder rest = new StringBuilder();
        Matcher unit = KOREAN_UNIT.matcher(text);
        int last = 0;
        while (unit.find()) {
            BigInteger value = unit.group(3) != null
                    ? integer(unit.group(3)).multiply(HUNDRED_MILLION)
                    : integer(unit.group(2)).multiply(TEN_THOUSAND)
                            .add(unit.group(1) == null ? BigInteger.ZERO : integer(unit.group(1)).multiply(HUNDRED_MILLION));
            numbers.add(value.toString());
            rest.append(text, last, unit.start()).append(' ');
            last = unit.end();
        }
        rest.append(text, last, text.length());
        numbers.addAll(plainNumbers(rest));
        return numbers;
    }
        
    static Set<String> englishNumbers(String text) {
        Set<String> numbers = plainNumbers(text);
        Matcher month = MONTH.matcher(text);
        while (month.find()) {
            String name = month.group(1);
            for (int i = 0; i < MONTHS.size(); i++) {
                if (MONTHS.get(i).startsWith(name)) {
                    numbers.add(String.valueOf(i + 1));
                    break;
                }
            }
        }
        return numbers;
    }
    
    // 쉼표와 앞자리 0, 소수점 뒤 0을 지워 "7,700"과 "7700", "1.50"과 "1.5"를 같게 본다
    private static Set<String> plainNumbers(CharSequence text) {
        Set<String> numbers = new HashSet<>();
        Matcher number = NUMBER.matcher(text);
        while (number.find()) {
            numbers.add(new BigDecimal(number.group().replace(",", "")).stripTrailingZeros().toPlainString());
        }
        return numbers;
    }
    
    private static BigInteger integer(String digits) {
        return new BigInteger(digits.replace(",", ""));
    }
}
