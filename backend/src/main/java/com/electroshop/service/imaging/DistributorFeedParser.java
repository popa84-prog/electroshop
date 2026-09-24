package com.electroshop.service.imaging;

import com.electroshop.exception.BadRequestException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Citirea unui feed de distribuitor: Excel sau XML, fără configurare prealabilă.
 *
 * <h2>Ce problemă rezolvă</h2>
 *
 * Icecat acoperă sub 1% din acest catalog, iar fotografierea proprie cere timp
 * pentru fiecare produs. Distribuitorul are însă deja fotografiile mărfii pe
 * care o vinde, iar contractul de distribuție există tocmai ca revânzătorul să
 * le folosească. Un feed acoperă, dintr-un fișier, tot ce ține de acel
 * distribuitor.
 *
 * <h2>De ce nu cere configurare</h2>
 *
 * Fiecare distribuitor își numește coloanele altfel: „EAN", „Cod bare",
 * „barcode", „GTIN13" sunt același lucru. O unealtă care ar cere maparea
 * manuală a coloanelor la fiecare fișier ar fi abandonată după al doilea
 * import. Cititorul recunoaște în schimb sinonimele, în română și engleză,
 * după ce normalizează numele coloanei: fără diacritice, fără spații, fără
 * semne, litere mici.
 *
 * <p>Ordinea verificărilor este semnificativă, nu alfabetică. „Cod producător"
 * conține și „cod" și „producător"; dacă s-ar verifica întâi „cod", coloana ar
 * ajunge la codul intern al distribuitorului, iar potrivirea cu catalogul
 * nostru ar căuta un cod de producător unde nu este. De aceea tiparele
 * specifice se testează înaintea celor generale.</p>
 *
 * <h2>De ce XML-ul se citește în flux, nu în memorie</h2>
 *
 * Un feed XML real are zeci de megaocteți. Încărcat ca arbore în memorie (DOM),
 * ocupă de câteva ori dimensiunea fișierului, iar acest backend are 400 MB de
 * heap și rulează în același proces cu magazinul. {@link XMLStreamReader}
 * citește secvențial, cu memorie constantă: fișierul poate crește fără ca
 * riscul să crească.
 *
 * <h2>Structura XML se deduce, nu se presupune</h2>
 *
 * Nu există un standard: se întâlnesc {@code <products><product>},
 * {@code <root><items><item>}, {@code <catalog><offer>}. Cititorul face două
 * treceri. Prima numără, pentru fiecare nume de element, de câte ori apare și
 * dacă are printre descendenți un câmp de identitate (denumire, cod sau cod de
 * bare). A doua trece efectiv, extrăgând elementul câștigător. Un element
 * precum {@code <Images>} nu are câmp de identitate, deci nu poate fi luat
 * drept produs — iar asta este exact confuzia care ar produce rânduri cu poze
 * și fără produs.
 *
 * <h2>Securitate</h2>
 *
 * Fișierul vine din afară. Parserul XML are dezactivate declarațiile DOCTYPE și
 * entitățile externe, altfel un feed ostil ar putea cere serverului să citească
 * fișiere locale sau să deschidă conexiuni în rețeaua internă (XXE). Refuzul
 * este total: nu se acceptă DTD deloc, nici local.
 */
@Component
public class DistributorFeedParser {

    /** Dincolo de atâtea rânduri, fișierul este aproape sigur altceva decât un feed. */
    private static final int MAX_RANDURI = 50_000;

    /** Câte adrese de imagine se păstrează pentru un produs. */
    private static final int MAX_IMAGINI_PE_RAND = 8;

    /** Câmpurile pe care le căutăm. Restul coloanelor din feed se ignoră. */
    public enum Camp {
        DENUMIRE, MARCA, COD_PRODUCATOR, COD_DISTRIBUITOR, GTIN, IMAGINE
    }

    /**
     * Un rând din feed, deja curățat.
     *
     * @param rand             numărul rândului sau al înregistrării, pentru mesaje
     * @param denumire         denumirea la distribuitor
     * @param marca            marca, dacă feedul o are separat
     * @param codProducator    codul producătorului (MPN) — cheia de potrivire
     * @param codDistribuitor  codul intern al distribuitorului
     * @param gtin             codul de bare, cea mai tare cheie de potrivire
     * @param imagini          adresele fotografiilor, în ordinea din feed
     */
    public record RandFeed(int rand, String denumire, String marca, String codProducator,
                           String codDistribuitor, String gtin, List<String> imagini) {

        /** Fără niciuna dintre acestea, rândul nu poate fi legat de nimic. */
        public boolean areIdentitate() {
            return prezent(denumire) || prezent(codProducator) || prezent(gtin)
                    || prezent(codDistribuitor);
        }

        private static boolean prezent(String s) {
            return s != null && !s.isBlank();
        }
    }

