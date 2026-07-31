package com.electroshop.dto;

/** Returned by POST /auth/2fa/setup — the secret for manual entry plus the otpauth:// URI. */
public record TwoFactorSetupResponse(String secret, String otpAuthUrl) {
}
