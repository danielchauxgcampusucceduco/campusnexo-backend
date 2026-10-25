package edu.campusnexo.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminNetworkFilterTest {
    private static MockHttpServletRequest request(String path, String remote, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRemoteAddr(remote);
        if (forwardedFor != null) {
            request.addHeader(ClientAddress.FORWARDED_FOR, forwardedFor);
        }
        return request;
    }

    private static int status(AdminNetworkFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    @DisplayName("Detrás de un proxy, el cliente es la entrada que agregó el proxy, no la que escribió el cliente")
    void clientIsTheEntryTheProxyAppended() {
        assertEquals("10.0.0.5", ClientAddress.of(request("/x", "10.0.0.5", "203.0.113.7"), 0));
        assertEquals("203.0.113.7", ClientAddress.of(request("/x", "10.0.0.5", "203.0.113.7"), 1));
        assertEquals("198.51.100.9", ClientAddress.of(request("/x", "10.0.0.5", "203.0.113.7, 198.51.100.9"), 1),
                "la entrada más a la izquierda la escribe el cliente y no se cree");
        assertEquals("10.0.0.5", ClientAddress.of(request("/x", "10.0.0.5", null), 1));
    }

    @Test
    @DisplayName("Las rutas de administración solo responden desde la red de la VPN")
    void adminRoutesOnlyFromTheVpn() throws Exception {
        AdminNetworkFilter filter = new AdminNetworkFilter("203.0.113.7/32, 2001:db8::/32", 1);
        assertEquals(200, status(filter, request("/api/v1/admin/personas", "10.0.0.5", "203.0.113.7")));
        assertEquals(200, status(filter, request("/api/v1/admin/personas", "10.0.0.5", "2001:db8::1")));
        assertEquals(403, status(filter, request("/api/v1/admin/personas", "10.0.0.5", "198.51.100.9")));
        assertEquals(403, status(filter, request("/api/v1/admin/personas", "10.0.0.5", "203.0.113.7, 198.51.100.9")),
                "un X-Forwarded-For falsificado no abre la administración");
        assertEquals(200, status(filter, request("/api/v1/twin/spaces", "10.0.0.5", "198.51.100.9")),
                "el resto de la API no depende de la red");
    }

    @Test
    @DisplayName("Sin redes configuradas (un portátil) no restringe; una red mal escrita impide arrancar")
    void openWithoutNetworksAndStrictOnTypos() throws Exception {
        assertEquals(200, status(new AdminNetworkFilter("", 0), request("/api/v1/admin/personas", "198.51.100.9", null)));
        assertThrows(IllegalStateException.class, () -> new AdminNetworkFilter("vpn.campus.edu/32", 0));
        assertThrows(IllegalStateException.class, () -> new AdminNetworkFilter("203.0.113.7/40", 0));
        AdminNetworkFilter.Cidr network = AdminNetworkFilter.Cidr.parse("203.0.113.0/24");
        assertTrue(network.contains("203.0.113.200"));
        assertFalse(network.contains("203.0.114.1"));
        assertFalse(network.contains("no-es-una-ip"));
    }
}
