package com.taktak.controller;

import com.taktak.dto.AuthResponse;
import com.taktak.service.IAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;

    @PostMapping("/admin/login")
    public ResponseEntity<AuthResponse> adminLogin(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(authService.adminLogin(body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).build();
        }
    }

    @PostMapping("/staff/login")
    public ResponseEntity<AuthResponse> staffLogin(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(authService.staffLogin(body));
    }
}
