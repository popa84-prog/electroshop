package com.electroshop.service.imaging;

import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.Product;
import com.electroshop.model.ProductImage;
import com.electroshop.model.Supplier;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.SupplierRepository;
import com.electroshop.service.AuditService;
import com.electroshop.service.CloudinaryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Preluarea fotografiilor din feedul unui distribuitor.
 *
 * <h2>Temeiul juridic, pentru că aici este singura diferență care contează</h2>
 *
 * Tehnic, a lua o fotografie dintr-un feed și a lua una de pe site-ul unui
 * magazin este aceeași operație. Juridic sunt opuse.
 *
 * <p>CJUE a stabilit în <b>Renckhoff (C-161/17)</b> că republicarea unei
 * fotografii pe serverul propriu este reproducere și comunicare publică
 * neautorizată, chiar dacă fotografia era liber accesibilă la sursă. În
 * <b>GS Media (C-160/15)</b> a adăugat că un site comercial este prezumat să
 * cunoască nelegalitatea sursei, deci nici simpla trimitere către imaginea
 * altcuiva nu este o soluție. Legea 8/1996, art. 139 alin. (2) lit. b),
 * permite despăgubiri de <b>trei ori</b> remunerația legal datorată.</p>
 *
 * <p>Feedul de distribuitor este altceva: distribuitorul îl pune la dispoziție
 * tocmai pentru ca revânzătorii lui să vândă marfa, iar dreptul de folosință
 * vine din relația comercială. Acel drept nu se presupune — se dovedește. De
 * aceea unealta cere obligatoriu două lucruri și le scrie pe fiecare imagine:
 * <b>care distribuitor</b> a dat fișierul și <b>pe ce bază</b> avem dreptul să
 * îl folosim. O fotografie fără aceste două informații este, la o verificare,
 * imposibil de deosebit de una copiată.</p>
 *
 * <h2>Potrivirea: de la cheie sigură la simplă asemănare</h2>
 *
 * Ordinea este de la tare la slab, iar tăria contează pentru ce se bifează
 * automat și ce nu:
 *
 * <ol>
 *   <li><b>EAN</b> — codul de bare identifică exact articolul fizic. Dacă se
 *       potrivește, este același produs.</li>
 *   <li><b>Cod producător</b> — aproape la fel de tare; ambiguu doar între
 *       variante de culoare la unii producători.</li>
 *   <li><b>Codul distribuitorului față de SKU-ul nostru</b> — valid doar dacă
 *       SKU-ul nostru chiar vine de la acel distribuitor.</li>
 *   <li><b>Asemănarea denumirilor</b> — sugestie, niciodată certitudine.</li>
 * </ol>
 *
 * <p>Primele două se propun bifate. Ultimele două nu: o fotografie greșită pe
 * un produs se vede abia când clientul deschide cutia, iar atunci costă un
 * retur și încrederea, nu un clic de corectură.</p>
 */
@Service
public class DistributorFeedService {

    /** Dosarul Cloudinary, separat de fotografiile proprii și de Icecat. */
    private static final String FOLDER = "electroshop/distribuitor";

    /** Câte fotografii se preiau pentru un produs. */
    private static final int MAX_PE_PRODUS = 5;

    /**
     * Câte imagini se preiau într-un singur apel.
     *
     * <p>Fiecare preluare este o cerere pe care Cloudinary o face către
     * distribuitor și o așteptare pentru noi. Peste această limită, cererea
     * HTTP ar depăși timpul de răspuns al serverului, iar operatorul ar vedea o
     * eroare pentru o operație care de fapt a reușit pe jumătate. Interfața
     * trimite în tranșe.</p>
     */
    private static final int MAX_IMAGINI_PE_APEL = 40;

    /**
     * Limitele coloanelor din baza de date, respectate aici și nu la flush.
     *
     * <h2>De ce contează atât de mult</h2>
     *
     * Preluarea imaginii pe Cloudinary se întâmplă <em>înainte</em> de a se
     * scrie rândul, iar Cloudinary nu participă la tranzacția noastră. O
     * valoare prea lungă ar arunca abia la flush, ar anula toată tranzacția, și
     * ar lăsa pe Cloudinary toate imaginile deja urcate — plătite, dar legate
     * de nimic. De aceea fiecare valoare este verificată sau scurtată aici,
     * unde eșecul costă un rând din raport, nu un lot întreg.
     */
    private static final int LUNGIME_MARCA_SURSA = 80;
    private static final int LUNGIME_REFERINTA = 120;
    private static final int LUNGIME_ADRESA = 500;
    private static final int LUNGIME_TEMEI = 120;
    private static final int LUNGIME_MPN = 80;

