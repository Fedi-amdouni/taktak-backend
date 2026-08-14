package com.taktak.service.impl;

import com.taktak.auth.AuthTokenService;
import com.taktak.dto.AuthResponse;
import com.taktak.dto.WaiterDTO;
import com.taktak.dto.WaiterLoginRequest;
import com.taktak.repository.CafeRepository;
import com.taktak.service.IAuthService;
import com.taktak.service.IWaiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements IAuthService {

    private final AuthTokenService tokenService;
    private final IWaiterService waiterService;
    private final CafeRepository cafeRepository;

    @Value("${taktak.auth.owner-accounts:wael:wael123:monastir-lounge,carthage-lounge}")
    private String ownerAccounts;

    @Value("${taktak.auth.admin-password:wael123}")
    private String legacyAdminPassword;

    @Override
    public AuthResponse adminLogin(Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");
        OwnerAccount account = ownerAccount(username);
        if (account == null || !MessageDigest.isEqual(account.password().getBytes(StandardCharsets.UTF_8),
                password.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Mot de passe invalide");
        }
        return new AuthResponse(tokenService.issueForCafes(account.username(), "ADMIN", account.cafeSlugs()), "ADMIN", null,
                account.cafeSlugs(), null);
    }

    @Override
    public AuthResponse staffLogin(Map<String, String> body) {
        String cafeSlug = body.getOrDefault("cafeSlug", "");
        cafeRepository.findBySlug(cafeSlug).orElseThrow(() -> new RuntimeException("Café introuvable"));
        WaiterLoginRequest request = new WaiterLoginRequest();
        request.setPinCode(body.get("pinCode"));
        WaiterDTO waiter = waiterService.loginByPin(cafeSlug, request.getPinCode());
        return new AuthResponse(
                tokenService.issue(waiter.getId(), "STAFF", cafeSlug), "STAFF", cafeSlug, List.of(cafeSlug), waiter);
    }

    private OwnerAccount ownerAccount(String username) {
        String accounts = ownerAccounts.isBlank()
                ? "wael:" + legacyAdminPassword + ":monastir-lounge,carthage-lounge;sami:" + legacyAdminPassword + ":sousse-palm-beach"
                : ownerAccounts;
        return Arrays.stream(accounts.split(";"))
                .map(String::trim)
                .map(this::parseOwnerAccount)
                .filter(account -> account != null && account.username().equals(username))
                .findFirst()
                .orElse(null);
    }

    private OwnerAccount parseOwnerAccount(String value) {
        String[] pieces = value.split(":", 3);
        if (pieces.length != 3 || pieces[0].isBlank() || pieces[1].isBlank()) return null;
        List<String> cafes = Arrays.stream(pieces[2].split(","))
                .map(String::trim).filter(slug -> !slug.isBlank()).collect(Collectors.toList());
        return cafes.isEmpty() ? null : new OwnerAccount(pieces[0].trim().toLowerCase(), pieces[1], cafes);
    }

    private record OwnerAccount(String username, String password, List<String> cafeSlugs) {}
}
