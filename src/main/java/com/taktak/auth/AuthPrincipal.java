package com.taktak.auth;

import java.util.Set;

public record AuthPrincipal(String subject, String role, String cafeSlug, Set<String> cafeSlugs, long expiresAt) {
    public AuthPrincipal(String subject, String role, String cafeSlug, long expiresAt) {
        this(subject, role, cafeSlug, cafeSlug == null || cafeSlug.isBlank() ? Set.of() : Set.of(cafeSlug), expiresAt);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean canAccessCafe(String slug) {
        return cafeSlugs.contains(slug);
    }
}
