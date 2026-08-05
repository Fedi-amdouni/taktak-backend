package com.taktak.auth;

public final class AuthContext {
    private static final ThreadLocal<AuthPrincipal> CURRENT = new ThreadLocal<>();

    private AuthContext() {}

    public static void set(AuthPrincipal principal) { CURRENT.set(principal); }
    public static AuthPrincipal get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
