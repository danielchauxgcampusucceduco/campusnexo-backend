package edu.campusnexo.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * The caller's address as the last trusted proxy saw it. Each proxy appends the address it received the request
 * from to {@code X-Forwarded-For}, so with {@code trustedProxies} proxies in front (Render: 1) the caller is
 * that many entries from the right. The leftmost entry is whatever the caller wrote and is never trusted.
 */
final class ClientAddress {
    static final String FORWARDED_FOR = "X-Forwarded-For";

    private ClientAddress() {
    }

    static String of(HttpServletRequest request, int trustedProxies) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (trustedProxies <= 0 || forwarded == null || forwarded.isBlank()) {
            return request.getRemoteAddr();
        }
        String[] hops = forwarded.split(",");
        return hops[Math.max(0, hops.length - trustedProxies)].trim();
    }
}
