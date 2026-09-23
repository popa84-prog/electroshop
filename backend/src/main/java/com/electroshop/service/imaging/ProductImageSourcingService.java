package com.electroshop.service.imaging;

import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.Product;
import com.electroshop.model.ProductImage;
import com.electroshop.repository.ProductRepository;
import com.electroshop.service.AuditService;
import com.electroshop.service.CloudinaryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Propune fotografii pentru produsele care nu au niciuna, din catalogul Icecat.
 *
 * <h2>Propune, nu publică</h2>
 *
 * Serviciul nu scrie nicio imagine pe cont propriu. Compune propuneri pe care
 * operatorul le vede alături de denumirea noastră și de denumirea din Icecat,
 * și abia o confirmare explicită declanșează urcarea.
 *
 * <p>Separarea nu este prudență de formă. În catalog există produsul
 * „Acumulator pentru Sony DSC-RX100 Sony NP-BX1": codul cu cea mai mare
 * încredere, {@code DSC-RX100}, este aparatul foto, nu acumulatorul vândut.
 * Icecat are fișe pentru amândouă și ar întoarce o poză perfect validă — a
 * produsului greșit. O poză greșită pe produsul greșit este mai rea decât
 * lipsa ei: generează comandă, retur și reclamație.</p>
 *
 * <h2>Ordinea încercărilor</h2>
 *
 * Pentru fiecare produs se încearcă cei mai promițători câțiva candidați, în
 * ordinea scorului, iar căutarea se oprește la primul care rezolvă. Nu se
 * interoghează toți: Icecat are o politică de utilizare corectă pe nivelul
 * gratuit, iar un candidat de rang cinci care nimerește ceva este mai probabil
 * o coincidență decât o potrivire.
 */
@Service
public class ProductImageSourcingService {

    /** Câți candidați de cod se încearcă per produs, în ordinea încrederii. */
    private static final int CANDIDATI_INCERCATI = 3;

    /** Folderul Cloudinary, separat ca să se vadă ce a venit din catalog. */
    private static final String FOLDER = "electroshop/icecat";

    private final ProductRepository productRepository;
    private final IcecatClient icecat;
    private final CloudinaryService cloudinary;
    private final AuditService auditService;

    public ProductImageSourcingService(ProductRepository productRepository,
                                       IcecatClient icecat,
                                       CloudinaryService cloudinary,
                                       AuditService auditService) {
        this.productRepository = productRepository;
        this.icecat = icecat;
        this.cloudinary = cloudinary;
        this.auditService = auditService;
    }

    /** Dacă integrarea are acreditări. */
    public boolean esteConfigurat() {
        return icecat.esteConfigurat();
    }

