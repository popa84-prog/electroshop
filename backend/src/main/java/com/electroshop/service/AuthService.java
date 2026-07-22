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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final LoginEventRepository loginEventRepository;
    private final GeoIpService geoIpService;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       LoginEventRepository loginEventRepository, GeoIpService geoIpService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.loginEventRepository = loginEventRepository;
        this.geoIpService = geoIpService;
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

    public AuthResponse login(LoginRequest req, String ip, String userAgent) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.email(), req.password()));
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Email sau parolă incorecte.");
        }
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Email sau parolă incorecte."));

        // Block accounts that an administrator has not approved yet.
        if (!Boolean.TRUE.equals(user.getApproved())) {
            throw new BadRequestException(
                    "Contul tău așteaptă aprobarea administratorului. Vei putea intra după ce este aprobat.");
        }

        recordLogin(user, ip, userAgent);
        return buildAuthResponse(user);
    }

    /** Persists a login-event row and updates the user's quick "last login" fields. */
    private void recordLogin(User user, String ip, String userAgent) {
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

    public AuthResponse refresh(RefreshTokenRequest req) {
        String token = req.refreshToken();
        if (!jwtService.isRefreshToken(token)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
        String email = jwtService.extractEmail(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("User no longer exists"));
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String access = jwtService.generateAccessToken(user.getEmail(), user.getId());
        String refresh = jwtService.generateRefreshToken(user.getEmail(), user.getId());
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName).map(Enum::name).collect(Collectors.toSet());
        return AuthResponse.of(access, refresh, user.getId(), user.getFullName(), user.getEmail(), roles);
    }
}
