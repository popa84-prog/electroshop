package com.electroshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Body for POST /auth/2fa/confirm and POST /auth/2fa/disable — just the current 6-digit code. */
public record TwoFactorCodeRequest(
          @NotBlank(message = "Codul este obligatoriu")
          @Pattern(regexp = "\\d{6}", message = "Codul trebuie să aibă 6 cifre")
          String code
  ) {
}
