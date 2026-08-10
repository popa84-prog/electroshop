package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.GoodsReceiptRequest;
import com.electroshop.dto.GoodsReceiptResultDto;
import com.electroshop.exception.BadRequestException;
import com.electroshop.service.GoodsReceiptService;
import com.electroshop.service.ReceptionNotePdfService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * Intrarea de marfă dintr-un fișier Excel.
 *
 * <p>Rute complete: {@code /api/admin/goods-receipts/…}.</p>
 *
 * <h2>De ce o rută separată de importul obișnuit</h2>
 *
 * <p>Importul de catalog și recepția de marfă arată la fel — același fișier,
 * aceleași coloane — dar produc lucruri diferite: primul actualizează
 * informații, al doilea mișcă stoc și emite un document. Puse pe aceeași rută,
 * cu un parametru care le distinge, ar împărți și permisiunea: cine poate
 * corecta denumiri ar putea, dintr-o greșeală de parametru, să emită o
 * recepție. Aici cele două sunt separate și la nivel de drept:
 * {@code PRODUCTS_IMPORT} pentru catalog, {@code PURCHASES_MANAGE} pentru
 * intrări de marfă.</p>
 */
@RestController
@RequestMapping("/admin/goods-receipts")
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;
    private final ReceptionNotePdfService receptionNotePdfService;

    public GoodsReceiptController(GoodsReceiptService goodsReceiptService,
                                  ReceptionNotePdfService receptionNotePdfService) {
        this.goodsReceiptService = goodsReceiptService;
        this.receptionNotePdfService = receptionNotePdfService;
    }

    /**
     * Încarcă un fișier ca intrare de marfă.
     *
     * <p>{@code POST /api/admin/goods-receipts?dryRun=true}</p>
     *
     * <p>Implicit {@code dryRun=true}. Alegerea nu este întâmplătoare: dacă
     * parametrul lipsește dintr-o cerere scrisă greșit, rezultatul este o
     * previzualizare, nu o recepție reală care mișcă stocul și consumă un număr
     * de document. Valoarea implicită a unei operații ireversibile trebuie să
     * fie cea care nu face nimic.</p>
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@permissionService.has('PURCHASES_MANAGE')")
    public ResponseEntity<ApiResponse<GoodsReceiptResultDto>> receive(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "supplierId") Long supplierId,
            @RequestParam(name = "supplierInvoiceNumber", required = false) String supplierInvoiceNumber,
            @RequestParam(name = "invoiceDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate invoiceDate,
            @RequestParam(name = "receptionDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate receptionDate,
            @RequestParam(name = "notes", required = false) String notes,
            @RequestParam(name = "dryRun", defaultValue = "true") boolean dryRun) {

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Nu ai atașat niciun fișier.");
        }

        GoodsReceiptRequest request = new GoodsReceiptRequest(
                supplierId, supplierInvoiceNumber, invoiceDate, receptionDate, notes);

        return ResponseEntity.ok(ApiResponse.ok(
                goodsReceiptService.receive(file, request, dryRun)));
    }

    /**
     * Descarcă nota de intrare-recepție a unei achiziții.
     *
     * <p>{@code GET /api/admin/goods-receipts/{purchaseId}/nir}</p>
     */
    @GetMapping("/{purchaseId}/nir")
    @PreAuthorize("@permissionService.has('PURCHASES_MANAGE')")
    public ResponseEntity<byte[]> receptionNote(@PathVariable Long purchaseId) {
        ReceptionNotePdfService.ReceptionFile file = receptionNotePdfService.generate(purchaseId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(file.content());
    }
}
