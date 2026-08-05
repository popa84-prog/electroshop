package com.electroshop.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Derives a product's manufacturer from its name using whole-word matching against a
 * curated brand table.
 *
 * <h2>The defect this replaces</h2>
 *
 * <p>The {@code brand} column arrived from the import spreadsheet already damaged by
 * unbounded substring matching — the same defect class that put "MOTOROLA" into the
 * motorcycle category because it contains "moto". On the brand column it produced:</p>
 *
 * <ul>
 *   <li>Beh<b>ring</b>er DJX900USB mixer → brand "Ring"</li>
 *   <li>Öhlins Stee<b>ring</b> Damper → brand "Ring"</li>
 *   <li>Sennheiser IE 100 PRO In-Ear Monito<b>ring</b> → brand "Ring"</li>
 *   <li>Statie meteo Ecowitt <b>HP</b>2564 Wittboy Pro → brand "HP"</li>
 *   <li>Obiectiv Foto Mirrorless <b>Nikkor</b> Z 26mm → brand "Nikon" (right family,
 *       wrong reason: the sheet guessed from the lens line, not the maker)</li>
 * </ul>
 *
 * <p>Tokenising the name into whole words removes this entire class of error at once:
 * "behringer" is one token and never equals "ring".</p>
 *
 * <h2>The second defect: compatibility mentions</h2>
 *
 * <p>A harder shape survives whole-word matching. These names contain the brand as a
 * genuine standalone word, but the brand names the device the product <em>attaches
 * to</em>, not the product's maker:</p>
 *
 * <ul>
 *   <li>"Obiectiv Foto Mirrorless Sigma 10-18mm … <b>pentru Sony</b> E" — a Sigma lens</li>
 *   <li>"Flash VK750II TTL Speedlite <b>for Nikon</b> D7200" — not a Nikon flash</li>
 *   <li>"Incarcator Belkin BoostCharge Pro <b>pentru Apple</b> Watch" — a Belkin charger</li>
 * </ul>
 *
 * <p>Position alone cannot separate these from correct values, because legitimate
 * Romanian product names put the brand well after a long descriptive prefix: "Casti
 * audio in ear <b>Jabra</b> Elite 85t" carries its brand at word five, further from the
 * start than several of the wrong values above. Two mechanisms handle it instead:</p>
 *
 * <ol>
 *   <li><b>First match wins.</b> Scanning left to right, the maker is named before the
 *       compatibility list, because a name introduces its product before it lists what
 *       the product fits. "Obiectiv … Sigma … pentru Sony" resolves to Sigma at word
 *       three and never reaches Sony.</li>
 *   <li><b>Compatibility suppression.</b> When no earlier brand exists, a match is
 *       discarded if a compatibility marker ("pentru", "for", "compatibil") reaches it
 *       across nothing but connectors and device nouns. "for Nikon" and "compatibil cu
 *       Apple" are suppressed; "pentru gaming Logitech" is not, because "gaming" is
 *       neither a connector nor a device noun — it describes the use, so Logitech is
 *       still the maker.</li>
 * </ol>
 *
 * <h2>What it refuses to do</h2>
 *
 * <p>When nothing in the table matches, the method returns {@code null} rather than
 * inventing a brand from the first capitalised word. A missing brand is a gap; a wrong
 * brand is a lie that reaches the storefront filters.</p>
 */
@Component
public class ProductBrandResolver {

    /**
     * How far past the head of the name a brand flagged {@link BrandDef#nearHeadOnly()}
     * may sit. Those entries are ordinary vocabulary as well as trademarks — "orient",
     * "polar", "sharp", "ring" — and are only credible where a maker's name belongs:
     * right after the product noun. Six words covers the longest real Romanian prefix
     * observed in the catalogue ("Aparat foto compact digital de buzunar <i>Canon</i>").
     */
    private static final int NEAR_HEAD_LIMIT = 6;

    /**
     * The word that hands a product line to its parent company: "Ninebot <b>by</b>
     * Segway", "Beats <b>by</b> Dre". Both names are real, but the catalogue and the
     * shopper file the product under the company, so the maker named after this word
     * outranks the line named before it.
     */
    private static final String PARENT_LINK = "by";

    /**
     * Words that announce a compatibility list. Everything they reach names the device
     * the product fits rather than the product's maker.
     */
    private static final Set<String> COMPATIBILITY_MARKERS = Set.of(
            "pentru", "for", "pt", "compatibil", "compatibila", "compatibile", "compatibili",
            "compatible", "compatibility", "compatibilitate", "fits", "fit", "suitable",
            "replacement", "inlocuire", "potrivit", "potrivita", "potrivite",
            "adaptabil", "adaptabila", "works", "supports", "suporta"
    );

    /**
     * The subset of {@link #COMPATIBILITY_MARKERS} that only <em>suggests</em> a
     * compatibility list.
     *
     * <p>"pentru" and "for" carry both readings in a retail name. "Sistem de Stabilizare
     * pentru Telefon DJI Osmo Mobile 8" is a DJI product — "pentru Telefon" describes
     * what the stabiliser holds, and DJI heads its own model line right after it. The
     * same word in "Husa pentru Samsung Galaxy" does announce a fit target. The word
     * alone cannot separate the two, so a weak marker is allowed to lose a tie but is
     * never allowed to erase the only manufacturer a name contains; see
     * {@link #rescueMatch}.</p>
     *
     * <p>Everything outside this set is explicit — "compatible with", "fits",
     * "replacement for" — and states the compatibility reading outright. A strong
     * marker always wins, and no rescue applies behind it.</p>
     */
    private static final Set<String> WEAK_MARKERS = Set.of("pentru", "pt", "for");

