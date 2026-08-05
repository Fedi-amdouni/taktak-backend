package com.taktak.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class AuthInterceptorTest {
    private AuthTokenService tokens;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper();
        tokens = new AuthTokenService(mapper, "test-secret-with-enough-entropy", 12);
        interceptor = new AuthInterceptor(tokens, mapper);
    }

    @Test
    void publicMenuDoesNotRequireAuthentication() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/cafes/monastir-lounge/menu");
        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
    }

    @Test
    void protectedAnalyticsRejectsAnonymousRequests() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(
                request("GET", "/api/cafes/monastir-lounge/analytics"), response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void staffCannotAccessAnotherCafeOrAdminOperations() throws Exception {
        String token = tokens.issue("waiter-1", "STAFF", "monastir-lounge");

        MockHttpServletResponse otherCafeResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(authenticated("GET", "/api/cafes/carthage-lounge/orders", token),
                otherCafeResponse, new Object()));
        assertEquals(403, otherCafeResponse.getStatus());

        MockHttpServletResponse adminResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(authenticated("POST", "/api/products", token),
                adminResponse, new Object()));
        assertEquals(403, adminResponse.getStatus());
    }

    @Test
    void adminCanAccessProtectedOperations() throws Exception {
        String token = tokens.issue("owner", "ADMIN", null);
        assertTrue(interceptor.preHandle(authenticated("POST", "/api/products", token),
                new MockHttpServletResponse(), new Object()));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = tokens.issue("owner", "ADMIN", null) + "x";
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(authenticated("GET", "/api/cafes/monastir-lounge/orders", token),
                response, new Object()));
        assertEquals(401, response.getStatus());
    }

    private MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }

    private MockHttpServletRequest authenticated(String method, String path, String token) {
        MockHttpServletRequest request = request(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
