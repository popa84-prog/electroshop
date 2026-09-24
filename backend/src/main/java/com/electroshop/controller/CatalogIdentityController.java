package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.service.imaging.CatalogIdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Completarea mărcii și a codului de produs din denumire.
 *
 * <p>Pasul care vine înaintea oricărei surse de fotografii. Măsurat pe catalog:
 * 63 din 100 de produse fără imagine nu pot fi identificate de nicio sursă
 * externă, pentru că le lipsește marca, codul sau amândouă.</p>
 *
 * <p>Ca peste tot în modulul acesta, căutarea este {@code GET} și nu schimbă
 * nimic, iar scrierea este {@code POST} și scrie doar ce a confirmat
 * operatorul.</p>
 */
@RestController
@RequestMapping("/admin/products/identity")
public class CatalogIdentityController {

    private final CatalogIdentityService service;

    public CatalogIdentityController(CatalogIdentityService service) {
        this.service = service;
    }

    /**
     * Ce se poate deduce, pentru produsele cu identitate incompletă.
     *
     * @param doarFaraImagine implicit adevărat: acelea blochează pașii următori
     * @param limita          câte rânduri de confirmat într-o rundă
     */
    @GetMapping("/propuneri")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<CatalogIdentityService.Raport>> propuneri(
            @RequestParam(defaultValue = "true") boolean doarFaraImagine,
            @RequestParam(defaultValue = "60") int limita) {
        int sigur = Math.max(1, Math.min(limita, 300));
        CatalogIdentityService.Raport raport = service.propune(doarFaraImagine, sigur);
        return ResponseEntity.ok(ApiResponse.ok(
                raport.incomplete() + " produse cu identitate incompletă, "
                        + raport.propuneri().size() + " pot fi completate automat.",
                raport));
    }

    /** Scrie mărcile și codurile confirmate. Nu suprascrie nimic existent. */
    @PostMapping("/aplica")
    @PreAuthorize("@permissionService.has('PRODUCTS_MANAGE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aplica(
            @Valid @RequestBody AplicaIdentitateRequest cerere) {
        List<CatalogIdentityService.Intrare> intrari = cerere.getIntrari().stream()
                .map(i -> new CatalogIdentityService.Intrare(i.getProductId(), i.getMarca(), i.getMpn()))
                .toList();
        CatalogIdentityService.Rezultat r = service.aplica(intrari);
        return ResponseEntity.ok(ApiResponse.ok(
                r.marciScrise() + " mărci și " + r.coduriScrise() + " coduri completate.",
                r.caMesaj()));
    }

    /** Corpul confirmării în masă. */
    public static class AplicaIdentitateRequest {
        @NotEmpty(message = "Nu s-a trimis nicio intrare.")
        private List<IntrareDto> intrari;

        public List<IntrareDto> getIntrari() {
            return intrari;
        }

        public void setIntrari(List<IntrareDto> intrari) {
            this.intrari = intrari;
        }
    }

    /** O confirmare. Câmpurile lăsate goale sunt ignorate, nu șterg nimic. */
    public static class IntrareDto {
        private Long productId;
        private String marca;
        private String mpn;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
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
    }
}
