package com.electroshop.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Used by ADMIN to create / update users. Password is optional on update.
 */
public record UserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
        String password,

        Boolean enabled,

        // e.g. ["ROLE_USER", "ROLE_ADMIN"]
        Set<String> roles
) {
}
