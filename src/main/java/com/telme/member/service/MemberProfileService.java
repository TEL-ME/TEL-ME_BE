package com.telme.member.service;

import com.telme.member.dto.res.MemberMeResponse;
import com.telme.member.entity.SocialAccount;
import com.telme.member.entity.User;
import com.telme.member.repository.SocialAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberProfileService {

    private final CurrentMemberResolver currentMemberResolver;
    private final SocialAccountRepository socialAccountRepository;

    @Transactional(readOnly = true)
    public MemberMeResponse getMe(HttpServletRequest request) {
        return currentMemberResolver.resolveOptional(request)
                .map(this::toResponse)
                .orElseGet(MemberMeResponse::guest);
    }

    private MemberMeResponse toResponse(User user) {
        List<MemberMeResponse.LoginMethod> loginMethods = new ArrayList<>();
        boolean emailLoginEnabled = user.getEmail() != null && user.getPasswordHash() != null;
        if (emailLoginEnabled) {
            loginMethods.add(MemberMeResponse.LoginMethod.EMAIL);
        }
        if (socialAccountRepository.existsByUser_UserIdAndProvider(user.getUserId(), SocialAccount.Provider.KAKAO)) {
            loginMethods.add(MemberMeResponse.LoginMethod.KAKAO);
        }
        if (socialAccountRepository.existsByUser_UserIdAndProvider(user.getUserId(), SocialAccount.Provider.GOOGLE)) {
            loginMethods.add(MemberMeResponse.LoginMethod.GOOGLE);
        }
        return MemberMeResponse.member(user, loginMethods);
    }
}
