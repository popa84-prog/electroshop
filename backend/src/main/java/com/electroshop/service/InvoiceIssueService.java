package com.electroshop.service;

import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.CompanySettings;
import com.electroshop.model.Invoice;
import com.electroshop.model.InvoiceLine;
import com.electroshop.model.InvoiceStatus;
import com.electroshop.model.InvoiceType;
import com.electroshop.model.Order;
import com.electroshop.model.OrderItem;
import com.electroshop.model.Product;
import com.electroshop.model.User;
import com.electroshop.repository.InvoiceRepository;
import com.electroshop.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Emiterea facturilor. O acțiune explicită, nu un efect secundar.
 *
 * <h2>Ce s-a schimbat față de comportamentul anterior</h2>
 *
 * <p>Până acum numărul de factură se aloca în {@code InvoiceService
 * .generateForOrder}, adică la <b>prima descărcare</b> a PDF-ului. Consecințele
 * erau două, ambele grave pentru un document fiscal. Numerele ieșeau în ordinea
 * în care cineva apăsa butonul de descărcare, nu în ordinea emiterii, deci seria
 * nu mai reflecta cronologia. Și un simplu clic pe o comandă neplătită consuma
 * definitiv un număr; dacă acea comandă era apoi ștearsă, numărul rămânea o gaură
 * în serie pentru care nu exista niciun document.</p>
 *
 * <p>Acum numărul se alocă aici, la o cerere explicită {@code POST}, iar
 * descărcarea doar tipărește ce există deja. O cerere {@code GET} nu mai
 * modifică nimic din starea fiscală, ceea ce este și regula corectă pentru
 * verbul respectiv.</p>
 *
 * <h2>Instantaneul</h2>
 *
 * <p>Factura copiază la emitere tot ce va tipări: datele firmei, datele
 * cumpărătorului, regimul de TVA și fiecare linie cu denumire, cantitate și preț
 * unitar. Nimic nu se mai citește ulterior din catalog sau din setări. O
 * redenumire de produs, o schimbare de preț sau o mutare a sediului nu au cum să
 * modifice un document deja emis — ceea ce înainte se întâmpla la fiecare
 * descărcare.</p>
 */
@Service
public class InvoiceIssueService {

    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final CompanySettingsService companySettingsService;
    private final AuditService auditService;

    public InvoiceIssueService(InvoiceRepository invoiceRepository,
                               OrderRepository orderRepository,
                               CompanySettingsService companySettingsService,
                               AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.companySettingsService = companySettingsService;
        this.auditService = auditService;
    }

