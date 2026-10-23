package com.telme.store.controller;

import com.telme.store.config.StoreOriginWalkProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

@Component
public class StoreRouteClientIpResolver {

    private static final int MAX_FORWARDED_LENGTH = 4096;
    private final List<IpAddressMatcher> trustedProxies;

    public StoreRouteClientIpResolver(StoreOriginWalkProperties properties) {
        trustedProxies = properties.trustedProxies().stream().map(IpAddressMatcher::new).toList();
    }

    public String resolve(HttpServletRequest request) {
        // framework 전략의 ForwardedHeaderFilter는 IP를 바꾸고 전달 헤더를 감춘다.
        // 가공된 값 대신 원본 소켓 IP와 헤더로 신뢰 프록시를 판별한다.
        while (request instanceof HttpServletRequestWrapper wrapper
                && wrapper.getRequest() instanceof HttpServletRequest original) {
            request = original;
        }
        String peer = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (!isTrusted(peer) || forwarded == null || forwarded.length() > MAX_FORWARDED_LENGTH) {
            return peer;
        }
        // 오른쪽부터 신뢰하는 프록시만 건너뛴다. 접속자가 임의로 앞에 넣은 주소는 신뢰하지 않는다.
        String[] addresses = forwarded.split(",", -1);
        String client = peer;
        for (int i = addresses.length - 1; i >= 0 && isTrusted(client); i--) {
            String candidate = addresses[i].trim();
            // 호스트 이름은 DNS로 해석하지 않는다. 잘못된 헤더는 소켓의 실제 접속 IP로 묶는다.
            if (!candidate.matches("[0-9a-fA-F:.]+")
                    || (!candidate.contains(":") && !candidate.matches("[0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+"))) {
                return peer;
            }
            try {
                client = InetAddress.getByName(candidate).getHostAddress();
            } catch (UnknownHostException | IllegalArgumentException e) {
                return peer;
            }
        }
        return client;
    }

    private boolean isTrusted(String address) {
        return trustedProxies.stream().anyMatch(proxy -> proxy.matches(address));
    }
}
