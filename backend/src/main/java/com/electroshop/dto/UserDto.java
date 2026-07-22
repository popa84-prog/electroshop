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
        String lastLoginLocation
) {
    public static UserDto from(User u) {
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
                u.getLastLoginLocation()
        );
    }
}