    /**
     * Caută propuneri pentru produsele fără imagine.
     *
     * @param limita câte produse se examinează într-o rundă. Interfața cere
     *               loturi mici pentru că fiecare produs înseamnă până la trei
     *               cereri HTTP, iar o rundă peste tot catalogul ar ține
     *               operatorul într-un ecran gol un minut.
     */
    @Transactional(readOnly = true)
    public Raport propune(int limita) {
        if (!icecat.esteConfigurat()) {
            throw new BadRequestException(
                    "Integrarea Icecat nu este configurată. Lipsesc ICECAT_SHOPNAME și ICECAT_API_TOKEN.");
        }

        List<Product> faraImagine = productRepository.findActiveWithNoImage();
        List<String> marciCunoscute = productRepository.findAllBrands();

        List<Propunere> propuneri = new ArrayList<>();
        // De ce nu s-a găsit, pe categorii. Fără defalcarea asta, o rundă cu o
        // singură potrivire din 37 de interogări arată identic indiferent dacă
        // produsele lipsesc din Icecat, dacă mărcile lor nu sunt în nivelul
        // gratuit sau dacă jetonul a fost refuzat — trei cauze cu trei remedii
        // diferite. Prima rulare reală a fost exact acest caz.
        Map<IcecatClient.Motiv, Integer> motive = new EnumMap<>(IcecatClient.Motiv.class);
        List<String> exempleEsec = new ArrayList<>();
        int examinate = 0;
        int faraMarca = 0;
        int faraCod = 0;

        for (Product p : faraImagine) {
            if (examinate >= limita) {
                break;
            }
            examinate++;

            // Marca din coloană, iar dacă lipsește, ghicită din denumire: 118
            // din cele 307 produse fără imagine nu au marca completată, dar o
            // au scrisă în denumire.
            String marcaBruta = (p.getBrand() != null && !p.getBrand().isBlank())
                    ? p.getBrand()
                    : BrandNormalizer.ghicesteDinDenumire(p.getName(), marciCunoscute);
            String marca = BrandNormalizer.pentruIcecat(marcaBruta);
            if (marca == null) {
                faraMarca++;
                continue;
            }

            List<MpnExtractor.Candidat> candidati = MpnExtractor.candidati(p.getName(), marca);
            if (candidati.isEmpty()) {
                faraCod++;
                continue;
            }

            IcecatClient.Raspuns ultimul = null;
            for (int i = 0; i < Math.min(CANDIDATI_INCERCATI, candidati.size()); i++) {
                MpnExtractor.Candidat c = candidati.get(i);
                ultimul = icecat.interogheaza(marca, c.cod());
                IcecatClient.Rezultat r = ultimul.rezultat();
                if (r != null) {
                    propuneri.add(new Propunere(
                            p.getId(), p.getName(), p.getBrand(), marca, c.cod(), c.scor(),
                            r.icecatId(), r.titlu(), r.codMarca(), r.gtin(),
                            List.copyOf(r.imaginiDistincte()),
                            marcaBruta != null && !marcaBruta.equals(p.getBrand())));
                    break;
                }
                // Un jeton refuzat sau o cotă depășită se vor repeta identic la
                // fiecare candidat. Oprim seria în loc să ardem interogări.
                if (ultimul.motiv() == IcecatClient.Motiv.NEAUTORIZAT
                        || ultimul.motiv() == IcecatClient.Motiv.COTA_DEPASITA) {
                    break;
                }
            }
            if (ultimul != null && ultimul.rezultat() == null) {
                motive.merge(ultimul.motiv(), 1, Integer::sum);
                if (exempleEsec.size() < 8) {
                    exempleEsec.add(marca + " " + candidati.get(0).cod()
                            + " → " + ultimul.motiv()
                            + (ultimul.detaliu() == null ? "" : ": " + scurt(ultimul.detaliu())));
                }
            }
        }

        Map<String, Integer> motiveText = new java.util.LinkedHashMap<>();
        motive.forEach((k, v) -> motiveText.put(k.name(), v));

        return new Raport(faraImagine.size(), examinate, propuneri.size(), faraMarca, faraCod,
                List.copyOf(propuneri), Map.copyOf(motiveText), List.copyOf(exempleEsec));
    }

    /** Interogare unică, pentru diagnostic. Nu schimbă nimic. */
    public IcecatClient.Raspuns diagnostic(String marca, String cod) {
        return icecat.interogheaza(BrandNormalizer.pentruIcecat(marca), cod);
    }

    /** Taie textul de eroare la o lungime citibilă într-un tabel. */
    private static String scurt(String text) {
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= 120 ? t : t.substring(0, 117) + "…";
    }

