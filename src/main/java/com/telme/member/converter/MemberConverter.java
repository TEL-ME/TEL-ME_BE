package com.telme.member.converter;

import com.telme.member.dto.res.LoginResponse;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.entity.User;
import org.springframework.stereotype.Component;

@Component
public class MemberConverter {

    public SignUpResponse toSignUpResponse(User user) {
        return new SignUpResponse(user.getUserId(), user.getEmail());
    }

    public LoginResponse toLoginResponse(User user) {
        return new LoginResponse(user.getUserId(), user.getEmail());
    }
}
