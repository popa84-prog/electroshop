package com.electroshop.controller;

import com.electroshop.dto.ApiResponse;
import com.electroshop.dto.InvoiceDto;
import com.electroshop.dto.InvoiceSummaryDto;
import com.electroshop.model.Invoice;
import com.electroshop.model.InvoiceStatus;
import com.electroshop.model.InvoiceType;
import com.electroshop.repository.InvoiceRepository;
import com.electroshop.service.InvoiceCancellationService;
import com.electroshop.service.InvoiceIssueService;
import com.electroshop.service.InvoiceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registrul de facturi și acțiunile asupra documentelor fiscale.
 *
 * <p>Rute complete: {@code /api/admin/invoices/…}.</p>
 *
 * <h2>Emiterea este POST, descărcarea este GET</h2>
 *
 * <p>Separarea nu este un detaliu de stil. Până acum, numărul de factură se
 * aloca în interiorul rutei de descărcare, deci un {@code GET} — un verb despre
 * care fiecare browser, fiecare proxy și fiecare mecanism de preîncărcare
 * presupune că nu schimbă nimic — consuma un număr fiscal. Acum
 * {@code POST /admin/invoices} emite, iar {@code GET /admin/invoices/{id}/pdf}
 * doar tipărește.</p>
 */
@RestController
@RequestMapping("/admin/invoices")
public class InvoiceController {

