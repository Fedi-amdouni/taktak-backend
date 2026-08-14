package com.taktak.service;

import com.taktak.dto.AuthResponse;

import java.util.Map;

public interface IAuthService {
    AuthResponse adminLogin(Map<String, String> body);
    AuthResponse staffLogin(Map<String, String> body);
}
