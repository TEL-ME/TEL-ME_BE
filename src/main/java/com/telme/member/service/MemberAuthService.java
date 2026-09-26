package com.telme.member.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.member.converter.MemberConverter;
import com.telme.member.dto.req.LoginRequest;
import com.telme.member.dto.req.SignUpRequest;
import com.telme.member.dto.res.LoginResponse;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class MemberAuthService {

    // 계정이 없을 때도 이 해시와 비교해 응답 시간을 맞춘다(계정 존재 여부가 시간차로 드러나지 않게) — 실제 사용자 해시 아님
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$SOUYRlvZh8tfmnADbmsOAeSgXPOdelwf/EX31iKbTPWNtdhvHvw.G";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberConverter memberConverter;
    private final GuestSuccessionService guestSuccessionService;
    private final MemberStatusChecker memberStatusChecker;
    private final LoginCompletionService loginCompletionService;
    private final GuestIdResolver guestIdResolver;
    private final EmailUniqueConstraintChecker emailUniqueConstraintChecker;
    // DB 승계·저장을 커밋까지 끝낸 뒤에만 세션에 로그인 상태를 반영하기 위해 트랜잭션 경계를 직접 다룬다
    private final TransactionTemplate transactionTemplate;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public SignUpResponse signUp(SignUpRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UUID guestId = guestIdResolver.resolve(httpRequest);
        // BCrypt 해싱은 CPU 작업이라 DB 커넥션을 잡기 전에 끝낸다
        String passwordHash = passwordEncoder.encode(request.password());

        // 커밋이 끝나기 전에는 세션에 아무 것도 반영하지 않는다 — 커밋 실패 시 DB는 롤백되는데 세션만 로그인 상태로 남는 것을 막는다
        User saved = transactionTemplate.execute(status -> {
            if (userRepository.findByEmail(request.email()).isPresent()) {
                throw new GeneralException(MemberErrorCode.EMAIL_ALREADY_EXISTS);
            }

            User user = User.builder()
                    .email(request.email())
                    .passwordHash(passwordHash)
                    .build();

            User savedUser;
            try {
                savedUser = userRepository.save(user);
            } catch (DataIntegrityViolationException exception) {
                // findByEmail 통과 직후 동시 INSERT 대비 최종 방어선 — email UNIQUE 제약(users_email_key)인지 확인 후에만 변환
                if (!emailUniqueConstraintChecker.isViolation(exception)) {
                    throw exception;
                }
                throw new GeneralException(MemberErrorCode.EMAIL_ALREADY_EXISTS);
            }

            if (guestId != null) {
                guestSuccessionService.succeedGuest(guestId, savedUser);
            }
            return savedUser;
        });

        loginCompletionService.completeLogin(saved, guestId, httpRequest, httpResponse);
        return memberConverter.toSignUpResponse(saved);
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UUID guestId = guestIdResolver.resolve(httpRequest);

        // 조회·해시 비교는 DB 쓰기가 없어 트랜잭션이 필요 없다 — BCrypt 비교로 커넥션을 오래 잡지 않는다
        Optional<User> candidate = userRepository.findByEmail(request.email());
        boolean passwordMatches = passwordEncoder.matches(
                request.password(), candidate.map(User::getPasswordHash).orElse(DUMMY_PASSWORD_HASH));
        User found = candidate.filter(u -> passwordMatches)
                .orElseThrow(() -> new GeneralException(MemberErrorCode.INVALID_CREDENTIALS));

        // 비밀번호 검증 이후에만 상태를 본다 — 상태 코드로는 계정 존재 여부가 노출되지 않는다(응답 시간은 위에서 별도로 맞춤)
        memberStatusChecker.checkActive(found);

        if (guestId != null) {
            // DB 쓰기(게스트 승계)가 있을 때만 트랜잭션을 연다 — 커밋 성공 후에만 세션 반영은 그대로 유지
            transactionTemplate.executeWithoutResult(status -> guestSuccessionService.succeedGuest(guestId, found));
        }

        loginCompletionService.completeLogin(found, guestId, httpRequest, httpResponse);
        return memberConverter.toLoginResponse(found);
    }

    // 세션 무효화 + SecurityContext 초기화 — 다음 요청은 GuestIdentityFilter가 새 게스트로 발급
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logoutHandler.logout(httpRequest, httpResponse, authentication);
    }

}
