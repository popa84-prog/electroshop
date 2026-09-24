package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.service.imaging.DistributorFeedService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Preluarea fotografiilor din feedul unui distribuitor.
 *
 * <h2>De ce citirea este POST, deși nu schimbă nimic</h2>
 *
 * În restul modulului, căutarea este {@code GET} tocmai pentru a spune prin
 * metoda HTTP că nu are consecințe. Aici nu se poate: cererea transportă un
 * fișier, iar un fișier se trimite în corpul cererii, pe care {@code GET} nu îl
 * are. Contractul se păstrează însă în conținut — {@code /analizeaza} nu scrie
 * nimic în baza de date și nu preia nicio imagine, iar operatorul poate să o
 * repete de câte ori vrea fără efect.
 *
 * <h2>Cele două lucruri fără care preluarea nu pornește</h2>
 *
 * {@code /aplica} cere obligatoriu furnizorul și temeiul folosirii imaginilor.
 * Nu sunt formalități administrative: ele sunt singura diferență între o
 * fotografie pe care avem dreptul să o publicăm și una copiată. Se scriu pe
 * fiecare imagine, în coloanele de proveniență, și rămân verificabile ulterior.
 */
@RestController
@RequestMapping("/admin/products/distributor-feed")
public class DistributorFeedController {

    private final DistributorFeedService service;

    public DistributorFeedController(DistributorFeedService service) {
        this.service = service;
    }

    /**
     * Citește feedul și arată ce s-ar lega de ce. Nu scrie nimic.
     *
     * @param file            feedul, .xlsx sau .xml
     * @param doarFaraImagine implicit adevărat: produsele care au deja
     *                        fotografie nu sunt ținta principală
     * @param limita          câte potriviri se întorc într-o rundă
     */
    @PostMapping("/analizeaza")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<DistributorFeedService.Raport>> analizeaza(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean doarFaraImagine,
            @RequestParam(defaultValue = "200") int limita) {
        int sigur = Math.max(1, Math.min(limita, 500));
        DistributorFeedService.Raport raport = service.analizeaza(file, doarFaraImagine, sigur);
        return ResponseEntity.ok(ApiResponse.ok(
                raport.potrivite() + " produse potrivite din " + raport.randuriUtile()
                        + " rânduri utile ale feedului.",
                raport));
    }

    /** Preia imaginile confirmate. Scrie proveniența pe fiecare. */
    @PostMapping("/aplica")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aplica(
            @Valid @RequestBody AplicaFeedRequest cerere) {
        List<DistributorFeedService.Intrare> intrari = cerere.getIntrari().stream()
                .map(i -> new DistributorFeedService.Intrare(
                        i.getProductId(), i.getImagini(), i.getCodProducator(),
                        i.getGtin(), i.getCodDistribuitor()))
                .toList();
        DistributorFeedService.Aplicare r = service.aplica(
                cerere.getSupplierId(), cerere.getTemeiLicenta(), intrari);
        return ResponseEntity.ok(ApiResponse.ok(
                r.imaginiUrcate() + " fotografii preluate pentru " + r.produseModificate()
                        + " produse"
                        + (r.esecuri().isEmpty() ? "." : ", " + r.esecuri().size() + " eșecuri."),
                r.caMesaj()));
    }

    /** Corpul confirmării. */
    public static class AplicaFeedRequest {

        @NotNull(message = "Furnizorul trebuie selectat: proveniența se scrie pe fiecare imagine.")
        private Long supplierId;

        // Validat aici, înainte ca serviciul să pornească: o valoare prea lungă
        // descoperită după prima preluare Cloudinary ar anula tranzacția și ar
        // lăsa imaginile deja urcate legate de nimic.
        @NotBlank(message = "Temeiul folosirii imaginilor nu poate lipsi.")
        @Size(max = 120, message = "Temeiul folosirii poate avea cel mult 120 de caractere.")
        private String temeiLicenta;

        @NotEmpty(message = "Nu s-a confirmat nicio potrivire.")
        private List<IntrareDto> intrari;

        public Long getSupplierId() {
            return supplierId;
        }

        public void setSupplierId(Long supplierId) {
            this.supplierId = supplierId;
        }

        public String getTemeiLicenta() {
            return temeiLicenta;
        }

        public void setTemeiLicenta(String temeiLicenta) {
            this.temeiLicenta = temeiLicenta;
        }

        public List<IntrareDto> getIntrari() {
            return intrari;
        }

        public void setIntrari(List<IntrareDto> intrari) {
            this.intrari = intrari;
        }
    }

    /** O confirmare: produsul și adresele acceptate pentru el. */
    public static class IntrareDto {
        private Long productId;
        private List<String> imagini;
        private String codProducator;
        private String gtin;
        private String codDistribuitor;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public List<String> getImagini() {
            return imagini;
        }

        public void setImagini(List<String> imagini) {
            this.imagini = imagini;
        }

        public String getCodProducator() {
            return codProducator;
        }

        public void setCodProducator(String codProducator) {
            this.codProducator = codProducator;
        }

        public String getGtin() {
            return gtin;
        }

        public void setGtin(String gtin) {
            this.gtin = gtin;
        }

        public String getCodDistribuitor() {
            return codDistribuitor;
        }

        public void setCodDistribuitor(String codDistribuitor) {
            this.codDistribuitor = codDistribuitor;
        }
    }
}
