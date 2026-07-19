package com.electroshop.dto;

import java.util.Set;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long userId,
        String fullName,
        String email,
        Set<String> roles
) {
    public static AuthResponse of(String accessToken, String refreshToken,
                                  Long userId, String fullName, String email, Set<String> roles) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", userId, fullName, email, roles);
    }
}
