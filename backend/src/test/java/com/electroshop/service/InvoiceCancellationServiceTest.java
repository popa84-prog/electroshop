package com.electroshop.service;

import com.electroshop.exception.BadRequestException;
import com.electroshop.model.Invoice;
import com.electroshop.model.InvoiceLine;
import com.electroshop.model.InvoiceStatus;
import com.electroshop.model.InvoiceType;
import com.electroshop.repository.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Regulile care apără stornarea de cereri incoerente.
 *
 * <p>Toate verificările de mai jos se produc <b>înainte</b> ca ceva să fie
 * scris. Aceasta este și structura serviciului: întâi se validează fiecare
 * linie a cererii, abia apoi se construiește documentul. O cerere cu o linie
 * validă și una invalidă trebuie respinsă în întregime — altfel operatorul ar
 * rămâne cu un storno parțial pe care nu l-a cerut și pe care ar trebui, la
 * rândul lui, să îl corecteze.</p>
 *
 * <p>Recalcularea statutului este testată separat, ca funcție pură. Ea este
 * motivul pentru care factura nu are un indicator „anulată" pus de operator:
 * doar liniile știu dacă a mai rămas ceva de stornat, iar un indicator manual ar
 * putea ajunge să le contrazică.</p>
 */
class InvoiceCancellationServiceTest {

    private InvoiceRepository invoices;
    private InvoiceCancellationService service;

    @BeforeEach
    void setUp() {
        invoices = Mockito.mock(InvoiceRepository.class);
        CompanySettingsService settings = Mockito.mock(CompanySettingsService.class);
        AuditService audit = Mockito.mock(AuditService.class);

        // Colaboratorii sunt instanțe reale, construite peste depozite simulate,
        // nu duble de test. Fiecare verificare de mai jos se produce înainte ca
        // ei să fie atinși, deci nu trebuie să se comporte în vreun fel anume;
        // iar dacă o validare ar fi mutată din greșeală după primul apel către
        // ei, testul ar semnala imediat, în loc să treacă pe un dublu tăcut.
        InvoiceIssueService issue = new InvoiceIssueService(
                invoices,
                Mockito.mock(com.electroshop.repository.OrderRepository.class),
                settings, audit);
        OrderRestockService restock = new OrderRestockService(
                Mockito.mock(com.electroshop.repository.ProductRepository.class),
                audit,
                Mockito.mock(NotificationService.class));

        service = new InvoiceCancellationService(invoices, issue, restock, settings, audit);
    }

    // ---- Motivul ---------------------------------------------------------

    @Test
    void stornareaFaraMotivEsteRefuzata() {
        // Un storno fără explicație este, la un control, un storno
        // nejustificat. Refuzul aici costă mai puțin decât explicația de peste
        // doi ani.
        given(invoiceWith(line(1L, 3)));

        assertThrows(BadRequestException.class,
                () -> service.storno(1L, Map.of(1L, 1), null, true));
        assertThrows(BadRequestException.class,
                () -> service.storno(1L, Map.of(1L, 1), "", true));
        assertThrows(BadRequestException.class,
                () -> service.storno(1L, Map.of(1L, 1), "    ", true));
    }