    /**
     * Rezultatul citirii.
     *
     * @param randuri         rândurile utilizabile
     * @param totalCitite     câte înregistrări a avut fișierul
     * @param faraIdentitate  câte au fost sărite pentru că nu aveau nici denumire, nici cod
     * @param faraImagini     câte au identitate dar nicio adresă de fotografie
     * @param coloaneGasite   ce câmp a fost recunoscut în ce coloană — se arată
     *                        operatorului, pentru ca o recunoaștere greșită să
     *                        fie vizibilă înainte de a produce potriviri greșite
     * @param elementXml      numele elementului tratat ca produs, la feed XML
     */
    public record Feed(List<RandFeed> randuri, int totalCitite, int faraIdentitate,
                       int faraImagini, Map<String, String> coloaneGasite, String elementXml) {
    }

    /** Citește fișierul, alegând cititorul după extensie și după conținut. */
    public Feed citeste(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Nu s-a trimis niciun fișier.");
        }
        String nume = file.getOriginalFilename() == null
                ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);

        if (nume.endsWith(".xml")) {
            return citesteXml(file);
        }
        if (nume.endsWith(".xlsx") || nume.endsWith(".xlsm") || nume.endsWith(".xls")) {
            return citesteExcel(file);
        }
        // Extensia lipsește sau este necunoscută: decidem după primul caracter
        // neblank. Un XML începe cu '<', un xlsx este o arhivă ZIP ('PK').
        char prim = primulCaracterUtil(file);
        if (prim == '<') {
            return citesteXml(file);
        }
        if (prim == 'P') {
            return citesteExcel(file);
        }
        throw new BadRequestException(
                "Formatul nu este recunoscut. Se acceptă fișiere .xlsx și .xml.");
    }

    // ---------------------------------------------------------------- Excel

    private Feed citesteExcel(MultipartFile file) {
        List<RandFeed> randuri = new ArrayList<>();
        Map<String, String> coloane = new LinkedHashMap<>();
        int total = 0;
        int faraIdentitate = 0;
        int faraImagini = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new BadRequestException("Fișierul nu are nicio foaie cu date.");
            }
            int idxCap = sheet.getFirstRowNum();
            Row cap = sheet.getRow(idxCap);
            if (cap == null) {
                throw new BadRequestException("Lipsește rândul de capăt de tabel.");
            }

            // Imaginile pot veni pe mai multe coloane („Image 1", „Image 2"),
            // de aceea pentru ele se păstrează toate potrivirile, iar pentru
            // celelalte câmpuri prima — o a doua coloană „Nume" este o eroare
            // de fișier, nu o informație suplimentară.
            Map<Camp, Integer> unice = new HashMap<>();
            List<Integer> coloaneImagini = new ArrayList<>();
            short ultima = cap.getLastCellNum();
            for (int c = 0; c < ultima; c++) {
                String titlu = celula(cap.getCell(c));
                String norm = normalizeaza(titlu);
                if (norm.isEmpty()) {
                    continue;
                }
                Camp camp = potrivesteCamp(norm);
                if (camp == null) {
                    continue;
                }
                if (camp == Camp.IMAGINE) {
                    coloaneImagini.add(c);
                    coloane.put(titlu.trim(), "imagine");
                } else if (!unice.containsKey(camp)) {
                    unice.put(camp, c);
                    coloane.put(titlu.trim(), eticheta(camp));
                }
            }

            if (coloaneImagini.isEmpty()) {
                throw new BadRequestException("Fișierul nu are nicio coloană de imagini. "
                        + "Se caută coloane numite Imagine, Poza, Image, Picture sau URL imagine.");
            }
            if (!unice.containsKey(Camp.DENUMIRE) && !unice.containsKey(Camp.COD_PRODUCATOR)
                    && !unice.containsKey(Camp.GTIN) && !unice.containsKey(Camp.COD_DISTRIBUITOR)) {
                throw new BadRequestException("Fișierul nu are nicio coloană de identificare. "
                        + "Este nevoie de cel puțin una: denumire, cod produs sau EAN.");
            }

            for (int r = idxCap + 1; r <= sheet.getLastRowNum() && total < MAX_RANDURI; r++) {
                Row row = sheet.getRow(r);
                if (row == null || randGol(row)) {
                    continue;
                }
                total++;

                List<String> imagini = new ArrayList<>();
                for (int c : coloaneImagini) {
                    adaugaAdrese(imagini, celula(row.getCell(c)));
                }

                RandFeed rf = new RandFeed(r + 1,
                        text(row, unice.get(Camp.DENUMIRE)),
                        text(row, unice.get(Camp.MARCA)),
                        text(row, unice.get(Camp.COD_PRODUCATOR)),
                        text(row, unice.get(Camp.COD_DISTRIBUITOR)),
                        text(row, unice.get(Camp.GTIN)),
                        List.copyOf(imagini));

                if (!rf.areIdentitate()) {
                    faraIdentitate++;
                    continue;
                }
                if (imagini.isEmpty()) {
                    faraImagini++;
                    continue;
                }
                randuri.add(rf);
            }
        } catch (IOException e) {
            throw new BadRequestException("Fișierul nu a putut fi citit: " + e.getMessage());
        } catch (BadRequestException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BadRequestException("Fișierul Excel nu a putut fi interpretat: " + e.getMessage());
        }

        return new Feed(List.copyOf(randuri), total, faraIdentitate, faraImagini,
                Map.copyOf(coloane), null);
    }

    private String text(Row row, Integer coloana) {
        if (coloana == null) {
            return null;
        }
        String v = celula(row.getCell(coloana));
        return v.isBlank() ? null : v.trim();
    }

    // ------------------------------------------------------------------ XML

    private Feed citesteXml(MultipartFile file) {
        String element = descoperaElementulProdus(file);
        if (element == null) {
            throw new BadRequestException("În XML nu s-a găsit niciun element care să arate ca un produs. "
                    + "Este nevoie ca fiecare produs să aibă cel puțin o denumire, un cod sau un EAN.");
        }
        return extrageXml(file, element);
    }

    /**
     * Prima trecere: care nume de element reprezintă un produs.
     *
     * <p>Se numără, pentru fiecare nume, de câte ori apare cu cel puțin un câmp
     * de identitate printre descendenți. Câștigă cel mai frecvent, pentru că un
     * înveliș apare o dată, iar produsul de câte ori sunt produse.</p>
     *
     * <h3>Egalitatea, adică feedul cu un singur produs</h3>
     *
     * Regula frecvenței nu separă nimic atunci când feedul are un singur
     * produs: {@code <products>}, {@code <product>} și rădăcina apar toate o
     * dată. În acel caz câștigă <b>cel mai adânc</b>, pentru că învelișurile
     * sunt întotdeauna deasupra produsului, niciodată sub el. Varianta inversă
     * — cel mai de sus — ar alege rădăcina și ar citi tot fișierul ca un singur
     * produs, adică exact greșeala pe care un feed de test cu un rând ar fi
     * arătat-o la prima folosire.
     */
    private String descoperaElementulProdus(MultipartFile file) {
        Map<String, Integer> aparitii = new LinkedHashMap<>();
        Map<String, Integer> adancimi = new HashMap<>();

        try (InputStream in = file.getInputStream()) {
            XMLStreamReader r = fabricaSecurizata().createXMLStreamReader(in);
            // Fiecare cadru ține dacă subarborele lui a avut un câmp de
            // identitate. Informația urcă la părinte la închidere: un produs
            // rămâne produs chiar dacă denumirea îi este nepot, nu copil.
            Deque<Cadru> stiva = new ArrayDeque<>();
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    Cadru c = new Cadru(normalizeaza(r.getLocalName()), stiva.size());
                    // Unele feeduri scriu produsul integral în atribute:
                    // `<product name="…" ean="…" image="…"/>`. Un astfel de
                    // element nu are copii, dar este tot un produs, deci
                    // atributele contează atât pentru identitate, cât și ca
                    // dovadă că elementul poartă date.
                    c.areAtribute = r.getAttributeCount() > 0;
                    for (int a = 0; a < r.getAttributeCount(); a++) {
                        Camp camp = potrivesteCamp(normalizeaza(r.getAttributeLocalName(a)));
                        if (esteIdentitate(camp)) {
                            c.identitate = true;
                        }
                    }
                    stiva.push(c);
                } else if (ev == XMLStreamConstants.CHARACTERS && !r.isWhiteSpace()) {
                    Cadru c = stiva.peek();
                    if (c != null && !r.getText().isBlank()) {
                        c.areText = true;
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    Cadru c = stiva.pop();
                    // Un element-frunza cu text este el însuși un câmp: își
                    // anunță părintelui ce câmp a fost.
                    if (c.areText && !c.areCopii) {
                        Camp camp = potrivesteCamp(c.nume);
                        if (esteIdentitate(camp)) {
                            c.identitate = true;
                        }
                    }
                    Cadru parinte = stiva.peek();
                    if (parinte != null) {
                        parinte.areCopii = true;
                        parinte.identitate = parinte.identitate || c.identitate;
                    }
                    if (c.identitate && (c.areCopii || c.areAtribute)) {
                        aparitii.merge(c.nume, 1, Integer::sum);
                        adancimi.putIfAbsent(c.nume, c.adancime);
                    }
                }
            }
            r.close();
        } catch (IOException e) {
            throw new BadRequestException("Fișierul nu a putut fi citit: " + e.getMessage());
        } catch (XMLStreamException e) {
            throw new BadRequestException("XML-ul nu este valid: " + e.getMessage());
        }

        String castigator = null;
        int maxim = 0;
        for (Map.Entry<String, Integer> e : aparitii.entrySet()) {
            int n = e.getValue();
            if (n > maxim
                    || (n == maxim && castigator != null
                        && adancimi.get(e.getKey()) > adancimi.get(castigator))) {
                maxim = n;
                castigator = e.getKey();
            }
        }
        return castigator;
    }

    /** A doua trecere: extragerea efectivă a elementului stabilit. */
    private Feed extrageXml(MultipartFile file, String elementProdus) {
        List<RandFeed> randuri = new ArrayList<>();
        Set<String> numeVazute = new LinkedHashSet<>();
        int total = 0;
        int faraIdentitate = 0;
        int faraImagini = 0;

        try (InputStream in = file.getInputStream()) {
            XMLStreamReader r = fabricaSecurizata().createXMLStreamReader(in);
            // Valorile înregistrării curente, adunate pe nume normalizat. Un
            // nume poate apărea de mai multe ori — exact cazul imaginilor.
            Map<String, List<String>> valori = null;
            int adancimeIntrare = -1;
            int adancime = 0;
            String numeCurent = null;
            StringBuilder text = new StringBuilder();

            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    adancime++;
                    String nume = normalizeaza(r.getLocalName());
                    if (valori == null && nume.equals(elementProdus)) {
                        valori = new LinkedHashMap<>();
                        adancimeIntrare = adancime;
                    }
                    if (valori != null) {
                        numeCurent = nume;
                        text.setLength(0);
                        // Atributele contează: `<Image url="…"/>` nu are text.
                        // Adresa se înregistrează și sub numele atributului, și
                        // sub numele elementului, pentru ca oricare dintre cele
                        // două să o poată revendica la potrivirea câmpurilor.
                        for (int a = 0; a < r.getAttributeCount(); a++) {
                            String na = normalizeaza(r.getAttributeLocalName(a));
                            String va = r.getAttributeValue(a);
                            if (va == null || va.isBlank()) {
                                continue;
                            }
                            adauga(valori, na, va);
                            if (na.equals("url") || na.equals("src") || na.equals("href")
                                    || na.equals("link") || na.equals("value")) {
                                adauga(valori, nume, va);
                            }
                        }
                    }
                } else if (ev == XMLStreamConstants.CHARACTERS || ev == XMLStreamConstants.CDATA) {
                    if (valori != null) {
                        text.append(r.getText());
                    }
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if (valori != null) {
                        String nume = normalizeaza(r.getLocalName());
                        if (nume.equals(numeCurent) && !text.isEmpty() && !text.toString().isBlank()) {
                            adauga(valori, nume, text.toString());
                        }
                        text.setLength(0);
                        numeCurent = null;

                        if (adancime == adancimeIntrare && nume.equals(elementProdus)) {
                            total++;
                            RandFeed rf = construieste(total, valori, numeVazute);
                            if (!rf.areIdentitate()) {
                                faraIdentitate++;
                            } else if (rf.imagini().isEmpty()) {
                                faraImagini++;
                            } else if (randuri.size() < MAX_RANDURI) {
                                randuri.add(rf);
                            }
                            valori = null;
                            adancimeIntrare = -1;
                        }
                    }
                    adancime--;
                }
            }
            r.close();
        } catch (IOException e) {
            throw new BadRequestException("Fișierul nu a putut fi citit: " + e.getMessage());
        } catch (XMLStreamException e) {
            throw new BadRequestException("XML-ul nu este valid: " + e.getMessage());
        }

        Map<String, String> recunoscute = new LinkedHashMap<>();
        for (String n : numeVazute) {
            Camp c = potrivesteCamp(n);
            if (c != null) {
                recunoscute.put(n, eticheta(c));
            }
        }

        return new Feed(List.copyOf(randuri), total, faraIdentitate, faraImagini,
                Map.copyOf(recunoscute), elementProdus);
    }

    /** Transformă valorile adunate ale unei înregistrări în rândul final. */
    private RandFeed construieste(int numar, Map<String, List<String>> valori, Set<String> numeVazute) {
        Map<Camp, String> simple = new LinkedHashMap<>();
        List<String> imagini = new ArrayList<>();

        for (Map.Entry<String, List<String>> e : valori.entrySet()) {
            Camp camp = potrivesteCamp(e.getKey());
            if (camp == null) {
                continue;
            }
            numeVazute.add(e.getKey());
            if (camp == Camp.IMAGINE) {
                for (String v : e.getValue()) {
                    adaugaAdrese(imagini, v);
                }
            } else if (!simple.containsKey(camp)) {
                String v = e.getValue().get(0);
                if (v != null && !v.isBlank()) {
                    simple.put(camp, v.trim());
                }
            }
        }

        return new RandFeed(numar,
                simple.get(Camp.DENUMIRE), simple.get(Camp.MARCA),
                simple.get(Camp.COD_PRODUCATOR), simple.get(Camp.COD_DISTRIBUITOR),
                simple.get(Camp.GTIN), List.copyOf(imagini));
    }

    private void adauga(Map<String, List<String>> valori, String cheie, String valoare) {
        valori.computeIfAbsent(cheie, k -> new ArrayList<>()).add(valoare.trim());
    }

    /**
     * Fabrica XML fără DTD și fără entități externe.
     *
     * <p>Fișierul este încărcat de operator, dar conținutul lui vine de la un
     * terț. Un feed care declară o entitate externă ar putea cere serverului să
     * citească un fișier local sau să deschidă o conexiune într-o rețea la care
     * doar el are acces, și să întoarcă rezultatul în date (XXE, respectiv SSRF
     * prin parser). Refuzul DTD-ului elimină ambele, fără a pierde nimic: un
     * feed comercial nu are nevoie de DTD.</p>
     */
    private XMLInputFactory fabricaSecurizata() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
        f.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
        return f;
    }

    /** Un nivel din arborele XML, în prima trecere. */
    private static final class Cadru {
        final String nume;
        final int adancime;
        boolean areCopii;
        boolean areText;
        boolean areAtribute;
        boolean identitate;

        Cadru(String nume, int adancime) {
            this.nume = nume;
            this.adancime = adancime;
        }
    }

    // ------------------------------------------------------- Câmpuri comune

    /**
     * Recunoaște câmpul dintr-un nume de coloană sau de element, normalizat.
     *
     * <p>Ordinea este regula, nu o preferință de stil: tiparele specifice se
     * testează înaintea celor generale, altfel „codproducator" ar cădea pe
     * „cod" și ar fi tratat drept cod intern al distribuitorului.</p>
     */
    public static Camp potrivesteCamp(String n) {
        if (n == null || n.isEmpty()) {
            return null;
        }
        // Codul de bare, primul: „codbare" conține „cod".
        if (n.contains("ean") || n.contains("gtin") || n.contains("barcode")
                || n.contains("codbare") || n.contains("upc")) {
            return Camp.GTIN;
        }
        // Imaginile, înaintea oricărui „url" generic.
        if (n.contains("imagin") || n.contains("image") || n.contains("poza") || n.contains("poze")
                || n.contains("picture") || n.contains("photo") || n.contains("foto")
                || n.equals("img") || n.startsWith("img") || n.contains("thumbnail")) {
            return Camp.IMAGINE;
        }
        // Codul producătorului, înaintea codului generic.
        if (n.contains("mpn") || n.contains("codproducator") || n.contains("codfabricant")
                || n.contains("partnumber") || n.contains("manufacturercode")
                || n.contains("manufacturerpart") || n.contains("oem")
                || n.equals("model") || n.contains("codmodel")) {
            return Camp.COD_PRODUCATOR;
        }
        if (n.contains("sku") || n.contains("codarticol") || n.contains("articlenumber")
                || n.contains("itemnumber") || n.contains("itemcode") || n.contains("codfurnizor")
                || n.contains("suppliercode") || n.contains("referinta") || n.contains("reference")
                || n.contains("codintern") || n.equals("cod") || n.contains("codprodus")) {
            return Camp.COD_DISTRIBUITOR;
        }
        if (n.contains("marca") || n.contains("brand") || n.contains("producator")
                || n.contains("manufacturer") || n.contains("vendor") || n.equals("make")) {
            return Camp.MARCA;
        }
        if (n.contains("denumire") || n.contains("numeprodus") || n.equals("nume")
                || n.contains("productname") || n.equals("name") || n.equals("title")
                || n.contains("titlu") || n.equals("produs") || n.equals("product")) {
            return Camp.DENUMIRE;
        }
        return null;
    }

    private static boolean esteIdentitate(Camp c) {
        return c == Camp.DENUMIRE || c == Camp.GTIN
                || c == Camp.COD_PRODUCATOR || c == Camp.COD_DISTRIBUITOR;
    }

    private static String eticheta(Camp c) {
        return switch (c) {
            case DENUMIRE -> "denumire";
            case MARCA -> "marcă";
            case COD_PRODUCATOR -> "cod producător";
            case COD_DISTRIBUITOR -> "cod distribuitor";
            case GTIN -> "EAN";
            case IMAGINE -> "imagine";
        };
    }

    /**
     * Desparte una sau mai multe adrese dintr-o singură valoare.
     *
     * <p>Unele feeduri pun toate fotografiile într-o celulă, separate prin
     * virgulă, punct și virgulă sau bară verticală. Se acceptă numai adrese
     * {@code http}/{@code https}: o cale locală sau o adresă {@code file:} nu
     * are ce căuta într-un feed, iar trimiterea ei mai departe ar fi o cerere
     * făcută de server către ce a scris altcineva.</p>
     */
    private static void adaugaAdrese(List<String> destinatie, String valoare) {
        if (valoare == null || valoare.isBlank()) {
            return;
        }
        for (String bucata : valoare.split("[,;|\\s]+")) {
            String a = bucata.trim();
            if (a.length() < 12) {
                continue;
            }
            String mic = a.toLowerCase(Locale.ROOT);
            if (!mic.startsWith("http://") && !mic.startsWith("https://")) {
                continue;
            }
            if (destinatie.size() >= MAX_IMAGINI_PE_RAND || destinatie.contains(a)) {
                continue;
            }
            destinatie.add(a);
        }
    }

    /** Fără diacritice, fără semne, litere mici — ca la importul de produse. */
    public static String normalizeaza(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private char primulCaracterUtil(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] buf = new byte[64];
            int n = in.read(buf);
            for (int i = 0; i < n; i++) {
                int b = buf[i] & 0xFF;
                // Marca de ordine a octeților UTF-8 (EF BB BF) se sare: altfel
                // primul „caracter util" al unui XML salvat din Excel ar fi
                // 0xEF, iar fișierul ar fi declarat de format necunoscut.
                if (b == 0xEF || b == 0xBB || b == 0xBF) {
                    continue;
                }
                char c = (char) b;
                if (!Character.isWhitespace(c)) {
                    return c;
                }
            }
        } catch (IOException e) {
            throw new BadRequestException("Fișierul nu a putut fi citit: " + e.getMessage());
        }
        return 0;
    }

    private boolean randGol(Row row) {
        short prima = row.getFirstCellNum();
        if (prima < 0) {
            return true;
        }
        short ultima = row.getLastCellNum();
        for (int c = prima; c < ultima; c++) {
            if (!celula(row.getCell(c)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valoarea textuală a unei celule.
     *
     * <p>Numerele întregi se scriu fără zecimale, pentru că un EAN citit ca
     * {@code 5.901234123457E12} nu se potrivește cu nimic. Codul de bare rămâne
     * text și în restul sistemului, tocmai pentru că poate începe cu zero.</p>
     */
    private String celula(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> formula(cell);
            case NUMERIC -> numeric(cell);
            default -> "";
        };
    }

    private String formula(Cell cell) {
        try {
            return cell.getStringCellValue();
        } catch (IllegalStateException e) {
            return numeric(cell);
        }
    }

    private String numeric(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return String.valueOf(cell.getDateCellValue());
        }
        double d = cell.getNumericCellValue();
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }
}
