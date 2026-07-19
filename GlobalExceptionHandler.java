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
        Set<String> roles,
        LocalDateTime createdAt
) {
    public static UserDto from(User u) {
        return new UserDto(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.isEnabled(),
                u.getRoles().stream().map(Role::getName).map(Enum::name).collect(Collectors.toSet()),
                u.getCreatedAt()
        );
    }
}
