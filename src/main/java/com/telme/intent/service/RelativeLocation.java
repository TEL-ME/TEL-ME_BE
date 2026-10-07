package com.telme.intent.service;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// "현재 위치", "근처", "여기"처럼 기준점만 가리키는 말은 지역명이 아니다. location으로 받으면 GPS 대신 카카오 지명 검색으로 간다
final class RelativeLocation {

    private static final Pattern TRAILING_WORDS = Pattern.compile("(근처|주변|부근|쪽|에서|에|으로|로|기준)$");

    private static final Pattern MENTION = Pattern.compile(
            "(현재|지금|내|제)\\s*위치|현위치|여기|이곳|근처|주변|가까운\\s*(곳|데|매장)|우리\\s*동네|(지금|현재)\\s*있는\\s*곳");

    private static final Pattern PLACE_BEFORE = Pattern.compile(
            "(?<![가-힣A-Za-z0-9])([가-힣A-Za-z0-9]{1,14}?)(?:에서|에)?\\s*(?:근처|주변|부근|쪽|가까운)");

    private static final Set<String> BASES = Set.of(
            "", "위치", "현재위치", "현위치", "지금위치", "내위치", "나의위치", "제위치", "현재", "지금", "내", "제",
            "여기", "이곳", "이", "그", "거기", "저기", "가까운곳", "가까운데", "가까운매장", "지금있는곳", "현재있는곳",
            "내가있는곳", "우리동네", "동네", "집", "우리집", "회사", "gps");

    private RelativeLocation() {
    }

    static boolean isOnlyRelative(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.replaceAll("\\s+", "").toLowerCase();
        String previous;
        do {
            previous = normalized;
            normalized = TRAILING_WORDS.matcher(normalized).replaceFirst("");
        } while (!normalized.equals(previous));
        return BASES.contains(normalized);
    }

    static boolean mentions(String text) {
        return text != null && MENTION.matcher(text).find();
    }

    // "판교 근처요"처럼 기준점 표현 바로 앞에 실제 지명이 있으면 그 지명을, 없으면 null을 돌려준다
    static String placeBefore(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = PLACE_BEFORE.matcher(text);
        while (matcher.find()) {
            String place = matcher.group(1);
            if (!isOnlyRelative(place)) {
                return place;
            }
        }
        return null;
    }
}