    /**
     * Câte fotografii poate avea un produs în total.
     *
     * <p>Mai mari decât {@link #MAX_PE_PRODUS} pentru că produsul poate avea
     * deja fotografii proprii sau din Icecat, iar feedul adaugă la galerie, nu
     * o înlocuiește. Peste opt, galeria nu mai este informație, este derulare.</p>
     */
    private static final int MAX_TOTAL_GALERIE = 8;

    /** Sub atâtea cuvinte comune, asemănarea denumirilor nu spune nimic. */
    private static final int MIN_CUVINTE_COMUNE = 2;

    /** Cât din denumirea mai scurtă trebuie să se regăsească în cealaltă. */
    private static final double PRAG_ASEMANARE = 0.6;

    private final DistributorFeedParser parser;
    private final ProductRepository productRepository;
    private final SupplierRepository supplierRepository;
    private final CloudinaryService cloudinary;
    private final AuditService auditService;

    public DistributorFeedService(DistributorFeedParser parser,
                                  ProductRepository productRepository,
                                  SupplierRepository supplierRepository,
                                  CloudinaryService cloudinary,
                                  AuditService auditService) {
        this.parser = parser;
        this.productRepository = productRepository;
        this.supplierRepository = supplierRepository;
        this.cloudinary = cloudinary;
        this.auditService = auditService;
    }

    /**
     * Citește feedul și arată ce s-ar lega de ce. Nu preia nicio imagine.
     *
     * @param file             feedul, .xlsx sau .xml
     * @param doarFaraImagine  când e adevărat, se propun numai produsele fără
     *                         fotografie; altfel se arată și cele care au deja
     *                         una, ca sursă de fotografii suplimentare
     * @param limita           câte potriviri se întorc într-o rundă
     */
    @Transactional(readOnly = true)
    public Raport analizeaza(MultipartFile file, boolean doarFaraImagine, int limita) {
        DistributorFeedParser.Feed feed = parser.citeste(file);
        if (feed.randuri().isEmpty()) {
            throw new BadRequestException("Feedul a fost citit, dar nu are niciun rând cu "
                    + "identitate și fotografie. Rânduri citite: " + feed.totalCitite()
                    + ", fără identificare: " + feed.faraIdentitate()
                    + ", fără adresă de fotografie: " + feed.faraImagini() + ".");
        }

        Index index = construiesteIndex();

        List<Potrivire> potriviri = new ArrayList<>();
        Map<String, Integer> dupaCheie = new LinkedHashMap<>();
        int nepotrivite = 0;
        int sariteAuImagine = 0;
        // Un produs nu se propune de două ori, chiar dacă feedul îl are pe două
        // rânduri — altfel operatorul ar bifa același produs de două ori și ar
        // primi aceeași fotografie duplicată în galerie.
        Set<Long> dejaPropuse = new HashSet<>();

        for (DistributorFeedParser.RandFeed rand : feed.randuri()) {
            Rezultat r = potriveste(rand, index);
            if (r == null) {
                nepotrivite++;
                continue;
            }
            if (!dejaPropuse.add(r.produs().getId())) {
                continue;
            }
            boolean areImagine = areImagine(r.produs());
            if (areImagine && doarFaraImagine) {
                sariteAuImagine++;
                continue;
            }
            if (potriviri.size() >= limita) {
                continue;
            }

            dupaCheie.merge(r.cum(), 1, Integer::sum);
            potriviri.add(new Potrivire(
                    r.produs().getId(), r.produs().getName(), r.produs().getBrand(),
                    r.produs().getMpn(), r.produs().getGtin(), areImagine,
                    rand.rand(), rand.denumire(), rand.marca(),
                    rand.codProducator(), rand.gtin(),
                    rand.imagini().size() > MAX_PE_PRODUS
                            ? List.copyOf(rand.imagini().subList(0, MAX_PE_PRODUS))
                            : rand.imagini(),
                    r.cum(), r.sigura()));
        }

        return new Raport(feed.totalCitite(), feed.randuri().size(), feed.faraIdentitate(),
                feed.faraImagini(), potriviri.size(), nepotrivite, sariteAuImagine,
                Map.copyOf(dupaCheie), feed.coloaneGasite(), feed.elementXml(),
                List.copyOf(potriviri));
    }

