package com.electroshop.service;

import com.electroshop.dto.OfferDto;
import com.electroshop.dto.OfferPublicDto;
import com.electroshop.dto.OfferRequest;
import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.Offer;
import com.electroshop.repository.OfferRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Gestionează ofertele comerciale afișate în magazin.
 *
 * <p>Serviciul are două fețe. Către panoul de administrare expune un CRUD
 * complet, care returnează toate ofertele indiferent de stare — inclusiv pe
 * cele expirate sau programate în viitor, pentru că exact acelea trebuie
 * editate. Către magazin expune doar ofertele care sunt chiar acum în
 * fereastra lor de timp, prin {@link Offer#isLiveAt}.</p>
 *
 * <p>Regula „este oferta activă acum" trăiește într-un singur loc, pe entitate.
 * Dacă ar fi scrisă și ca predicat JPQL în repository, cele două variante ar
 * ajunge inevitabil să difere, iar panoul de administrare ar afișa o stare pe
 * care magazinul nu o respectă.</p>
 */
@Service
@Transactional
public class OfferService {

    /** Câte oferte sunt trimise cel mult către banda de beneficii. */
    private static final int BENEFIT_BAR_LIMIT = 4;

    /** Câte oferte sunt trimise cel mult către modulul de promoție. */
    private static final int HOME_PROMO_LIMIT = 1;

    private final OfferRepository offerRepository;
    private final AuditService auditService;

    public OfferService(OfferRepository offerRepository, AuditService auditService) {
        this.offerRepository = offerRepository;
        this.auditService = auditService;
    }

    // ---------------- Panoul de administrare ----------------

    @Transactional(readOnly = true)
    public Page<OfferDto> list(String search, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        Page<Offer> page = (search == null || search.isBlank())
                ? offerRepository.findAll(pageable)
                : offerRepository.findByTitleContainingIgnoreCaseOrHeadlineContainingIgnoreCase(
                        search, search, pageable);
        return page.map(o -> OfferDto.from(o, now));
    }

    @Transactional(readOnly = true)
    public OfferDto getById(Long id) {
        return OfferDto.from(findEntity(id), LocalDateTime.now());
    }

    public OfferDto create(OfferRequest req) {
        Offer o = new Offer();
        apply(o, req);
        Offer saved = offerRepository.save(o);
        auditService.log("OFFER_CREATED", "Offer", saved.getId(), saved.getTitle());
        return OfferDto.from(saved, LocalDateTime.now());
    }

    public OfferDto update(Long id, OfferRequest req) {
        Offer o = findEntity(id);
        apply(o, req);
        Offer saved = offerRepository.save(o);
        auditService.log("OFFER_UPDATED", "Offer", saved.getId(), saved.getTitle());
        return OfferDto.from(saved, LocalDateTime.now());
    }

    /**
     * Pornește sau oprește o ofertă fără a trimite tot formularul. Butonul de
     * comutare din tabel are nevoie exact de atât, iar un PUT complet ar
     * suprascrie câmpuri pe care operatorul nu a intenționat să le atingă.
     */
    public OfferDto toggleActive(Long id) {
        Offer o = findEntity(id);
        o.setActive(!o.isActive());
        Offer saved = offerRepository.save(o);
        auditService.log(saved.isActive() ? "OFFER_ACTIVATED" : "OFFER_DEACTIVATED",
                "Offer", saved.getId(), saved.getTitle());
        return OfferDto.from(saved, LocalDateTime.now());
    }

    public void delete(Long id) {
        Offer o = findEntity(id);
        String title = o.getTitle();
        Long offerId = o.getId();
        offerRepository.delete(o);
        auditService.log("OFFER_DELETED", "Offer", offerId, title);
    }

    // ---------------- Magazinul ----------------

    /**
     * Ofertele afișabile acum, grupate pe zone. Limitele sunt aplicate aici, nu
     * în interfață: banda de beneficii are patru sloturi în grila ei, iar
     * modulul de promoție unul singur. O a cincea ofertă activă ar rupe
     * aliniamentul grilei dacă ar fi trimisă către client.
     */
    @Transactional(readOnly = true)
    public List<OfferPublicDto> livePublic(Offer.Placement placement) {
        LocalDateTime now = LocalDateTime.now();
        int limit = placement == Offer.Placement.BENEFIT_BAR ? BENEFIT_BAR_LIMIT : HOME_PROMO_LIMIT;
        return offerRepository
                .findByPlacementAndActiveTrueOrderBySortOrderAscIdAsc(placement)
                .stream()
                .filter(o -> o.isLiveAt(now))
                .limit(limit)
                .map(OfferPublicDto::from)
                .toList();
    }

    /**
     * Varianta primită direct din parametrul de cerere. Numele zonei vine ca
     * text din interfață, iar validarea lui trebuie să dea același mesaj de
     * eroare ca la salvarea unei oferte — de aceea folosește exact același
     * {@link #parsePlacement}.
     */
    @Transactional(readOnly = true)
    public List<OfferPublicDto> livePublic(String placement) {
        return livePublic(parsePlacement(placement));
    }

    // ---------------- Intern ----------------

    public Offer findEntity(Long id) {
        return offerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", id));
    }

    /**
     * Copiază cererea peste entitate. Câmpurile de tip {@code Boolean} sunt
     * aplicate doar când sunt prezente, ca un client care le omite să nu
     * dezactiveze tăcut o ofertă activă.
     */
    private void apply(Offer o, OfferRequest req) {
        if (req.startsAt() != null && req.endsAt() != null && !req.endsAt().isAfter(req.startsAt())) {
            throw new BadRequestException(
                    "Data de sfârșit a ofertei trebuie să fie după data de început.");
        }

        o.setTitle(req.title().trim());
        o.setHeadline(trim(req.headline()));
        o.setDescription(req.description());
        o.setBadgeLabel(trim(req.badgeLabel()));
        o.setCtaLabel(trim(req.ctaLabel()));
        o.setCtaUrl(trim(req.ctaUrl()));

        if (req.icon() != null && !req.icon().isBlank()) {
            o.setIcon(req.icon().trim());
        }
        if (req.accent() != null && !req.accent().isBlank()) {
            o.setAccent(req.accent().trim());
        }
        if (req.placement() != null && !req.placement().isBlank()) {
            o.setPlacement(parsePlacement(req.placement()));
        }
        if (req.active() != null) {
            o.setActive(req.active());
        }
        if (req.showTimer() != null) {
            o.setShowTimer(req.showTimer());
        }
        if (req.recurringDaily() != null) {
            o.setRecurringDaily(req.recurringDaily());
        }
        if (req.sortOrder() != null) {
            o.setSortOrder(req.sortOrder());
        }

        o.setStartsAt(req.startsAt());
        o.setEndsAt(req.endsAt());
    }

    private Offer.Placement parsePlacement(String raw) {
        try {
            return Offer.Placement.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "Zona de afișare trebuie să fie HOME_PROMO sau BENEFIT_BAR.");
        }
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    // ---------------- Populare inițială ----------------

    /**
     * Creează ofertele implicite dacă tabelul este gol, ca prima pagină să
     * arate exact ca înainte de mutarea conținutului în baza de date. Sunt
     * exact promoția de transport gratuit și cele patru cartonașe de beneficii
     * care erau scrise în {@code Home.jsx}.
     *
     * <p>Verificarea se face pe titlu, nu doar pe „tabelul este gol": dacă
     * operatorul șterge intenționat o ofertă implicită, ea nu trebuie să
     * reapară la următoarea pornire a aplicației.</p>
     */
    public void seedDefaults() {
        if (offerRepository.count() > 0) {
            return;
        }

        Offer promo = new Offer();
        promo.setTitle("Transport gratuit");
        promo.setHeadline("la orice comandă");
        promo.setDescription("Fără prag minim și fără costuri ascunse. Oferta se resetează la miezul nopții.");
        promo.setBadgeLabel("Ofertă activă");
        promo.setCtaLabel("Profită acum");
        promo.setCtaUrl("/products");
        promo.setIcon("truck");
        promo.setAccent("var(--xx-magenta)");
        promo.setPlacement(Offer.Placement.HOME_PROMO);
        promo.setShowTimer(true);
        promo.setRecurringDaily(true);
        promo.setSortOrder(0);
        offerRepository.save(promo);

        offerRepository.save(benefit("Livrare rapidă", "Transport gratuit, oriunde în țară",
                "truck", "var(--xx-cyan)", 0));
        offerRepository.save(benefit("Garanție completă", "Produse originale, garanție legală",
                "shield", "var(--xx-lime)", 1));
        offerRepository.save(benefit("Cumpărăm electronice", "Evaluare corectă, plată pe loc",
                "coins", "var(--xx-amber)", 2));
        offerRepository.save(benefit("Plata la livrare", "Plătești doar când primești coletul",
                "tag", "var(--xx-purple)", 3));
    }

    private Offer benefit(String title, String headline, String icon, String accent, int order) {
        Offer o = new Offer();
        o.setTitle(title);
        o.setHeadline(headline);
        o.setIcon(icon);
        o.setAccent(accent);
        o.setPlacement(Offer.Placement.BENEFIT_BAR);
        o.setShowTimer(false);
        o.setRecurringDaily(false);
        o.setSortOrder(order);
        return o;
    }
}