    /**
     * Words a compatibility marker may cross while still reaching the brand behind
     * them. These are connectors and the generic device nouns that follow "pentru" in
     * Romanian listings — "pentru telefon Samsung", "compatibil cu ceas Apple". A word
     * outside this set breaks the reach, which is what keeps "pentru gaming Logitech"
     * from losing its real brand.
     */
    private static final Set<String> COMPATIBILITY_BRIDGE = Set.of(
            "cu", "la", "de", "si", "and", "with", "all", "toate", "orice", "any",
            "model", "modelul", "modele", "modelele", "seria", "series", "gama",
            "dispozitiv", "dispozitive", "device", "devices",
            "telefon", "telefoane", "smartphone", "smartphones", "phone", "phones",
            "camera", "camere", "cameras", "aparat", "aparate", "aparatul",
            "ceas", "ceasuri", "watch", "watches", "smartwatch", "smartwatches",
            "laptop", "laptopuri", "laptops", "tableta", "tablete", "tablet", "tablets",
            "casti", "casca", "headphones", "earbuds", "boxa", "boxe", "speaker",
            "mouse", "tastatura", "keyboard", "consola", "console",
            "drona", "drone", "obiectiv", "obiective", "lens", "lenses",
            "bicicleta", "masina", "masini", "motocicleta", "moto", "scuter",
            "aspirator", "frigider", "televizor", "tv", "monitor", "imprimanta"
    );

    /** The reach limit for a marker, measured in bridge words it may cross. */
    private static final int MARKER_REACH = 4;

    // ---------------------------------------------------------------------
    // Brand table
    // ---------------------------------------------------------------------

    /**
     * One manufacturer: the display spelling written to the database, one or more
     * aliases matched against the name, and the guards that keep an ambiguous alias
     * from firing on ordinary vocabulary.
     */
    private static final class BrandDef {

        private final String display;
        private final List<String[]> aliases = new ArrayList<>();
        private Set<String> blockedFollowers = Set.of();
        private boolean nearHeadOnly;
        private boolean deferring;

        private BrandDef(String display) {
            this.display = display;
        }

        private BrandDef alias(String value) {
            String[] words = ProductCategorizer.words(value).toArray(new String[0]);
            if (words.length > 0) {
                aliases.add(words);
            }
            return this;
        }

        /**
         * Disqualifies the match when one of these words follows it. "Ring" is the
         * doorbell company, but "ring light" is a lamp and "ring size" is a
         * measurement — the same token, three different meanings.
         */
        private BrandDef notFollowedBy(String... followers) {
            this.blockedFollowers = Set.of(followers);
            return this;
        }

        /**
         * Restricts the entry to the head region; see {@link #NEAR_HEAD_LIMIT}.
         *
         * <p>Every entry that needs this restriction is a trademark that doubles as
         * ordinary vocabulary, which is the same property {@link #deferring()} answers
         * to, so the two travel together. An entry may still be marked deferring on its
         * own when its alias is common vocabulary but its position is not in doubt.</p>
         */
        private BrandDef nearHeadOnly() {
            this.nearHeadOnly = true;
            this.deferring = true;
            return this;
        }

        /**
         * Marks an entry whose alias is also the ordinary noun for a product category,
         * so it yields to any other manufacturer the same name contains.
         *
         * <p>"Smart Ring SAMSUNG Galaxy Ring" opens with the category, not the maker.
         * Position alone credits the doorbell company, because "Ring" stands one word
         * ahead of "SAMSUNG" — and position alone is right in almost every other name,
         * which is exactly why this handful of entries needs its own rule rather than a
         * weakening of the general one.</p>
         *
         * <p>The rule is a yield, never a veto. A deferring brand still wins every name
         * that contains no other manufacturer, so "Ring Battery Video Doorbell Plus"
         * resolves to Ring exactly as before. It loses only where a second maker is
         * present, and a name that mentions two makers names the category with one of
         * them far more often than it names two competitors.</p>
         */
        private BrandDef deferring() {
            this.deferring = true;
            return this;
        }
    }

    private static BrandDef brand(String display, String... aliases) {
        BrandDef def = new BrandDef(display);
        if (aliases.length == 0) {
            def.alias(display);
        } else {
            for (String alias : aliases) {
                def.alias(alias);
            }
        }
        return def;
    }

