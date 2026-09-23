package com.electroshop.service.imaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Interogarea catalogului Icecat după marcă și cod de produs.
 *
 * <h2>Ce este Icecat și de ce el</h2>
 *
 * Producătorii plătesc Icecat ca să-și distribuie fișele și fotografiile către
 * comercianți — 600 de mărci sponsorizează nivelul gratuit „Open Icecat".
 * Pentru un magazin mic este singurul lanț de drepturi curat care se obține
 * fără negociere: marca a plătit ea însăși ca pozele să ajungă la revânzători.
 *
 * <p>Alternativa evidentă, descărcarea pozelor de pe site-ul producătorului,
 * este exact situația din hotărârea CJUE Renckhoff (C-161/17): repostarea unei
 * fotografii pe alt server este reproducere neautorizată chiar dacă originalul
 * era public. De aceea clasa aceasta există.</p>
 *
 * <h2>De ce marcă plus cod, nu cod de bare</h2>
 *
 * Icecat acceptă trei chei: {@code GTIN}, {@code icecat_id} și perechea
 * {@code Brand} + {@code ProductCode}. Catalogul acesta nu are niciun cod de
 * bare — coloana {@code sku} este goală pe toate produsele — deci perechea
 * marcă/cod este singura utilizabilă. Icecat întoarce însă GTIN-ul în răspuns,
 * așa că interogarea rezolvă și lipsa codurilor de bare, ca efect secundar.
 *
 * <h2>Ce nu face</h2>
 *
 * Nu descarcă imaginea și nu o publică. Întoarce ce a găsit; decizia dacă
 * imaginea aceea este a produsului nostru aparține operatorului, din motivele
 * explicate în {@link MpnExtractor}.
 *
 * <h2>Configurare</h2>
 *
 * Trei variabile de mediu. Fără ele serviciul raportează că nu este configurat
 * și nu încearcă nicio cerere — o interogare fără acreditări ar primi oricum
 * refuz, iar un log plin de 401-uri ascunde erorile reale.
 *
 * <pre>
 * ICECAT_SHOPNAME      numele de utilizator din contul Open Icecat
 * ICECAT_API_TOKEN     jetonul pentru date
 * ICECAT_CONTENT_TOKEN jetonul pentru imagini și alte active
 * </pre>
 */
@Service
public class IcecatClient {

    private static final Logger log = LoggerFactory.getLogger(IcecatClient.class);

    private static final String BAZA = "https://live.icecat.biz/api";

    /** Codul de eroare Icecat pentru „produsul nu există în baza de date". */
    private static final String EROARE_NEGASIT = "8";

