package com.electroshop.dto;

import com.electroshop.model.Offer;

import java.time.LocalDateTime;

/**
 * Reprezentarea completă a unei oferte, folosită în panoul de administrare.
 * Include câmpul calculat {@code live}, ca operatorul să vadă dintr-o privire
 * dacă oferta este chiar acum pe site — un comutator pornit nu înseamnă
 * automat că oferta se afișează, pentru că fereastra de timp poate fi în
 * viitor sau deja încheiată.
 */
public record OfferDto(
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
        boolean active,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean showTimer,
        boolean recurringDaily,
        Integer sortOrder,
        boolean live,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OfferDto from(Offer o, LocalDateTime now) {
        return new OfferDto(
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
                o.isActive(),
                o.getStartsAt(),
                o.getEndsAt(),
                o.isShowTimer(),
                o.isRecurringDaily(),
                o.getSortOrder(),
                o.isLiveAt(now),
                o.getCreatedAt(),
                o.getUpdatedAt()
        );
    }
}