    /**
     * Preia imaginile confirmate și le atașează produselor.
     *
     * @param supplierId     distribuitorul de la care vine feedul
     * @param temeiLicenta   pe ce bază avem dreptul: numărul contractului de
     *                       distribuție, acordul scris, ce anume există. Se
     *                       scrie pe fiecare imagine și nu poate lipsi.
     * @param intrari        ce s-a confirmat
     */
    @Transactional
    public Aplicare aplica(Long supplierId, String temeiLicenta, List<Intrare> intrari) {
        if (intrari == null || intrari.isEmpty()) {
            throw new BadRequestException("Nu s-a confirmat nicio potrivire.");
        }
        if (temeiLicenta == null || temeiLicenta.isBlank()) {
            throw new BadRequestException("Temeiul folosirii imaginilor nu poate lipsi. "
                    + "Scrieți contractul sau acordul în baza căruia distribuitorul pune "
                    + "fotografiile la dispoziția revânzătorilor.");
        }
        if (temeiLicenta.trim().length() > LUNGIME_TEMEI) {
            throw new BadRequestException("Temeiul folosirii este prea lung: "
                    + temeiLicenta.trim().length() + " caractere, maximul este " + LUNGIME_TEMEI
                    + ". Scrieți referința înțelegerii, nu textul ei.");
        }
        if (!cloudinary.isConfigured()) {
            throw new BadRequestException("Cloudinary nu este configurat, imaginile nu pot fi preluate.");
        }
        Supplier furnizor = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new ResourceNotFoundException("Furnizorul nu există: " + supplierId));

        int cerute = intrari.stream().mapToInt(i -> i.imagini() == null ? 0 : i.imagini().size()).sum();
        if (cerute > MAX_IMAGINI_PE_APEL) {
            throw new BadRequestException("Prea multe imagini într-o singură cerere: " + cerute
                    + ". Maximul este " + MAX_IMAGINI_PE_APEL
                    + ", pentru ca preluarea să se încheie înainte de expirarea cererii.");
        }

        String temei = temeiLicenta.trim();
        int produseModificate = 0;
        int imaginiUrcate = 0;
        List<Esec> esecuri = new ArrayList<>();

