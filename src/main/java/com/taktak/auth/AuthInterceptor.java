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
        if (principal == null) return reject(response, 401, "AUTHENTICATION_REQUIRED");
        if (requiresAdmin(method, path) && !principal.isAdmin()) {
            return reject(response, 403, "ADMIN_REQUIRED");
        }
        if (!principal.isAdmin() && !matchesCafeScope(principal, path)) {
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
        if (path.startsWith("/api/auth/")) return true;
        if ("GET".equals(method) && (path.equals("/api/cafes")
                || path.matches("/api/cafes/[^/]+")
                || path.matches("/api/cafes/[^/]+/menu")
                || path.matches("/api/v1/cafes/[^/]+/ambiance/active"))) return true;
        if ("POST".equals(method) && (path.equals("/api/orders")
                || path.matches("/api/cafes/[^/]+/service-calls")
                || path.matches("/api/v1/cafes/[^/]+/ambiance/(vote-poll|vote-music|propose-music)"))) return true;
        return false;
    }

    private boolean requiresAdmin(String method, String path) {
        if (path.contains("/analytics")) return true;
        if (path.startsWith("/api/categories") || path.startsWith("/api/products")) return true;
        if (path.contains("/floor-plans") || path.matches("/api/cafes/[^/]+/tables/batch")) return true;
        if (path.startsWith("/api/v1/waiters/")) return true;
        if (path.matches("/api/v1/cafes/[^/]+/waiters") && !"GET".equals(method)) return true;
        if (path.matches("/api/v1/cafes/[^/]+/ambiance/(polls|reset-music|music/.+)")) return true;
        return path.equals("/api/cafes/upload");
    }

    private boolean matchesCafeScope(AuthPrincipal principal, String path) {
        Matcher matcher = CAFE_PATH.matcher(path);
        return !matcher.find() || matcher.group(1).equals(principal.cafeSlug());
    }

    private boolean reject(HttpServletResponse response, int status, String code) throws Exception {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", code));
        return false;
    }
}
