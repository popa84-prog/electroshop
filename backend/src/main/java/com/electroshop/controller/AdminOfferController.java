package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.OfferDto;
import com.electroshop.dto.OfferRequest;
import com.electroshop.dto.PageResponse;
import com.electroshop.service.OfferService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint-urile de administrare pentru ofertele comerciale.
 *
 * <p>Toate cer permisiunea {@code OFFERS_MANAGE}, deținută de Administrator și
 * de Manager. Editorul nu o are: conținutul promoțional stabilește prețul
 * efectiv al livrării și durata campaniilor, deci ține de operare comercială,
 * nu de redactarea fișelor de produs.</p>
 *
 * <p>Ordonarea implicită a listei este cea de afișare — {@code sortOrder}, apoi
 * {@code id} — ca tabelul din panou să reflecte exact ordinea în care ofertele
 * apar în magazin.</p>
 */
@RestController
@RequestMapping("/admin/offers")
public class AdminOfferController {

    private final OfferService offerService;

    public AdminOfferController(OfferService offerService) {
        this.offerService = offerService;
    }

    @GetMapping
    @PreAuthorize("@permissionService.has('OFFERS_MANAGE')")
    public ResponseEntity<ApiResponse<PageResponse<OfferDto>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Page<OfferDto> result = offerService.list(search,
                PageRequest.of(page, size, Sort.by("sortOrder").ascending().and(Sort.by("id").ascending())));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(result)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.has('OFFERS_MANAGE')")
    public ResponseEntity<ApiResponse<OfferDto>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(offerService.getById(id)));
    }

    @PostMapping
    @PreAuthorize("@permissionService.has('OFFERS_MANAGE')")
    public ResponseEntity<ApiResponse<OfferDto>> create(@Valid @RequestBody OfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Ofertă creată", offerService.create(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@permissionService.has('OFFERS_MANAGE')")
    public ResponseEntity<ApiResponse<OfferDto>> update(@PathVariable Long id,
                                                        @Valid @RequestBody OfferRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Ofertă actualizată", offerService.update(id, request)));
    }

    /**
     * Comută steagul de activare. Există separat de {@code PUT} pentru că
     * bifa din tabel nu are formularul complet la dispoziție, iar un PUT
     * parțial ar șterge câmpurile pe care operatorul nu le-a trimis.
     */
    @PostMapping("/{id}/toggle")
    @PreAuthorize("@permissionService.has('OFFERS_MANAGE')")
    public ResponseEntity<ApiResponse<OfferDto>> toggle(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Stare actualizată", offerService.toggleActive(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@permissionService.has('OFFERS_MANAGE')")
    public ResponseEntity<ApiResponse<Object>> delete(@PathVariable Long id) {
        offerService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok("Ofertă ștearsă", null));
    }
}