    /**
     * Every manufacturer the catalogue actually sells, grouped by the department that
     * introduced it. The first 124 entries were lifted from the brand hints already
     * curated inside {@link ProductCategorizer}, so the two components recognise the
     * same vocabulary; the rest are the mainstream makers those hints never needed.
     *
     * <p>Order in this list does not decide anything — position in the product name
     * does. The grouping exists so a human can find an entry.</p>
     */
    private static final List<BrandDef> BRANDS = List.of(

            // --- Phones, tablets, computing -------------------------------
            brand("Apple", "apple", "iphone", "ipad", "imac", "macbook", "airpods",
                    "airtag", "ipod", "magsafe"),
            brand("Samsung", "samsung", "galaxy"),
            brand("Xiaomi", "xiaomi", "redmi", "poco"),
            brand("Huawei"),
            brand("Honor").nearHeadOnly(),
            brand("Motorola"),
            brand("Nokia"),
            brand("OnePlus", "oneplus", "one plus"),
            brand("Oppo"),
            brand("Vivo").nearHeadOnly(),
            brand("Realme"),
            brand("Google"),
            brand("Sony"),
            brand("Nothing").nearHeadOnly(),
            brand("Oukitel"),
            brand("Doogee"),
            brand("Ulefone"),
            brand("Blackview"),
            brand("Cubot"),
            brand("Invens"),
            brand("Allview"),
            brand("Asus"),
            brand("Acer"),
            brand("Dell"),
            brand("Lenovo"),
            brand("HP"),
            brand("MSI"),
            brand("Gigabyte"),
            brand("Microsoft", "microsoft", "surface"),
            brand("Huion"),
            brand("Wacom"),
            brand("XP-PEN", "xp pen", "xppen"),
            brand("Beelink"),
            brand("GEEKOM", "geekom"),
            brand("GMKtec", "gmktec"),
            brand("Minisforum"),
            brand("Intel"),
            brand("AMD"),
            brand("NVIDIA", "nvidia"),

            // --- Storage and networking -----------------------------------
            brand("SanDisk", "sandisk"),
            brand("Kingston"),
            brand("Crucial"),
            brand("Western Digital", "western digital"),
            brand("Seagate"),
            brand("Transcend"),
            brand("ADATA", "adata"),
            brand("Lexar"),
            brand("Toshiba"),
            brand("Synology"),
            brand("QNAP", "qnap"),
            brand("TerraMaster", "terramaster", "terra master"),
            brand("Asustor"),
            brand("TP-Link", "tp link", "tplink", "tapo", "deco"),
            brand("D-Link", "d link", "dlink"),
            brand("Netgear"),
            brand("Zyxel"),
            brand("Tenda"),
            brand("Mercusys"),
            brand("MikroTik", "mikrotik"),
            brand("Ubiquiti", "ubiquiti", "unifi"),

            // --- Audio -----------------------------------------------------
            brand("JBL", "jbl"),
            brand("Bose"),
            brand("Sennheiser"),
            brand("Jabra"),
            brand("Beats"),
            brand("Marshall"),
            brand("Skullcandy"),
            brand("Soundcore"),
            brand("Anker"),
            brand("Shokz"),
            brand("Shure"),
            brand("AKG", "akg"),
            brand("Audio-Technica", "audio technica"),
            brand("Beyerdynamic"),
            brand("RODE", "rode").nearHeadOnly(),
            brand("Boya"),
            brand("Saramonic"),
            brand("Comica"),
            brand("Focusrite", "focusrite", "scarlett"),
            brand("PreSonus", "presonus"),
            brand("iFi", "ifi"),
            brand("Pyle"),
            brand("Edifier"),
            brand("Tronsmart"),
            brand("Harman Kardon", "harman kardon", "harman"),
            brand("Sonos"),

            // --- Musical instruments ---------------------------------------
            brand("Behringer"),
            brand("Yamaha"),
            brand("Roland"),
            brand("Korg"),
            brand("Fender"),
            brand("Ibanez"),
            brand("Harley Benton", "harley benton"),
            brand("Native Instruments", "native instruments"),
            brand("Arturia"),
            brand("Novation"),
            brand("Akai"),
            brand("Donner"),
            brand("Joyo"),
            brand("Mooer"),
            brand("Hotone"),
            brand("LEKATO", "lekato"),
            brand("NUX", "nux"),

            // --- Photo and video -------------------------------------------
            brand("Nikon", "nikon", "nikkor"),
            brand("Canon"),
            brand("Fujifilm", "fujifilm", "fuji"),
            brand("Olympus"),
            brand("Panasonic", "panasonic", "lumix"),
            brand("Leica"),
            brand("Hasselblad"),
            brand("Pentax"),
            brand("Kodak", "kodak", "pixpro"),
            brand("Sigma"),
            brand("Tamron"),
            brand("Samyang"),
            brand("Viltrox"),
            brand("Laowa"),
            brand("Meike"),
            brand("Godox"),
            brand("Yongnuo"),
            brand("Neewer"),
            brand("SmallRig", "smallrig", "small rig"),
            brand("Ulanzi"),
            brand("Benro"),
            brand("Manfrotto"),
            brand("Joby"),
            brand("Zhiyun"),
            brand("FeiyuTech", "feiyutech", "feiyu"),
            brand("Hohem"),
            brand("DJI", "dji"),
            brand("GoPro", "gopro", "go pro"),
            brand("Insta360", "insta360", "insta 360"),
            brand("AKASO", "akaso"),
            brand("APEMAN", "apeman"),
            brand("Apexcam"),
            brand("HoverAir", "hoverair", "hover air"),
            brand("Autel"),
            brand("Helicon"),
            brand("Elgato"),

            // --- Wearables and health --------------------------------------
            brand("Garmin"),
            brand("Amazfit", "amazfit", "huami"),
            brand("Fitbit"),
            brand("Withings"),
            brand("Suunto"),
            brand("Polar").nearHeadOnly().notFollowedBy("filtru", "filter", "polarizat"),
            brand("Coros"),
            brand("Oura"),
            // "Launch monitor" is the ordinary name for a golf ball-flight sensor, so
            // "Rapsodo Golf MLM1 Launch Monitor" hands the column to the diagnostics
            // company Launch unless the real maker is on the table. Knowing the maker
            // is what settles it; the entry earns its place on that name alone.
            brand("Rapsodo"),
            brand("Casio"),
            brand("Seiko"),
            brand("Citizen").nearHeadOnly(),
            brand("Orient").nearHeadOnly(),
            brand("Tissot"),
            brand("Festina"),
            brand("Fossil"),
            brand("Timex"),
            brand("Bulova"),
            brand("Invicta"),
            brand("Skylight"),
            brand("Omron"),
            brand("Beurer"),
            brand("Medisana"),
            brand("Viatom"),
            brand("Carestream"),
            brand("Medicube"),
            brand("Babysense"),
            brand("Braun"),
            brand("Remington"),
            brand("BaByliss", "babyliss"),
            brand("ghd", "ghd"),
            brand("Oral-B", "oral b"),
            brand("Philips"),
            brand("Dyson"),

            // --- Smart home and surveillance --------------------------------
            brand("Ring").nearHeadOnly()
                    .notFollowedBy("light", "lights", "size", "sizes", "flash",
                            "holder", "lamp", "adapter", "adaptor", "mount", "led"),
            brand("Blink").nearHeadOnly().notFollowedBy("led"),
            brand("Arlo"),
            brand("Eufy"),
            brand("Reolink"),
            brand("EZVIZ", "ezviz"),
            brand("IMOU", "imou"),
            brand("Annke"),
            brand("INSTAR", "instar"),
            brand("Hikvision"),
            brand("Dahua"),
            brand("Wyze"),
            brand("Aqara"),
            brand("Shelly"),
            brand("Sonoff"),
            brand("Tuya"),
            brand("Fibaro"),
            brand("Nuki"),
            brand("Nest").nearHeadOnly(),
            brand("BTicino", "bticino"),
            brand("Wolfang"),
            brand("Ecowitt"),
            brand("Netatmo"),

            // --- Televisions and large appliances ---------------------------
            brand("LG", "lg"),
            brand("Hisense"),
            brand("TCL", "tcl"),
            brand("Thomson"),
            brand("Sharp").nearHeadOnly(),
            brand("Horizon").nearHeadOnly(),
            brand("Vortex").nearHeadOnly(),
            brand("Heinner"),
            brand("Star-Light", "star light", "starlight"),
            brand("Tesla").nearHeadOnly(),
            brand("Whirlpool"),
            brand("Bosch"),
            brand("Siemens"),
            brand("Electrolux"),
            brand("Beko"),
            brand("Arctic").nearHeadOnly(),
            brand("Gorenje"),
            brand("Candy").nearHeadOnly(),
            brand("Indesit"),
            brand("Hotpoint"),
            brand("Zanussi"),
            brand("Tefal"),
            brand("Moulinex"),
            brand("Rowenta"),
            brand("De'Longhi", "delonghi", "de longhi"),
            brand("Krups"),
            brand("Nespresso"),
            brand("Bartesian"),
            brand("Dreame"),
            brand("Roborock"),
            brand("Ecovacs"),
            brand("Bauhaus"),

            // --- Peripherals and accessories ---------------------------------
            brand("Logitech"),
            brand("Razer"),
            brand("Corsair"),
            brand("SteelSeries", "steelseries", "steel series"),
            brand("HyperX", "hyperx", "hyper x"),
            brand("Redragon"),
            brand("Trust"),
            brand("Keychron"),
            brand("Hori").nearHeadOnly(),
            brand("UGREEN", "ugreen"),
            brand("Baseus"),
            brand("Hoco"),
            brand("Remax"),
            brand("Belkin"),
            brand("Spigen"),
            brand("Nillkin"),
            brand("WIWU", "wiwu"),
            brand("Sandberg"),
            brand("Varta"),
            brand("Duracell"),
            brand("Energizer"),

            // --- Automotive, motorcycle, tools --------------------------------
            brand("Brembo"),
            brand("Öhlins", "ohlins", "hlins"),
            brand("Rizoma"),
            brand("Puig"),
            brand("Lightech"),
            brand("SENA", "sena"),
            brand("Cardo"),
            brand("Parani"),
            brand("Givi"),
            brand("SHAD", "shad"),
            brand("Shoei"),
            brand("AGV", "agv"),
            brand("HJC", "hjc"),
            brand("Nolan"),
            brand("Michelin"),
            brand("Pirelli"),
            brand("Continental"),
            brand("ATOTO", "atoto"),
            brand("Podofo"),
            brand("Autovox"),
            brand("Topdon"),
            brand("Xtool"),
            brand("Launch").nearHeadOnly(),
            brand("Ninebot"),
            brand("Segway"),
            brand("DeWalt", "dewalt"),
            brand("Makita"),
            brand("Einhell"),
            brand("Worx"),
            brand("Stanley"),
            brand("Milwaukee"),
            brand("Ryobi"),
            brand("Black+Decker", "black decker", "blackdecker"),
            brand("Metabo"),
            brand("HiKOKI", "hikoki"),
            brand("Fenix").nearHeadOnly(),
            brand("Olight"),
            brand("Nitecore"),
            brand("Ledlenser", "ledlenser", "led lenser"),

            // --- Miscellaneous specialists -------------------------------------
            brand("Cheyenne"),
            brand("Dragonhawk"),
            brand("Biomaser"),
            brand("Timekettle"),
            brand("Vasco"),
            brand("TomTom", "tomtom"),
            brand("Volam"),
            brand("iXroad", "ixroad"),
            brand("CarpodGo", "carpodgo"),
            brand("Amazon", "amazon", "kindle", "alexa")
    );

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * The brand named by a product's own name.
     *
     * @param name raw product name; {@code null} and blank are tolerated
     * @return the canonical display spelling, or {@code null} when the name contains no
     *         recognised manufacturer — never a guess
     */
    public String resolve(String name) {
        Match match = resolveMatch(name);
        return match == null ? null : match.brand();
    }

