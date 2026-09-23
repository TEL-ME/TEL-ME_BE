package com.telme.member.service;

import com.telme.chat.repository.ChatSessionRepository;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.feedback.repository.FeedbackStore;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.converter.MemberConverter;
import com.telme.member.dto.req.LoginRequest;
import com.telme.member.dto.req.SignUpRequest;
import com.telme.member.dto.res.LoginResponse;
import com.telme.member.dto.res.SignUpResponse;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.GuestRepository;
import com.telme.member.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class MemberAuthService {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "users_email_key";
    // 계정이 없을 때도 이 해시와 비교해 응답 시간을 맞춘다(계정 존재 여부가 시간차로 드러나지 않게) — 실제 사용자 해시 아님
    private static final String DUMMY_PASSWORD_HASH = "$2a$10$SOUYRlvZh8tfmnADbmsOAeSgXPOdelwf/EX31iKbTPWNtdhvHvw.G";

    private final UserRepository userRepository;
    private final GuestRepository guestRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberConverter memberConverter;
    private final SecurityContextRepository securityContextRepository;
    private final Clock clock;
    // FeedbackStore는 telme.feedback.enabled가 꺼져 있으면 빈이 등록되지 않으므로 ObjectProvider로 선택 주입한다
    private final ObjectProvider<FeedbackStore> feedbackStoreProvider;
    // DB 승계·저장을 커밋까지 끝낸 뒤에만 세션에 로그인 상태를 반영하기 위해 트랜잭션 경계를 직접 다룬다
    private final TransactionTemplate transactionTemplate;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public SignUpResponse signUp(SignUpRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UUID guestId = readGuestId(httpRequest);
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
                if (!isEmailUniqueViolation(exception)) {
                    throw exception;
                }
                throw new GeneralException(MemberErrorCode.EMAIL_ALREADY_EXISTS);
            }

            if (guestId != null) {
                succeedGuest(guestId, savedUser);
            }
            return savedUser;
        });

        completeSessionLogin(saved, guestId, httpRequest, httpResponse);
        return memberConverter.toSignUpResponse(saved);
    }

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        UUID guestId = readGuestId(httpRequest);

        // 조회·해시 비교는 DB 쓰기가 없어 트랜잭션이 필요 없다 — BCrypt 비교로 커넥션을 오래 잡지 않는다
        Optional<User> candidate = userRepository.findByEmail(request.email());
        boolean passwordMatches = passwordEncoder.matches(
                request.password(), candidate.map(User::getPasswordHash).orElse(DUMMY_PASSWORD_HASH));
        User found = candidate.filter(u -> passwordMatches)
                .orElseThrow(() -> new GeneralException(MemberErrorCode.INVALID_CREDENTIALS));

        // 비밀번호 검증 이후에만 상태를 본다 — 상태 코드로는 계정 존재 여부가 노출되지 않는다(응답 시간은 위에서 별도로 맞춤)
        if (found.getStatus() == User.Status.SUSPENDED) {
            throw new GeneralException(MemberErrorCode.ACCOUNT_SUSPENDED);
        }
        if (found.getStatus() == User.Status.WITHDRAWN) {
            throw new GeneralException(MemberErrorCode.ACCOUNT_WITHDRAWN);
        }

        if (guestId != null) {
            // DB 쓰기(게스트 승계)가 있을 때만 트랜잭션을 연다 — 커밋 성공 후에만 세션 반영은 그대로 유지
            transactionTemplate.executeWithoutResult(status -> succeedGuest(guestId, found));
        }

        completeSessionLogin(found, guestId, httpRequest, httpResponse);
        return memberConverter.toLoginResponse(found);
    }

    // DB 커밋 후에만 호출 — 세션ID 재발급 -> SecurityContext 저장 -> userId 설정, 가입/로그인 공통
    private void completeSessionLogin(
            User user, UUID guestId, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        HttpSession session = httpRequest.getSession();
        if (guestId != null) {
            // 승계 처리는 1회로 끝나야 한다 — 지우지 않으면 같은 세션에서 재로그인 시 이미 승계된 게스트를 다시 읽어 재승계를 시도한다
            session.removeAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE);
        }

        // 세션 고정 공격 방지 — invalidate 후 재생성 대신 속성을 유지한 채 ID만 바꾼다
        httpRequest.changeSessionId();

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUserId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // SecurityContextHolderFilter는 로드만 하고 저장은 안 하므로 명시적으로 저장해야 다음 요청에서도 인증이 유지된다
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, user.getUserId());
    }

    // 세션 무효화 + SecurityContext 초기화 — 다음 요청은 GuestIdentityFilter가 새 게스트로 발급
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        logoutHandler.logout(httpRequest, httpResponse, authentication);
    }

    private void succeedGuest(UUID guestId, User user) {
        // merged_user_id가 비어있는 행만 원자적 갱신 — 동시 승계 레이스에서 하나만 통과시켜 채팅·피드백도 그 요청만 이어감
        int updated = guestRepository.succeedGuest(guestId, user, clock.instant());
        if (updated > 0) {
            chatSessionRepository.succeedGuestSessions(guestId, user.getUserId());
            feedbackStoreProvider.ifAvailable(store -> store.succeedGuestFeedback(guestId, user.getUserId()));
        }
    }

    private boolean isEmailUniqueViolation(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException constraintViolation
                && EMAIL_UNIQUE_CONSTRAINT.equals(constraintViolation.getConstraintName());
    }

    // getSession(false) — 실패한 로그인·중복 가입까지 세션이 없는 요청마다 빈 세션을 새로 만들지 않도록 조회만 한다
    private UUID readGuestId(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE);
        return value instanceof UUID uuid ? uuid : null;
    }
}
