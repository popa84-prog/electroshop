package com.electroshop.service.imaging;

import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import com.electroshop.service.AuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Completarea mărcii și a codului de produs, acolo unde lipsesc.
 *
 * <h2>De ce înaintea oricărei surse de fotografii</h2>
 *
 * Măsurat pe catalogul real, pe un eșantion de 100 de produse fără imagine:
 * 36 nu au marcă nici în coloană, nici deductibilă din denumire, iar alte 27 au
 * marcă dar nimic care să semene a cod. <b>63 din 100 nu pot fi identificate de
 * nicio sursă</b> — nici Icecat, nici feed-ul unui distribuitor, nici o
 * potrivire automată cu fotografiile proprii. Toate au nevoie de aceeași
 * pereche: cine face produsul și care este codul lui.
 *
 * <p>De aceea pasul acesta vine primul. Nu aduce nicio fotografie, dar fără el
 * fiecare pas următor se lovește de același zid, iar munca s-ar reface de trei
 * ori.</p>
 *
 * <h2>Propune, nu scrie</h2>
 *
 * Serviciul citește denumirea și propune ce a găsit, cu un scor. Nu scrie nimic
 * până la confirmare, din același motiv pentru care nici potrivirea de
 * fotografii nu publică singură: „Acumulator pentru Sony DSC-RX100 Sony NP-BX1"
 * are două coduri plauzibile, iar cel cu scorul mai mare este aparatul foto, nu
 * acumulatorul vândut. Diferența nu este în text, este în ce se vinde.
 *
 * <p>Confirmarea se face totuși <em>în masă</em>, pentru că majoritatea
 * cazurilor sunt evidente. Operatorul bifează ce e corect și scrie manual doar
 * excepțiile — altfel ar completa 251 de formulare.</p>
 */
@Service
public class CatalogIdentityService {

    private final ProductRepository productRepository;
    private final AuditService auditService;

    public CatalogIdentityService(ProductRepository productRepository, AuditService auditService) {
        this.productRepository = productRepository;
        this.auditService = auditService;
    }

    /**
     * Produsele cărora le lipsește marca sau codul, cu ce se poate deduce.
     *
     * @param doarFaraImagine când e adevărat, se uită numai la produsele fără
     *                        fotografie — acelea sunt cele care blochează pașii
     *                        următori. Fals parcurge tot catalogul, util o dată,
     *                        ca igienă generală.
     * @param limita          câte rânduri se întorc într-o rundă
     */
    @Transactional(readOnly = true)
    public Raport propune(boolean doarFaraImagine, int limita) {
        List<Product> produse = doarFaraImagine
                ? productRepository.findActiveWithNoImage()
                : productRepository.findAll();
        List<String> marciCunoscute = productRepository.findAllBrands();

        List<Propunere> propuneri = new ArrayList<>();
        int faraMarcaSiDupa = 0;
        int faraCodSiDupa = 0;
        int complete = 0;

        for (Product p : produse) {
            boolean lipseMarca = gol(p.getBrand());
            boolean lipseCod = gol(p.getMpn());
            if (!lipseMarca && !lipseCod) {
                complete++;
                continue;
            }

            String marcaGhicita = lipseMarca
                    ? BrandNormalizer.ghicesteDinDenumire(p.getName(), marciCunoscute)
                    : null;
            String marcaDeLucru = lipseMarca ? marcaGhicita : p.getBrand();

            List<MpnExtractor.Candidat> coduri = lipseCod
                    ? MpnExtractor.candidati(p.getName(), marcaDeLucru)
                    : List.of();

            if (lipseMarca && marcaGhicita == null) {
                faraMarcaSiDupa++;
            }
            if (lipseCod && coduri.isEmpty()) {
                faraCodSiDupa++;
            }

            // Un rând fără nicio propunere nu are ce căuta în ecranul de
            // confirmare: nu e nimic de bifat. Rămâne numărat în raport, ca să
            // se vadă cât nu se poate rezolva automat, dar nu ocupă un loc.
            if (marcaGhicita == null && coduri.isEmpty()) {
                continue;
            }
            if (propuneri.size() >= limita) {
                continue;
            }

            propuneri.add(new Propunere(
                    p.getId(), p.getName(), p.getBrand(), p.getMpn(),
                    marcaGhicita,
                    coduri.stream().limit(4)
                            .map(c -> new CodPropus(c.cod(), c.scor()))
                            .toList()));
        }

        int incomplete = produse.size() - complete;
        return new Raport(produse.size(), complete, incomplete,
                faraMarcaSiDupa, faraCodSiDupa, List.copyOf(propuneri));
    }

