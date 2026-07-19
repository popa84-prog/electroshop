package com.electroshop.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "Y2hhbmdlLW1lLXRoaXMtaXMtYS1kZXYtc2VjcmV0LWtleS1lbGVjdHJvc2hvcC0yMDI2fQ==",
            900000L,
            604800000L);

    @Test
    void accessTokenRoundTrip() {
        String token = jwtService.generateAccessToken("john@example.com", 42L);
        assertEquals("john@example.com", jwtService.extractEmail(token));
        assertEquals(42L, jwtService.extractUserId(token));
        assertTrue(jwtService.isTokenValid(token, "john@example.com"));
        assertFalse(jwtService.isTokenValid(token, "other@example.com"));
        assertFalse(jwtService.isRefreshToken(token));
    }

    @Test
    void refreshTokenIsRecognized() {
        String token = jwtService.generateRefreshToken("jane@example.com", 7L);
        assertTrue(jwtService.isRefreshToken(token));
        assertEquals("jane@example.com", jwtService.extractEmail(token));
    }

    @Test
    void tamperedTokenIsInvalid() {
        String token = jwtService.generateAccessToken("john@example.com", 1L);
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertFalse(jwtService.isTokenValid(tampered, "john@example.com"));
    }
}
