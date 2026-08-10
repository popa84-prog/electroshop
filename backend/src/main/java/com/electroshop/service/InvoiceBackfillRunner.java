package com.electroshop.service;

import com.electroshop.model.CompanySettings;
import com.electroshop.model.Invoice;
import com.electroshop.model.InvoiceLine;
import com.electroshop.model.InvoiceStatus;
import com.electroshop.model.InvoiceType;
import com.electroshop.model.Order;
import com.electroshop.model.OrderItem;
import com.electroshop.model.User;
import com.electroshop.repository.InvoiceRepository;
import com.electroshop.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Aduce în registru facturile emise înainte ca factura să devină entitate.
 *
 * <h2>Ce migrează</h2>
 *
 * <p>Comenzile care au deja {@code invoice_series} și {@code invoice_number}
 * completate au primit cândva un număr fiscal, dar nu au niciun rând în
 * {@code invoices}. Fără migrare, pagina de facturi ar porni goală și ar arăta
 * ca și cum magazinul nu ar fi emis niciodată nimic, iar prima factură nouă ar
 * apărea singură într-un registru care în realitate are istoric.</p>
 *
 * <h2>Trei reguli care fac migrarea sigură</h2>
 *
 * <p><b>Nu alocă niciun număr.</b> Seria și numărul se copiază din comandă exact
 * cum sunt. Contorul din setările firmei nu se atinge — el a fost deja avansat
 * atunci când numărul a fost consumat prima oară, iar o nouă incrementare aici
 * ar sări peste numere care nu au fost niciodată folosite.</p>
 *
 * <p><b>Este idempotentă.</b> Interogarea selectează doar comenzile care nu au
 * încă document, iar înainte de fiecare inserare se verifică și dacă perechea
 * serie plus număr este liberă. Rulată de zece ori la zece reporniri, produce
 * același număr de rânduri ca prima dată. Aceasta este condiția ca ea să poată
 * trăi într-un {@code ApplicationRunner} care se execută la fiecare pornire.</p>
 *
 * <p><b>Nu poate opri aplicația.</b> Fiecare comandă se migrează separat, iar o
 * eroare pe una dintre ele este consemnată și trecută cu vederea. O migrare de
 * date istorice care împiedică pornirea contextului ar transforma o neplăcere
 * cosmetică — o factură veche lipsă din listă — într-o cădere a magazinului.</p>
 *
 * <h2>Ce nu poate reconstitui</h2>
 *
 * <p>Datele firmei se iau din setările actuale, pentru că versiunea de atunci nu
 * a fost salvată nicăieri — exact defectul pe care entitatea nouă îl repară de
 * acum înainte. Denumirile și prețurile produselor, în schimb, vin din
 * instantaneele pe care {@code OrderItem} le păstra deja, deci pozițiile sunt
 * corecte. Documentele astfel reconstruite sunt marcate în {@code notes}, ca
 * nimeni să nu le confunde cu instantanee complete.</p>
 */
