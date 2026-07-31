package com.electroshop.dto;

import java.util.Set;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long userId,
        String fullName,
        String email,
        Set<String> roles,
        // Feature #6 (2FA): when true, accessToken/refreshToken/userId/... above are
        // all null — the frontend must show a code-entry screen and call
        // POST /auth/2fa/verify with twoFactorToken + the 6-digit code to finish login.
        boolean requiresTwoFactor,
        String twoFactorToken
) {
    public static AuthResponse of(String accessToken, String refreshToken,
                                  Long userId, String fullName, String email, Set<String> roles) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", userId, fullName, email, roles,
                false, null);
    }

    public static AuthResponse twoFactorRequired(String twoFactorToken) {
        return new AuthResponse(null, null, "Bearer", null, null, null, null, true, twoFactorToken);
    }
}