        for (Intrare intrare : intrari) {
            Product p = productRepository.findById(intrare.productId()).orElse(null);
            if (p == null) {
                esecuri.add(new Esec(intrare.productId(), null, "produsul nu mai există"));
                continue;
            }
            List<String> adrese = intrare.imagini() == null ? List.of() : intrare.imagini();
            if (adrese.isEmpty()) {
                continue;
            }

            boolean ceva = false;
            for (String adresa : adrese) {
                if (p.getImages().size() >= MAX_TOTAL_GALERIE) {
                    esecuri.add(new Esec(p.getId(), adresa, "produsul are deja prea multe fotografii"));
                    continue;
                }
                if (adresa == null || adresa.isBlank()) {
                    continue;
                }
                String curata = adresa.trim();
                if (!curata.toLowerCase(Locale.ROOT).startsWith("http")) {
                    esecuri.add(new Esec(p.getId(), curata, "adresa nu este http(s)"));
                    continue;
                }
                // O adresă prea lungă se refuză, nu se scurtează: o adresă
                // tăiată nu mai este adresa sursei, iar coloana de proveniență
                // ar conține exact informația care nu se poate verifica.
                if (curata.length() > LUNGIME_ADRESA) {
                    esecuri.add(new Esec(p.getId(), curata.substring(0, 80) + "…",
                            "adresa depășește " + LUNGIME_ADRESA + " de caractere"));
                    continue;
                }
                // Aceeași adresă nu se preia de două ori pentru același produs:
                // provenienta scrisa anterior ne spune ce a intrat deja.
                boolean existaDeja = p.getImages().stream()
                        .anyMatch(im -> curata.equals(im.getSourceUrl()));
                if (existaDeja) {
                    continue;
                }

                try {
                    CloudinaryService.UploadResult urcat = cloudinary.uploadFromUrl(curata, FOLDER);
                    ProductImage imagine = new ProductImage(p, urcat.url(), urcat.publicId(),
                            p.getImages().size());
                    imagine.setWidth(urcat.width());
                    imagine.setHeight(urcat.height());
                    imagine.setFormat(urcat.format());
                    imagine.setBytes(urcat.bytes());
                    // „SUPPLIER" este valoarea documentată în ProductImage
                    // pentru feedul de distribuitor. Un al doilea nume pentru
                    // aceeași sursă ar rupe exact interogarea pentru care
                    // coloana există: „șterge toate imaginile venite de la X".
                    imagine.setSource("SUPPLIER");
                    imagine.setSourceBrand(taie(furnizor.getName(), LUNGIME_MARCA_SURSA));
                    imagine.setSourceRef(referinta(furnizor, intrare));
                    imagine.setSourceUrl(curata);
                    imagine.setLicenceRef(temei);

                    boolean prima = p.getImages().isEmpty();
                    imagine.setPrimary(prima);
                    p.getImages().add(imagine);
                    if (prima) {
                        p.setImageUrl(urcat.url());
                    }
                    imaginiUrcate++;
                    ceva = true;
                } catch (RuntimeException e) {
                    esecuri.add(new Esec(p.getId(), curata, scurt(e.getMessage())));
                }
            }

            // Codul și EAN-ul din feed completează golurile. Nu suprascriu:
            // dacă la noi scrie deja un cod, cineva l-a pus acolo cu un motiv.
            if (prezent(intrare.codProducator()) && !prezent(p.getMpn())) {
                String cod = intrare.codProducator().trim().toUpperCase(Locale.ROOT);
                if (cod.length() <= LUNGIME_MPN) {
                    p.setMpn(cod);
                    ceva = true;
                }
            }
            // EAN-ul se scrie numai dacă arată ca un EAN. Un feed poate pune în
            // coloana aceea „n/a", „vezi cutia" sau două coduri lipite; scrise
            // în catalog, ar strica potrivirea următorului feed, care caută
            // exact pe coloana asta. Iar peste 14 caractere ar arunca la flush
            // și ar anula tot lotul.
            if (!prezent(p.getGtin())) {
                String ean = gtinValid(intrare.gtin());
                if (ean != null) {
                    p.setGtin(ean);
                    ceva = true;
                }
            }

            if (ceva) {
                productRepository.save(p);
                produseModificate++;
            }
        }

        auditService.log("PRODUCT_IMAGES_FROM_DISTRIBUTOR", "Product", null,
                imaginiUrcate + " fotografii preluate din feedul furnizorului „" + furnizor.getName()
                        + "” pentru " + produseModificate + " produse. Temeiul folosirii: " + temei
                        + (esecuri.isEmpty() ? "." : ". Eșecuri: " + esecuri.size() + "."));