    /** Cât de mare poate fi o pagină cerută de client. */
    private static final int MAX_PAGE_SIZE = 100;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceIssueService issueService;
    private final InvoiceCancellationService cancellationService;
    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceRepository invoiceRepository,
                             InvoiceIssueService issueService,
                             InvoiceCancellationService cancellationService,
                             InvoiceService invoiceService) {
        this.invoiceRepository = invoiceRepository;
        this.issueService = issueService;
        this.cancellationService = cancellationService;
        this.invoiceService = invoiceService;
    }

    // ---- Citire ---------------------------------------------------------

    /**
     * Registrul filtrat.
     *
     * <p>{@code GET /api/admin/invoices?type=&status=&from=&to=&q=&page=&size=}</p>
     *
     * <p>Toate filtrele sunt opționale. Un parametru absent dezactivează
     * condiția respectivă în interogare, ceea ce evită construirea dinamică a
     * clauzei şi păstrează un singur plan de execuție.</p>
     */
    @GetMapping
    @PreAuthorize("@permissionService.has('INVOICE_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        InvoiceType t = parseType(type);
        InvoiceStatus s = parseStatus(status);
        LocalDate f = parseDate(from);
        LocalDate u = parseDate(to);
        String query = blankToNull(q);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Invoice> found = invoiceRepository.search(t, s, f, u, query,
                PageRequest.of(Math.max(page, 0), safeSize));

        List<InvoiceDto> content = new ArrayList<>(found.getNumberOfElements());
        for (Invoice inv : found.getContent()) {
            content.add(InvoiceDto.summary(inv));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", content);
        body.put("page", found.getNumber());
        body.put("size", found.getSize());
        body.put("totalElements", found.getTotalElements());
        body.put("totalPages", found.getTotalPages());
        body.put("summary", summaryFor(t, s, f, u, query));

        return ResponseEntity.ok(ApiResponse.ok(body));
    }

    /**
     * Un document, cu toate poziţiile.
     *
     * <p>{@code GET /api/admin/invoices/{id}}</p>
     */
    @GetMapping("/{id}")
    @PreAuthorize("@permissionService.has('INVOICE_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<InvoiceDto>> get(@PathVariable Long id) {
        Invoice inv = invoiceRepository.findWithLines(id)
                .orElseThrow(() -> new com.electroshop.exception.ResourceNotFoundException("Invoice", id));
        return ResponseEntity.ok(ApiResponse.ok(InvoiceDto.full(inv)));
    }

    /**
     * Toate documentele emise pentru o comandă: factura şi stornările ei.
     *
     * <p>{@code GET /api/admin/invoices/by-order/{orderId}}</p>
     */
    @GetMapping("/by-order/{orderId}")
    @PreAuthorize("@permissionService.has('INVOICE_VIEW')")
    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<InvoiceDto>>> byOrder(@PathVariable Long orderId) {
        List<InvoiceDto> out = new ArrayList<>();
        for (Invoice inv : invoiceRepository.findByOrderIdWithLines(orderId)) {
            out.add(InvoiceDto.full(inv));
        }
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    /**
     * PDF-ul documentului.
     *
     * <p>{@code GET /api/admin/invoices/{id}/pdf}</p>
     *
     * <p>Tipăreşte, nu emite. Dacă documentul nu există, răspunsul este 404, nu
     * o factură nouă.</p>
     */
    @GetMapping("/{id}/pdf")
    @PreAuthorize("@permissionService.has('INVOICE_VIEW')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        InvoiceService.InvoiceFile file = invoiceService.generate(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(file.content());
    }

    // ---- Scriere --------------------------------------------------------

    /**
     * Emite factura pentru o comandă.
     *
     * <p>{@code POST /api/admin/invoices}</p>
     */
    @PostMapping
    @PreAuthorize("@permissionService.has('INVOICE_ISSUE')")
    public ResponseEntity<ApiResponse<InvoiceDto>> issue(@RequestBody IssueRequest request) {
        if (request == null || request.orderId() == null) {
            throw new com.electroshop.exception.BadRequestException("Lipseşte identificatorul comenzii.");
        }
        Invoice issued = issueService.issueForOrder(request.orderId(), request.notes());
        return ResponseEntity.ok(ApiResponse.ok(InvoiceDto.full(issued)));
    }

    /**
     * Stornează o factură, integral sau pe cantităţi selectate.
     *
     * <p>{@code POST /api/admin/invoices/{id}/storno}</p>
     *
     * <p>Când {@code lines} lipseşte sau este gol, stornarea este totală. Când
     * conţine poziţii, se stornează exact cantităţile cerute. Motivul este
     * obligatoriu în ambele cazuri, iar {@code restock} decide dacă marfa se
     * întoarce în stoc — implicit da, pentru că acesta este cazul obişnuit, dar
     * dezactivabil pentru marfa deteriorată sau pierdută, care nu s-a întors
     * fizic niciodată.</p>
     */
    @PostMapping("/{id}/storno")
    @PreAuthorize("@permissionService.has('INVOICE_CANCEL')")
    public ResponseEntity<ApiResponse<InvoiceDto>> storno(@PathVariable Long id,
                                                          @RequestBody StornoRequest request) {
        if (request == null) {
            throw new com.electroshop.exception.BadRequestException("Cerere goală.");
        }
        boolean restock = request.restock() == null || request.restock();

        Invoice created;
        if (request.lines() == null || request.lines().isEmpty()) {
            created = cancellationService.stornoFull(id, request.reason(), restock);
        } else {
            Map<Long, Integer> byLine = new LinkedHashMap<>();
            for (StornoLine line : request.lines()) {
                if (line == null || line.lineId() == null) {
                    continue;
                }
                byLine.merge(line.lineId(),
                        line.quantity() == null ? 0 : line.quantity(), Integer::sum);
            }
            created = cancellationService.storno(id, byLine, request.reason(), restock);
        }
        return ResponseEntity.ok(ApiResponse.ok(InvoiceDto.full(created)));
    }

    // ---- Ajutătoare ------------------------------------------------------

    private InvoiceSummaryDto summaryFor(InvoiceType t, InvoiceStatus s,
                                         LocalDate f, LocalDate u, String q) {
        List<Object[]> rows = invoiceRepository.totalsFor(t, s, f, u, q);
        if (rows == null || rows.isEmpty() || rows.get(0) == null) {
            return InvoiceSummaryDto.of(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        Object[] row = rows.get(0);
        long count = row[0] == null ? 0 : ((Number) row[0]).longValue();
        return InvoiceSummaryDto.of(count, toDecimal(row[1]), toDecimal(row[2]), toDecimal(row[3]));
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }

    /**
     * Un filtru necunoscut nu opreşte pagina.
     *
     * <p>Valoarea vine dintr-un parametru de interogare, iar un semn de carte
     * vechi poate purta un tip care între timp a fost redenumit. Ignorarea
     * filtrului afişează tot, ceea ce este vizibil şi corectabil; un 400 ar lăsa
     * operatorul cu o pagină goală şi fără nicio indicaţie despre cauză.</p>
     */
    private static InvoiceType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return InvoiceType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static InvoiceStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return InvoiceStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // ---- Cereri ---------------------------------------------------------

    /** Corpul cererii de emitere. */
    public record IssueRequest(Long orderId, String notes) {}

    /** O poziţie de stornat, cu cantitatea ei. */
    public record StornoLine(Long lineId, Integer quantity) {}

    /**
     * Corpul cererii de stornare.
     *
     * @param reason  motivul, obligatoriu
     * @param restock dacă marfa se întoarce în stoc; {@code null} înseamnă da
     * @param lines   poziţiile de stornat; gol înseamnă stornare totală
     */
    public record StornoRequest(String reason, Boolean restock, List<StornoLine> lines) {}
}