    private final String shopname;
    private final String apiToken;
    private final String contentToken;
    private final String limba;
    private final String limbaRezerva;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public IcecatClient(
            @Value("${app.icecat.shopname:}") String shopname,
            @Value("${app.icecat.api-token:}") String apiToken,
            @Value("${app.icecat.content-token:}") String contentToken,
            @Value("${app.icecat.lang:ro}") String limba,
            @Value("${app.icecat.lang-fallback:en}") String limbaRezerva) {
        this.shopname = shopname == null ? "" : shopname.trim();
        this.apiToken = apiToken == null ? "" : apiToken.trim();
        this.contentToken = contentToken == null ? "" : contentToken.trim();
        this.limba = (limba == null || limba.isBlank()) ? "ro" : limba.trim();
        this.limbaRezerva = (limbaRezerva == null || limbaRezerva.isBlank()) ? "en" : limbaRezerva.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Dacă există acreditări. Fals înseamnă „nu s-a configurat", nu „a eșuat". */
    public boolean esteConfigurat() {
        return !shopname.isEmpty() && !apiToken.isEmpty();
    }

    /**
     * Caută un produs după marcă și cod.
     *
     * @return rezultatul, sau {@link Optional#empty()} dacă produsul nu există
     *         în Icecat, dacă lipsesc acreditările sau dacă cererea a eșuat.
     *         Cele trei cazuri se disting în loguri, nu în tipul returnat:
     *         pentru apelant toate înseamnă „nu am o poză de propus".
     */
    public Optional<Rezultat> cauta(String marca, String cod) {
        Raspuns r = interogheaza(marca, cod);
        return Optional.ofNullable(r.rezultat());
    }

    /**
     * Ca {@link #cauta}, dar spune și <em>de ce</em> nu a găsit.
     *
     * <p>Prima versiune întorcea doar {@code Optional.empty()} pentru orice
     * eșec. A fost o greșeală de proiectare, descoperită la prima rulare reală:
     * din 37 de produse interogabile a potrivit unul singur, și nu exista nicio
     * cale de a afla dacă restul lipsesc din Icecat, dacă marca nu este în
     * nivelul gratuit, dacă jetonul a fost refuzat sau dacă s-a depășit cota.
     * Patru cauze cu remedii complet diferite, toate arătând identic.</p>
     *
     * <p>Se încearcă întâi limba configurată, apoi cea de rezervă. Icecat nu
     * întoarce automat fișa în engleză când nu există una în română; ea trebuie
     * cerută explicit, iar conținutul românesc acoperă o mică parte din
     * catalog.</p>
     */
    public Raspuns interogheaza(String marca, String cod) {
        if (!esteConfigurat()) {
            return Raspuns.esec(Motiv.NECONFIGURAT, "Lipsesc acreditările Icecat.");
        }
        if (marca == null || marca.isBlank() || cod == null || cod.isBlank()) {
            return Raspuns.esec(Motiv.DATE_INSUFICIENTE, "Marca sau codul lipsesc.");
        }

        Raspuns intai = unaSingura(marca, cod, limba);
        if (intai.rezultat() != null || !intai.meritaReincercat()) {
            return intai;
        }
        Raspuns aDoua = unaSingura(marca, cod, limbaRezerva);
        // Dacă nici în limba de rezervă nu există, raportăm al doilea răspuns:
        // el reflectă întrebarea cu cele mai mari șanse, deci motivul lui este
        // cel care descrie corect situația.
        return aDoua;
    }

    private Raspuns unaSingura(String marca, String cod, String limbaCeruta) {

        String url = BAZA
                + "?lang=" + enc(limbaCeruta)
                + "&shopname=" + enc(shopname)
                + "&Brand=" + enc(marca)
                // Icecat cere codurile cu majuscule, iar „#" trebuie codificat
                // explicit pentru că altfel taie restul adresei ca fragment.
                + "&ProductCode=" + enc(cod.toUpperCase(java.util.Locale.ROOT))
                + "&content=";

        try {
            HttpRequest.Builder cerere = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("api-token", apiToken)
                    .header("Accept", "application/json")
                    .GET();
            if (!contentToken.isEmpty()) {
                cerere.header("content-token", contentToken);
            }

            HttpResponse<String> raspuns = http.send(cerere.build(), HttpResponse.BodyHandlers.ofString());

            // 401 și 403 nu înseamnă același lucru, deși amândouă sună a refuz.
            //
            // Măsurat pe acest cont: aceeași pereche de jetoane întoarce fișa
            // completă pentru LG și 403 pentru Sony, EZVIZ și Black & Decker.
            // Dacă jetonul ar fi greșit, ar fi greșit și pentru LG. Deci 403
            // este despre CONȚINUT, nu despre identitate: produsul există în
            // Icecat, dar marca lui nu sponsorizează nivelul gratuit, iar fișa
            // se vinde doar în abonamentul plătit.
            //
            // Distincția decide ce are de făcut operatorul. La 401 își verifică
            // jetonul. La 403 nu are ce verifica — marca aceea nu va veni
            // niciodată din Open Icecat, oricât ar reîncerca, și trebuie
            // rezolvată din feed-ul distribuitorului sau cu fotografie proprie.
            if (raspuns.statusCode() == 401) {
                log.warn("Icecat a refuzat acreditările (HTTP 401). Verifică ICECAT_API_TOKEN.");
                return Raspuns.esec(Motiv.NEAUTORIZAT, "Icecat a refuzat jetonul, HTTP 401.");
            }
            if (raspuns.statusCode() == 403) {
                return Raspuns.esec(Motiv.MARCA_INDISPONIBILA,
                        "Marca nu este inclusă în nivelul Open Icecat, HTTP 403.");
            }
            if (raspuns.statusCode() == 404) {
                return Raspuns.esec(Motiv.INEXISTENT,
                        "Nu există fișă pentru această marcă și acest cod, HTTP 404.");
            }
            if (raspuns.statusCode() == 429) {
                return Raspuns.esec(Motiv.COTA_DEPASITA, "Cota de interogări Icecat este depășită.");
            }
            if (raspuns.statusCode() != 200) {
                return Raspuns.esec(Motiv.EROARE,
                        "Icecat a răspuns HTTP " + raspuns.statusCode() + ".");
            }
            return citeste(raspuns.body(), marca, cod, limbaCeruta);

        } catch (java.io.InterruptedIOException e) {
            Thread.currentThread().interrupt();
            return Raspuns.esec(Motiv.EROARE, "Interogarea a fost întreruptă.");
        } catch (Exception e) {
            return Raspuns.esec(Motiv.EROARE, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** Desface răspunsul JSON. Structura este cea din manualul Icecat. */
    private Raspuns citeste(String corp, String marca, String cod, String limbaCeruta) throws Exception {
        JsonNode radacina = json.readTree(corp);
        JsonNode date = radacina.path("data");

        // Icecat raportează „produs inexistent" cu HTTP 200 și un câmp de
        // eroare în interiorul datelor, nu cu un cod de stare. Fără verificarea
        // asta, un produs negăsit ar arăta ca un rezultat gol și valid.
        JsonNode erori = date.path("ContentErrors");
        if (!erori.isMissingNode() && !erori.isNull() && !erori.asText("").isBlank()) {
            String text = erori.isTextual() ? erori.asText() : erori.toString();
            String jos = text.toLowerCase(java.util.Locale.ROOT);
            log.debug("Icecat ContentErrors pentru {} {} ({}): {}", marca, cod, limbaCeruta, text);
            if (jos.contains("not present") || text.contains("\"" + EROARE_NEGASIT + "\"")) {
                return Raspuns.esec(Motiv.INEXISTENT, text);
            }
            if (jos.contains("authoriz") || jos.contains("permission") || jos.contains("access")) {
                // Marca există în Icecat, dar nu în nivelul la care avem acces.
                // Este cazul mărcilor care nu sponsorizează Open Icecat.
                return Raspuns.esec(Motiv.MARCA_INDISPONIBILA, text);
            }
            if (jos.contains("limit") || jos.contains("quota")) {
                return Raspuns.esec(Motiv.COTA_DEPASITA, text);
            }
            return Raspuns.esec(Motiv.EROARE, text);
        }

        JsonNode general = date.path("GeneralInfo");
        JsonNode imagine = date.path("Image");

        List<String> poze = new ArrayList<>();
        adauga(poze, imagine.path("HighPic").asText(null));
        adauga(poze, imagine.path("Pic500x500").asText(null));
        for (JsonNode g : date.path("Gallery")) {
            adauga(poze, g.path("Pic").asText(null));
            adauga(poze, g.path("Pic500x500").asText(null));
        }
        if (poze.isEmpty()) {
            // Fișă fără nicio fotografie. Există în Icecat, dar nu ne ajută.
            return Raspuns.esec(Motiv.FARA_IMAGINI, "Fișa există, dar nu are nicio fotografie.");
        }

        return Raspuns.gasit(new Rezultat(
                general.path("IcecatId").asText(null),
                titlu(general),
                general.path("BrandPartCode").asText(null),
                gtin(general),
                List.copyOf(poze)));
    }

    /** De ce nu s-a găsit. Fiecare motiv are alt remediu. */
    public enum Motiv {
        GASIT,
        /** Produsul nu există în baza Icecat sub marca și codul cerute. */
        INEXISTENT,
        /** Există, dar marca nu este în nivelul nostru de acces (Open Icecat). */
        MARCA_INDISPONIBILA,
        /** Fișa există, fără fotografii. */
        FARA_IMAGINI,
        /** Jetonul a fost refuzat. */
        NEAUTORIZAT,
        /** S-a depășit cota lunară. */
        COTA_DEPASITA,
        /** Nu avem marcă sau cod de trimis. */
        DATE_INSUFICIENTE,
        /** Integrarea nu are acreditări. */
        NECONFIGURAT,
        /** Rețea, format neașteptat, altceva. */
        EROARE
    }

    /**
     * Răspunsul unei interogări: ce s-a găsit, sau de ce nu.
     *
     * @param rezultat fișa, sau {@code null}
     * @param motiv    clasificarea
     * @param detaliu  textul brut de la Icecat, pentru diagnostic
     */
    public record Raspuns(Rezultat rezultat, Motiv motiv, String detaliu) {

        static Raspuns gasit(Rezultat r) {
            return new Raspuns(r, Motiv.GASIT, null);
        }

        static Raspuns esec(Motiv motiv, String detaliu) {
            return new Raspuns(null, motiv, detaliu);
        }

        /**
         * Dacă are rost să reîncercăm în altă limbă.
         *
         * <p>Doar pentru „inexistent": o fișă poate exista în engleză și nu în
         * română. Un jeton refuzat sau o cotă depășită vor fi refuzate identic
         * în orice limbă, iar reîncercarea ar consuma o interogare degeaba.</p>
         */
        public boolean meritaReincercat() {
            return motiv == Motiv.INEXISTENT;
        }
    }

    /** Titlul local dacă există, altfel cel internațional. */
    private static String titlu(JsonNode general) {
        String t = general.path("Title").asText(null);
        if (t != null && !t.isBlank()) {
            return t;
        }
        JsonNode info = general.path("TitleInfo");
        String local = info.path("GeneratedLocalTitle").path("Value").asText(null);
        if (local == null || local.isBlank()) {
            local = info.path("GeneratedLocalTitle").asText(null);
        }
        if (local != null && !local.isBlank()) {
            return local;
        }
        return info.path("GeneratedIntTitle").asText(null);
    }

    /**
     * Primul cod de bare din răspuns.
     *
     * <p>Icecat expune GTIN-urile în două forme, după versiunea fișei: un tablou
     * simplu de șiruri în {@code GTIN} sau un tablou de obiecte în
     * {@code GTINs}. Se citesc amândouă, pentru că o fișă are una sau alta, iar
     * a presupune doar una ar pierde tăcut codul pe jumătate din produse.</p>
     */
    private static String gtin(JsonNode general) {
        for (JsonNode n : general.path("GTIN")) {
            String v = n.asText(null);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        for (JsonNode n : general.path("GTINs")) {
            String v = n.path("GTIN").asText(null);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static void adauga(List<String> lista, String url) {
        if (url != null && !url.isBlank() && !lista.contains(url)) {
            lista.add(url.trim());
        }
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    /**
     * Ce a găsit Icecat.
     *
     * @param icecatId identificatorul intern Icecat, păstrat ca să putem
     *                 reinteroga fișa fără să refacem potrivirea
     * @param titlu    denumirea din Icecat — se arată operatorului lângă
     *                 denumirea noastră, pentru ca potrivirea greșită să sară
     *                 în ochi înainte de confirmare
     * @param codMarca codul de produs așa cum îl scrie producătorul
     * @param gtin     codul de bare, dacă fișa îl conține
     * @param imagini  adresele fotografiilor, cea mai mare prima
     */
    public record Rezultat(String icecatId, String titlu, String codMarca, String gtin, List<String> imagini) {

        /** Adresele distincte, în ordinea primită. */
        public Set<String> imaginiDistincte() {
            return new LinkedHashSet<>(imagini);
        }
    }
}