        return new Aplicare(produseModificate, imaginiUrcate, List.copyOf(esecuri));
    }

    private String referinta(Supplier furnizor, Intrare intrare) {
        StringBuilder sb = new StringBuilder("furnizor:").append(furnizor.getId());
        if (prezent(intrare.codDistribuitor())) {
            sb.append(" cod:").append(intrare.codDistribuitor().trim());
        }
        return taie(sb.toString(), LUNGIME_REFERINTA);
    }

    /** Scurtează la limita coloanei. Null rămâne null. */
    private static String taie(String valoare, int limita) {
        if (valoare == null) {
            return null;
        }
        return valoare.length() <= limita ? valoare : valoare.substring(0, limita);
    }

    /**
     * EAN-ul curățat, sau {@code null} dacă valoarea nu este un cod de bare.
     *
     * <p>Se acceptă 8, 12, 13 sau 14 cifre: EAN-8, UPC-A, EAN-13 și GTIN-14,
     * adică tot ce se întâlnește în comerț. Zeroul din față se păstrează —
     * pentru asta coloana este text, nu număr.</p>
     */
    private static String gtinValid(String valoare) {
        if (valoare == null || valoare.isBlank()) {
            return null;
        }
        String cifre = valoare.trim().replaceAll("[^0-9]", "");
        if (cifre.length() != valoare.trim().length()) {
            // Conținea și altceva decât cifre: „EAN 5901234123457" sau două
            // coduri lipite. Nu se ghicește care parte este codul.
            return null;
        }
        return switch (cifre.length()) {
            case 8, 12, 13, 14 -> cifre;
            default -> null;
        };
    }

    // ------------------------------------------------------------ potrivire

    /** Indexul catalogului, construit o dată pentru tot feedul. */
    private Index construiesteIndex() {
        List<Product> toate = productRepository.findAll();
        Map<String, Product> dupaGtin = new HashMap<>();
        Map<String, Product> dupaMpn = new HashMap<>();
        Map<String, Product> dupaSku = new HashMap<>();
        List<Cuvinte> dupaDenumire = new ArrayList<>(toate.size());

        for (Product p : toate) {
            cheie(p.getGtin()).ifPresent(k -> dupaGtin.putIfAbsent(k, p));
            cheie(p.getMpn()).ifPresent(k -> dupaMpn.putIfAbsent(k, p));
            cheie(p.getSku()).ifPresent(k -> dupaSku.putIfAbsent(k, p));
            Set<String> c = cuvinte(p.getName());
            if (!c.isEmpty()) {
                dupaDenumire.add(new Cuvinte(p, c));
            }
        }
        return new Index(dupaGtin, dupaMpn, dupaSku, dupaDenumire);
    }

    private Rezultat potriveste(DistributorFeedParser.RandFeed rand, Index index) {
        // 1. EAN. Identifică articolul fizic; nu există potrivire mai tare.
        Product p = cauta(index.dupaGtin(), rand.gtin());
        if (p != null) {
            return new Rezultat(p, "EAN", true);
        }
        // 2. Codul producătorului.
        p = cauta(index.dupaMpn(), rand.codProducator());
        if (p != null) {
            return new Rezultat(p, "cod producător", true);
        }
        // 3. Codul distribuitorului față de SKU-ul nostru. Slabă, pentru că
        //    SKU-ul nostru nu vine neapărat de la acest distribuitor.
        p = cauta(index.dupaSku(), rand.codDistribuitor());
        if (p != null) {
            return new Rezultat(p, "cod distribuitor", false);
        }
        // 4. Codul producătorului din feed găsit în SKU-ul nostru și invers —
        //    unele cataloage păstrează codul producătorului în coloana SKU.
        p = cauta(index.dupaSku(), rand.codProducator());
        if (p != null) {
            return new Rezultat(p, "cod producător în SKU", false);
        }
        // 5. Asemănarea denumirilor. Sugestie.
        Set<String> aleFeedului = cuvinte(rand.denumire());
        if (aleFeedului.size() >= MIN_CUVINTE_COMUNE) {
            Product celMaiBun = null;
            double scorMax = 0;
            for (Cuvinte c : index.dupaDenumire()) {
                int comune = 0;
                for (String cuv : aleFeedului) {
                    if (c.cuvinte().contains(cuv)) {
                        comune++;
                    }
                }
                if (comune < MIN_CUVINTE_COMUNE) {
                    continue;
                }
                double scor = (double) comune / Math.min(aleFeedului.size(), c.cuvinte().size());
                if (scor > scorMax) {
                    scorMax = scor;
                    celMaiBun = c.produs();
                }
            }
            if (celMaiBun != null && scorMax >= PRAG_ASEMANARE) {
                return new Rezultat(celMaiBun, "denumire", false);
            }
        }
        return null;
    }

    private Product cauta(Map<String, Product> harta, String valoare) {
        return cheie(valoare).map(harta::get).orElse(null);
    }

    /** Cheia de comparare: majuscule, fără semne. Gol înseamnă fără cheie. */
    private java.util.Optional<String> cheie(String valoare) {
        if (valoare == null || valoare.isBlank()) {
            return java.util.Optional.empty();
        }
        String k = valoare.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        // Sub patru caractere, o „potrivire" este zgomot: „12", „A1" sau „SET"
        // apar în zeci de produse fără legătură între ele.
        return k.length() >= 4 ? java.util.Optional.of(k) : java.util.Optional.empty();
    }

    /** Cuvintele lungi ale unei denumiri, fără diacritice. */
    private Set<String> cuvinte(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String n = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        Set<String> out = new HashSet<>();
        for (String bucata : Arrays.asList(n.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+"))) {
            if (bucata.length() >= 4) {
                out.add(bucata);
            }
        }
        return out;
    }

    /**
     * Dacă produsul are deja fotografie.
     *
     * <p>Se uită numai la {@code imageUrl}, nu la galerie, pentru că aceasta
     * este definiția folosită deja în restul sistemului: interogarea
     * {@code findActiveWithNoImage} și avertismentul „produs fără imagini"
     * numără exact la fel. O a doua definiție ar produce două numere diferite
     * pentru aceeași întrebare. În plus, citirea galeriei pentru fiecare produs
     * ar însemna o interogare separată per produs, la construirea indexului.</p>
     */
    private boolean areImagine(Product p) {
        return p.getImageUrl() != null && !p.getImageUrl().isBlank();
    }

    private static boolean prezent(String s) {
        return s != null && !s.isBlank();
    }

    private static String scurt(String s) {
        if (s == null) {
            return "eroare necunoscută";
        }
        return s.length() <= 160 ? s : s.substring(0, 160) + "…";
    }

    private record Index(Map<String, Product> dupaGtin, Map<String, Product> dupaMpn,
                         Map<String, Product> dupaSku, List<Cuvinte> dupaDenumire) {
    }

    private record Cuvinte(Product produs, Set<String> cuvinte) {
    }

    private record Rezultat(Product produs, String cum, boolean sigura) {
    }

    // ---------------------------------------------------------------- forme

    /**
     * O potrivire propusă operatorului.
     *
     * @param productId        produsul nostru
     * @param denumireNoastra  cum se cheamă la noi
     * @param marcaNoastra     marca din coloana noastră
     * @param mpnNostru        codul nostru, dacă există
     * @param gtinNostru       EAN-ul nostru, dacă există
     * @param areImagineDeja   dacă produsul are deja fotografie — atunci
     *                         fotografiile din feed se adaugă în galerie, nu
     *                         înlocuiesc nimic
     * @param randFeed         rândul din fișier, pentru verificare în fișier
     * @param denumireFeed     cum se cheamă la distribuitor; comparația celor
     *                         două denumiri este verificarea operatorului
     * @param marcaFeed        marca din feed
     * @param codFeed          codul producătorului din feed
     * @param gtinFeed         EAN-ul din feed
     * @param imagini          adresele propuse, în ordinea din feed
     * @param cum              după ce cheie s-a făcut potrivirea
     * @param sigura           dacă potrivirea se poate bifa automat
     */
    public record Potrivire(Long productId, String denumireNoastra, String marcaNoastra,
                            String mpnNostru, String gtinNostru, boolean areImagineDeja,
                            int randFeed, String denumireFeed, String marcaFeed,
                            String codFeed, String gtinFeed, List<String> imagini,
                            String cum, boolean sigura) {
    }

    /** Ce se confirmă pentru un produs. */
    public record Intrare(Long productId, List<String> imagini, String codProducator,
                          String gtin, String codDistribuitor) {
    }

    /** O preluare care nu a reușit, cu motivul ei. */
    public record Esec(Long productId, String adresa, String motiv) {
    }

    /**
     * Rezultatul citirii feedului.
     *
     * @param randuriFeed       câte înregistrări a avut fișierul
     * @param randuriUtile      câte aveau și identitate și fotografie
     * @param faraIdentitate    câte nu aveau nici denumire, nici cod
     * @param faraFotografie    câte aveau identitate dar nicio adresă
     * @param potrivite         câte s-au legat de un produs din catalog
     * @param nepotrivite       câte nu s-au legat de nimic
     * @param sariteAuImagine   câte s-au legat, dar produsul avea deja poză
     * @param dupaCheie         câte potriviri pe fiecare tip de cheie — arată
     *                          dintr-o privire cât din rezultat este sigur
     * @param coloaneGasite     ce coloană a fost citită ca ce câmp
     * @param elementXml        elementul tratat ca produs, la feed XML
     * @param potriviri         potrivirile de confirmat
     */
    public record Raport(int randuriFeed, int randuriUtile, int faraIdentitate, int faraFotografie,
                         int potrivite, int nepotrivite, int sariteAuImagine,
                         Map<String, Integer> dupaCheie, Map<String, String> coloaneGasite,
                         String elementXml, List<Potrivire> potriviri) {
    }

    /** Ce s-a preluat efectiv. */
    public record Aplicare(int produseModificate, int imaginiUrcate, List<Esec> esecuri) {

        public Map<String, Object> caMesaj() {
            return Map.of("produseModificate", produseModificate,
                    "imaginiUrcate", imaginiUrcate,
                    "esecuri", esecuri);
        }
    }
}
