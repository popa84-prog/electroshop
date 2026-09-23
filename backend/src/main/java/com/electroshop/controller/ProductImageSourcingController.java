package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.service.imaging.ProductImageSourcingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Completarea fotografiilor lipsă din catalogul Icecat.
 *
 * <p>Două rute și o stare. Căutarea nu schimbă nimic și este {@code GET};
 * confirmarea urcă efectiv o imagine și este {@code POST}. Separarea contează:
 * o căutare consumă din cota lunară Icecat, iar dacă ar fi fost pusă pe aceeași
 * rută cu aplicarea, orice reîncărcare de pagină ar fi consumat cotă și ar fi
 * riscat o publicare.</p>
 *
 * <p>Gestionat de {@code PRODUCTS_MANAGE}, aceeași permisiune ca încărcarea
 * manuală a unei imagini — fapta este aceeași, doar că fișierul vine de la un
 * catalog în loc să vină de pe disc.</p>
 */
@RestController
@RequestMapping("/admin/products/image-sourcing")
public class ProductImageSourcingController {

    private final ProductImageSourcingService service;

    public ProductImageSourcingController(ProductImageSourcingService service) {
        this.service = service;
    }

    /**
     * Dacă integrarea poate fi folosită.
     *
     * <p>Interogat de interfață înainte de a afișa butonul de căutare, ca
     * operatorul să vadă „nu este configurat" în loc să apese și să primească o
     * eroare care arată ca o defecțiune.</p>
     */
    @GetMapping("/status")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> status() {
        boolean gata = service.esteConfigurat();
        return ResponseEntity.ok(ApiResponse.ok(
                gata ? "Integrarea Icecat este configurată."
                     : "Integrarea Icecat nu este configurată.",
                Map.of("configurat", gata,
                       "variabile", java.util.List.of(
                               "ICECAT_SHOPNAME", "ICECAT_API_TOKEN", "ICECAT_CONTENT_TOKEN"))));
    }

    /**
     * Caută potriviri pentru produsele fără imagine.
     *
     * @param limita câte produse se examinează. Implicit 25: fiecare înseamnă
     *               până la trei cereri către Icecat, deci un lot mare ar ține
     *               interfața în așteptare fără niciun rezultat intermediar.
     */
    @GetMapping("/propuneri")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<ProductImageSourcingService.Raport>> propuneri(
            @RequestParam(defaultValue = "25") int limita) {
        int sigur = Math.max(1, Math.min(limita, 100));
        ProductImageSourcingService.Raport raport = service.propune(sigur);
        return ResponseEntity.ok(ApiResponse.ok(
                raport.gasite() + " potriviri din " + raport.examinate() + " produse examinate.",
                raport));
    }

    /**
     * O singură interogare, cu răspunsul brut de la Icecat.
     *
     * <p>Există pentru că prima rulare reală a potrivit un produs din 37, iar
     * raportul agregat nu putea spune de ce. Aici se întreabă punctual o
     * pereche marcă/cod și se vede exact ce răspunde Icecat — dacă produsul
     * lipsește, dacă marca nu este în nivelul gratuit sau dacă jetonul este
     * refuzat.</p>
     */
    @GetMapping("/diagnostic")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> diagnostic(
            @RequestParam String marca, @RequestParam String cod) {
        var r = service.diagnostic(marca, cod);
        Map<String, Object> date = new java.util.LinkedHashMap<>();
        date.put("motiv", r.motiv().name());
        date.put("detaliu", r.detaliu());
        date.put("gasit", r.rezultat() != null);
        if (r.rezultat() != null) {
            date.put("titlu", r.rezultat().titlu());
            date.put("icecatId", r.rezultat().icecatId());
            date.put("gtin", r.rezultat().gtin());
            date.put("numarImagini", r.rezultat().imagini().size());
        }
        return ResponseEntity.ok(ApiResponse.ok("Interogare de diagnostic.", date));
    }

    /** Confirmă o potrivire: preia imaginea aleasă și o leagă de produs. */
    @PostMapping("/aplica")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<Object>> aplica(@Valid @RequestBody AplicaRequest cerere) {
        service.aplica(cerere.getProductId(), cerere.getAdresa(), cerere.getIcecatId(),
                cerere.getMarca(), cerere.getMpn(), cerere.getGtin());
        return ResponseEntity.ok(ApiResponse.ok("Imaginea a fost preluată și atașată produsului.", null));
    }

    /** Corpul confirmării. */
    public static class AplicaRequest {
        @NotNull(message = "Produsul este obligatoriu.")
        private Long productId;

        @NotBlank(message = "Adresa imaginii este obligatorie.")
        private String adresa;

        private String icecatId;
        private String marca;
        private String mpn;
        private String gtin;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public String getAdresa() {
            return adresa;
        }

        public void setAdresa(String adresa) {
            this.adresa = adresa;
        }

        public String getIcecatId() {
            return icecatId;
        }

        public void setIcecatId(String icecatId) {
            this.icecatId = icecatId;
        }

        public String getMarca() {
            return marca;
        }

        public void setMarca(String marca) {
            this.marca = marca;
        }

        public String getMpn() {
            return mpn;
        }

        public void setMpn(String mpn) {
            this.mpn = mpn;
        }

        public String getGtin() {
            return gtin;
        }

        public void setGtin(String gtin) {
            this.gtin = gtin;
        }
    }
}
