package com.electroshop.service;

import com.electroshop.model.CompanySettings;
import com.electroshop.model.Invoice;
import com.electroshop.model.InvoiceLine;
import com.electroshop.repository.InvoiceRepository;
import com.electroshop.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Numerotarea și descompunerea TVA.
 *
 * <p>Ambele repară un defect concret al versiunii anterioare. Numerotarea se
 * făcea în interiorul rutei de descărcare, deci un {@code GET} — verbul despre
 * care fiecare browser și fiecare proxy presupune că nu schimbă nimic — consuma
 * definitiv un număr fiscal, iar dacă respectiva comandă era ștearsă, numărul
 * rămânea o gaură în serie. TVA-ul se calcula pe totalul general, nu pe linii,
 * deci adunarea coloanei tipărite putea să difere de total cu bani — exact
 * diferența pe care o sesizează un contabil.</p>
 *
 * <p>Testele atacă cele două mecanisme direct, fără să treacă prin fluxul
 * complet de emitere: acela are nevoie de un depozit real, iar ce se verifică
 * aici sunt regulile de calcul, care nu depind de persistență.</p>
 */
class InvoiceIssueServiceTest {

    private InvoiceRepository invoices;
    private CompanySettings settings;
    private InvoiceIssueService service;

    @BeforeEach
    void setUp() {
        invoices = Mockito.mock(InvoiceRepository.class);
        OrderRepository orders = Mockito.mock(OrderRepository.class);
        CompanySettingsService companySettings = Mockito.mock(CompanySettingsService.class);
        AuditService audit = Mockito.mock(AuditService.class);

        settings = new CompanySettings();
        settings.setLegalName("ELECTROSHOP SRL");
        settings.setVatPayer(true);
        settings.setVatRate(new BigDecimal("19.00"));
        settings.setInvoiceSeries("ELS");
        settings.setInvoiceNextNumber(1);

        when(companySettings.getEntity()).thenReturn(settings);
        service = new InvoiceIssueService(invoices, orders, companySettings, audit);
    }

    // ---- Numerotarea -----------------------------------------------------

    @Test
    void numereleIesConsecutivDinContor() {
        when(invoices.maxNumberInSeries("ELS")).thenReturn(null);

        int first = service.allocateNumber(settings);
        int second = service.allocateNumber(settings);

        assertEquals(1, first);
        assertEquals(2, second, "fără gol între două emiteri");
        assertEquals(3, settings.getInvoiceNextNumber().intValue());
    }

    @Test
    void contorulRamasInUrmaEsteAdusLaZi() {
        // Un document introdus direct în bază, ocolind aplicația, lasă contorul
        // în urmă. Fără corecție, emiterea următoare ar cere un număr deja
        // folosit și ar lovi constrângerea de unicitate din tabelă.
        when(invoices.maxNumberInSeries("ELS")).thenReturn(57);
        settings.setInvoiceNextNumber(3);

        int allocated = service.allocateNumber(settings);

        assertEquals(58, allocated, "următorul liber este 58, nu 3");
        assertEquals(59, settings.getInvoiceNextNumber().intValue());
    }

    @Test
    void contorulAflatInFataEsteRespectat() {
        // Cazul invers: administratorul a sărit intenționat la 1000 pentru anul
        // nou. Maximul existent nu are voie să îl tragă înapoi.
        when(invoices.maxNumberInSeries("ELS")).thenReturn(57);
        settings.setInvoiceNextNumber(1000);

        assertEquals(1000, service.allocateNumber(settings));
    }

    @Test
    void seriaLipsaCadePeValoareaImplicita() {
        settings.setInvoiceSeries(null);
        assertEquals("ELS", service.seriesOf(settings));

        settings.setInvoiceSeries("   ");
        assertEquals("ELS", service.seriesOf(settings));

        settings.setInvoiceSeries("  FCT  ");
        assertEquals("FCT", service.seriesOf(settings), "seria se curăță de spații");
    }

    // ---- Descompunerea TVA ----------------------------------------------

    @Test
    void pretulDeRaftEsteDescompusInBazaSiTva() {
        // 119 cu TVA 19% înseamnă 100 bază și 19 TVA. O implementare care ar
        // trata prețul de raft ca bază ar tipări 119 + 22.61 = 141.61, adică o
        // factură care nu corespunde sumei încasate efectiv de la client.
        InvoiceLine line = new InvoiceLine();
        InvoiceIssueService.applyAmounts(line, new BigDecimal("119.00"), true, new BigDecimal("19.00"));

        assertEquals(0, new BigDecimal("100.00").compareTo(line.getLineNet()));
        assertEquals(0, new BigDecimal("19.00").compareTo(line.getLineVat()));
        assertEquals(0, new BigDecimal("119.00").compareTo(line.getLineGross()));
    }

