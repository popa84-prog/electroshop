package com.electroshop.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    /** Short window to complete a 2FA challenge after the password step succeeds. */
    private static final long TWO_FACTOR_TOKEN_EXPIRATION_MS = 5 * 60 * 1000L;

    private final SecretKey key;
    private final long accessTokenExpirationMs;
    private final long refreshTokenExpirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiration-ms}") long accessTokenExpirationMs,
            @Value("${app.jwt.refresh-token-expiration-ms}") long refreshTokenExpirationMs) {
        // Secret is Base64-encoded; must decode to at least 256 bits for HS256.
        this.key = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(secret));
        this.accessTokenExpirationMs = accessTokenExpirationMs;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    public String generateAccessToken(String email, Long userId, int tokenVersion) {
        return buildToken(email, userId, "access", accessTokenExpirationMs, tokenVersion);
    }

    public String generateRefreshToken(String email, Long userId, int tokenVersion) {
        return buildToken(email, userId, "refresh", refreshTokenExpirationMs, tokenVersion);
    }

    /** Proof that the password step succeeded; only usable against /auth/2fa/verify. */
    public String generateTwoFactorChallengeToken(String email, Long userId) {
        return buildToken(email, userId, "2fa_pending", TWO_FACTOR_TOKEN_EXPIRATION_MS, 0);
    }

    private String buildToken(String email, Long userId, String type, long expirationMs, int tokenVersion) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(email)
                .claim("uid", userId)
                .claim("type", type)
                .claim("tv", tokenVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("uid", Long.class));
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get("type", String.class));
    }

    public int extractTokenVersion(String token) {
        Integer tv = extractClaim(token, claims -> claims.get("tv", Integer.class));
        return tv == null ? 0 : tv;
    }

    public boolean isTokenValid(String token, String email) {
        try {
            return email.equals(extractEmail(token)) && !isExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            return "refresh".equals(extractTokenType(token)) && !isExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTwoFactorChallengeToken(String token) {
        try {
            return "2fa_pending".equals(extractTokenType(token)) && !isExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
