package com.electroshop.service;

import com.electroshop.dto.*;
import com.electroshop.exception.BadRequestException;
import com.electroshop.model.LoginEvent;
import com.electroshop.model.Role;
import com.electroshop.model.RoleName;
import com.electroshop.model.User;
import com.electroshop.repository.LoginEventRepository;
import com.electroshop.repository.RoleRepository;
import com.electroshop.repository.UserRepository;
import com.electroshop.security.JwtService;
import com.electroshop.security.TotpService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthService {

    /** Brute-force protection (feature #6): lock the account after this many bad passwords in a row. */
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final LoginEventRepository loginEventRepository;
    private final GeoIpService geoIpService;
    private final TotpService totpService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       LoginEventRepository loginEventRepository, GeoIpService geoIpService,
                       TotpService totpService, AuditService auditService,
                       NotificationService notificationService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.loginEventRepository = loginEventRepository;
        this.geoIpService = geoIpService;
        this.totpService = totpService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    /**
     * Self-registration. Creates a PENDING account (approved = false). The user
     * cannot log in until an administrator approves it, so no tokens are issued.
     */
    public void register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new BadRequestException("Acest email este deja înregistrat.");
        }
        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ROLE_USER)));

        User user = new User();
        user.setFullName(req.fullName());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setApproved(false); // pending admin approval
        Set<Role> roles = new HashSet<>();
        roles.add(userRole);
        user.setRoles(roles);

        userRepository.save(user);
    }

    /**
     * Step 1 of login: email + password. Enforces brute-force lockout (an
     * account already locked from too many failed attempts is rejected by
     * Spring Security itself — {@code UserPrincipal.isAccountNonLocked()} —
     * before the password is even checked) and records every attempt,
     * successful or not, feature #6's "loguri pentru autentificări
     * reușite/eșuate". When the account has 2FA enabled, returns a short-lived
     * challenge token instead of real tokens; the frontend must then call
     * {@link #verifyTwoFactor}.
     */
    public AuthResponse login(LoginRequest req, String ip, String userAgent) {
        User existing = userRepository.findByEmail(req.email()).orElse(null);

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (LockedException e) {
            recordFailure(existing, req.email(), ip, userAgent, "account_locked");
            throw new BadRequestException(lockedMessage(existing));
        } catch (org.springframework.security.authentication.DisabledException e) {
            recordFailure(existing, req.email(), ip, userAgent, "account_disabled");
            throw new BadRequestException("Acest cont a fost dezactivat de un administrator.");
        } catch (BadCredentialsException e) {
            if (existing != null) {
                registerFailedAttempt(existing);
            }
            recordFailure(existing, req.email(), ip, userAgent, "bad_credentials");
            throw new BadCredentialsException("Email sau parolă incorecte.");
        }

        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Email sau parolă incorecte."));

        // Block accounts that an administrator has not approved yet.
        if (!Boolean.TRUE.equals(user.getApproved())) {
            recordFailure(user, req.email(), ip, userAgent, "not_approved");
            throw new BadRequestException(
                    "Contul tău așteaptă aprobarea administratorului. Vei putea intra după ce este aprobat.");
        }

        // Correct password → the brute-force counter resets.
        if (user.getFailedLoginAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }

        if (user.isTwoFactorEnabled()) {
            String challenge = jwtService.generateTwoFactorChallengeToken(user.getEmail(), user.getId());
            return AuthResponse.twoFactorRequired(challenge);
        }

        recordSuccess(user, ip, userAgent);
        return buildAuthResponse(user);
    }

    /** Step 2 of login for 2FA accounts: the 6-digit authenticator code. */
    public AuthResponse verifyTwoFactor(TwoFactorVerifyRequest req, String ip, String userAgent) {
        if (!jwtService.isTwoFactorChallengeToken(req.twoFactorToken())) {
            throw new BadRequestException("Sesiunea de verificare a expirat. Reia autentificarea.");
        }
        String email = jwtService.extractEmail(req.twoFactorToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Utilizator inexistent."));

        if (!user.isTwoFactorEnabled() || user.getTwoFactorSecret() == null) {
            throw new BadRequestException("Autentificarea în doi pași nu este activă pentru acest cont.");
        }
        if (!totpService.verifyCode(user.getTwoFactorSecret(), req.code())) {
            recordFailure(user, email, ip, userAgent, "bad_2fa_code");
            throw new BadRequestException("Cod de verificare incorect.");
        }

        recordSuccess(user, ip, userAgent);
        return buildAuthResponse(user);
    }

    // ---------- 2FA setup / disable (Admin self-service, feature #6) ----------

    public TwoFactorSetupResponse setupTwoFactor(String email) {
        User user = requireUser(email);
        String secret = totpService.generateSecret();
        user.setTwoFactorPendingSecret(secret);
        userRepository.save(user);
        return new TwoFactorSetupResponse(secret, totpService.buildOtpAuthUrl(secret, user.getEmail()));
    }

    public void confirmTwoFactor(String email, String code) {
        User user = requireUser(email);
        if (user.getTwoFactorPendingSecret() == null) {
            throw new BadRequestException("Nu există o configurare 2FA în așteptare. Inițiază din nou setarea.");
        }
        if (!totpService.verifyCode(user.getTwoFactorPendingSecret(), code)) {
            throw new BadRequestException("Cod incorect. Verifică ora dispozitivului și încearcă din nou.");
        }
        user.setTwoFactorSecret(user.getTwoFactorPendingSecret());
        user.setTwoFactorPendingSecret(null);
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        auditService.log("TWO_FACTOR_ENABLED", "User", user.getId(),
                "Autentificare în doi pași activată pentru " + user.getEmail());
    }

    public void disableTwoFactor(String email, String code) {
        User user = requireUser(email);
        if (!user.isTwoFactorEnabled() || user.getTwoFactorSecret() == null) {
            throw new BadRequestException("Autentificarea în doi pași nu este activă.");
        }
        if (!totpService.verifyCode(user.getTwoFactorSecret(), code)) {
            throw new BadRequestException("Cod incorect.");
        }
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        user.setTwoFactorPendingSecret(null);
        userRepository.save(user);
        auditService.log("TWO_FACTOR_DISABLED", "User", user.getId(),
                "Autentificare în doi pași dezactivată pentru " + user.getEmail());
    }

    // ---------- Refresh / session revocation ----------

    public AuthResponse refresh(RefreshTokenRequest req) {
        String token = req.refreshToken();
        if (!jwtService.isRefreshToken(token)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
        String email = jwtService.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User no longer exists"));

        // A logout-all (or any future forced-revoke) bumps tokenVersion — a refresh
        // token minted before that must not be able to mint new access tokens.
        if (jwtService.extractTokenVersion(token) != user.getTokenVersion()) {
            throw new BadRequestException("Sesiune expirată. Te rugăm să te autentifici din nou.");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BadRequestException(lockedMessage(user));
        }
        return buildAuthResponse(user);
    }

    /** "Deconectează toate sesiunile" — invalidates every access/refresh token issued so far. */
    public void logoutAllSessions(String email) {
        User user = requireUser(email);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }

    // ---------- internals ----------

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Utilizator inexistent."));
    }

    private void registerFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            userRepository.save(user);
            // Feature #6: "notificare admin la activitate suspectă" — surfaced in the
            // existing audit log (Jurnal de activitate), filterable/exportable today;
            // feature #8's notification center will also read from this same trail.
            auditService.log("ACCOUNT_LOCKED", "User", user.getId(),
                    "Cont blocat " + LOCK_MINUTES + " minute după " + attempts + " încercări eșuate de autentificare");
            // Feature #8 — "notificare admin la activitate suspectă" surfaced in the
            // notification center too, not just the audit log.
            notificationService.notifyAccountLocked(user.getId(), user.getEmail(), attempts);
        } else {
            userRepository.save(user);
        }
    }

    private String lockedMessage(User user) {
        if (user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            long minutes = Math.max(1, Duration.between(LocalDateTime.now(), user.getLockedUntil()).toMinutes());
            return "Prea multe încercări eșuate. Contul este blocat temporar — mai încearcă în aproximativ "
                    + minutes + " minute.";
        }
        return "Contul este blocat temporar din cauza prea multor încercări eșuate de autentificare.";
    }

    /** Persists a successful login-event row and updates the user's quick "last login" fields. */
    private void recordSuccess(User user, String ip, String userAgent) {
        try {
            GeoIpService.GeoInfo geo = geoIpService.lookup(ip);

            LoginEvent ev = new LoginEvent();
            ev.setUserId(user.getId());
            ev.setUserEmail(user.getEmail());
            ev.setUserName(user.getFullName());
            ev.setIpAddress(ip);
            ev.setCountry(geo.country());
            ev.setCity(geo.city());
            ev.setUserAgent(userAgent != null && userAgent.length() > 400
                    ? userAgent.substring(0, 400) : userAgent);
            ev.setSuccess(true);
            ev.setLoginAt(LocalDateTime.now());
            loginEventRepository.save(ev);

            String loc;
            if (geo.city() != null && geo.country() != null) loc = geo.city() + ", " + geo.country();
            else if (geo.country() != null) loc = geo.country();
            else loc = null;
            user.setLastLoginAt(ev.getLoginAt());
            user.setLastLoginIp(ip);
            user.setLastLoginLocation(loc);
            userRepository.save(user);
        } catch (Exception ignored) {
            // Never let login tracking break the actual login.
        }
    }

    /** Persists a failed login-event row (bad password, locked account, not approved, bad 2FA code, ...). */
    private void recordFailure(User user, String attemptedEmail, String ip, String userAgent, String reason) {
        try {
            LoginEvent ev = new LoginEvent();
            ev.setUserId(user != null ? user.getId() : null);
            ev.setUserEmail(user != null ? user.getEmail() : attemptedEmail);
            ev.setUserName(user != null ? user.getFullName() : null);
            ev.setIpAddress(ip);
            try {
                GeoIpService.GeoInfo geo = geoIpService.lookup(ip);
                ev.setCountry(geo.country());
                ev.setCity(geo.city());
            } catch (Exception ignored) {
                // geo lookup is best-effort; a failed login must still be recorded without it
            }
            ev.setUserAgent(userAgent != null && userAgent.length() > 400
                    ? userAgent.substring(0, 400) : userAgent);
            ev.setSuccess(false);
            ev.setFailureReason(reason);
            ev.setLoginAt(LocalDateTime.now());
            loginEventRepository.save(ev);
        } catch (Exception ignored) {
            // Never let login tracking break the actual authentication flow.
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        String access = jwtService.generateAccessToken(user.getEmail(), user.getId(), user.getTokenVersion());
        String refresh = jwtService.generateRefreshToken(user.getEmail(), user.getId(), user.getTokenVersion());
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName).map(Enum::name).collect(Collectors.toSet());
        return AuthResponse.of(access, refresh, user.getId(), user.getFullName(), user.getEmail(), roles);
    }
}