    /**
     * Confirmă o propunere: preia imaginea, o urcă și o leagă de produs.
     *
     * <p>Se scriu în același timp și codul de produs, și codul de bare venite de
     * la Icecat. Al doilea este motivul pentru care merită integrarea chiar și
     * când poza nu ajunge: catalogul nu are niciun cod de bare, iar fără ele nu
     * există feed pentru Google Shopping și nicio altă potrivire automată.</p>
     *
     * @param productId produsul de completat
     * @param adresa    adresa imaginii alese dintre cele propuse
     * @param icecatId  fișa din care provine
     * @param marca     marca sub care s-a făcut interogarea
     * @param mpn       codul de produs confirmat
     * @param gtin      codul de bare, dacă a venit
     */
    @Transactional
    public Product aplica(Long productId, String adresa, String icecatId,
                          String marca, String mpn, String gtin) {
        Product p = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Produsul nu există: " + productId));

        if (adresa == null || adresa.isBlank()) {
            throw new BadRequestException("Adresa imaginii lipsește.");
        }
        if (!cloudinary.isConfigured()) {
            throw new BadRequestException("Cloudinary nu este configurat, imaginea nu poate fi preluată.");
        }

        CloudinaryService.UploadResult urcat = cloudinary.uploadFromUrl(adresa, FOLDER);

        ProductImage imagine = new ProductImage(p, urcat.url(), urcat.publicId(), p.getImages().size());
        imagine.setWidth(urcat.width());
        imagine.setHeight(urcat.height());
        imagine.setFormat(urcat.format());
        imagine.setBytes(urcat.bytes());
        imagine.setSource("ICECAT");
        imagine.setSourceBrand(marca);
        imagine.setSourceRef(icecatId);
        imagine.setSourceUrl(adresa);
        // Nivelul contează juridic: pe Open Icecat marca a plătit ca pozele să
        // ajungă la revânzători, pe nivelul complet Icecat dă datele, nu
        // drepturile asupra imaginilor. Scris aici, se poate audita ulterior.
        imagine.setLicenceRef("openicecat");

        // Prima imagine a produsului devine cea principală și se oglindește pe
        // produs, pentru ca toate cardurile existente să funcționeze neschimbat.
        boolean prima = p.getImages().isEmpty();
        imagine.setPrimary(prima);
        p.getImages().add(imagine);
        if (prima) {
            p.setImageUrl(urcat.url());
        }

        if (mpn != null && !mpn.isBlank() && (p.getMpn() == null || p.getMpn().isBlank())) {
            p.setMpn(mpn.trim());
        }
        if (gtin != null && !gtin.isBlank() && (p.getGtin() == null || p.getGtin().isBlank())) {
            p.setGtin(gtin.trim());
        }

        Product salvat = productRepository.save(p);

        auditService.log("PRODUCT_IMAGE_SOURCED", "Product", p.getId(),
                "Imagine preluată din Icecat pentru „" + p.getName() + "”. Marcă interogată: " + marca
                        + ", cod: " + mpn + ", fișă Icecat: " + icecatId
                        + (gtin != null && !gtin.isBlank() ? ", GTIN completat: " + gtin : "")
                        + ". Sursă: " + adresa);

        return salvat;
    }

    /**
     * O potrivire găsită, gata de confirmat sau de respins.
     *
     * @param productId       produsul nostru
     * @param denumireNoastra cum se cheamă la noi
     * @param marcaCatalog    marca din coloana noastră, poate fi null
     * @param marcaInterogata marca efectiv trimisă la Icecat
     * @param codIncercat     codul care a dat rezultat
     * @param increderea      scorul extractorului pentru codul acela
     * @param icecatId        fișa găsită
     * @param titluIcecat     cum se cheamă acolo — comparația celor două
     *                        denumiri este verificarea principală a
     *                        operatorului
     * @param codMarca        codul așa cum îl scrie producătorul
     * @param gtin            codul de bare, dacă există în fișă
     * @param imagini         adresele propuse, cea mai mare prima
     * @param marcaGhicita    dacă marca a fost dedusă din denumire, nu citită
     *                        din coloană — semnal că potrivirea merită privită
     *                        cu mai multă atenție
     */
    public record Propunere(Long productId, String denumireNoastra, String marcaCatalog,
                            String marcaInterogata, String codIncercat, double increderea,
                            String icecatId, String titluIcecat, String codMarca, String gtin,
                            List<String> imagini, boolean marcaGhicita) {
    }

    /**
     * Rezultatul unei runde.
     *
     * @param totalFaraImagine câte produse active nu au nicio imagine
     * @param examinate        câte s-au examinat în runda aceasta
     * @param gasite           pentru câte s-a găsit o fișă
     * @param sariteFaraMarca  câte nu au marcă nici în coloană, nici în denumire
     * @param sariteFaraCod    câte au marcă, dar nimic care să semene a cod
     * @param propuneri        potrivirile, de confirmat una câte una
     * @param motive           de ce au eșuat celelalte, numărate pe categorii
     * @param exempleEsec      câteva eșecuri cu textul brut de la Icecat,
     *                         pentru că un număr spune că ceva nu merge, iar
     *                         mesajul spune ce anume
     */
    public record Raport(int totalFaraImagine, int examinate, int gasite,
                         int sariteFaraMarca, int sariteFaraCod, List<Propunere> propuneri,
                         Map<String, Integer> motive, List<String> exempleEsec) {
    }
}
