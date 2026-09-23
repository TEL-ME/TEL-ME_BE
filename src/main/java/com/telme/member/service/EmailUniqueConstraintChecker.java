package com.telme.member.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class EmailUniqueConstraintChecker {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_key";

    public boolean isViolation(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException constraintViolation
                && EMAIL_UNIQUE_CONSTRAINT.equals(constraintViolation.getConstraintName());
    }
}
