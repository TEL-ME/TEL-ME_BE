package com.telme.faq.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

// NOT NULL·외래키 위반도 같은 예외로 오므로, 중복 인덱스를 어긴 경우만 가려낸다
@Component
public class FaqContentConstraintChecker {

    private static final String CONTENT_UNIQUE_CONSTRAINT = "uk_faqs_content_active";

    public boolean isViolation(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException constraintViolation
                && CONTENT_UNIQUE_CONSTRAINT.equals(constraintViolation.getConstraintName());
    }
}