    /**
     * Scrie marca și codul confirmate.
     *
     * <p>Completează doar câmpurile goale. O valoare deja existentă nu se
     * suprascrie niciodată dintr-o ghicire: dacă cineva a scris manual codul,
     * omul acela a știut mai bine decât o expresie regulată.</p>
     *
     * @param intrari perechile confirmate, id de produs către ce se scrie
     * @return câte produse au fost efectiv modificate
     */
    @Transactional
    public Rezultat aplica(List<Intrare> intrari) {
        int marciScrise = 0;
        int coduriScrise = 0;
        List<Long> inexistente = new ArrayList<>();

        for (Intrare i : intrari) {
            Product p = productRepository.findById(i.productId()).orElse(null);
            if (p == null) {
                inexistente.add(i.productId());
                continue;
            }
            boolean modificat = false;
            if (!gol(i.marca()) && gol(p.getBrand())) {
                p.setBrand(i.marca().trim());
                marciScrise++;
                modificat = true;
            }
            if (!gol(i.mpn()) && gol(p.getMpn())) {
                p.setMpn(i.mpn().trim().toUpperCase(java.util.Locale.ROOT));
                coduriScrise++;
                modificat = true;
            }
            if (modificat) {
                productRepository.save(p);
            }
        }

        auditService.log("CATALOG_IDENTITY_FILLED", "Product", null,
                marciScrise + " mărci și " + coduriScrise + " coduri de produs completate din denumiri, "
                        + "pentru ca produsele să poată fi identificate la surse externe de conținut.");

        return new Rezultat(marciScrise, coduriScrise, List.copyOf(inexistente));
    }

    /** Verifică dacă produsul există, pentru mesaje de eroare utile. */
    @Transactional(readOnly = true)
    public Product cere(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Produsul nu există: " + id));
    }

    private static boolean gol(String s) {
        return s == null || s.isBlank();
    }

    /** Un cod propus, cu încrederea extractorului. */
    public record CodPropus(String cod, double scor) {
    }

    /**
     * Un produc cu identitate incompletă și ce se poate deduce pentru el.
     *
     * @param productId       produsul
     * @param denumire        denumirea comercială, din care s-a dedus totul
     * @param marcaActuala    ce scrie acum în coloană, poate fi null
     * @param mpnActual       codul actual, poate fi null
     * @param marcaPropusa    marca recunoscută în denumire, sau null
     * @param coduriPropuse   codurile candidate, cel mai promițător primul
     */
    public record Propunere(Long productId, String denumire, String marcaActuala, String mpnActual,
                            String marcaPropusa, List<CodPropus> coduriPropuse) {
    }

    /** Ce se confirmă pentru un produs. Câmpurile goale se ignoră. */
    public record Intrare(Long productId, String marca, String mpn) {
    }

    /**
     * Starea identității în catalog.
     *
     * @param examinate       câte produse s-au parcurs
     * @param complete        câte au și marcă, și cod
     * @param incomplete      câte au nevoie de completare
     * @param faraMarcaDeloc  câte nu au marcă și nici nu se poate deduce una
     * @param faraCodDeloc    câte nu au cod și nici nu se poate deduce unul
     * @param propuneri       rândurile pe care operatorul le poate confirma
     */
    public record Raport(int examinate, int complete, int incomplete,
                         int faraMarcaDeloc, int faraCodDeloc, List<Propunere> propuneri) {
    }

    /** Ce s-a scris efectiv. */
    public record Rezultat(int marciScrise, int coduriScrise, List<Long> inexistente) {

        public Map<String, Object> caMesaj() {
            return Map.of("marciScrise", marciScrise, "coduriScrise", coduriScrise,
                    "inexistente", inexistente);
        }
    }
}
