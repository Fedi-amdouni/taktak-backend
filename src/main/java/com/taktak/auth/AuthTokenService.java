package com.taktak.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AuthTokenService {
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long ttlSeconds;

    public AuthTokenService(ObjectMapper objectMapper,
                            @Value("${taktak.auth.secret}") String secret,
                            @Value("${taktak.auth.token-ttl-hours:12}") long ttlHours) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlHours * 3600;
    }

    public String issue(String subject, String role, String cafeSlug) {
        try {
            long expiresAt = Instant.now().getEpochSecond() + ttlSeconds;
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", subject);
            claims.put("role", role);
            claims.put("cafe", cafeSlug == null ? "" : cafeSlug);
            claims.put("exp", expiresAt);
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(claims));
            return payload + "." + sign(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de créer la session", e);
        }
    }

    public AuthPrincipal verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !constantTimeEquals(sign(parts[0]), parts[1])) return null;
            Map<String, Object> claims = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[0]), new TypeReference<>() {});
            long expiresAt = ((Number) claims.get("exp")).longValue();
            if (expiresAt <= Instant.now().getEpochSecond()) return null;
            return new AuthPrincipal(
                    String.valueOf(claims.get("sub")),
                    String.valueOf(claims.get("role")),
                    String.valueOf(claims.get("cafe")),
                    expiresAt);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
