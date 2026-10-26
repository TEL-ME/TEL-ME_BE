package com.telme.faq.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

// content_hash = SHA-256(question + answer), 구분자 없음, 소문자 hex
// Python 쪽 scripts/check_eval_questions.py의 content_hash()와 같은 규칙
public final class FaqContentHash {

    private FaqContentHash() {
    }

    public static String of(String question, String answer) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((question + answer).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 지원하지 않는 JVM입니다.", e);
        }
    }
}
