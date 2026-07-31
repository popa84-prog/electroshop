package com.electroshop.dto;

import com.electroshop.model.Role;
import com.electroshop.model.User;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

public record UserDto(
        Long id,
        String fullName,
        String email,
        boolean enabled,
        boolean approved,
        Set<String> roles,
        LocalDateTime createdAt,
        LocalDateTime lastLoginAt,
        String lastLoginIp,
        String lastLoginLocation,
        // Feature #6 — never expose the secret itself, only whether 2FA is on and
        // whether the account is currently brute-force-locked (+ until when).
        boolean twoFactorEnabled,
        boolean locked,
        LocalDateTime lockedUntil
) {
    public static UserDto from(User u) {
        boolean locked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now());
        return new UserDto(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.isEnabled(),
                Boolean.TRUE.equals(u.getApproved()),
                u.getRoles().stream().map(Role::getName).map(Enum::name).collect(Collectors.toSet()),
                u.getCreatedAt(),
                u.getLastLoginAt(),
                u.getLastLoginIp(),
                u.getLastLoginLocation(),
                u.isTwoFactorEnabled(),
                locked,
                locked ? u.getLockedUntil() : null
        );
    }
}
