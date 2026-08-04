package com.electroshop.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Derives the category / subcategory pair of a product from its commercial name.
 *
 * <p><b>Why this class was rewritten.</b> The first implementation walked an ordered
 * rule list and returned the first rule whose keyword appeared anywhere inside the
 * lower-cased product name, using a plain {@code String.contains}. That design has
 * two structural defects, both observed on live data:</p>
 *
 * <ol>
 *   <li><b>Unbounded substring matching.</b> {@code contains("moto")} is true for
 *       "MOTOROLA", {@code contains("oled")} is true for "AMOLED",
 *       {@code contains("casti")} is true for "Casting",
 *       {@code contains("monitor")} is true for "Monitorizarea",
 *       {@code contains("ipl")} is true for "multiple". A phone was filed under
 *       Auto &amp; Moto, a phone with an AMOLED panel under Televizoare, and a wireless
 *       screen-casting adapter under Casti.</li>
 *   <li><b>First-match-wins ignores relevance.</b> A single incidental word buried at
 *       the end of a 200-character title outranked the head noun that actually names
 *       the product, because its rule happened to sit higher in the list. "Smartwatch
 *       Garmin Instinct 3, 50mm, AMOLED, GPS" matched the {@code gps} rule before the
 *       {@code smartwatch} rule and landed in Auto &amp; Moto / GPS &amp; Navigatie.</li>
 * </ol>
 *
 * <p><b>The model implemented here.</b> The name is tokenised into whole words, so a
 * keyword only matches a complete word — "moto" no longer matches "motorola" and
 * "monitor" no longer matches "monitorizarea". Every rule then accumulates an additive
 * score instead of short-circuiting, and the highest total wins:</p>
 *
 * <ul>
 *   <li><b>Weighted terms.</b> A multi-word phrase ("camera de supraveghere", "power
 *       bank", "aparat foto") is worth {@value #W_PHRASE}; a decisive single type noun
 *       ("telefon", "casti", "husa") {@value #W_TYPE}; an ambiguous noun ("camera")
 *       {@value #W_WEAK}; a bare brand ("garmin", "dji") {@value #W_BRAND}; an
 *       incidental hint ("gps", "gaming") {@value #W_HINT}. A brand can therefore never
 *       outvote the noun that names the product.</li>
 *   <li><b>Head-noun weighting.</b> Romanian retail titles lead with the product type:
 *       "<i>Telefon</i> MOTOROLA Edge 60", "<i>Husa</i> tastatura WIWU iPad Pro",
 *       "<i>Trotineta</i> electrica Ninebot ... Motor 450 W". A term matching the first
 *       meaningful word scores &times;{@value #HEAD_MULTIPLIER}, one inside the first
 *       {@value #NEAR_HEAD_LIMIT} words &times;{@value #NEAR_HEAD_MULTIPLIER}, anything
 *       later &times;1. Weak, brand and hint terms have that bonus capped at
 *       &times;{@value #CAPPED_MULTIPLIER} so a leading brand cannot hijack the result.
 *       This is what makes an accessory beat the device it is an accessory for: in
 *       "Husa tastatura WIWU iPad Pro" the head noun <i>husa</i> scores 24 against
 *       <i>tastatura</i> 12 and <i>ipad</i> 4.</li>
 *   <li><b>Negative keywords.</b> A rule is disqualified outright when a negative term
 *       is present, which is how "baby monitor", "sleep monitor" and "golf launch
 *       monitor" are kept out of Monitoare.</li>
 *   <li><b>Condition prefixes are ignored for position.</b> "Folosit", "Nou",
 *       "Resigilat", "Set", "Kit" and leading digits are skipped when deciding which
 *       word is the head noun, so "Folosit Telefon rugged Doogee" is scored exactly
 *       like "Telefon rugged Doogee".</li>
 * </ul>
 *
 * <p>Ties are broken by rule order, so the more specific rule is declared first. When
 * no rule reaches {@value #MIN_SCORE} the product falls back to
 * {@value #DEFAULT_CATEGORY} / {@value #DEFAULT_SUBCATEGORY}.</p>
 *
 * <p>The rule table doubles as the canonical taxonomy: every subcategory belongs to
 * exactly one category, and {@link #canonicalCategoryFor(String)} exposes that mapping
 * so already-stored rows with a mismatched pair can be repaired.</p>
 */
@Component
public class ProductCategorizer {

    /** The category / subcategory pair a product name resolves to. */
    public record Categorization(String category, String subcategory) {}

    // ---------------------------------------------------------------------
    // Scoring constants
    // ---------------------------------------------------------------------

    /** A multi-word phrase that names the product type almost unambiguously. */
    private static final int W_PHRASE = 10;
    /** A single noun that names the product type. */
    private static final int W_TYPE = 6;
    /** A noun that names the product type but is used in many other contexts. */
    private static final int W_WEAK = 4;
    /** A manufacturer name, which alone never identifies the product type. */
    private static final int W_BRAND = 2;
    /** An incidental feature word. */
    private static final int W_HINT = 1;

    /** Multiplier applied when the term matches the first meaningful word. */
    private static final int HEAD_MULTIPLIER = 4;
    /** Multiplier applied when the term matches inside the opening words. */
    private static final int NEAR_HEAD_MULTIPLIER = 2;
    /** Last word offset that still counts as "near the head". */
    private static final int NEAR_HEAD_LIMIT = 3;
    /** Highest multiplier a weak / brand / hint term may receive. */
    private static final int CAPPED_MULTIPLIER = 2;
    /** Extra words tolerated inside a phrase, so "camera de supraveghere" matches "camera supraveghere". */
    private static final int PHRASE_SLACK = 2;
    /** Score a rule must reach before it is preferred over the default. */
    private static final int MIN_SCORE = 4;

    public static final String DEFAULT_CATEGORY = "Diverse electronice";
    public static final String DEFAULT_SUBCATEGORY = "Gadgeturi";

    // ---------------------------------------------------------------------
    // Term / Rule model
    // ---------------------------------------------------------------------

    /**
     * One keyword of a rule. {@code words} holds the already-normalised tokens the
     * keyword is made of; {@code capped} marks the low-confidence weights whose
     * head-position bonus is limited to {@value #CAPPED_MULTIPLIER}.
     */
    private record Term(String[] words, int weight, boolean capped) {}

    /** A candidate classification together with the evidence that supports it. */
    private static final class Rule {
        private final String category;
        private final String subcategory;
        private final List<Term> terms = new ArrayList<>();
        private final List<String[]> negatives = new ArrayList<>();
        private final List<String[]> headNegatives = new ArrayList<>();

        private Rule(String category, String subcategory) {
            this.category = category;
            this.subcategory = subcategory;
        }

        private Rule add(int weight, boolean capped, String... keywords) {
            for (String keyword : keywords) {
                terms.add(new Term(split(keyword), weight, capped));
            }
            return this;
        }

        /** Multi-word phrases that name the product type almost unambiguously. */
        private Rule phrase(String... keywords) { return add(W_PHRASE, false, keywords); }

        /** Single nouns that name the product type. */
        private Rule type(String... keywords) { return add(W_TYPE, false, keywords); }

        /** Nouns that suggest the product type but appear in many other contexts. */
        private Rule weak(String... keywords) { return add(W_WEAK, true, keywords); }

        /** Manufacturer names, which alone never identify the product type. */
        private Rule brand(String... keywords) { return add(W_BRAND, true, keywords); }

        /** Incidental feature words. */
        private Rule hint(String... keywords) { return add(W_HINT, true, keywords); }

        /** Words whose presence anywhere in the name disqualifies this rule entirely. */
        private Rule not(String... keywords) {
            for (String keyword : keywords) {
                negatives.add(split(keyword));
            }
            return this;
        }

        /**
         * Words that disqualify this rule only when they sit at or near the head of the
         * name. Used for accessory nouns: "Husa smartwatch Garmin" is a case, but
         * "Smartwatch Amazfit Active 2, resigilat lipsa curele/incarcator" is still a
         * smartwatch — the accessory noun there is trailing detail, not the product.
         */
        private Rule notHead(String... keywords) {
            for (String keyword : keywords) {
                headNegatives.add(split(keyword));
            }
            return this;
        }
    }

    private static Rule rule(String category, String subcategory) {
        return new Rule(category, subcategory);
    }

    /**
     * Leading words that describe the condition or the packaging rather than the
     * product, and are skipped when locating the head noun.
     */
    private static final Set<String> LEADING_FILLERS = Set.of(
            "folosit", "folosita", "folosite", "folositi",
            "nou", "noua", "noi", "sigilat", "sigilata", "sigilate",
            "resigilat", "resigilata", "resigilate", "desigilat", "desigilata",
            "used", "second", "hand", "refurbished", "open", "box", "openbox",
            "set", "kit", "pachet", "pack", "bundle", "combo", "promo", "oferta",
            "original", "premium", "profesional", "profesionala", "de", "cu", "la", "si", "and"
    );

    // ---------------------------------------------------------------------
    // Rule table — ordered most specific first; order only breaks score ties.
    // ---------------------------------------------------------------------

    private static final List<Rule> RULES = List.of(

            // --- Tattoo equipment -------------------------------------------------
            rule("Ingrijire personala", "Aparate tatuat")
                    .phrase("aparat tatuat", "tattoo machine", "tattoo pen", "tattoo printer",
                            "masina tatuat", "masina tatuaj", "tattoo gun", "tattoo kit")
                    .type("tatuat", "tatuaj", "tattoo", "eztatttoo")
                    .brand("dragonhawk", "biomaser", "cheyenne"),

            // --- Medical devices --------------------------------------------------
            rule("Sanatate", "Dispozitive medicale")
                    .phrase("digital radiography", "radiography system", "intraoral camera",
                            "camera intraorala", "tensiometru digital", "aparat masurat tensiunea",
                            "circulation booster", "wrist oximeter", "pulse oximeter",
                            "blood pressure", "aparat aerosoli", "nebulizator medical",
                            "termometru medical", "electrostimulator muscular")
                    .type("oximeter", "oxymeter", "pulsoximetru", "tensiometru", "glucometru",
                            "nebulizator", "aerosoli", "stetoscop", "defibrilator", "rvg",
                            "revitive", "checkme")
                    .brand("carestream", "omron", "beurer", "medisana", "viatom")
                    .hint("medical", "dentar", "dental", "radiologie"),

            // --- Vaping -----------------------------------------------------------
            rule("Diverse electronice", "Vaping")
                    .phrase("dry herb", "herb vaporizer", "herb vaporiser", "tigara electronica")
                    .type("vaporizer", "vaporiser", "vape", "vaping", "vaporesso", "vaporeso",
                            "atomizor"),

            // --- Translators ------------------------------------------------------
            rule("Traducatoare", "Translatoare AI")
                    .phrase("translator vocal", "dispozitiv traducere", "language translator",
                            "traducator vocal", "casti traducere")
                    .type("translator", "traducator", "traducatoare")
                    .brand("vasco", "timekettle"),

            // --- Car parts --------------------------------------------------------
            rule("Auto & Moto", "Piese auto")
                    .phrase("jante aliaj", "jante auto", "set jante", "placute frana",
                            "kit distributie")
                    .type("jante", "janta", "anvelope", "anvelopa")
                    .not("moto", "motocicleta", "motorcycle"),

            // --- Motorcycle parts -------------------------------------------------
            rule("Auto & Moto", "Piese moto")
                    .phrase("steering damper", "clutch master", "brake lever", "piese moto",
                            "amortizor directie", "scarite moto", "ghidon moto")
                    .type("rearset", "rearsets", "semimanere")
                    .brand("ohlins", "brembo", "lightech", "rizoma", "puig"),

            // --- Electric mobility ------------------------------------------------
            rule("Mobilitate electrica", "Trotinete")
                    .phrase("trotineta electrica", "electric scooter", "e scooter")
                    .type("trotineta", "trotinete", "kickscooter", "scooter", "scuter")
                    .brand("ninebot", "segway")
                    .not("lanterna", "far", "husa", "incarcator", "acumulator", "camera"),

            rule("Mobilitate electrica", "Biciclete electrice")
                    .phrase("bicicleta electrica", "electric bike", "e bike",
                            "mountain bike electric")
                    .type("bicicleta", "biciclete", "ebike")
                    .not("lanterna", "far", "husa", "suport", "pompa", "casca", "ciclocomputer",
                            "camera", "sonerie"),

            rule("Mobilitate electrica", "Hoverboard")
                    .type("hoverboard", "hoverboards", "monociclu"),

            // --- Wearables --------------------------------------------------------
            rule("Wearables", "Smart Rings")
                    .phrase("smart ring", "inel inteligent", "galaxy ring", "oura ring")
                    .brand("oura"),

            rule("Wearables", "Ochelari smart / AR-VR")
                    .phrase("smart glasses", "ochelari smart", "ochelari inteligenti",
                            "ar glasses", "vr headset", "casca vr", "oakley meta",
                            "ray ban meta", "meta quest", "ochelari video")
                    .type("xreal", "viture", "rokid")
                    .hint("ochelari"),

            rule("Wearables", "Smartwatch & Ceasuri")
                    .phrase("apple watch", "galaxy watch", "smart band", "bratara fitness",
                            "ceas inteligent", "sport watch", "ceas barbatesc", "ceas de dama",
                            "ceas dama", "ceas automatic", "ceas clasic", "fitness tracker",
                            "activity tracker", "watch fit")
                    .type("smartwatch", "smartwatches", "ceas", "ceasuri", "bratara", "wearable")
                    .brand("fitbit", "amazfit", "coros", "suunto", "garmin", "withings", "polar",
                            "citizen", "seiko", "bulova", "invicta", "orient", "timex", "bauhaus")
                    .hint("watch")
                    .not("launch", "golf", "baby", "bebelusi")
                    .notHead("husa", "curea", "curele", "folie", "incarcator", "statie",
                            "dock", "protectie"),

            // --- Phones -----------------------------------------------------------
            rule("Telefoane", "Telefoane")
                    .phrase("telefon mobil", "mobile phone", "telefon rugged", "smart phone",
                            "telefon smart")
                    .type("telefon", "telefoane", "smartphone", "smartphones", "iphone", "phone")
                    .brand("oukitel", "doogee", "ulefone", "blackview", "cubot")
                    .hint("5g", "dual", "sim")
                    // Only words a genuine phone listing can never contain stay absolute.
                    // A garage-door opener advertises "control from your phone"; no phone
                    // advertises a garage.
                    .not("opener", "gate", "garage")
                    // Everything else here names an accessory, and an accessory announces
                    // itself at the head of the name: "Husa telefon", "Incarcator rapid",
                    // "Suport auto". The same words appear deep inside the spec dump of a
                    // real phone — "16MP Camera", "casti incluse", "lentila macro" — where
                    // they describe a feature, not the product. Absolute negatives could
                    // not tell the two apart and threw the phone away: that is how
                    // "Telefon invens NOTE TK01 ... 16MP Camera" ended up under
                    // Foto & Video / Camere. Position is what separates them.
                    .notHead("husa", "folie", "carcasa", "suport", "incarcator", "cablu",
                            "adaptor", "gamepad", "rama", "casti", "boxa", "camera",
                            "tripod", "stativ", "lentila", "gimbal"),

            // --- Tablets & readers -------------------------------------------------
            rule("Tablete", "E-readere")
                    .phrase("e reader", "e book", "cititor ebook", "cititor carti",
                            "ebook reader", "onyx boox", "e ink")
                    .type("ereader", "ebook", "kindle", "kobo", "boox"),

            rule("Tablete", "Tablete")
                    .type("tableta", "tablete", "tablet", "ipad")
                    .brand("amazon")
                    .hint("fire")
                    .not("husa", "folie", "carcasa", "tastatura", "keyboard", "suport", "stand",
                            "incarcator", "cablu", "adaptor", "pencil", "stylus", "grafica",
                            "graphics", "acumulator", "power", "bank", "protectie", "curea"),

            // --- Computers ---------------------------------------------------------
            rule("Laptopuri", "Laptopuri")
                    .phrase("laptop gaming", "notebook business", "ultrabook")
                    .type("laptop", "laptopuri", "macbook", "notebook", "chromebook")
                    .not("husa", "folie", "suport", "stand", "rucsac", "geanta", "incarcator",
                            "cooler", "docking", "dock", "adaptor"),

            rule("Sisteme PC", "Mini PC")
                    .phrase("mini pc", "mini computer", "barebone pc")
                    .type("nuc")
                    .brand("geekom", "gmktec", "beelink", "minisforum"),

            rule("Sisteme PC", "Desktop PC")
                    .phrase("sistem desktop", "desktop pc", "unitate pc", "calculator desktop",
                            "all in one pc", "workstation pc"),

            // --- Monitors ----------------------------------------------------------
            rule("Monitoare", "Monitoare")
                    .phrase("monitor gaming", "monitor curbat", "monitor pc")
                    .type("monitor", "monitoare")
                    .not("baby", "bebe", "bebelusi", "bebelus", "sleep", "hr", "heart", "blood",
                            "cardiac", "fetal", "launch", "golf", "supraveghere", "camera",
                            "casti", "headphones", "monitoring", "suport", "brat", "stand"),

            // --- PC peripherals ----------------------------------------------------
            rule("Periferice PC", "Periferice")
                    .phrase("magic keyboard", "apple pencil", "tableta grafica", "graphics tablet",
                            "mouse gaming", "tastatura mecanica", "kvm switch", "docking station",
                            "magic trackpad", "mouse wireless", "s pen",
                            "barcode scanner", "barcode reader", "rfid reader", "cititor coduri",
                            "canoscan", "scanner documente", "document scanner")
                    .type("tastatura", "tastaturi", "keyboard", "mouse", "trackpad", "stylus",
                            "gamepad", "docking", "webcam", "barcode")
                    .brand("wiwu")
                    .hint("qwertz", "azerty", "dpi")
                    .not("husa", "folie", "carcasa", "protectie", "consola", "console",
                            "playstation", "xbox", "nintendo"),

            rule("Componente PC", "Componente")
                    .phrase("placa video", "placa de baza", "sursa alimentare", "cooler procesor",
                            "memorie ram", "ram ddr", "ddr4", "ddr5")
                    .type("procesor", "cpu", "gpu", "motherboard", "psu")
                    .brand("nvidia")
                    .not("nas", "laptop", "mini", "telefon", "tableta"),

            // --- Storage -----------------------------------------------------------
            rule("Stocare", "NAS")
                    .phrase("nas storage", "network attached", "server nas", "nas server")
                    .type("nas", "nasync", "drivestor")
                    .brand("synology", "qnap", "asustor", "terramaster")
                    .not("ups", "backup", "husa", "cablu"),

            rule("Stocare", "Stocare & Memorie")
                    .phrase("hard disk", "card de memorie", "memory card", "usb stick",
                            "stick usb", "usb flash", "flash drive", "ssd extern", "hdd extern",
                            "dvd rw", "solid state")
                    .type("ssd", "hdd", "microsd", "sdxc", "sdhc", "cfexpress")
                    .not("laptop", "nas"),

            // --- Network -----------------------------------------------------------
            rule("Retea", "Retea & Wi-Fi")
                    .phrase("access point", "switch retea", "range extender", "wifi mesh",
                            "sistem mesh", "powerline adapter", "placa de retea", "modem router")
                    .type("router", "routere", "mesh", "modem", "repeater")
                    .hint("wlan", "ethernet")
                    .not("husa", "cablu", "suport"),

            // --- Audio -------------------------------------------------------------
            rule("Audio", "Casti")
                    .phrase("casti audio", "casti wireless", "casti gaming", "true wireless",
                            "in ear", "over ear", "on ear", "open ear", "bone conduction",
                            "noise cancelling headphones")
                    .type("casti", "casca", "headphone", "headphones", "headset", "earbud",
                            "earbuds", "earphone", "earphones", "airpods", "nearphones")
                    .brand("shokz")
                    .hint("buds")
                    .not("husa", "folie", "suport", "stand", "cablu", "incarcator", "adaptor",
                            "casting", "traducere"),

            rule("Audio", "Microfoane")
                    .phrase("wireless mic", "microfon wireless", "voice recorder",
                            "reportofon digital", "microfon lavaliera", "lavalier microphone",
                            "microfon studio", "set microfoane", "microphone system")
                    .type("microfon", "microfoane", "microphone", "microphones", "lavaliera",
                            "dictafon", "reportofon")
                    .brand("rode", "shure")
                    .not("casti", "headset", "boxa", "camera", "pedal", "pedala"),

            rule("Audio", "Boxe & Soundbar")
                    .phrase("boxa bluetooth", "boxa portabila", "sistem audio", "party box",
                            "bass combo")
                    .type("boxa", "boxe", "soundbar", "difuzor", "difuzoare", "speaker",
                            "speakers", "speakerphone", "subwoofer")
                    .not("husa", "suport", "stand", "cablu", "camera", "supraveghere", "microfon"),

            rule("Audio", "Mixere & Interfete")
                    .phrase("interfata audio", "audio interface", "mixer dj", "consola mixaj",
                            "amplificator audio", "streaming mixer")
                    .type("mixer", "mixere", "preamp", "goxlr", "maonocaster")
                    .brand("focusrite", "scarlett", "behringer", "helicon")
                    .not("bucatarie", "kitchen", "tremolo", "pedala", "pedal", "chitara",
                            "guitar"),

            rule("Audio", "Audio Hi-Fi")
                    .phrase("blu ray player", "pick up", "dvd player", "cd player",
                            "network streamer", "amplificator hi fi", "dac portabil",
                            "stereo amplifier", "amplificator stereo", "amplificator putere")
                    .type("bluray", "turntable", "pickup", "dac", "streamer", "receiver",
                            "amplificator", "amplifier", "ampli")
                    .brand("ifi", "pyle")
                    .not("camera", "casti", "boxa"),

            // --- Musical instruments -----------------------------------------------
            rule("Instrumente muzicale", "Chitare & Efecte")
                    .phrase("pedala efect", "effects pedal", "digital delay", "bass amp",
                            "guitar amp", "looper pedal", "page turner")
                    .type("chitara", "chitare", "guitar", "tremolo", "pedala", "pedal",
                            "distortion", "overdrive")
                    .brand("joyo", "hotone", "lekato", "donner", "mooer")
                    .not("controller", "gaming", "xbox", "playstation", "nintendo"),

            rule("Instrumente muzicale", "Instrumente muzicale")
                    .phrase("pian digital", "orga electronica", "tobe electronice",
                            "midi keyboard", "claviatura midi", "midi controller",
                            "controller midi")
                    .type("pian", "synthesizer", "sintetizator", "metronom", "midi"),

            // --- Photo & video ------------------------------------------------------
            rule("Foto & Video", "Drone")
                    .phrase("dji mini", "dji air", "dji mavic", "dji avata", "dji neo",
                            "flight battery", "drone combo", "dji rc", "dji goggles",
                            "dji fpv", "telecomanda drona")
                    .type("drona", "drone", "quadcopter")
                    .brand("hoverair", "autel")
                    .not("pocket", "gimbal", "mic", "microfon", "microfoane", "microphone"),

            rule("Foto & Video", "Camere de actiune")
                    .phrase("camera actiune", "camere actiune", "camera sport", "action cam",
                            "action camera", "osmo action", "osmo pocket")
                    .type("gopro", "runcam")
                    .brand("akaso", "insta360", "apexcam", "wolfang")
                    .not("trail", "hunting", "wildlife", "fauna", "vanatoare", "urmarit",
                            "supraveghere", "web", "videoconferinta"),

            rule("Foto & Video", "Stabilizatoare / Gimbal")
                    .phrase("osmo mobile", "stabilizator imagine", "gimbal stabilizer")
                    .type("gimbal", "gimbaluri", "stabilizator", "steadicam"),

            rule("Foto & Video", "Obiective")
                    .phrase("obiectiv foto", "camera lens", "lentila obiectiv", "teleobiectiv")
                    .type("obiectiv", "obiective", "lens", "lenses", "lentila", "teleconvertor")
                    .brand("sigma", "tamron", "samyang")
                    .not("aparat", "camera", "husa", "filtru"),

            rule("Foto & Video", "Optica")
                    .phrase("night vision", "vedere nocturna", "luneta vanatoare",
                            "spotting scope")
                    .type("binoclu", "binocular", "binoculars", "monocular", "telescop",
                            "telemetru", "luneta")
                    .not("telefon", "smartphone", "camera", "cameras", "camere", "trail",
                            "hunting", "wildlife", "supraveghere", "securitate", "security"),

            rule("Foto & Video", "Aparate foto")
                    .phrase("aparat foto", "camere foto", "camera foto", "aparat fotografiat",
                            "aparat instant", "instant camera", "film scanner", "aparat digital")
                    .type("mirrorless", "dslr", "polaroid", "fotografiat", "eos")
                    .brand("lumix", "kodak", "pixpro",
                            "canon", "nikon", "fujifilm", "olympus", "pentax", "hasselblad")
                    .hint("body")
                    .not("supraveghere", "actiune", "sport", "trail", "hunting", "wildlife",
                            "radiography", "intraoral", "dental", "husa", "suport", "trepied",
                            "imprimanta", "printer", "toner", "cartus"),

            // --- Surveillance --------------------------------------------------------
            rule("Smart Home", "Camere supraveghere")
                    .phrase("camera supraveghere", "camere supraveghere", "security camera",
                            "camera securitate", "video doorbell", "sonerie video",
                            "security cameras", "surveillance camera", "camere securitate",
                            "stick up cam", "spotlight cam", "baby monitor", "monitor bebelusi",
                            "camera bebelusi", "camera interior", "camera exterior",
                            "surveillance camera")
                    .type("doorbell", "sonerie", "videointerfon", "bodycam")
                    .brand("arlo", "ezviz", "eufy", "reolink", "instar", "blink", "babysense",
                            "annke", "imou")
                    .not("trail", "hunting", "wildlife", "vanatoare", "fauna", "actiune",
                            "sport", "termoviziune", "videoconferinta", "intraoral"),

            rule("Foto & Video", "Camere")
                    .phrase("camera videoconferinta", "trail camera", "camera trail",
                            "hunting camera", "camera hunting", "wildlife camera",
                            "camera wildlife", "camera vanatoare", "camera fauna",
                            "camera urmarit", "camera termoviziune", "thermal camera",
                            "camera web")
                    .weak("camera", "camere", "cam")
                    .brand("topdon")
                    .not("doorbell", "sonerie", "actiune", "sport",
                            "intraoral", "radiography", "husa", "suport", "trepied", "obiectiv",
                            "moto", "auto", "bord", "dash"),

            rule("Foto & Video", "Accesorii foto-video")
                    .phrase("video light", "led video", "video panel", "matte box",
                            "lumina video", "blitz extern", "softbox studio", "kit iluminare",
                            "filtru obiectiv", "lens filter", "filtru uv", "filtru nd",
                            "filtru polarizare", "filtru lens", "camera flash", "flash speedlite",
                            "monitor dslr", "field monitor", "camera monitor", "monitor camera")
                    .type("smallrig", "neewer", "godox", "tilta", "blit", "blitz", "colorimetru",
                            "calibrite", "trepied", "tripod", "speedlite")
                    .brand("manfrotto"),

            // --- TV & projectors -------------------------------------------------------
            rule("TV & Proiectoare", "Televizoare")
                    .phrase("smart tv", "led tv", "tv box", "android tv", "televizor led",
                            "google tv", "oled tv", "qled tv")
                    .type("televizor", "televizoare", "tv", "oled", "qled")
                    .not("amoled", "husa", "suport", "stand", "cablu", "telecomanda", "monitor"),

            rule("TV & Proiectoare", "Proiectoare")
                    .phrase("videoproiector laser", "proiector video", "home cinema projector")
                    .type("proiector", "proiectoare", "projector", "videoproiector")
                    .not("husa", "suport", "ecran"),

            // --- Gaming ------------------------------------------------------------------
            rule("Gaming", "Gaming")
                    .phrase("consola jocuri", "guitar controller", "fighting stick",
                            "wireless controller", "gaming chair", "steam deck", "joc video")
                    .type("playstation", "xbox", "nintendo", "consola", "joystick", "8bitdo")
                    .brand("hori")
                    .hint("controller", "console", "gaming", "gamer")
                    .not("mouse", "tastatura", "keyboard", "casti", "headset", "monitor",
                            "laptop", "camera", "display", "husa", "scaun"),

            // --- Smart home --------------------------------------------------------------
            rule("Smart Home", "Smart Home")
                    .phrase("smart home", "smart lock", "detector fum", "smoke detector",
                            "priza smart", "bec smart", "senzor miscare", "statie meteo",
                            "weather station", "gate opener", "garage door", "smart plug",
                            "smart bulb")
                    .type("switchbot", "aqara", "termostat", "incuietoare", "interfon",
                            "intercom", "sirena", "senzor", "zigbee")
                    .brand("nuki", "fibaro", "bticino", "shelly", "tuya", "sonoff")
                    .not("moto", "motorcycle", "casca", "helmet"),

            // --- Home appliances ----------------------------------------------------------
            rule("Electrocasnice", "Electrocasnice")
                    .phrase("robot aspirator", "aspirator vertical", "fier de calcat",
                            "statie de calcat", "espressor cafea", "rasnita cafea",
                            "filtrare apa", "reverse osmosis", "window cleaning",
                            "robot geamuri", "kitchen thermometer", "friteuza aer",
                            "air fryer", "cuptor electric", "statie de abur", "statie abur",
                            "aparat de calcat", "masina de spalat", "masina de cusut")
                    .type("espressor", "aspirator", "friteuza", "blender", "cafetiera",
                            "thermometer", "termometru", "calcat")
                    .brand("bartesian")
                    .not("medical", "corp", "auto", "moto"),

            // --- Personal care ---------------------------------------------------------------
            rule("Ingrijire personala", "Ingrijire personala")
                    .phrase("uscator par", "hair dryer", "hair styler", "placa de par",
                            "placa indreptat", "aparat de ras", "aparat ras", "aparat barbierit",
                            "aparat de tuns", "aparat tuns", "periuta de dinti",
                            "electric toothbrush", "masina de tuns", "epilator ipl",
                            "aparat epilat", "ingrijire a pielii", "ingrijire piele",
                            "skin care", "skincare", "silk expert", "silk expret",
                            "booster pro", "aparat de masaj", "perie de par")
                    .type("epilator", "trimmer", "shaver", "ondulator", "toothbrush", "periuta",
                            "barbierit", "ipl")
                    .brand("remington", "medicube", "dreame", "ghd", "babyliss", "braun")
                    .not("auto", "moto", "camera"),

            // --- Tools --------------------------------------------------------------------------
            rule("Scule & Unelte", "Scule")
                    .phrase("aparat sudura", "welding machine", "nivela laser",
                            "masina de gaurit", "surubelnita electrica", "polizor unghiular",
                            "pistol de lipit")
                    .type("sudura", "welding", "nivela", "bormasina", "surubelnita", "letcon")
                    .brand("worx", "makita", "dewalt", "einhell"),

            // --- Navigation ----------------------------------------------------------------------
            rule("Auto & Moto", "GPS & Navigatie")
                    .phrase("navigatie auto", "sistem navigatie", "navigatie gps", "car stereo",
                            "android auto", "apple carplay", "sat nav", "navigator auto",
                            "gps auto", "gps moto", "ciclocomputer gps", "head unit",
                            "multimedia auto")
                    .type("carplay", "navigatie", "navigator", "ciclocomputer", "gps")
                    .brand("tomtom", "podofo", "autovox", "atoto", "carpodgo", "volam")
                    .not("smartwatch", "ceas", "bratara", "telefon", "smartphone", "husa",
                            "dash", "bord", "casti"),

            // --- Auto & moto electronics -----------------------------------------------------------
            rule("Auto & Moto", "Auto & Moto")
                    .phrase("dash cam", "dashcam", "camera auto", "camera moto", "camera bord",
                            "intercom moto", "statie moto", "motorcycle intercom", "casca moto",
                            "alarma auto", "redresor auto", "compresor auto",
                            "senzor parcare", "senzori parcare", "sistem parcare",
                            "camera marsarier", "sistem audio auto")
                    .type("moto", "motocicleta", "motorcycle", "carpuride")
                    .brand("sena", "cardo", "parani", "ixroad")
                    .not("trotineta", "kickscooter", "bicicleta", "motorola"),

            // --- Accessories --------------------------------------------------------------------------
            rule("Accesorii", "Incarcatoare")
                    .phrase("incarcator wireless", "wireless charger", "charging pad",
                            "wireless pad", "statie de incarcare", "charging station",
                            "power adapter", "car charger", "incarcator retea",
                            "incarcator auto", "battery charger", "magsafe charger")
                    .type("incarcator", "incarcatoare", "charger", "magsafe", "qi2",
                            "boostcharge")
                    .not("power bank", "powerbank", "drona", "dji"),

            rule("Accesorii", "Baterii externe")
                    .phrase("acumulator extern", "baterie externa", "power bank", "powerbank",
                            "baterie portabila", "battery backup", "uninterruptible battery",
                            "portable charger")
                    .type("ups")
                    .brand("anker")
                    .not("dji", "drona", "drone", "flight", "worx", "bormasina", "auto", "moto"),

            rule("Accesorii", "Lanterne")
                    .phrase("lanterna led", "lanterna frontala", "far bicicleta", "head lamp")
                    .type("lanterna", "lanterne", "flashlight", "headlamp", "frontala")
                    .brand("olight", "fenix", "nitecore"),

            rule("Accesorii", "Huse & Folii")
                    .phrase("husa protectie", "screen protector", "folie protectie",
                            "folie sticla", "book cover", "flip cover", "keyboard case")
                    .type("husa", "huse", "folie", "folii", "carcasa", "carcase", "bumper"),

            rule("Accesorii", "Cabluri & Adaptoare")
                    .phrase("hub usb", "usb hub", "cablu date", "cablu incarcare", "adaptor usb",
                            "docking hub", "cablu hdmi", "multi display hub")
                    .type("cablu", "cabluri", "cable", "adaptor", "adaptoare", "adapter", "hub",
                            "splitter", "prelungitor")
                    .not("incarcator", "charger", "casti", "power bank"),

            rule("Accesorii", "Suporturi")
                    .phrase("suport telefon", "suport laptop", "suport monitor", "brat monitor",
                            "suport auto", "phone holder", "suport perete")
                    .type("suport", "suporturi", "stand", "mount", "holder", "stativ")
                    .not("standard"),

            // --- Miscellaneous gadgets -----------------------------------------------------------------
            rule("Diverse electronice", "Gadgeturi")
                    .phrase("launch monitor", "golf simulator", "rama foto",
                            "digital picture frame", "calculator grafic", "calculator stiintific",
                            "smart tracker", "bluetooth tracker", "statie de lipit")
                    .type("airtag", "airtags", "chipolo", "golf")
                    .brand("skylight")
    );


    // ---------------------------------------------------------------------
    // Canonical taxonomy, derived from the rule table above
    // ---------------------------------------------------------------------

    /** Category to the subcategories it owns, in declaration order. */
    private static final Map<String, List<String>> TAXONOMY;
    /** Subcategory to the single category that owns it. */
    private static final Map<String, String> SUBCATEGORY_OWNER;
    /** Lower-cased subcategory to its canonical spelling. */
    private static final Map<String, String> SUBCATEGORY_LOOKUP;
    /** Lower-cased category to its canonical spelling. */
    private static final Map<String, String> CATEGORY_LOOKUP;

    static {
        Map<String, List<String>> taxonomy = new LinkedHashMap<>();
        Map<String, String> owner = new LinkedHashMap<>();
        Map<String, String> subLookup = new LinkedHashMap<>();
        Map<String, String> catLookup = new LinkedHashMap<>();

        List<Rule> all = new ArrayList<>(RULES);
        all.add(rule(DEFAULT_CATEGORY, DEFAULT_SUBCATEGORY));

        for (Rule r : all) {
            List<String> subs = taxonomy.computeIfAbsent(r.category, k -> new ArrayList<>());
            if (!subs.contains(r.subcategory)) {
                subs.add(r.subcategory);
            }
            owner.putIfAbsent(r.subcategory, r.category);
            subLookup.putIfAbsent(r.subcategory.toLowerCase(Locale.ROOT), r.subcategory);
            catLookup.putIfAbsent(r.category.toLowerCase(Locale.ROOT), r.category);
        }

        Map<String, List<String>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : taxonomy.entrySet()) {
            frozen.put(e.getKey(), List.copyOf(e.getValue()));
        }

        TAXONOMY = java.util.Collections.unmodifiableMap(frozen);
        SUBCATEGORY_OWNER = java.util.Collections.unmodifiableMap(owner);
        SUBCATEGORY_LOOKUP = java.util.Collections.unmodifiableMap(subLookup);
        CATEGORY_LOOKUP = java.util.Collections.unmodifiableMap(catLookup);
    }

    /**
     * Spreadsheet cell values that carry no usable category information: Excel error
     * markers, dashes, and condition words typed into the category column by mistake.
     */
    private static final Set<String> PLACEHOLDER_WORDS = Set.of(
            "n a", "na", "null", "nan", "none", "nil", "x", "xx", "xxx",
            "necunoscut", "nedefinit", "nedefinita", "fara", "fara categorie",
            "folosit", "folosita", "folosite", "second hand", "used", "refurbished",
            "nou", "noua", "sigilat", "sigilata", "resigilat", "resigilata",
            "desigilat", "desigilata", "open box", "altele", "alte"
    );

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Returns the best-matching category / subcategory for a product name, or the
     * default pair when no rule gathers enough evidence.
     */
    public Categorization categorize(String name) {
        List<String> words = tokenize(name);
        if (words.isEmpty()) {
            return new Categorization(DEFAULT_CATEGORY, DEFAULT_SUBCATEGORY);
        }

        int headStart = 0;
        while (headStart < words.size() && isFiller(words.get(headStart))) {
            headStart++;
        }
        if (headStart >= words.size()) {
            headStart = 0;
        }

        Rule best = null;
        int bestScore = 0;
        for (Rule r : RULES) {
            int score = score(r, words, headStart);
            if (score > bestScore) {
                bestScore = score;
                best = r;
            }
        }

        if (best == null || bestScore < MIN_SCORE) {
            return new Categorization(DEFAULT_CATEGORY, DEFAULT_SUBCATEGORY);
        }
        return new Categorization(best.category, best.subcategory);
    }

    /**
     * The single category that owns this subcategory, or {@code null} when the
     * subcategory is not part of the canonical taxonomy.
     */
    public String canonicalCategoryFor(String subcategory) {
        String canonical = canonicalSubcategory(subcategory);
        return canonical == null ? null : SUBCATEGORY_OWNER.get(canonical);
    }

    /** The canonical spelling of a subcategory, or {@code null} when it is unknown. */
    public String canonicalSubcategory(String subcategory) {
        if (subcategory == null) {
            return null;
        }
        return SUBCATEGORY_LOOKUP.get(subcategory.trim().toLowerCase(Locale.ROOT));
    }

    /** The canonical spelling of a category, or {@code null} when it is unknown. */
    public String canonicalCategory(String category) {
        if (category == null) {
            return null;
        }
        return CATEGORY_LOOKUP.get(category.trim().toLowerCase(Locale.ROOT));
    }

    /** The full canonical taxonomy: category to the subcategories it owns. */
    public Map<String, List<String>> taxonomy() {
        return TAXONOMY;
    }

    /** Every subcategory known to the taxonomy, in declaration order. */
    public Set<String> knownSubcategories() {
        return new LinkedHashSet<>(SUBCATEGORY_OWNER.keySet());
    }

    /** Every category known to the taxonomy, in declaration order. */
    public Set<String> knownCategories() {
        return new LinkedHashSet<>(TAXONOMY.keySet());
    }

    /**
     * True when a cell carries no usable category information: empty, a numeric
     * artefact such as {@code "0"}, a dash, an Excel error marker, or a condition
     * word ("Folosit", "Nou", "Resigilat") typed into the category column by mistake.
     */
    public static boolean isPlaceholder(String value) {
        if (value == null) {
            return true;
        }
        String n = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (n.isEmpty()) {
            return true;
        }
        if (n.matches("[0-9]+( [0-9]+)*")) {
            return true;
        }
        return PLACEHOLDER_WORDS.contains(n);
    }

    // ---------------------------------------------------------------------
    // Scoring internals
    // ---------------------------------------------------------------------

    /** Total evidence a rule accumulates for this name, or 0 when disqualified. */
    private int score(Rule rule, List<String> words, int headStart) {
        for (String[] negative : rule.negatives) {
            if (position(words, negative) >= 0) {
                return 0;
            }
        }
        for (String[] negative : rule.headNegatives) {
            int at = position(words, negative);
            if (at >= 0 && at - headStart <= NEAR_HEAD_LIMIT) {
                return 0;
            }
        }
        int total = 0;
        for (Term term : rule.terms) {
            int at = position(words, term.words());
            if (at < 0) {
                continue;
            }
            int offset = Math.max(0, at - headStart);
            int multiplier;
            if (offset == 0) {
                multiplier = HEAD_MULTIPLIER;
            } else if (offset <= NEAR_HEAD_LIMIT) {
                multiplier = NEAR_HEAD_MULTIPLIER;
            } else {
                multiplier = 1;
            }
            if (term.capped() && multiplier > CAPPED_MULTIPLIER) {
                multiplier = CAPPED_MULTIPLIER;
            }
            total += term.weight() * multiplier;
        }
        return total;
    }

    /**
     * Word index at which a keyword matches, or {@code -1}. A single-word keyword must
     * equal a whole word, which is what stops "moto" from matching "motorola". A
     * multi-word keyword matches when all of its words occur inside a window that
     * starts on one of them and is at most {@value #PHRASE_SLACK} words longer than the
     * keyword, so "camera supraveghere" also matches "Camera de supraveghere" and
     * "Supraveghere camera".
     */
    private static int position(List<String> words, String[] keyword) {
        if (keyword.length == 0) {
            return -1;
        }
        if (keyword.length == 1) {
            String single = keyword[0];
            for (int i = 0; i < words.size(); i++) {
                if (words.get(i).equals(single)) {
                    return i;
                }
            }
            return -1;
        }
        int span = keyword.length - 1 + PHRASE_SLACK;
        for (int i = 0; i < words.size(); i++) {
            if (!contains(keyword, words.get(i))) {
                continue;
            }
            int end = Math.min(words.size(), i + span + 1);
            boolean complete = true;
            for (String w : keyword) {
                boolean found = false;
                for (int j = i; j < end; j++) {
                    if (words.get(j).equals(w)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    complete = false;
                    break;
                }
            }
            if (complete) {
                return i;
            }
        }
        return -1;
    }

    private static boolean contains(String[] haystack, String needle) {
        for (String s : haystack) {
            if (s.equals(needle)) {
                return true;
            }
        }
        return false;
    }

    /** Condition, packaging and glue words that never act as the head noun. */
    private static boolean isFiller(String word) {
        return LEADING_FILLERS.contains(word) || word.matches("[0-9]+");
    }

    /** Lower-cases, strips diacritics and splits on everything that is not a letter or digit. */
    private static List<String> tokenize(String s) {
        if (s == null) {
            return List.of();
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (n.isEmpty()) {
            return List.of();
        }
        return List.of(n.split(" "));
    }

    /** Applies the same tokenisation to a keyword, so rules and names compare on equal terms. */
    private static String[] split(String keyword) {
        return tokenize(keyword).toArray(new String[0]);
    }

}
