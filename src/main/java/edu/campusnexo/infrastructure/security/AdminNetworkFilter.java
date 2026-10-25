package edu.campusnexo.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The administration routes ({@code /api/v1/admin/**}) answer only requests from the administration network:
 * the administrators connect to the university's VPN in AWS and leave it through its fixed public address. It
 * runs after authentication, so the caller already is an ADMIN; this adds where the request comes from. With no
 * network configured (a laptop) every address is accepted.
 */
final class AdminNetworkFilter extends OncePerRequestFilter {
    private static final Logger audit = LoggerFactory.getLogger("campusnexo.audit");
    static final String ADMIN_PATH = "/api/v1/admin/";

    private final List<Cidr> allowed;
    private final int trustedProxies;

    AdminNetworkFilter(String networks, int trustedProxies) {
        this.allowed = networks == null ? List.of() : Arrays.stream(networks.split(","))
                .map(String::trim).filter(cidr -> !cidr.isEmpty()).map(Cidr::parse).toList();
        this.trustedProxies = trustedProxies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return allowed.isEmpty() || !request.getRequestURI().substring(request.getContextPath().length())
                .startsWith(ADMIN_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = ClientAddress.of(request, trustedProxies);
        if (allowed.stream().noneMatch(cidr -> cidr.contains(client))) {
            audit.warn("AUDIT denegado motivo=red_admin metodo={} ruta={} ip={}", request.getMethod(),
                    request.getRequestURI(), client);
            AuthenticationFilter.ProblemResponses.write(response, 403, "Fuera de la red de administración",
                    "La administración solo se atiende desde la VPN de la universidad. Dirección vista: " + client + ".",
                    "admin_network_required");
            return;
        }
        chain.doFilter(request, response);
    }

    /** An IPv4 or IPv6 network in CIDR notation ({@code 203.0.113.7/32}). */
    record Cidr(byte[] network, int prefix) {
        static Cidr parse(String text) {
            String[] parts = text.split("/", 2);
            byte[] address = literal(parts[0]);
            if (address == null) {
                throw new IllegalStateException("Red de administración no válida: " + text);
            }
            int bits = address.length * 8;
            int prefix = parts.length == 1 ? bits : Integer.parseInt(parts[1]);
            if (prefix < 0 || prefix > bits) {
                throw new IllegalStateException("Prefijo no válido en la red de administración: " + text);
            }
            return new Cidr(address, prefix);
        }

        boolean contains(String address) {
            byte[] candidate = literal(address);
            if (candidate == null || candidate.length != network.length) {
                return false;
            }
            int shift = network.length * 8 - prefix;
            return new BigInteger(1, candidate).shiftRight(shift).equals(new BigInteger(1, network).shiftRight(shift));
        }

        /** Bytes of an address literal, or {@code null} if it is not one; never resolves a host name. */
        private static byte[] literal(String text) {
            String value = text.trim();
            if (value.isEmpty() || !value.matches("[0-9A-Fa-f:.]+")) {
                return null;
            }
            try {
                return InetAddress.getByName(value).getAddress();
            } catch (UnknownHostException notAnAddress) {
                return null;
            }
        }
    }
}
