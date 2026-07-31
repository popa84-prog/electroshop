package com.electroshop.controller;

import com.electroshop.dto.*;
import com.electroshop.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Object>> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                "Cont creat cu succes. Contul tău așteaptă aprobarea administratorului "
                        + "înainte de a te putea autentifica.", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest http) {
        AuthResponse response = authService.login(request, clientIp(http), http.getHeader("User-Agent"));
        String message = response.requiresTwoFactor()
                ? "Cod de verificare necesar"
                : "Login successful";
        return ResponseEntity.ok(ApiResponse.ok(message, response));
    }

    /** Second step of login when the account has 2FA enabled — the 6-digit authenticator code. */
    @PostMapping("/2fa/verify")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyTwoFactor(
            @Valid @RequestBody TwoFactorVerifyRequest request, HttpServletRequest http) {
        AuthResponse response = authService.verifyTwoFactor(request, clientIp(http), http.getHeader("User-Agent"));
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    /** Real client IP behind the Railway proxy: first hop of X-Forwarded-For, else remote address. */
    private String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String real = req.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return req.getRemoteAddr();
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", response));
    }

    // ---------- Admin 2FA self-service (feature #6) ----------

    /** Starts 2FA setup: returns a fresh secret + otpauth:// URI for an authenticator app. Not active until confirmed. */
    @PostMapping("/2fa/setup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<TwoFactorSetupResponse>> setupTwoFactor(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(authService.setupTwoFactor(auth.getName())));
    }

    /** Confirms setup with one valid code from the authenticator app — only then is 2FA actually enabled. */
    @PostMapping("/2fa/confirm")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> confirmTwoFactor(@Valid @RequestBody TwoFactorCodeRequest request,
                                                                Authentication auth) {
        authService.confirmTwoFactor(auth.getName(), request.code());
        return ResponseEntity.ok(ApiResponse.ok("Autentificarea în doi pași a fost activată.", null));
    }

    /** Disables 2FA — requires one valid current code, so a stolen session alone can't turn it off. */
    @PostMapping("/2fa/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Object>> disableTwoFactor(@Valid @RequestBody TwoFactorCodeRequest request,
                                                                 Authentication auth) {
        authService.disableTwoFactor(auth.getName(), request.code());
        return ResponseEntity.ok(ApiResponse.ok("Autentificarea în doi pași a fost dezactivată.", null));
    }

    /** Revokes every access/refresh token issued so far for the current account ("deconectează toate sesiunile"). */
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Object>> logoutAll(Authentication auth) {
        authService.logoutAllSessions(auth.getName());
        return ResponseEntity.ok(ApiResponse.ok("Toate sesiunile au fost deconectate.", null));
    }
}