    @Test
    void bazaPlusTvaEsteIntotdeaunaExactBrutul() {
        // Valori alese tocmai pentru că nu se împart frumos la 1.19. TVA-ul se
        // obține prin scădere, nu printr-o a doua înmulțire, deci nu poate
        // rămâne un ban pe dinafară.
        String[] amounts = {"99.99", "33.33", "0.01", "1234.56", "7.77", "19.19"};
        for (String amount : amounts) {
            InvoiceLine line = new InvoiceLine();
            InvoiceIssueService.applyAmounts(line, new BigDecimal(amount), true, new BigDecimal("19.00"));

            assertEquals(0,
                    line.getLineNet().add(line.getLineVat()).compareTo(line.getLineGross()),
                    "bază + TVA trebuie să dea exact brutul pentru " + amount);
            assertEquals(0, new BigDecimal(amount).compareTo(line.getLineGross()));
        }
    }

    @Test
    void neplatitorulDeTvaNuScoateNimicDinPret() {
        InvoiceLine line = new InvoiceLine();
        InvoiceIssueService.applyAmounts(line, new BigDecimal("119.00"), false, new BigDecimal("19.00"));

        assertEquals(0, new BigDecimal("119.00").compareTo(line.getLineNet()));
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getLineVat()));
        assertEquals(0, new BigDecimal("119.00").compareTo(line.getLineGross()));
    }

    @Test
    void cotaZeroSeComportaCaNeplatitorul() {
        InvoiceLine line = new InvoiceLine();
        InvoiceIssueService.applyAmounts(line, new BigDecimal("50.00"), true, BigDecimal.ZERO);

        assertEquals(0, new BigDecimal("50.00").compareTo(line.getLineNet()));
        assertEquals(0, BigDecimal.ZERO.compareTo(line.getLineVat()));
    }

    @Test
    void valorileNegativeAleUnuiStornoSeDescompunSimetric() {
        // Stornarea folosește exact aceeași metodă, pe valori negative. Dacă
        // descompunerea nu ar fi simetrică, suma liniilor de storno nu ar anula
        // exact suma liniilor facturate și ar rămâne bani în urmă la fiecare
        // corecție.
        InvoiceLine positive = new InvoiceLine();
        InvoiceIssueService.applyAmounts(positive, new BigDecimal("119.00"), true, new BigDecimal("19.00"));

        InvoiceLine negative = new InvoiceLine();
        InvoiceIssueService.applyAmounts(negative, new BigDecimal("-119.00"), true, new BigDecimal("19.00"));

        assertEquals(0, positive.getLineNet().add(negative.getLineNet()).compareTo(BigDecimal.ZERO),
                "bazele se anulează");
        assertEquals(0, positive.getLineVat().add(negative.getLineVat()).compareTo(BigDecimal.ZERO),
                "TVA-urile se anulează");
        assertEquals(0, positive.getLineGross().add(negative.getLineGross()).compareTo(BigDecimal.ZERO),
                "brutele se anulează");
    }

    // ---- Totalurile documentului ----------------------------------------

    @Test
    void totalurileSuntSumaLiniilorNuORecalculare() {
        // Rotunjirea pe linie și rotunjirea pe total dau rezultate care diferă
        // cu bani. Documentul tipărește liniile, deci cine adună coloana de pe
        // hârtie trebuie să obțină exact totalul de pe hârtie.
        Invoice invoice = new Invoice();
        invoice.addLine(lineOf("33.33", true));
        invoice.addLine(lineOf("33.33", true));
        invoice.addLine(lineOf("33.33", true));

        invoice.recalculateTotals();

        assertEquals(0, new BigDecimal("99.99").compareTo(invoice.getTotalGross()));
        assertEquals(0,
                invoice.getTotalNet().add(invoice.getTotalVat()).compareTo(invoice.getTotalGross()));

        BigDecimal sumOfLineNets = BigDecimal.ZERO;
        for (InvoiceLine l : invoice.getLines()) {
            sumOfLineNets = sumOfLineNets.add(l.getLineNet());
        }
        assertEquals(0, sumOfLineNets.compareTo(invoice.getTotalNet()),
                "totalul este exact suma coloanei, nu o recalculare din brut");
    }

    @Test
    void unDocumentFaraLiniiAreTotaluriZero() {
        Invoice invoice = new Invoice();
        invoice.recalculateTotals();

        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getTotalNet()));
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getTotalVat()));
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getTotalGross()));
    }

    @Test
    void etichetaDocumentuluiCombinaSeriaSiNumarul() {
        Invoice invoice = new Invoice();
        invoice.setSeries("ELS");
        invoice.setNumber(42);
        assertTrue(invoice.getDocumentNumber().equals("ELS 42"));
    }

    private static InvoiceLine lineOf(String gross, boolean vatPayer) {
        InvoiceLine line = new InvoiceLine();
        line.setQuantity(1);
        line.setUnitPrice(new BigDecimal(gross));
        InvoiceIssueService.applyAmounts(line, new BigDecimal(gross), vatPayer, new BigDecimal("19.00"));
        return line;
    }
}