    /**
     * Emite factura pentru o comandă.
     *
     * @param orderId comanda facturată
     * @param notes   mențiuni suplimentare pe document, opționale
     * @return documentul emis, cu număr alocat
     * @throws BadRequestException dacă respectiva comandă are deja factură sau
     *                             nu are nicio linie
     */
    @Transactional
    public Invoice issueForOrder(Long orderId, String notes) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (invoiceRepository.existsByOrderIdAndType(orderId, InvoiceType.INVOICE)) {
            throw new BadRequestException(
                    "Comanda #" + orderId + " are deja factură emisă. "
                            + "Pentru corecții emite un storno, nu o a doua factură.");
        }
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new BadRequestException(
                    "Comanda #" + orderId + " nu are nicio linie, deci nu poate fi facturată.");
        }

        CompanySettings cs = companySettingsService.getEntity();

        Invoice invoice = new Invoice();
        invoice.setType(InvoiceType.INVOICE);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setOrder(order);
        invoice.setIssuedAt(LocalDate.now());
        invoice.setSeries(seriesOf(cs));
        invoice.setNumber(allocateNumber(cs));
        invoice.setNotes(blankToNull(notes) != null ? notes.trim() : blankToNull(cs.getInvoiceNotes()));

        copySeller(invoice, cs);
        copyBuyer(invoice, order);

        invoice.setVatPayer(cs.isVatPayer());
        invoice.setVatRate(cs.getVatRate() == null ? BigDecimal.ZERO : cs.getVatRate());

        for (OrderItem item : order.getItems()) {
            invoice.addLine(buildLine(item, invoice.isVatPayer(), invoice.getVatRate()));
        }
        invoice.recalculateTotals();

        Invoice saved = invoiceRepository.save(invoice);

        // Cele trei coloane vechi de pe comandă rămân sincronizate, pentru că
        // lista de comenzi și exporturile existente le citesc. Sursa adevărului
        // este de acum documentul; acestea sunt o oglindă pentru compatibilitate.
        order.setInvoiceSeries(saved.getSeries());
        order.setInvoiceNumber(saved.getNumber());
        order.setInvoiceIssuedAt(saved.getIssuedAt());
        orderRepository.save(order);

        auditService.log("INVOICE_ISSUED", "Invoice", saved.getId(),
                "Factura " + saved.getDocumentNumber() + " pentru comanda #" + orderId
                        + " · total " + saved.getTotalGross() + " " + saved.getCurrency());

        return saved;
    }

    /**
     * Ia următorul număr din contorul firmei și îl avansează.
     *
     * <p>Metoda este publică pentru că stornarea are nevoie de exact aceeași
     * numerotare: magazinul folosește o serie unică pentru toate documentele,
     * deci facturile și stornările împart contorul. Duplicarea logicii în cele
     * două servicii ar fi însemnat două locuri în care se poate strecura o
     * diferență de comportament la incrementare.</p>
     *
     * <p>Contorul din setări rămâne sursa numerotării, dar este comparat cu cel
     * mai mare număr existent deja în serie. Dacă un document a fost introdus
     * direct în bază, ocolind aplicația, contorul ar rămâne în urmă și ar
     * încerca să emită un număr deja folosit; comparația îl aduce la zi. Chiar
     * și așa, apărarea reală este constrângerea de unicitate din tabelă, pentru
     * că verificarea din cod se poate pierde între două cereri concurente.</p>
     */
    @Transactional
    public int allocateNumber(CompanySettings cs) {
        int fromCounter = cs.getInvoiceNextNumber() != null ? cs.getInvoiceNextNumber() : 1;

        Integer maxUsed = invoiceRepository.maxNumberInSeries(seriesOf(cs));
        int next = (maxUsed != null && maxUsed >= fromCounter) ? maxUsed + 1 : fromCounter;

        cs.setInvoiceNextNumber(next + 1);
        return next;
    }

    /**
     * Seria configurată, cu revenire la valoarea implicită dacă lipsește.
     */
    public String seriesOf(CompanySettings cs) {
        String s = cs.getInvoiceSeries();
        return (s != null && !s.isBlank()) ? s.trim() : "ELS";
    }

    // ---- Construcția liniilor -------------------------------------------

    /**
     * Transformă o linie de comandă într-o poziție de factură.
     *
     * <p>Prețul unitar din comandă este prețul de raft, cu TVA inclus, la fel ca
     * cel din catalog. Factura trebuie să arate baza și TVA-ul separat, deci
     * descompune înapoi în loc să adauge cota peste. A trata prețul de raft ca
     * bază de impozitare ar umfla documentul cu exact cota de TVA, iar totalul
     * facturii nu ar mai corespunde sumei încasate efectiv de la client.</p>
     */
    private InvoiceLine buildLine(OrderItem item, boolean vatPayer, BigDecimal vatRate) {
        InvoiceLine line = new InvoiceLine();
        line.setOrderItemId(item.getId());
        line.setProduct(item.getProduct());
        line.setProductName(nameOf(item));
        line.setSku(skuOf(item));

        int qty = item.getQuantity() == null ? 0 : item.getQuantity();
        BigDecimal unit = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();

        line.setQuantity(qty);
        line.setUnitPrice(unit.setScale(SCALE, RoundingMode.HALF_UP));
        line.setStornoedQuantity(0);

        BigDecimal gross = unit.multiply(BigDecimal.valueOf(qty)).setScale(SCALE, RoundingMode.HALF_UP);
        applyAmounts(line, gross, vatPayer, vatRate);
        return line;
    }

    /**
     * Împarte o valoare brută în bază și TVA și o scrie pe linie.
     *
     * <p>Public și static pentru că stornarea are nevoie de aceeași descompunere
     * pe valori negative, iar o a doua implementare ar putea rotunji altfel — cu
     * consecința că suma liniilor de storno nu ar mai anula exact suma liniilor
     * facturate, lăsând bani în urmă la fiecare corecție.</p>
     */
    public static void applyAmounts(InvoiceLine line, BigDecimal gross,
                                    boolean vatPayer, BigDecimal vatRate) {
        BigDecimal g = gross.setScale(SCALE, RoundingMode.HALF_UP);

        if (!vatPayer || vatRate == null || vatRate.compareTo(BigDecimal.ZERO) == 0) {
            // Neplătitor de TVA: baza este chiar valoarea încasată, iar coloana
            // de TVA rămâne zero. Nu se scoate nimic dintr-un preț care nu
            // conține nimic de scos.
            line.setLineNet(g);
            line.setLineVat(BigDecimal.ZERO.setScale(SCALE));
            line.setLineGross(g);
            return;
        }

        BigDecimal divisor = BigDecimal.ONE.add(vatRate.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        BigDecimal net = g.divide(divisor, SCALE, RoundingMode.HALF_UP);
        // TVA-ul se obține prin scădere, nu printr-o a doua înmulțire: așa
        // baza plus TVA dă întotdeauna exact valoarea brută, fără bănuțul de
        // diferență pe care l-ar produce două rotunjiri independente.
        BigDecimal vat = g.subtract(net);

        line.setLineNet(net);
        line.setLineVat(vat);
        line.setLineGross(g);
    }

    // ---- Instantanee -----------------------------------------------------

    private void copySeller(Invoice invoice, CompanySettings cs) {
        invoice.setSellerName(notBlank(cs.getLegalName()) ? cs.getLegalName().trim() : "ElectroShop");
        invoice.setSellerCui(blankToNull(cs.getCui()));
        invoice.setSellerRegCom(blankToNull(cs.getRegCom()));
        invoice.setSellerAddress(joinAddress(cs.getAddress(), cs.getCity(), cs.getCounty(),
                cs.getPostalCode(), cs.getCountry()));
        invoice.setSellerIban(blankToNull(cs.getIban()));
        invoice.setSellerBank(blankToNull(cs.getBankName()));
    }

    private void copyBuyer(Invoice invoice, Order order) {
        User u = order.getUser();
        if (u != null) {
            invoice.setBuyerName(notBlank(u.getFullName()) ? u.getFullName().trim() : u.getEmail());
            invoice.setBuyerEmail(blankToNull(u.getEmail()));
        } else {
            invoice.setBuyerName("Client");
        }
        invoice.setBuyerAddress(blankToNull(order.getShippingAddress()));
    }

    // ---- Ajutătoare ------------------------------------------------------

    private static String nameOf(OrderItem item) {
        if (notBlank(item.getProductName())) {
            return item.getProductName().trim();
        }
        Product p = item.getProduct();
        if (p != null && notBlank(p.getName())) {
            return p.getName().trim();
        }
        return "Produs";
    }

    private static String skuOf(OrderItem item) {
        Product p = item.getProduct();
        // Null pentru produsele șterse definitiv: linia de factură își păstrează
        // denumirea copiată, iar codul rămâne gol în loc să oprească tipărirea.
        return p == null ? null : blankToNull(p.getSku());
    }

    private static String joinAddress(String street, String city, String county,
                                      String postal, String country) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, street);
        appendPart(sb, city);
        appendPart(sb, county);
        appendPart(sb, postal);
        appendPart(sb, country);
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void appendPart(StringBuilder sb, String part) {
        if (!notBlank(part)) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(", ");
        }
        sb.append(part.trim());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
