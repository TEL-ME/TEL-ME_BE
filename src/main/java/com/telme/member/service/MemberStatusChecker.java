package com.telme.member.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import org.springframework.stereotype.Component;

@Component
public class MemberStatusChecker {

    public void checkActive(User user) {
        if (user.getStatus() == User.Status.SUSPENDED) {
            throw new GeneralException(MemberErrorCode.ACCOUNT_SUSPENDED);
        }
        if (user.getStatus() == User.Status.WITHDRAWN) {
            throw new GeneralException(MemberErrorCode.ACCOUNT_WITHDRAWN);
        }
    }
}
