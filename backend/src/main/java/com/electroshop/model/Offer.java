package com.electroshop.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * O ofertă comercială afișată în magazin (ex: „Transport gratuit la orice
 * comandă"). Până acum textul promoției și cele patru beneficii de pe prima
 * pagină erau scrise direct în {@code Home.jsx}, deci orice schimbare de
 * campanie însemna o modificare de cod și o redeployare. Entitatea aceasta le
 * mută în baza de date, unde pot fi editate din panoul de administrare.
 *
 * <p>Trei decizii de proiectare merită explicate:
 *
 * <ol>
 *   <li><b>{@code placement} separă cele două zone vizuale.</b> Magazinul are
 *       două locuri distincte în care apare o ofertă: modulul mare de promoție
 *       cu cronometru holografic și banda de patru beneficii de sub hero.
 *       Aceeași entitate le acoperă pe amândouă, deci nu există două tabele
 *       aproape identice de întreținut.</li>
 *   <li><b>{@code startsAt} și {@code endsAt} sunt amândouă opționale.</b> O
 *       ofertă fără dată de început pornește imediat; una fără dată de sfârșit
 *       rulează până este dezactivată manual. Fereastra de timp este astfel
 *       independentă de comutatorul {@code active}: operatorul poate pregăti o
 *       campanie din timp și o poate opri instantaneu fără să piardă datele.</li>
 *   <li><b>{@code recurringDaily} reproduce comportamentul actual al site-ului.</b>
 *       Promoția existentă „se resetează la miezul nopții". Ora aceea este ora
 *       locală a vizitatorului, nu ora serverului, deci ținta cronometrului nu
 *       poate fi calculată aici — steagul este trimis către interfață, care
 *       calculează miezul nopții local. Un cronometru calculat în UTC ar afișa
 *       unui client din România o oră greșită cu trei ore.</li>
 * </ol>
 */
@Entity
@Table(name = "offers")
@Getter
@Setter
@NoArgsConstructor
public class Offer {

    /** Zona din magazin în care este afișată oferta. */
    public enum Placement {
        /** Modulul mare de promoție de pe prima pagină, cu cronometru. */
        HOME_PROMO,
        /** Banda de beneficii de sub hero (patru cartonașe mici). */
        BENEFIT_BAR
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ---- Conținut ----

    /** Titlul principal, evidențiat cu degrade (ex: „Transport gratuit"). */
    @Column(nullable = false, length = 150)
    private String title;

    /** Continuarea titlului, în text normal (ex: „la orice comandă"). */
    @Column(length = 150)
    private String headline;

    /** Paragraful explicativ de sub titlu. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Textul din insigna de deasupra titlului (ex: „Ofertă activă"). */
    @Column(length = 60)
    private String badgeLabel;

    // ---- Apel la acțiune ----

    @Column(length = 80)
    private String ctaLabel;

    @Column(length = 300)
    private String ctaUrl;

    // ---- Aspect ----

    /** Numele pictogramei din setul GeoIcon (truck, shield, refresh, coins…). */
    @Column(length = 40)
    private String icon = "tag";

    /** Culoarea de accent, ca variabilă CSS XXII (ex: var(--xx-cyan)). */
    @Column(length = 60)
    private String accent = "var(--xx-cyan)";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Placement placement = Placement.HOME_PROMO;

    // ---- Programare ----

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;

    /** Afișează cronometrul holografic lângă ofertă. */
    @Column(nullable = false)
    private boolean showTimer = false;

    /** Oferta se reîncarcă la miezul nopții locale, în lipsa unei date de sfârșit. */
    @Column(nullable = false)
    private boolean recurringDaily = false;

    /** Ordinea de afișare; valorile mai mici apar primele. */
    @Column(nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Adevărat dacă oferta trebuie afișată acum: comutatorul este pornit și
     * momentul curent se află în fereastra de timp configurată. O margine
     * nesetată înseamnă „fără limită în direcția aceea".
     */
    public boolean isLiveAt(LocalDateTime now) {
        if (!active) {
            return false;
        }
        if (startsAt != null && now.isBefore(startsAt)) {
            return false;
        }
        return endsAt == null || now.isBefore(endsAt);
    }
}
