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
    void qrCapabilityTransferEndpointAcceptsAnonymousPostButNotPatch() throws Exception {
        String path = "/api/cafes/monastir-lounge/orders/00000000-0000-0000-0000-000000000001/transfer-table";

        assertTrue(interceptor.preHandle(
                request("POST", path),
                new MockHttpServletResponse(),
                new Object()
        ));

        MockHttpServletResponse patchResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(request("PATCH", path), patchResponse, new Object()));
        assertEquals(401, patchResponse.getStatus());
    }

    @Test
    void protectedAnalyticsRejectsAnonymousRequests() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(
                request("GET", "/api/cafes/monastir-lounge/analytics"), response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void tableTokensAreOnlyAvailableToAuthenticatedCafeStaff() throws Exception {
        MockHttpServletResponse anonymousResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(
                request("GET", "/api/cafes/monastir-lounge/tables"), anonymousResponse, new Object()));
        assertEquals(401, anonymousResponse.getStatus());

        String token = tokens.issue("waiter-1", "STAFF", "monastir-lounge");
        assertTrue(interceptor.preHandle(
                authenticated("GET", "/api/cafes/monastir-lounge/tables", token),
                new MockHttpServletResponse(), new Object()));
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
    void staffCanReadTheirCafesFloorPlanButCannotModifyIt() throws Exception {
        String token = tokens.issue("waiter-1", "STAFF", "monastir-lounge");

        assertTrue(interceptor.preHandle(
                authenticated("GET", "/api/cafes/monastir-lounge/floor-plans", token),
                new MockHttpServletResponse(), new Object()));
        assertTrue(interceptor.preHandle(
                authenticated("GET", "/api/floor-plans/plan-1/obstacles", token),
                new MockHttpServletResponse(), new Object()));

        MockHttpServletResponse mutationResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(
                authenticated("POST", "/api/cafes/monastir-lounge/floor-plans", token),
                mutationResponse, new Object()));
        assertEquals(403, mutationResponse.getStatus());
    }

    @Test
    void staffCanAssignOnlyTheirOwnTables() throws Exception {
        String token = tokens.issue("waiter-1", "STAFF", "monastir-lounge");

        assertTrue(interceptor.preHandle(
                authenticated("POST", "/api/v1/waiters/waiter-1/assign-tables", token),
                new MockHttpServletResponse(), new Object()));

        MockHttpServletResponse otherWaiterResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(
                authenticated("POST", "/api/v1/waiters/waiter-2/assign-tables", token),
                otherWaiterResponse, new Object()));
        assertEquals(403, otherWaiterResponse.getStatus());
    }

    @Test
    void adminCanAccessProtectedOperations() throws Exception {
        String token = tokens.issue("owner", "ADMIN", null);
        assertTrue(interceptor.preHandle(authenticated("POST", "/api/products", token),
                new MockHttpServletResponse(), new Object()));
    }

    @Test
    void ownerCanOnlyAccessCafesIncludedInTheirSession() throws Exception {
        String token = tokens.issueForCafes("wael", "ADMIN", java.util.List.of("monastir-lounge", "carthage-lounge"));

        assertTrue(interceptor.preHandle(authenticated("GET", "/api/cafes/monastir-lounge/orders", token),
                new MockHttpServletResponse(), new Object()));

        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(authenticated("GET", "/api/cafes/sousse-palm-beach/orders", token),
                response, new Object()));
        assertEquals(403, response.getStatus());
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
