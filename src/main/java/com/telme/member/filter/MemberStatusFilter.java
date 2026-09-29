package com.telme.member.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.member.entity.User;
import com.telme.member.exception.MemberErrorCode;
import com.telme.member.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// 로그인할 때 담은 권한은 세션에 그대로 남아, 그 뒤 계정이 정지되거나 권한이 바뀌어도 반영되지 않는다.
// 요청마다 회원을 다시 읽어 상태를 확인하고 권한을 현재 값으로 세운다
@Component
@RequiredArgsConstructor
public class MemberStatusFilter extends OncePerRequestFilter {

    // 정지된 회원도 로그아웃은 할 수 있어야 세션을 버릴 수 있다
    private static final RequestMatcher SKIP_PATHS = new OrRequestMatcher(
            PathPatternRequestMatcher.withDefaults().matcher("/api/auth/**"),
            PathPatternRequestMatcher.withDefaults().matcher("/api/v1/auth/**")
    );

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("OPTIONS") || SKIP_PATHS.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Long userId = loggedInUserId();
        if (userId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<User> found = userRepository.findById(userId);
        // 회원이 사라졌으면 남은 세션을 더 믿을 수 없다
        if (found.isEmpty()) {
            reject(response, MemberErrorCode.UNAUTHENTICATED);
            return;
        }

        User user = found.get();
        MemberErrorCode blocked = blockedReason(user);
        if (blocked != null) {
            reject(response, blocked);
            return;
        }

        refreshAuthorities(userId, user);
        filterChain.doFilter(request, response);
    }

    // 로그인 성공 시 principal에 userId를 넣는다. 테스트의 @WithMockUser처럼 다른 값이면 건너뛴다
    private Long loggedInUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return authentication.getPrincipal() instanceof Long userId ? userId : null;
    }

    private MemberErrorCode blockedReason(User user) {
        return switch (user.getStatus()) {
            case ACTIVE -> null;
            case SUSPENDED -> MemberErrorCode.ACCOUNT_SUSPENDED;
            case WITHDRAWN -> MemberErrorCode.ACCOUNT_WITHDRAWN;
        };
    }

    // 로그인 이후 role이 바뀌었을 수 있어 현재 값으로 다시 세운다. 판정은 스프링이 한다
    private void refreshAuthorities(Long userId, User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    // 필터 단계라 GlobalExceptionHandler가 못 잡는다. 다른 API 오류와 같은 형식으로 직접 쓴다
    private void reject(HttpServletResponse response, MemberErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), errorCode.getErrorResponse());
    }
}
