package com.taktak.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {
    private static final Pattern CAFE_PATH = Pattern.compile("/cafes/([^/]+)");
    private final AuthTokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("OPTIONS".equals(method) || isPublic(method, path)) return true;

        AuthPrincipal principal = authenticate(request);
        if (principal == null) {
            String header = request.getHeader("Authorization");
            System.out.println("[AUTH] Rejet 401 sur " + method + " " + path + " | Header: " + (header == null ? "NULL" : header.substring(0, Math.min(header.length(), 20)) + "..."));
            return reject(response, 401, "AUTHENTICATION_REQUIRED");
        }
        if (requiresAdmin(method, path) && !principal.isAdmin() && !isOwnTableAssignment(principal, method, path)) {
            System.out.println("[AUTH] Rejet 403 ADMIN sur " + method + " " + path + " pour " + principal);
            return reject(response, 403, "ADMIN_REQUIRED");
        }
        if (!matchesCafeScope(principal, path)) {
            System.out.println("[AUTH] Rejet 403 CAFE SCOPE sur " + method + " " + path + " pour " + principal);
            return reject(response, 403, "CAFE_ACCESS_DENIED");
        }
        AuthContext.set(principal);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private AuthPrincipal authenticate(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        return tokenService.verify(header.substring(7).trim());
    }

    private boolean isPublic(String method, String path) {
        if ("GET".equals(method) && path.equals("/api/health")) return true;
        if ("GET".equals(method) && path.matches("/api/orders/[0-9a-fA-F-]{36}")) return true;
        if ("POST".equals(method) && (path.equals("/api/auth/admin/login") || path.equals("/api/auth/staff/login"))) return true;
        if ("GET".equals(method) && (path.equals("/api/cafes")
                || path.matches("/api/cafes/[^/]+")
                || path.matches("/api/cafes/[^/]+/check-wifi")
                || path.matches("/api/cafes/[^/]+/tables/[0-9]+/status")
                || path.matches("/api/cafes/[^/]+/floor-plans")
                || path.matches("/api/floor-plans/[^/]+/obstacles")
                || path.matches("/api/cafes/[^/]+/menu")
                || path.matches("/api/cafes/[^/]+/rewards/campaign")
                || path.matches("/api/v1/cafes/[^/]+/ambiance/active")
                || path.matches("/api/v1/cafes/[^/]+/waiters/active"))) return true;
        if ("POST".equals(method) && (path.equals("/api/orders")
                || path.matches("/api/cafes/[^/]+/staff-heartbeat")
                || path.matches("/api/cafes/[^/]+/service-calls")
                || path.matches("/api/cafes/[^/]+/rewards/(feedback|coupons/validate)")
                || path.matches("/api/v1/cafes/[^/]+/ambiance/(vote-poll|vote-music|propose-music)"))) return true;
        return false;
    }

    private boolean requiresAdmin(String method, String path) {
        if (path.contains("/analytics")) return true;
        if (path.matches("/api/cafes/[^/]+/location") && "PUT".equals(method)) return true;
        if (path.startsWith("/api/categories") || path.startsWith("/api/products")) return true;
        if ((path.contains("/floor-plans") && !"GET".equals(method))
                || path.matches("/api/cafes/[^/]+/tables/batch")) return true;
        if (path.startsWith("/api/v1/waiters/")) return true;
        if (path.matches("/api/v1/cafes/[^/]+/waiters") && !"GET".equals(method)) return true;
        if (path.matches("/api/v1/cafes/[^/]+/ambiance/(polls|reset-music|music/.+)")) return true;
        if (path.matches("/api/cafes/[^/]+/game-rooms")) return true;
        if (path.matches("/api/cafes/[^/]+/orders/in-progress")) return true;
        if (path.matches("/api/cafes/[^/]+/rewards/campaign") && "PUT".equals(method)) return true;
        if (path.matches("/api/cafes/[^/]+/rewards/coupons/manual")) return true;
        return path.equals("/api/cafes/upload");
    }

    private boolean isOwnTableAssignment(AuthPrincipal principal, String method, String path) {
        if (!"STAFF".equals(principal.role()) || !"POST".equals(method)) return false;
        Matcher matcher = Pattern.compile("/api/v1/waiters/([^/]+)/assign-tables").matcher(path);
        return matcher.matches() && principal.subject().equals(matcher.group(1));
    }

    private boolean matchesCafeScope(AuthPrincipal principal, String path) {
        Matcher matcher = CAFE_PATH.matcher(path);
        return !matcher.find() || principal.canAccessCafe(matcher.group(1));
    }

    private boolean reject(HttpServletResponse response, int status, String code) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", code));
        return false;
    }
}
