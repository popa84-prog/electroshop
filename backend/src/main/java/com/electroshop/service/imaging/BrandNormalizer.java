package com.electroshop.service.imaging;

import java.util.Locale;
import java.util.Map;

/**
 * Aduce marca din catalog la forma pe care o recunoaște Icecat.
 *
 * <h2>De ce este nevoie</h2>
 *
 * Icecat caută după marcă plus cod de produs, iar marca trebuie scrisă cum o
 * scrie el. Catalogul are „Black+Decker" acolo unde Icecat are „Black &amp;
 * Decker", are „EZVIZ" cu majuscule, are „Momcozy" scris uneori doar în
 * denumire și deloc în coloana de marcă. O interogare cu marca greșită nu dă
 * eroare — dă zero rezultate, ceea ce este mai rău, pentru că arată ca „produsul
 * nu există în Icecat" când de fapt am întrebat greșit.
 *
 * <h2>Cum funcționează</h2>
 *
 * Două straturi. Întâi o normalizare mecanică: majuscule, fără diacritice, fără
 * separatori. „Black+Decker", „black &amp; decker" și „BLACK-DECKER" devin toate
 * {@code BLACKDECKER}, deci se potrivesc fără să fie nevoie de o intrare pentru
 * fiecare scriere. Abia apoi un tabel de alias-uri, pentru cazurile pe care
 * normalizarea nu le poate rezolva: „HP" și „Hewlett-Packard" nu se reduc una la
 * alta prin nicio regulă de text.
 *
 * <p>Tabelul este deliberat scurt. Conține mărcile din catalogul acesta care au
 * nevoie de traducere, nu un dicționar universal de mărci — o listă lungă
 * întreținută fără date care s-o susțină ar da impresia de acoperire pe care
 * nu o avem.</p>
 */
public final class BrandNormalizer {

    private BrandNormalizer() {
    }

    /**
     * Alias-uri care nu pot fi deduse mecanic.
     *
     * <p>Cheia este forma normalizată a ce scrie în catalog; valoarea este ce
     * trimitem la Icecat.</p>
     */
    private static final Map<String, String> ALIAS = Map.ofEntries(
            Map.entry("BLACKDECKER", "Black & Decker"),
            Map.entry("HP", "HP"),
            Map.entry("HEWLETTPACKARD", "HP"),
            Map.entry("HPINC", "HP"),
            Map.entry("TPLINK", "TP-Link"),
            Map.entry("DLINK", "D-Link"),
            Map.entry("ASUSTEK", "Asus"),
            Map.entry("SAMSUNGELECTRONICS", "Samsung"),
            Map.entry("LGELECTRONICS", "LG"),
            Map.entry("SONYCORPORATION", "Sony"),
            Map.entry("PHILIPSAVENT", "Philips"),
            Map.entry("XIAOMIMI", "Xiaomi"),
            Map.entry("MI", "Xiaomi"),
            Map.entry("APPLEINC", "Apple"),
            Map.entry("WESTERNDIGITAL", "Western Digital"),
            Map.entry("WD", "Western Digital"),
            Map.entry("SEAGATETECHNOLOGY", "Seagate"),
            Map.entry("JBLHARMAN", "JBL"),
            Map.entry("HARMANKARDON", "Harman Kardon"),
            Map.entry("BOSCHPT", "Bosch"),
            Map.entry("EZVIZ", "EZVIZ"),
            Map.entry("UGREEN", "Ugreen"),
            Map.entry("BEQUIET", "be quiet!")
    );

    /**
     * Marca în forma de interogat, sau {@code null} dacă produsul nu are marcă.
     *
     * <p>Când nu există alias, se întoarce marca originală curățată de spații la
     * capete, nu forma normalizată: Icecat vrea „Logitech", nu „LOGITECH".</p>
     */
    public static String pentruIcecat(String marcaDinCatalog) {
        if (marcaDinCatalog == null || marcaDinCatalog.isBlank()) {
            return null;
        }
        String cheie = normalizeaza(marcaDinCatalog);
        String alias = ALIAS.get(cheie);
        return alias != null ? alias : marcaDinCatalog.trim();
    }

    /**
     * Forma de comparat: majuscule, fără diacritice, fără nimic în afară de
     * litere și cifre.
     *
     * <p>Diacriticele sunt scoase prin descompunere Unicode, nu printr-un tabel
     * de înlocuiri — altfel „ș" scris cu virgulă dedesubt și „ş" scris cu
     * sedilă, care arată identic dar sunt puncte de cod diferite, ar da chei
     * diferite pentru aceeași marcă.</p>
     */
    public static String normalizeaza(String valoare) {
        if (valoare == null) {
            return "";
        }
        String faraDiacritice = java.text.Normalizer
                .normalize(valoare, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return faraDiacritice.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    /**
     * Dacă marca apare în denumire, pentru produsele care nu au coloana de marcă
     * completată.
     *
     * <p>118 din cele 307 produse fără imagine nu au marcă deloc, dar multe o au
     * scrisă în denumire — „Lampă cu muzică cu zgomot alb Momcozy - WN03".
     * Fără pasul acesta, produsele acelea nu pot fi interogate niciodată.</p>
     *
     * @param denumire denumirea comercială
     * @param marci    mărcile cunoscute din catalog, în forma lor originală
     * @return prima marcă recunoscută în denumire, sau {@code null}
     */
    public static String ghicesteDinDenumire(String denumire, Iterable<String> marci) {
        if (denumire == null || denumire.isBlank() || marci == null) {
            return null;
        }
        String normDenumire = normalizeaza(denumire);
        String gasita = null;
        int celMaiLung = 0;
        for (String marca : marci) {
            if (marca == null || marca.isBlank()) {
                continue;
            }
            String normMarca = normalizeaza(marca);
            // Sub trei caractere, o potrivire în interiorul denumirii este
            // aproape sigur întâmplătoare: „LG" apare în „LUNG", „LGA" și în
            // orice cuvânt care conține literele acelea alăturat.
            if (normMarca.length() < 3) {
                continue;
            }
            if (normDenumire.contains(normMarca) && normMarca.length() > celMaiLung) {
                // Cea mai lungă potrivire câștigă: dacă denumirea conține și
                // „SONY", și „SONYERICSSON", a doua este cea intenționată.
                gasita = marca;
                celMaiLung = normMarca.length();
            }
        }
        return gasita;
    }
}
