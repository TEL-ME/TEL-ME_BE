package com.telme.faq.service;

import java.util.List;

public interface FaqReranker {

    // documents 순서대로 0~1 관련도(시그모이드)를 반환한다
    double[] score(String query, List<String> documents);
}