    /**
     * A recognised manufacturer together with the word index at which the name
     * introduces it.
     *
     * @param brand the canonical display spelling
     * @param index zero-based position of the first word of the match
     */
    public record Match(String brand, int index) {}

    /**
     * The same decision as {@link #resolve(String)}, with the position kept.
     *
     * <p>The backfill needs the position to settle one case the display name alone
     * cannot: a product whose stored brand is a maker this table has never heard of,
     * sitting <em>before</em> a brand the table does recognise. In "Obiectiv Benoison
     * 85mm cu adaptor Sony E" the table can only see Sony, but the name introduces
     * Benoison first, and a name introduces its own maker before anything else. The
     * stored value wins that comparison, which is how the catalogue keeps brands the
     * table has yet to learn.</p>
     *
     * @return the match, or {@code null} when the name names no known manufacturer
     */
    public Match resolveMatch(String name) {
        List<String> words = ProductCategorizer.words(name);
        if (words.isEmpty()) {
            return null;
        }
        int headStart = headStart(words);

        List<Candidate> candidates = new ArrayList<>();
        for (BrandDef def : BRANDS) {
            for (String[] alias : def.aliases) {
                int at = firstAcceptedMatch(def, alias, words, headStart);
                if (at >= 0) {
                    candidates.add(new Candidate(def, alias, at));
                }
            }
        }
        if (candidates.isEmpty()) {
            // Every candidate sat behind a compatibility marker. Before the product is
            // left with an empty brand column, give the suggestive markers a second look.
            return rescueMatch(name, words, headStart);
        }

        Candidate best = null;
        for (Candidate c : candidates) {
            if (yieldsTo(c, candidates, words)) {
                continue;
            }
            // Earliest wins: a name introduces its product before it lists what the
            // product fits. At equal position the longer alias wins, so "Western
            // Digital" beats a hypothetical single-word entry starting on the same
            // token.
            if (best == null
                    || c.at < best.at
                    || (c.at == best.at && c.alias.length > best.alias.length)) {
                best = c;
            }
        }
        if (best == null) {
            // Every survivor yielded to another. That can only happen when the name
            // holds nothing but deferring entries pointing at each other, so fall back
            // to plain position rather than returning an empty brand.
            for (Candidate c : candidates) {
                if (best == null
                        || c.at < best.at
                        || (c.at == best.at && c.alias.length > best.alias.length)) {
                    best = c;
                }
            }
        }
        return new Match(parentOf(best, candidates, words).def.display, best.at);
    }

