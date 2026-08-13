package com.taktak.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String role;
    private String cafeSlug;
    private List<String> cafeSlugs;
    private WaiterDTO waiter;
}
