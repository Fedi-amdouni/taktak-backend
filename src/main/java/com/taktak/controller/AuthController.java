package com.taktak.controller;

import com.taktak.auth.AuthTokenService;
import com.taktak.dto.AuthResponse;
import com.taktak.dto.WaiterDTO;
import com.taktak.dto.WaiterLoginRequest;
import com.taktak.repository.CafeRepository;
import com.taktak.service.WaiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthTokenService tokenService;
    private final WaiterService waiterService;
    private final CafeRepository cafeRepository;

    @Value("${taktak.auth.admin-password}")
    private String adminPassword;

    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponse> adminLogin(@RequestBody Map<String, String> body) {
        String password = body.getOrDefault("password", "");
        if (!MessageDigest.isEqual(adminPassword.getBytes(StandardCharsets.UTF_8),
                password.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(new AuthResponse(
                tokenService.issue("owner", "ADMIN", null), "ADMIN", null, null));
    }

    @PostMapping("/staff/login")
    public ResponseEntity<AuthResponse> staffLogin(@RequestBody Map<String, String> body) {
        String cafeSlug = body.getOrDefault("cafeSlug", "");
        cafeRepository.findBySlug(cafeSlug).orElseThrow(() -> new RuntimeException("Café introuvable"));
        WaiterLoginRequest request = new WaiterLoginRequest();
        request.setPinCode(body.get("pinCode"));
        WaiterDTO waiter = waiterService.loginByPin(cafeSlug, request.getPinCode());
        return ResponseEntity.ok(new AuthResponse(
                tokenService.issue(waiter.getId(), "STAFF", cafeSlug), "STAFF", cafeSlug, waiter));
    }
}
