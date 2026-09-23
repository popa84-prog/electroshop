package com.electroshop.service.imaging;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Scoate codul de produs (MPN) din denumirea comercială.
 *
 * <h2>De ce din denumire</h2>
 *
 * Catalogul are coloana {@code sku} goală pe toate cele 620 de produse — am
 * numărat. Codul de produs există totuși, scris de operator în mijlocul
 * denumirii: „Acumulator scule Black &amp; Decker 18V 1.5Ah Li-Ion BL1518-XJ".
 * Icecat caută după marcă plus cod, deci codul acela este singura cheie pe care
 * o avem, iar a-l cere completat manual pentru 307 produse ar însemna aceeași
 * muncă pe care o evităm.
 *
 * <h2>Ce se întoarce și de ce mai multe</h2>
 *
 * O listă ordonată descrescător după încredere, nu un singur răspuns. Motivul
 * este un caz real din catalog: <em>„Acumulator pentru Sony DSC-RX100 Sony
 * NP-BX1"</em>. Candidatul cu scorul cel mai mare este {@code DSC-RX100} — are
 * cratimă, are lungime, arată perfect ca un cod. Este însă aparatul foto
 * <em>pentru care</em> se potrivește acumulatorul; produsul vândut este
 * {@code NP-BX1}. Amândouă există în catalogul Icecat și amândouă ar întoarce o
 * poză, doar că una este poza produsului greșit.
 *
 * <p>Nicio euristică nu distinge cele două cazuri din text, pentru că diferența
 * nu este în text, ci în ce anume se vinde. De aceea clasa nu pretinde că alege:
 * propune, ordonat, iar decizia rămâne la operator.</p>
 *
 * <h2>Ce este respins</h2>
 *
 * Un token trece doar dacă are și literă, și cifră — regula care elimină
 * singură cea mai mare parte din zgomot, pentru că nici cuvintele obișnuite,
 * nici numerele goale nu sunt coduri de produs. Peste asta stă o listă de
 * excepții pentru tiparele care <em>arată</em> ca un cod fără să fie:
 * capacități ({@code 64GB}), tensiuni ({@code 18V}), standarde ({@code USB3},
 * {@code IP67}), rezoluții ({@code 4K}) și sufixele de gamă ({@code 16PLUS},
 * {@code 2DIN}) — toate găsite în datele reale, nu imaginate.
 */
public final class MpnExtractor {

    private MpnExtractor() {
    }

    /**
     * Tipare care seamănă cu un cod de produs, dar descriu o caracteristică.
     *
     * <p>Lista este derivată din denumirile reale ale celor 307 produse fără
     * imagine. Fiecare intrare a fost un fals pozitiv observat, nu o
     * presupunere: {@code 2DIN} venea din „7inch car 2din", {@code 16PLUS} din
     * „husa iphone 16plus clear case".</p>
     */
    private static final Pattern ZGOMOT = Pattern.compile(
            "^(?:"
            + "\\d+(?:GB|TB|MB|KB|W|V|MAH|AH|MM|CM|M|HZ|KHZ|MHZ|MP|INCH|IN|K|FPS|BIT|MS|NM|LM|G|DPI|OHM|C)?"
            + "|USB\\d?|USB-?C|USB-?A|HDMI\\d?|VGA|DVI|RJ\\d+|WIFI\\d?|BT\\d?|NFC"
            + "|4K|8K|2K|FHD|UHD|HD|QHD|WQHD|SVGA|XGA"
            + "|LED|LCD|OLED|QLED|AMOLED|IPS|TFT|TN|VA|RGB|ARGB"
            + "|DDR\\d|LPDDR\\d|NVME|SSD|HDD|SATA|PCIE|M\\.2|TYPE-?[AC]"
            + "|IP\\d\\d|IPX\\d|CLASS\\d+|CAT\\d+"
            + "|\\d+DIN|\\d+PLUS|\\d+PRO|\\d+MAX|\\d+MINI|\\d+ULTRA|\\d+SE|\\d+LITE"
            + "|PRO|MAX|PLUS|MINI|LITE|ULTRA|SE|NEO|AIR"
            + ")$");

    /** Caractere admise într-un cod: alfanumerice plus separatorii uzuali. */
    private static final Pattern FORMA_COD = Pattern.compile("^[A-Z0-9][A-Z0-9\\-/.]*$");