    /** One accepted alias occurrence, kept so the candidates can be compared to each other. */
    private static final class Candidate {

        private final BrandDef def;
        private final String[] alias;
        private final int at;

        private Candidate(BrandDef def, String[] alias, int at) {
            this.def = def;
            this.alias = alias;
            this.at = at;
        }

        private int end() {
            return at + alias.length;
        }
    }

    /**
     * Whether a deferring entry stands aside for another manufacturer in the same name.
     *
     * <p>Two shapes trigger the yield, and only these two.</p>
     *
     * <ol>
     *   <li><b>The alias is repeated.</b> "Smart <i>Ring</i> SAMSUNG Galaxy <i>Ring</i>"
     *       says "ring" twice and "Samsung" once. A maker is named once; a category is
     *       restated. The repetition is what separates this name from "Ring Video
     *       Doorbell Plus", where the single occurrence is the company.</li>
     *   <li><b>Another maker begins on the very next word.</b> "Smart <i>Ring</i>
     *       <i>SAMSUNG</i> Galaxy" puts the two adjacent, which is what a category noun
     *       followed by its manufacturer looks like. Adjacency is required rather than
     *       mere proximity, because "Televizor <i>Sharp</i> 55GP6260E 4K <i>Google</i>
     *       TV" is a Sharp television with Google's operating system and must not
     *       change hands.</li>
     * </ol>
     *
     * <p>In both shapes the yield is conditional on a second manufacturer actually being
     * present. A deferring entry alone in a name always wins it.</p>
     */
    private boolean yieldsTo(Candidate candidate, List<Candidate> all, List<String> words) {
        if (!candidate.def.deferring) {
            return false;
        }
        boolean repeated = matchAt(words, candidate.alias, candidate.at + 1) >= 0;
        for (Candidate other : all) {
            if (other.def == candidate.def) {
                continue;
            }
            if (repeated || other.at == candidate.end()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The company behind a product line, when the name spells the relationship out.
     *
     * <p>"Trotineta electrica <i>Ninebot by Segway</i> KickScooter" names both; the
     * catalogue files it under Segway, because that is the company and the word "by"
     * says so. Without the link word the earlier name stands, which is why this reads
     * one specific token rather than guessing at corporate structure.</p>
     */
    private Candidate parentOf(Candidate candidate, List<Candidate> all, List<String> words) {
        int link = candidate.end();
        if (link >= words.size() || !PARENT_LINK.equals(words.get(link))) {
            return candidate;
        }
        for (Candidate other : all) {
            if (other.def != candidate.def && other.at == link + 1) {
                return other;
            }
        }
        return candidate;
    }

    /**
     * Where a value already stored in the brand column appears in the product name as
     * that product's own brand.
     *
     * <p>Same acceptance rules as {@link #mentionsAsOwnBrand(String, String)} — this is
     * that method with the position kept instead of collapsed to a boolean.</p>
     *
     * @return the zero-based word index, or {@code -1} when the value does not survive
     */
    public int mentionIndex(String name, String stored) {
        if (stored == null || stored.isBlank()) {
            return -1;
        }
        List<String> words = ProductCategorizer.words(name);
        String[] candidate = ProductCategorizer.words(stored).toArray(new String[0]);
        if (words.isEmpty() || candidate.length == 0) {
            return -1;
        }
        int headStart = headStart(words);
        for (BrandDef def : BRANDS) {
            for (String[] alias : def.aliases) {
                if (sameWords(alias, List.of(candidate))) {
                    return firstAcceptedMatch(def, alias, words, headStart);
                }
            }
        }
        int from = 0;
        while (true) {
            int at = matchAt(words, candidate, from);
            if (at < 0) {
                return -1;
            }
            from = at + 1;
            if (!suppressedByCompatibility(words, at)) {
                return at;
            }
        }
    }

    /** Whether the table already knows this exact value as a brand alias. */
    public boolean isKnownBrand(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        List<String> words = ProductCategorizer.words(value);
        if (words.isEmpty()) {
            return false;
        }
        for (BrandDef def : BRANDS) {
            for (String[] alias : def.aliases) {
                if (sameWords(alias, words)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a value already stored in the {@code brand} column is still defensible as
     * this product's own manufacturer.
     *
     * <p>This is the safety net that keeps the backfill from destroying information it
     * merely fails to recognise. A brand the table has never heard of — a niche seller,
     * a new maker — is left alone as long as it appears in the name as a whole word
     * outside a compatibility list. Only a value that is <em>provably</em> wrong is
     * cleared: absent from the name entirely (the substring artefacts "Ring" and "HP"),
     * or present only as the device the product attaches to.</p>
     *
     * @param name    the product name
     * @param stored  the value currently in the brand column
     * @return {@code true} when the stored value survives inspection
     */
    public boolean mentionsAsOwnBrand(String name, String stored) {
        return mentionIndex(name, stored) >= 0;
    }

    /**
     * The table's spelling for a value the sheet supplied in arbitrary casing, so
     * "LOGITECH", "logitech" and "Logitech" collapse into one filter entry on the
     * storefront.
     *
     * @return the canonical spelling when the value names a known brand, otherwise the
     *         trimmed input unchanged
     */
    public String canonicalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        List<String> words = ProductCategorizer.words(trimmed);
        if (words.isEmpty()) {
            return trimmed;
        }
        for (BrandDef def : BRANDS) {
            for (String[] alias : def.aliases) {
                if (sameWords(alias, words)) {
                    return def.display;
                }
            }
        }
        return trimmed;
    }

    /** Every display spelling the resolver can produce, for administration screens. */
    public Set<String> knownBrands() {
        Set<String> all = new LinkedHashSet<>();
        for (BrandDef def : BRANDS) {
            all.add(def.display);
        }
        return all;
    }

    // ---------------------------------------------------------------------
    // Matching internals
    // ---------------------------------------------------------------------

    /** The index of this alias's earliest occurrence that passes every guard, or -1. */
    private int firstAcceptedMatch(BrandDef def, String[] alias, List<String> words, int headStart) {
        int from = 0;
        while (true) {
            int at = matchAt(words, alias, from);
            if (at < 0) {
                return -1;
            }
            from = at + 1;
            if (accepted(def, alias, words, at, headStart)) {
                return at;
            }
        }
    }

    private boolean accepted(BrandDef def, String[] alias, List<String> words, int at, int headStart) {
        if (def.nearHeadOnly && at - headStart > NEAR_HEAD_LIMIT) {
            return false;
        }
        if (!def.blockedFollowers.isEmpty()) {
            int after = at + alias.length;
            if (after < words.size() && def.blockedFollowers.contains(words.get(after))) {
                return false;
            }
        }
        if (suppressedByCompatibility(words, at)) {
            return false;
        }
        return !repeatedAsFitTarget(def, alias, words, at);
    }

    /**
     * True when the same alias appears again later in the name, that later occurrence is
     * explicitly a fit target, and the alias names a product line rather than the company.
     *
     * <p>"Husa tastatura WIWU iPad Pro 13 inch Keyboard Case <b>for iPad Pro 13</b>" states
     * once, unambiguously, what the case fits. The earlier "iPad Pro 13" is the same
     * phrase describing the same device, so reading it as the manufacturer would credit
     * Apple with a WIWU accessory. One explicit mention settles every repetition of it.</p>
     *
     * <p>The rule is deliberately confined to product-line aliases. A company name
     * repeated after a marker — "Incarcator Belkin … for Belkin devices" — still names
     * Belkin as the maker at its first occurrence, and nothing about the repetition
     * changes that.</p>
     */
    /**
     * True when the name states its own model code before the given position.
     *
     * <p>A model code is a token that mixes letters and digits — "VK750II", "85mm",
     * "DJX900USB". Generic Romanian and English retail vocabulary never does, and a bare
     * number ("13", "8") is a size or a count rather than an identity, so both are
     * excluded. When such a code precedes a compatibility marker the product has already
     * introduced itself, and everything the marker reaches describes what that product
     * fits. When no code precedes it, the marker is the only thing standing between the
     * catalogue and the single manufacturer the name contains.</p>
     */
    private static boolean identifiedBefore(List<String> words, int at) {
        for (int i = 0; i < at && i < words.size(); i++) {
            String word = words.get(i);
            if (word.length() < 3) {
                continue;
            }
            boolean letter = false;
            boolean digit = false;
            for (int c = 0; c < word.length(); c++) {
                char ch = word.charAt(c);
                if (ch >= '0' && ch <= '9') {
                    digit = true;
                } else {
                    letter = true;
                }
            }
            if (letter && digit) {
                return true;
            }
        }
        return false;
    }

    private boolean repeatedAsFitTarget(BrandDef def, String[] alias, List<String> words, int at) {
        if (isCompanyAlias(def, alias)) {
            return false;
        }
        int from = at + 1;
        while (true) {
            int later = matchAt(words, alias, from);
            if (later < 0) {
                return false;
            }
            if (markerStrength(words, later) != MARKER_NONE) {
                return true;
            }
            from = later + 1;
        }
    }

    /** Whether this alias is the manufacturer's own name rather than one of its product lines. */
    private static boolean isCompanyAlias(BrandDef def, String[] alias) {
        return sameWords(alias, ProductCategorizer.words(def.display));
    }

    /**
     * True when a compatibility marker reaches this position across nothing but
     * connectors, device nouns and numbers.
     *
     * <p>The bridge test is what makes the rule usable. A marker that simply appears
     * somewhere earlier in a long name proves nothing — "Casti gaming pentru PC, driver
     * 50mm, Logitech G435" would lose its brand. A marker that reaches the brand across
     * only "cu", "la", "telefon", "ceas" and the like is describing what the product
     * fits, and the brand behind it belongs to that other device.</p>
     */
    private boolean suppressedByCompatibility(List<String> words, int at) {
        return markerStrength(words, at) != MARKER_NONE;
    }

    /** No compatibility marker reaches the position. */
    private static final int MARKER_NONE = 0;
    /** A "pentru"/"for" reaches the position — suggestive, not conclusive. */
    private static final int MARKER_WEAK = 1;
    /** An explicit "compatible with"/"fits"/"replacement" reaches the position. */
    private static final int MARKER_STRONG = 2;

    /**
     * The strength of the compatibility marker that reaches this position, or
     * {@link #MARKER_NONE}.
     *
     * <p>Same bridge walk as before — the marker must reach the brand across nothing but
     * connectors, device nouns and numbers — with the answer graded instead of collapsed
     * to a boolean, so {@link #rescueMatch} can tell an explicit compatibility list from
     * a Romanian "pentru" that happens to sit in front of the maker's own name.</p>
     */
    private int markerStrength(List<String> words, int at) {
        int earliest = Math.max(0, at - MARKER_REACH - 1);
        for (int m = at - 1; m >= earliest; m--) {
            String word = words.get(m);
            if (COMPATIBILITY_MARKERS.contains(word)) {
                return WEAK_MARKERS.contains(word) ? MARKER_WEAK : MARKER_STRONG;
            }
            if (!COMPATIBILITY_BRIDGE.contains(word) && !word.matches("[0-9]+")) {
                return MARKER_NONE;
            }
        }
        return MARKER_NONE;
    }

    /**
     * The word indices at which a punctuation-delimited segment of the name ends.
     *
     * <p>The shared tokeniser collapses every non-alphanumeric run to a space, which is
     * what makes whole-word matching reliable but also erases the comma in "… Lens for
     * Sony, Medium Telephoto Lenses". That comma is the evidence that "Sony" is a bare
     * fit target rather than the head of a model line, so the boundaries are recovered
     * here by tokenising each segment separately and accumulating the counts. Splitting
     * on punctuation and then tokenising yields exactly the same token sequence as
     * tokenising the whole name, because the separators collapse to spaces either way —
     * the two views cannot drift apart.</p>
     *
     * @param name the raw product name
     * @return the zero-based index of the last word of every segment
     */
    private static Set<Integer> segmentEnds(String name) {
        Set<Integer> ends = new LinkedHashSet<>();
        if (name == null || name.isBlank()) {
            return ends;
        }
        int consumed = 0;
        for (String segment : name.split("[,;:()\\[\\]{}/|\\-\\u2013\\u2014]+")) {
            int size = ProductCategorizer.words(segment).size();
            if (size == 0) {
                continue;
            }
            consumed += size;
            ends.add(consumed - 1);
        }
        return ends;
    }

    /**
     * The last resort for a name whose every candidate sits behind a compatibility
     * marker.
     *
     * <p>Suppression exists to break ties, not to destroy information. When it removes
     * the only manufacturer a name contains, the product is left with nothing — and an
     * empty brand column is strictly worse than the maker the name states plainly. This
     * pass reinstates such a candidate under three conditions, all of which must hold:</p>
     *
     * <ol>
     *   <li>the marker is weak. "Compatible with Nikon D3500 D3400 …" says outright that
     *       the Nikon bodies are what the flash fits; nothing is rescued behind an
     *       explicit statement.</li>
     *   <li>the candidate heads its own model designation — a further word follows it
     *       inside the same punctuation-delimited segment, and that word is not another
     *       connector. "pentru Telefon <b>DJI Osmo Mobile 8</b>" continues into the
     *       product; "Lens for <b>Sony</b>," stops at the comma and names only the
     *       mount.</li>
     *   <li>the name has not already identified the product before the marker. "Flash
     *       <b>VK750II</b> TTL Camera Flash Speedlite for Nikon D7200" states its own
     *       model code first, so everything after "for" is what that flash mounts on.
     *       "Sistem de Stabilizare pentru Telefon DJI Osmo Mobile 8" offers no such
     *       code — the name carries exactly one identity, and it is DJI's.</li>
     *   <li>it is the alias's first occurrence. A later repetition of a name the product
     *       already used as a fit target — "Husa … <b>iPad</b> Pro … for <b>iPad</b>
     *       Pro" — is the same fit target twice, not a maker.</li>
     * </ol>
     *
     * @param name    the raw product name, needed for the segment boundaries
     * @param words   the tokenised name
     * @param headStart the index at which the name stops being packaging filler
     * @return the reinstated match, or {@code null} when nothing qualifies
     */
    private Match rescueMatch(String name, List<String> words, int headStart) {
        Set<Integer> ends = segmentEnds(name);
        BrandDef bestDef = null;
        int bestIndex = Integer.MAX_VALUE;
        int bestLength = 0;

        for (BrandDef def : BRANDS) {
            for (String[] alias : def.aliases) {
                int at = matchAt(words, alias, 0);
                if (at < 0) {
                    continue;
                }
                if (def.nearHeadOnly && at - headStart > NEAR_HEAD_LIMIT) {
                    continue;
                }
                int after = at + alias.length;
                if (!def.blockedFollowers.isEmpty()
                        && after < words.size()
                        && def.blockedFollowers.contains(words.get(after))) {
                    continue;
                }
                if (markerStrength(words, at) != MARKER_WEAK) {
                    continue;
                }
                if (ends.contains(after - 1) || after >= words.size()) {
                    continue;
                }
                String next = words.get(after);
                if (COMPATIBILITY_BRIDGE.contains(next) || COMPATIBILITY_MARKERS.contains(next)) {
                    continue;
                }
                if (identifiedBefore(words, at)) {
                    continue;
                }
                if (at < bestIndex || (at == bestIndex && alias.length > bestLength)) {
                    bestDef = def;
                    bestIndex = at;
                    bestLength = alias.length;
                }
            }
        }
        return bestDef == null ? null : new Match(bestDef.display, bestIndex);
    }

    /** The first index at which the alias occurs as a contiguous run of whole words. */
    private static int matchAt(List<String> words, String[] alias, int from) {
        if (alias.length == 0 || alias.length > words.size()) {
            return -1;
        }
        int last = words.size() - alias.length;
        for (int i = Math.max(0, from); i <= last; i++) {
            boolean hit = true;
            for (int j = 0; j < alias.length; j++) {
                if (!words.get(i + j).equals(alias[j])) {
                    hit = false;
                    break;
                }
            }
            if (hit) {
                return i;
            }
        }
        return -1;
    }

    /** Skips the condition and packaging words the classifier also skips. */
    private static int headStart(List<String> words) {
        int head = 0;
        while (head < words.size() && ProductCategorizer.isLeadingFiller(words.get(head))) {
            head++;
        }
        return head >= words.size() ? 0 : head;
    }

    private static boolean sameWords(String[] alias, List<String> words) {
        if (alias.length != words.size()) {
            return false;
        }
        for (int i = 0; i < alias.length; i++) {
            if (!alias[i].equals(words.get(i))) {
                return false;
            }
        }
        return true;
    }
}
