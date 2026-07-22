package com.electroshop.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;

/**
 * Derives a category + subcategory from a product name using an ordered set of
 * keyword rules (first match wins). Used by the Excel import to auto-fill the
 * category/subcategory columns when they are left blank, so the owner only has
 * to paste product names + prices and the catalogue organises itself.
 */
@Component
public class ProductCategorizer {

    public record Categorization(String category, String subcategory) {}

    private record Rule(String[] keywords, String category, String subcategory) {}

    private static final String DEFAULT_CATEGORY = "Diverse electronice";
    private static final String DEFAULT_SUBCATEGORY = "Gadgeturi";

    // Ordered rules; first keyword hit (on the normalized name) wins.
    private static final List<Rule> RULES = List.of(
            new Rule(new String[]{"vaporizer", "vaporeso", "vaporesso", "vape ", "herb vaporiser"}, "Diverse electronice", "Vaping"),
            new Rule(new String[]{"tatuat", "tattoo", "peripage tattoo"}, "Ingrijire personala", "Aparate tatuat"),
            new Rule(new String[]{"ohlins", "brembo", "rearset", "steering damper", "lightech", "clutch master", "jante"}, "Auto & Moto", "Piese moto"),
            new Rule(new String[]{"translator", "vasco"}, "Traducatoare", "Translatoare AI"),
            new Rule(new String[]{"e-book", "ebook", "e-reader", "kobo", "onyx boox", "kindle", "cititor ebook"}, "Tablete", "E-readere"),
            new Rule(new String[]{"ipad", "tableta", "tablet", "fire hd", "amazon fire"}, "Tablete", "Tablete"),
            new Rule(new String[]{"mini pc", "geekom", " nuc "}, "Sisteme PC", "Mini PC"),
            new Rule(new String[]{"macbook", "laptop", "notebook"}, "Laptopuri", "Laptopuri"),
            new Rule(new String[]{"monitor"}, "Monitoare", "Monitoare"),
            new Rule(new String[]{"tomtom", "sat nav", "navigator de", "gps", "garmin edge", "garmin camper", "carplay", "android auto", "car stereo", "navigatie", "podofo", "autovox"}, "Auto & Moto", "GPS & Navigatie"),
            new Rule(new String[]{"moto", "carpuride", "intercom", "sena", "parani", "dash cam", "camera moto"}, "Auto & Moto", "Auto & Moto"),
            new Rule(new String[]{"smart ring", "galaxy ring", "oura"}, "Wearables", "Smart Rings"),
            new Rule(new String[]{"oximeter", "oxymeter", "revitive", "checkme", "tensiometru", "glucometru", "hrm-", "centura puls", "circulation booster", "wrist oximeter"}, "Sanatate", "Dispozitive medicale"),
            new Rule(new String[]{"smartwatch", "fitbit", "apple watch", "galaxy watch", "ceas", "watch", "smart band", "xiaomi band", "amazfit", "garmin"}, "Wearables", "Smartwatch & Ceasuri"),
            new Rule(new String[]{"ochelari", "xreal", "ar glass", " vr ", "smart glass"}, "Wearables", "Ochelari smart / AR-VR"),
            new Rule(new String[]{"guitar", "chitara", "joyo", "hotone", "lekato", "tremolo", "digital delay", " amp ", "pedala efect", "bass amp"}, "Instrumente muzicale", "Chitare & Efecte"),
            new Rule(new String[]{"mixer", "interfata audio", "audio interface", "amplificator", "streamer", "goxlr", "maonocaster", "behringer", "pyle", "helicon", "focusrite", "scarlett", "ifi audio", "ampli"}, "Audio", "Mixere & Interfete"),
            new Rule(new String[]{"casti", "headphone", "headset", "earbud", "airpods", "earphone", "nothing ear", "wf-1000", "wh-1000", "buds"}, "Audio", "Casti"),
            new Rule(new String[]{"soundbar", "boxa", "boxe", "speaker", "difuzor"}, "Audio", "Boxe & Soundbar"),
            new Rule(new String[]{"microfon", "microphone", "voice recorder", "dictafon", "em1", "donner"}, "Audio", "Microfoane"),
            new Rule(new String[]{"blu-ray", "bluray", "pick-up", "pickup", "turntable", " dac ", "sursa ifi"}, "Audio", "Audio Hi-Fi"),
            new Rule(new String[]{"smallrig", "neewer", "godox", "video light", "led video", "video panel", "matte box", "flash ", "blit", "colorimetru", "calibrite", "film scanner", "canoscan", " lide"}, "Foto & Video", "Accesorii foto-video"),
            new Rule(new String[]{"obiectiv", "lens", "lentila"}, "Foto & Video", "Obiective"),
            new Rule(new String[]{"binoclu", "binocular", "night vision", "monocular", "telemetru", "telescop", "scope cam"}, "Foto & Video", "Optica"),
            new Rule(new String[]{"action cam", "osmo action", "gopro", "dash cam", "trail", "wildlife", "camera de actiune", "actiune 4k", "runcam"}, "Foto & Video", "Camere de actiune"),
            new Rule(new String[]{"gimbal", "stabiliz", "osmo mobile"}, "Foto & Video", "Stabilizatoare / Gimbal"),
            new Rule(new String[]{"drona", "drone", "dji mini", "dji air", "dji mavic", "avata", "osmo pocket"}, "Foto & Video", "Drone"),
            new Rule(new String[]{"aparat foto", "camera foto", "aparat de fotografiat", "kodak", "fotografiat", "mirrorless", "dslr", "polaroid", "canon eos", "scanner"}, "Foto & Video", "Aparate foto"),
            new Rule(new String[]{"ring stick", "arlo", "ezviz", "supraveghere", "bodycam", "body cam", "doorbell", "sonerie"}, "Smart Home", "Camere supraveghere"),
            new Rule(new String[]{"camera", "webcam"}, "Foto & Video", "Camere"),
            new Rule(new String[]{"televizor", "televiziune", " tv ", "oled", "qled", "led tv"}, "TV & Proiectoare", "Televizoare"),
            new Rule(new String[]{"proiector", "projector"}, "TV & Proiectoare", "Proiectoare"),
            new Rule(new String[]{"trotineta", "scooter", "kickscooter"}, "Mobilitate electrica", "Trotinete"),
            new Rule(new String[]{"bicicleta", "e-bike", "ebike"}, "Mobilitate electrica", "Biciclete electrice"),
            new Rule(new String[]{"hoverboard"}, "Mobilitate electrica", "Hoverboard"),
            new Rule(new String[]{"controller", "8bitdo", "joystick", "console", "playstation", "xbox", "nintendo", "gaming", "fighting stick", "hori "}, "Gaming", "Gaming"),
            new Rule(new String[]{"ssd", "hdd", "hard disk", "hard-disk", " nas ", "memory card", "card de memorie", "microsd", "usb stick", "stick usb", "dvd-rw", "dvd rw"}, "Stocare", "Stocare & Memorie"),
            new Rule(new String[]{"router", "mesh", "access point", "switch retea", " network "}, "Retea", "Retea & Wi-Fi"),
            new Rule(new String[]{"keyboard", "tastatura", "mouse", "magic keyboard", "trackpad", "kvm switch", "docking"}, "Periferice PC", "Periferice"),
            new Rule(new String[]{"placa video", "placa de baza", "procesor", " cpu ", " gpu ", "ram ddr", "nvidia"}, "Componente PC", "Componente"),
            new Rule(new String[]{"espressor", "cafea", "statie de abur", "statie abur", "fier de calcat", "bartesian", "filtrare apa", "reverse osmosis", "robot geamuri", "window cleaning", "robot aspirator", "aspirator"}, "Electrocasnice", "Electrocasnice"),
            new Rule(new String[]{"uscator", "placa intins", "straighten", "ghd", "trimmer", "epilator", "aparat de ras", "shaver", "lumea", "ipl", "medicube", "age-r", "ondulator", "braun silk", "remington", "barbierit", "periuta", "toothbrush"}, "Ingrijire personala", "Ingrijire personala"),
            new Rule(new String[]{"switchbot", "aqara", "detector fum", "smoke detector", "sirena", "incuietoare", "smart lock", "nuki", "fibaro", "z-wave", "zigbee", "termostat", "senzor", "bec smart", "priza smart", "smart home", "bticino", "interfon"}, "Smart Home", "Smart Home"),
            new Rule(new String[]{"aparat de sudura", "sudura", "welding", "nivela laser", "worx", "scule"}, "Scule & Unelte", "Scule"),
            new Rule(new String[]{"incarcator", "charger", "magsafe", "qi2", "wireless charg"}, "Accesorii", "Incarcatoare"),
            new Rule(new String[]{"acumulator extern", "power bank", "powerbank", "baterie externa", "anker"}, "Accesorii", "Baterii externe"),
            new Rule(new String[]{"lanterna", "flashlight"}, "Accesorii", "Lanterne"),
            new Rule(new String[]{"cablu", "cable", "adaptor", "adapter", "hub usb"}, "Accesorii", "Cabluri & Adaptoare"),
            new Rule(new String[]{"husa", "carcasa", "folie", "screen protector"}, "Accesorii", "Huse & Folii"),
            new Rule(new String[]{"suport", "stand", "mount", "trepied", "tripod"}, "Accesorii", "Suporturi"),
            new Rule(new String[]{"telefon", "phone", "iphone", "smartphone", "galaxy s", "pixel", "redmi", "poco"}, "Telefoane", "Telefoane")
    );

    /** Returns the best-matching category/subcategory for a product name. */
    public Categorization categorize(String name) {
        String n = normalize(name);
        for (Rule rule : RULES) {
            for (String kw : rule.keywords()) {
                if (n.contains(kw)) {
                    return new Categorization(rule.category(), rule.subcategory());
                }
            }
        }
        return new Categorization(DEFAULT_CATEGORY, DEFAULT_SUBCATEGORY);
    }

    /** Lowercase + strip diacritics; keeps spaces so word-boundary keywords work. */
    private String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase();
    }
}