@Component
public class InvoiceBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InvoiceBackfillRunner.class);
    private static final int SCALE = 2;

    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final CompanySettingsService companySettingsService;

    public InvoiceBackfillRunner(InvoiceRepository invoiceRepository,
                                 OrderRepository orderRepository,
                                 CompanySettingsService companySettingsService) {
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.companySettingsService = companySettingsService;
    }

    /**
     * Punctul de intrare la pornire.
     *
     * <p>Adnotarea tranzacțională stă aici, pe metoda pe care o apelează Spring
     * prin proxy-ul bean-ului. Pusă pe o metodă privată chemată din interior nu
     * ar avea niciun efect, iar migrarea ar rula fără tranzacție.</p>
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        try {
            int created = backfill();
            if (created > 0) {
                log.info("Registru facturi: {} documente reconstruite din comenzi vechi.", created);
            }
        } catch (RuntimeException e) {
            // Migrarea istoricului nu are voie să împiedice pornirea magazinului.
            log.warn("Reconstrucția registrului de facturi a eșuat: {}", e.getMessage());
        }
    }

    /**
     * Creează documentele lipsă și returnează câte au fost create.
     */
    int backfill() {
        List<Long> orderIds = invoiceRepository.orderIdsMissingInvoice();
        if (orderIds == null || orderIds.isEmpty()) {
            return 0;
        }

        CompanySettings cs = companySettingsService.getEntity();
        int created = 0;

        for (Long orderId : orderIds) {
            try {
                if (migrateOne(orderId, cs)) {
                    created++;
                }
            } catch (RuntimeException e) {
                log.warn("Comanda #{} nu a putut fi reconstruită ca factură: {}",
                        orderId, e.getMessage());
            }
        }
        return created;
    }

    private boolean migrateOne(Long orderId, CompanySettings cs) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getInvoiceNumber() == null) {
            return false;
        }

        String series = (order.getInvoiceSeries() != null && !order.getInvoiceSeries().isBlank())
                ? order.getInvoiceSeries().trim()
                : "ELS";

        // A doua plasă de siguranță, peste interogarea care a selectat comanda:
        // dacă perechea serie plus număr este deja ocupată de alt document,
        // inserarea ar lovi constrângerea de unicitate și ar rupe tranzacția
        // pentru toate comenzile rămase.
        if (invoiceRepository.findBySeriesAndNumber(series, order.getInvoiceNumber()).isPresent()) {
            return false;
        }

        Invoice invoice = new Invoice();
        invoice.setType(InvoiceType.INVOICE);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setOrder(order);
        invoice.setSeries(series);
        invoice.setNumber(order.getInvoiceNumber());
        invoice.setIssuedAt(order.getInvoiceIssuedAt() != null
                ? order.getInvoiceIssuedAt()
                : (order.getCreatedAt() != null ? order.getCreatedAt().toLocalDate() : LocalDate.now()));

        invoice.setSellerName(notBlank(cs.getLegalName()) ? cs.getLegalName().trim() : "ElectroShop");
        invoice.setSellerCui(blankToNull(cs.getCui()));
        invoice.setSellerRegCom(blankToNull(cs.getRegCom()));
        invoice.setSellerAddress(joinAddress(cs));
        invoice.setSellerIban(blankToNull(cs.getIban()));
        invoice.setSellerBank(blankToNull(cs.getBankName()));

        User u = order.getUser();
        invoice.setBuyerName(u == null ? "Client"
                : (notBlank(u.getFullName()) ? u.getFullName().trim() : u.getEmail()));
        invoice.setBuyerEmail(u == null ? null : blankToNull(u.getEmail()));
        invoice.setBuyerAddress(blankToNull(order.getShippingAddress()));

        boolean vatPayer = cs.isVatPayer();
        BigDecimal rate = cs.getVatRate() == null ? BigDecimal.ZERO : cs.getVatRate();
        invoice.setVatPayer(vatPayer);
        invoice.setVatRate(rate);

        invoice.setNotes("Document reconstruit din comanda #" + order.getId()
                + ". Datele furnizorului provin din setările curente ale firmei, "
                + "pentru că versiunea de la data emiterii nu a fost păstrată.");

        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                invoice.addLine(lineFrom(item, vatPayer, rate));
            }
        }
        invoice.recalculateTotals();

        invoiceRepository.save(invoice);
        return true;
    }

    private InvoiceLine lineFrom(OrderItem item, boolean vatPayer, BigDecimal rate) {
        InvoiceLine line = new InvoiceLine();
        line.setOrderItemId(item.getId());
        line.setProduct(item.getProduct());
        line.setProductName(nameOf(item));
        line.setSku(item.getProduct() == null ? null : blankToNull(item.getProduct().getSku()));

        int qty = item.getQuantity() == null ? 0 : item.getQuantity();
        BigDecimal unit = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();

        line.setQuantity(qty);
        line.setUnitPrice(unit.setScale(SCALE, RoundingMode.HALF_UP));

        // Cantitatea deja stornată este zero prin definiție: până acum nu exista
        // niciun mecanism de stornare, deci niciun document vechi nu poate fi
        // parțial creditat.
        line.setStornoedQuantity(0);

        BigDecimal gross = unit.multiply(BigDecimal.valueOf(qty)).setScale(SCALE, RoundingMode.HALF_UP);
        InvoiceIssueService.applyAmounts(line, gross, vatPayer, rate);
        return line;
    }

    private static String nameOf(OrderItem item) {
        if (notBlank(item.getProductName())) {
            return item.getProductName().trim();
        }
        if (item.getProduct() != null && notBlank(item.getProduct().getName())) {
            return item.getProduct().getName().trim();
        }
        return "Produs";
    }

    private static String joinAddress(CompanySettings cs) {
        StringBuilder sb = new StringBuilder();
        append(sb, cs.getAddress());
        append(sb, cs.getCity());
        append(sb, cs.getCounty());
        append(sb, cs.getPostalCode());
        append(sb, cs.getCountry());
        return sb.length() == 0 ? null : sb.toString();
    }

    private static void append(StringBuilder sb, String part) {
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
