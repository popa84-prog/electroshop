package com.electroshop.dto;

import com.electroshop.model.Offer;

import java.time.LocalDateTime;

/**
 * Varianta publică a unei oferte — exact câmpurile de care are nevoie
 * magazinul pentru randare, nimic mai mult. Datele de administrare
 * ({@code active}, {@code createdAt}, {@code sortOrder}) nu ajung la client:
 * o ofertă care nu se afișează nu este trimisă deloc, deci steagul ei nu are
 * ce căuta în răspuns.
 *
 * <p>{@code endsAt} este trimis ca text ISO, iar {@code recurringDaily} rămâne
 * un steag separat. Interfața decide astfel ținta cronometrului: dacă există o
 * dată de sfârșit explicită o folosește ca atare, altfel — la ofertă
 * recurentă — calculează miezul nopții din fusul orar al vizitatorului. Este
 * singura variantă corectă: serverul rulează în UTC și nu cunoaște fusul orar
 * al clientului.</p>
 */
public record OfferPublicDto(
        Long id,
        String title,
        String headline,
        String description,
        String badgeLabel,
        String ctaLabel,
        String ctaUrl,
        String icon,
        String accent,
        String placement,
        LocalDateTime endsAt,
        boolean showTimer,
        boolean recurringDaily
) {
    public static OfferPublicDto from(Offer o) {
        return new OfferPublicDto(
                o.getId(),
                o.getTitle(),
                o.getHeadline(),
                o.getDescription(),
                o.getBadgeLabel(),
                o.getCtaLabel(),
                o.getCtaUrl(),
                o.getIcon(),
                o.getAccent(),
                o.getPlacement().name(),
                o.getEndsAt(),
                o.isShowTimer(),
                o.isRecurringDaily()
        );
    }
}