    @Test
    void motivulPreaLungEsteRefuzat() {
        given(invoiceWith(line(1L, 3)));
        String tooLong = "x".repeat(501);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.storno(1L, Map.of(1L, 1), tooLong, true));
        assertTrue(ex.getMessage().contains("500"));
    }

    // ---- Cantitățile -----------------------------------------------------

    @Test
    void stornareaPesteCantitateaFacturataEsteRefuzata() {
        given(invoiceWith(line(1L, 3)));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.storno(1L, Map.of(1L, 5), "retur client", true));

        assertTrue(ex.getMessage().contains("cel mult 3"),
                "mesajul trebuie să spună cât se mai poate storna: " + ex.getMessage());
    }

    @Test
    void stornareaPesteRestulRamasEsteRefuzata() {
        // Două bucăți din trei au fost deja stornate; a treia cerere de două
        // bucăți depășește restul.
        InvoiceLine l = line(1L, 3);
        l.setStornoedQuantity(2);
        given(invoiceWith(l));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.storno(1L, Map.of(1L, 2), "retur", true));
        assertTrue(ex.getMessage().contains("cel mult 1"));
    }

    @Test
    void oLinieDejaStornataIntegralEsteRefuzata() {
        InvoiceLine l = line(1L, 3);
        l.setStornoedQuantity(3);
        given(invoiceWith(l));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.storno(1L, Map.of(1L, 1), "retur", true));
        assertTrue(ex.getMessage().contains("deja stornat"));
    }

    @Test
    void oLinieCareNuApartineFacturiiEsteRefuzata() {
        given(invoiceWith(line(1L, 3)));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.storno(1L, Map.of(999L, 1), "retur", true));
        assertTrue(ex.getMessage().contains("nu aparţine") || ex.getMessage().contains("nu aparține")
                        || ex.getMessage().contains("999"),
                "mesajul trebuie să identifice linia străină: " + ex.getMessage());
    }

    @Test
    void oCerereFaraNicioCantitatePozitivaEsteRefuzata() {
        given(invoiceWith(line(1L, 3)));

        Map<Long, Integer> zeros = new LinkedHashMap<>();
        zeros.put(1L, 0);

        assertThrows(BadRequestException.class,
                () -> service.storno(1L, zeros, "retur", true));
        assertThrows(BadRequestException.class,
                () -> service.storno(1L, new LinkedHashMap<>(), "retur", true));
    }

    @Test
    void oCerereInvalidaPeOSinguraLinieRespingeIntreagaCerere() {
        // Prima linie este validă, a doua nu. Nimic nu trebuie scris, iar
        // contorul primei linii trebuie să rămână neatins.
        InvoiceLine ok = line(1L, 5);
        InvoiceLine tooSmall = line(2L, 1);
        given(invoiceWith(ok, tooSmall));

        Map<Long, Integer> plan = new LinkedHashMap<>();
        plan.put(1L, 2);
        plan.put(2L, 9);

        assertThrows(BadRequestException.class,
                () -> service.storno(1L, plan, "retur mixt", true));

        assertEquals(0, ok.getStornoedQuantity().intValue(),
                "linia validă nu are voie să fie modificată de o cerere respinsă");
    }

    // ---- Tipul documentului ---------------------------------------------

    @Test
    void unStornoNuSeStorneaza() {
        Invoice storno = invoiceWith(line(1L, 2));
        storno.setType(InvoiceType.STORNO);
        given(storno);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.storno(1L, Map.of(1L, 1), "corecție", true));
        assertTrue(ex.getMessage().contains("storno"));
    }

    @Test
    void oFacturaDejaStornataIntegralNuMaiPoateFiStornataTotal() {
        InvoiceLine l = line(1L, 4);
        l.setStornoedQuantity(4);
        given(invoiceWith(l));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.stornoFull(1L, "retur", true));
        assertTrue(ex.getMessage().contains("deja stornată") || ex.getMessage().contains("integral"));
    }

    // ---- Recalcularea statutului ----------------------------------------

    @Test
    void statutulRamaneEmisaCandNimicNuAFostStornat() {
        Invoice inv = invoiceWith(line(1L, 3), line(2L, 2));
        assertEquals(InvoiceStatus.ISSUED, InvoiceCancellationService.recomputeStatus(inv));
    }

    @Test
    void statutulDevineePartialCandDoarOParteAFostStornata() {
        InvoiceLine a = line(1L, 3);
        a.setStornoedQuantity(1);
        Invoice inv = invoiceWith(a, line(2L, 2));

        assertEquals(InvoiceStatus.PARTIALLY_STORNOED,
                InvoiceCancellationService.recomputeStatus(inv));
    }

    @Test
    void statutulDevineAnulataDoarCandFiecareLinieEsteStornataIntegral() {
        InvoiceLine a = line(1L, 3);
        InvoiceLine b = line(2L, 2);
        a.setStornoedQuantity(3);
        b.setStornoedQuantity(1);
        Invoice inv = invoiceWith(a, b);

        assertEquals(InvoiceStatus.PARTIALLY_STORNOED,
                InvoiceCancellationService.recomputeStatus(inv),
                "o linie stornată integral nu anulează întreaga factură");

        b.setStornoedQuantity(2);
        assertEquals(InvoiceStatus.CANCELLED, InvoiceCancellationService.recomputeStatus(inv));
    }

    // ---- Evidența pe linie -----------------------------------------------

    @Test
    void restulDeStornatScadeCuFiecareStornare() {
        InvoiceLine l = line(1L, 5);
        assertEquals(5, l.remainingToStorno());
        assertTrue(!l.isFullyStornoed());

        l.setStornoedQuantity(2);
        assertEquals(3, l.remainingToStorno());

        l.setStornoedQuantity(5);
        assertEquals(0, l.remainingToStorno());
        assertTrue(l.isFullyStornoed());
    }

    @Test
    void restulDeStornatNuDevineNiciodataNegativ() {
        InvoiceLine l = line(1L, 2);
        l.setStornoedQuantity(7);
        assertEquals(0, l.remainingToStorno(),
                "o valoare incoerentă în bază nu are voie să producă un rest negativ");
    }

    // ---- Ajutătoare ------------------------------------------------------

    private void given(Invoice invoice) {
        when(invoices.findWithLines(1L)).thenReturn(Optional.of(invoice));
    }

    private static Invoice invoiceWith(InvoiceLine... lines) {
        Invoice inv = new Invoice();
        inv.setId(1L);
        inv.setSeries("ELS");
        inv.setNumber(10);
        inv.setType(InvoiceType.INVOICE);
        inv.setStatus(InvoiceStatus.ISSUED);
        inv.setVatPayer(true);
        inv.setVatRate(new BigDecimal("19.00"));
        for (InvoiceLine l : lines) {
            inv.addLine(l);
        }
        return inv;
    }

    private static InvoiceLine line(Long id, int quantity) {
        InvoiceLine l = new InvoiceLine();
        l.setId(id);
        l.setOrderItemId(id * 10);
        l.setProductName("Produs " + id);
        l.setQuantity(quantity);
        l.setUnitPrice(new BigDecimal("119.00"));
        l.setStornoedQuantity(0);
        InvoiceIssueService.applyAmounts(l,
                new BigDecimal("119.00").multiply(BigDecimal.valueOf(quantity)),
                true, new BigDecimal("19.00"));
        return l;
    }
}