    private static final Pattern ARE_LITERA = Pattern.compile("[A-Z]");
    private static final Pattern ARE_CIFRA = Pattern.compile("\\d");
    private static final Pattern TREI_CIFRE = Pattern.compile("\\d{3,}");

    /** Sub această lungime un token este prea scurt ca să fie un cod util. */
    private static final int LUNGIME_MINIMA = 3;

    /** Peste această lungime este aproape sigur un șir lipit, nu un cod. */
    private static final int LUNGIME_MAXIMA = 24;

    /**
     * Un candidat de cod, cu încrederea asociată.
     *
     * @param cod     codul, normalizat cu majuscule
     * @param scor    între 0 și 1; nu este o probabilitate calibrată, ci o
     *                ordine de încercare
     * @param pozitie indicele cuvântului în denumire, util la depanare
     */
    public record Candidat(String cod, double scor, int pozitie) {
    }

    /**
     * Candidații din denumire, cei mai promițători primii.
     *
     * @param denumire denumirea comercială a produsului
     * @param marca    marca, dacă e cunoscută — se exclude din candidați, pentru
     *                 că marca se trimite separat la Icecat și un „LOGITECH"
     *                 întors ca număr de produs nu ar căuta nimic
     * @return listă ordonată descrescător, fără duplicate; goală dacă denumirea
     *         nu conține nimic care să semene a cod
     */
    public static List<Candidat> candidati(String denumire, String marca) {
        if (denumire == null || denumire.isBlank()) {
            return List.of();
        }

        String[] cuvinte = denumire.toUpperCase(Locale.ROOT)
                .replaceAll("[(),;:\\[\\]{}\"']", " ")
                .trim()
                .split("\\s+");

        String marcaMare = marca == null ? null : marca.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        List<Candidat> gasite = new ArrayList<>();
        Set<String> vazute = new LinkedHashSet<>();

        for (int i = 0; i < cuvinte.length; i++) {
            String token = curata(cuvinte[i]);
            if (!esteCod(token, marcaMare)) {
                continue;
            }
            if (!vazute.add(token)) {
                continue;
            }
            gasite.add(new Candidat(token, scor(token, i, cuvinte.length), i));
        }

        gasite.sort((a, b) -> Double.compare(b.scor(), a.scor()));
        return List.copyOf(gasite);
    }

    /** Taie punctuația de la capete, care se lipește de cod la despărțire. */
    private static String curata(String token) {
        return token.replaceAll("^[-–—.]+", "").replaceAll("[-–—.,]+$", "");
    }

    private static boolean esteCod(String token, String marcaMare) {
        if (token.length() < LUNGIME_MINIMA || token.length() > LUNGIME_MAXIMA) {
            return false;
        }
        // Regula care face cea mai mare parte a muncii: un cod de produs are și
        // litere, și cifre. Cuvintele obișnuite nu au cifre, iar specificațiile
        // pur numerice nu au litere.
        if (!ARE_LITERA.matcher(token).find() || !ARE_CIFRA.matcher(token).find()) {
            return false;
        }
        if (!FORMA_COD.matcher(token).matches()) {
            return false;
        }
        if (ZGOMOT.matcher(token).matches()) {
            return false;
        }
        // Marca scrisă lipit de un număr („XIAOMI14") nu este numărul de produs.
        return marcaMare == null || marcaMare.isEmpty()
                || !token.replaceAll("[^A-Z0-9]", "").startsWith(marcaMare);
    }

    /**
     * Cât de mult seamănă tokenul cu un cod de produs.
     *
     * <p>Ponderile nu vin dintr-un model, ci din forma codurilor din catalog:
     * cratima este semnul cel mai puternic ({@code BL1518-XJ}, {@code NP-BX1}),
     * lungimea și un grup de trei cifre urmează, iar poziția către finalul
     * denumirii contează pentru că operatorul scrie codul ultimul.</p>
     */
    private static double scor(String token, int indice, int total) {
        double s = 0.35;
        if (token.indexOf('-') >= 0) {
            s += 0.25;
        }
        if (token.length() >= 5) {
            s += 0.15;
        }
        if (token.length() >= 7) {
            s += 0.05;
        }
        if (TREI_CIFRE.matcher(token).find()) {
            s += 0.10;
        }
        if (indice >= total - 4) {
            s += 0.10;
        }
        return Math.min(s, 0.95);
    }
}
