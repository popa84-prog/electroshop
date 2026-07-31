package com.electroshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Second step of login when the account has 2FA enabled. */
public record TwoFactorVerifyRequest(
        @NotBlank(message = "Token de verificare lipsă")
        String twoFactorToken,

        @NotBlank(message = "Codul este obligatoriu")
        @Pattern(regexp = "\\d{6}", message = "Codul trebuie să aibă 6 cifre")
        String code
) {
}
