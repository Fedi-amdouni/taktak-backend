package com.taktak.auth;

public record AuthPrincipal(String subject, String role, String cafeSlug, long expiresAt) {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
