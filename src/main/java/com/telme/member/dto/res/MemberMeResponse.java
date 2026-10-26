package com.telme.member.dto.res;

import com.telme.member.entity.User;
import java.util.List;

public record MemberMeResponse(
        boolean authenticated,
        Long userId,
        String email,
        String name,
        Role role,
        List<LoginMethod> loginMethods
) {
    public enum Role { GUEST, USER, ADMIN }
    public enum LoginMethod { EMAIL, KAKAO, GOOGLE }

    public static MemberMeResponse guest() {
        return new MemberMeResponse(false, null, null, null, Role.GUEST, List.of());
    }

    public static MemberMeResponse member(User user, List<LoginMethod> loginMethods) {
        return new MemberMeResponse(
                true,
                user.getUserId(),
                user.getEmail(),
                user.getName(),
                Role.valueOf(user.getRole().name()),
                List.copyOf(loginMethods));
    }
}
