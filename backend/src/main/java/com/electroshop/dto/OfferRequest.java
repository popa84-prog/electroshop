package com.electroshop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Datele acceptate la crearea sau modificarea unei oferte. Doar titlul este
 * obligatoriu: o campanie poate fi salvată ca ciornă dezactivată, cu restul
 * câmpurilor completate ulterior.
 *
 * <p>Câmpurile {@code active}, {@code showTimer} și {@code recurringDaily} sunt
 * {@code Boolean} (obiect), nu {@code boolean} primitiv: interfața trimite
 * întotdeauna toate cele trei, dar un client care omite unul trebuie să
 * primească valoarea implicită a serviciului, nu {@code false} tăcut impus de
 * dezambalarea automată.</p>
 */
public record OfferRequest(
        @NotBlank(message = "Titlul ofertei este obligatoriu")
        @Size(max = 150, message = "Titlul poate avea cel mult 150 de caractere")
        String title,

        @Size(max = 150)
        String headline,

        String description,

        @Size(max = 60)
        String badgeLabel,

        @Size(max = 80)
        String ctaLabel,

        @Size(max = 300)
        String ctaUrl,

        @Size(max = 40)
        String icon,

        @Size(max = 60)
        String accent,

        /** HOME_PROMO sau BENEFIT_BAR. */
        String placement,

        Boolean active,

        LocalDateTime startsAt,

        LocalDateTime endsAt,

        Boolean showTimer,

        Boolean recurringDaily,

        Integer sortOrder
) {
}
