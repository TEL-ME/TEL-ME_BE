package com.telme.member.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.dto.req.EmailLoginMethodRequest;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class EmailLoginMethodService {

    private final CurrentMemberResolver currentMemberResolver;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailUniqueConstraintChecker emailUniqueConstraintChecker;
    private final TransactionTemplate transactionTemplate;

    public SignUpResponse addEmailLogin(EmailLoginMethodRequest request, HttpServletRequest httpRequest) {
        User currentUser = currentMemberResolver.resolve(httpRequest);
        if (currentUser.getEmail() != null) {
            throw new GeneralException(MemberErrorCode.EMAIL_LOGIN_ALREADY_SET);
        }
        // BCrypt 해싱은 CPU 작업이라 DB 커넥션을 잡기 전에 끝낸다
        String passwordHash = passwordEncoder.encode(request.password());

        transactionTemplate.executeWithoutResult(status -> {
            if (userRepository.findByEmail(request.email()).isPresent()) {
                throw new GeneralException(MemberErrorCode.EMAIL_ALREADY_EXISTS);
            }

            int updated;
            try {
                updated = userRepository.addEmailLogin(currentUser.getUserId(), request.email(), passwordHash);
            } catch (DataIntegrityViolationException exception) {
                // findByEmail 통과 직후 동시 등록 대비 최종 방어선
                if (!emailUniqueConstraintChecker.isViolation(exception)) {
                    throw exception;
                }
                throw new GeneralException(MemberErrorCode.EMAIL_ALREADY_EXISTS);
            }
            if (updated == 0) {
                // 같은 회원이 이 요청을 동시에 두 번 보낸 레이스의 패자
                throw new GeneralException(MemberErrorCode.EMAIL_LOGIN_ALREADY_SET);
            }
        });

        return new SignUpResponse(currentUser.getUserId(), request.email());
    }
}
