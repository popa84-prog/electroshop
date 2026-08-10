package com.electroshop.service;

import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.CompanySettings;
import com.electroshop.model.Invoice;
import com.electroshop.model.InvoiceLine;
import com.electroshop.model.InvoiceStatus;
import com.electroshop.model.InvoiceType;
import com.electroshop.model.Order;
import com.electroshop.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anularea unei facturi prin emiterea unui document de stornare.
 *
 * <h2>De ce storno și nu ștergere</h2>
 *
 * <p>O factură emisă are un număr care a intrat deja în serie și, în cazul
 * livrărilor către persoane juridice, a fost raportat. Ștergerea rândului ar
 * lăsa o gaură în numerotare pe care nimeni nu o mai poate explica, iar
 * golirea celor trei coloane de pe comandă — varianta simplă — ar face ca
 * documentul aflat în mâna clientului să nu mai aibă corespondent în magazin.
 * Corectarea se face prin document nou: același format, număr propriu din
 * aceeași serie, cantități și valori negative, și o trimitere explicită la
 * factura corectată.</p>
 *
 * <h2>Stornare parțială</h2>
 *
 * <p>Clientul returnează frecvent doar o parte din comandă. Cererea trimite
 * perechi de linie și cantitate; ce nu apare în cerere nu se stornează.
 * {@link InvoiceLine#getStornoedQuantity()} ține evidența pe fiecare linie, iar
 * o cerere nu poate depăși niciodată {@code quantity - stornoedQuantity}.
 * Depășirea este respinsă cu eroare, nu tăiată tăcut la maxim: un operator care
 * cere să storneze cinci bucăți dintr-o linie de trei are o neînțelegere despre
 * ce anume corectează, iar o stornare tăcută de trei bucăți i-ar confirma
 * greșit că totul a decurs cum credea.</p>
 *
 * <h2>Relația cu stocul</h2>
 *
 * <p>Restituirea trece prin {@link OrderRestockService}, niciodată direct.
 * Acesta este singurul motiv pentru care stornarea și anularea comenzii pot
 * exista în paralel fără să dubleze cantitățile: serviciul respectiv ține un
 * contor pe fiecare linie de comandă și adaugă doar restul nerestituit, în orice
 * ordine ar fi apăsate cele două butoane.</p>
 *
 * <p>Restituirea este totuși opțională. Marfa nu se întoarce întotdeauna fizic —
 * produs deteriorat, pierdut la transport, sau o factură emisă din greșeală
 * pentru o comandă care nu a plecat niciodată. A forța restituirea ar umfla
 * stocul cu bucăți inexistente, deci indicatorul există, este implicit activ, iar
 * dezactivarea lui se consemnează în jurnal.</p>
 *
 * <h2>Ce nu face</h2>
 *
 * <p>Nu schimbă statutul comenzii. Documentul fiscal și starea logistică sunt
 * două planuri diferite: o factură greșit emisă se stornează fără ca respectiva
 * comandă să fi fost anulată, iar o comandă anulată poate rămâne facturată până
 * la emiterea stornării. Operatorul decide separat, iar regula de idempotență de
 * mai sus îi permite să o facă în orice ordine.</p>
 */
@Service
public class InvoiceCancellationService {

    private static final int SCALE = 2;

    private final InvoiceRepository invoiceRepository;
    private final InvoiceIssueService issueService;
    private final OrderRestockService restockService;
    private final CompanySettingsService companySettingsService;
    private final AuditService auditService;

    public InvoiceCancellationService(InvoiceRepository invoiceRepository,
                                      InvoiceIssueService issueService,
                                      OrderRestockService restockService,
                                      CompanySettingsService companySettingsService,
                                      AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.issueService = issueService;
        this.restockService = restockService;
        this.companySettingsService = companySettingsService;
        this.auditService = auditService;
    }

    /**
     * Stornează integral o factură.
     *
     * @param invoiceId factura corectată
     * @param reason    motivul, obligatoriu
     * @param restock   dacă marfa se întoarce în stoc
     * @return documentul de storno emis
     */
    @Transactional
    public Invoice stornoFull(Long invoiceId, String reason, boolean restock) {
        Invoice original = load(invoiceId);
        Map<Long, Integer> everything = new LinkedHashMap<>();
        for (InvoiceLine line : original.getLines()) {
            int left = line.remainingToStorno();
            if (left > 0) {
                everything.put(line.getId(), left);
            }
        }
        if (everything.isEmpty()) {
            throw new BadRequestException(
                    "Factura " + original.getDocumentNumber()
                            + " este deja stornată integral. Nu mai există nimic de corectat.");
        }
        return storno(invoiceId, everything, reason, restock);
    }

    /**
     * Stornează cantități anume, pe linii anume.
     *
     * @param invoiceId       factura corectată
     * @param quantityByLine  cât se stornează din fiecare linie de factură
     * @param reason          motivul, obligatoriu
     * @param restock         dacă marfa se întoarce în stoc
     * @return documentul de storno emis
     */
    @Transactional
    public Invoice storno(Long invoiceId, Map<Long, Integer> quantityByLine,
                          String reason, boolean restock) {

        String cleanReason = reason == null ? "" : reason.trim();
        if (cleanReason.isEmpty()) {
            // Un storno fără motiv este, la un control, un storno nejustificat.
            // Refuzul aici este mai ieftin decât explicația de peste doi ani.
            throw new BadRequestException("Motivul stornării este obligatoriu.");
        }
        if (cleanReason.length() > 500) {
            throw new BadRequestException("Motivul stornării nu poate depăși 500 de caractere.");
        }

        Invoice original = load(invoiceId);

        if (original.getType() == InvoiceType.STORNO) {
            throw new BadRequestException(
                    "Documentul " + original.getDocumentNumber()
                            + " este deja un storno. Un storno nu se stornează; "
                            + "dacă a fost emis greșit, emite o factură nouă pentru ce a rămas de facturat.");
        }
        if (quantityByLine == null || quantityByLine.isEmpty()) {
            throw new BadRequestException("Nu ai selectat nicio linie de stornat.");
        }

        // ---- Validare completă înainte de orice modificare ------------------
        //
        // Toate liniile se verifică întâi, iar abia apoi se scrie ceva. O cerere
        // cu o linie validă și una invalidă trebuie respinsă în întregime; altfel
        // operatorul ar rămâne cu un storno parțial pe care nu l-a cerut și pe
        // care ar trebui să îl corecteze la rândul lui.

        Map<Long, InvoiceLine> byId = new LinkedHashMap<>();
        for (InvoiceLine line : original.getLines()) {
            byId.put(line.getId(), line);
        }

        Map<InvoiceLine, Integer> plan = new LinkedHashMap<>();
        for (Map.Entry<Long, Integer> entry : quantityByLine.entrySet()) {
            Long lineId = entry.getKey();
            Integer qty = entry.getValue();

            if (qty == null || qty <= 0) {
                continue;
            }
            InvoiceLine line = byId.get(lineId);
            if (line == null) {
                throw new BadRequestException(
                        "Linia " + lineId + " nu aparține facturii " + original.getDocumentNumber() + ".");
            }
            int left = line.remainingToStorno();
            if (left <= 0) {
                throw new BadRequestException(
                        "Poziția „" + line.getProductName() + "” este deja stornată integral.");
            }
            if (qty > left) {
                throw new BadRequestException(
                        "Pentru „" + line.getProductName() + "” se mai pot storna cel mult "
                                + left + " buc., nu " + qty + ".");
            }
            plan.put(line, qty);
        }

        if (plan.isEmpty()) {
            throw new BadRequestException("Nu ai selectat nicio cantitate de stornat.");
        }

        // ---- Documentul de storno ------------------------------------------

        CompanySettings cs = companySettingsService.getEntity();

        Invoice storno = new Invoice();
        storno.setType(InvoiceType.STORNO);
        storno.setStatus(InvoiceStatus.ISSUED);
        storno.setOrder(original.getOrder());
        storno.setOriginalInvoice(original);
        storno.setIssuedAt(LocalDate.now());
        storno.setSeries(original.getSeries());
        storno.setNumber(issueService.allocateNumber(cs));

        // Regimul fiscal se copiază de pe factura corectată, nu din setările de
        // azi. Dacă între timp firma a trecut de la neplătitor la plătitor de
        // TVA, sau cota s-a schimbat, stornarea trebuie să anuleze exact ce s-a
        // facturat atunci — altfel diferența de cotă rămâne agățată de comandă.
        storno.setVatPayer(original.isVatPayer());
        storno.setVatRate(original.getVatRate());

        storno.setSellerName(original.getSellerName());
        storno.setSellerCui(original.getSellerCui());
        storno.setSellerRegCom(original.getSellerRegCom());
        storno.setSellerAddress(original.getSellerAddress());
        storno.setSellerIban(original.getSellerIban());
        storno.setSellerBank(original.getSellerBank());

        storno.setBuyerName(original.getBuyerName());
        storno.setBuyerEmail(original.getBuyerEmail());
        storno.setBuyerAddress(original.getBuyerAddress());
        storno.setBuyerCui(original.getBuyerCui());
        storno.setBuyerRegCom(original.getBuyerRegCom());

        storno.setCurrency(original.getCurrency());
        storno.setCancelReason(cleanReason);
        storno.setCancelledAt(LocalDateTime.now());
        storno.setCancelledBy(auditService.currentActorName());
        storno.setNotes("Stornare a facturii " + original.getDocumentNumber());

        Map<Long, Integer> restockPlan = new LinkedHashMap<>();
        int stornoedPieces = 0;

        for (Map.Entry<InvoiceLine, Integer> entry : plan.entrySet()) {
            InvoiceLine source = entry.getKey();
            int qty = entry.getValue();

            InvoiceLine negative = new InvoiceLine();
            negative.setOrderItemId(source.getOrderItemId());
            negative.setProduct(source.getProduct());
            negative.setProductName(source.getProductName());
            negative.setSku(source.getSku());
            negative.setUnitPrice(source.getUnitPrice());
            negative.setQuantity(-qty);
            negative.setStornoedQuantity(0);

            // Valoarea brută se recalculează din prețul unitar și cantitatea
            // stornată, nu se ia proporțional din totalul liniei originale.
            // Regula de trei pe o valoare deja rotunjită lasă bani în urmă la
            // stornări parțiale repetate; înmulțirea directă nu.
            BigDecimal gross = source.getUnitPrice()
                    .multiply(BigDecimal.valueOf(qty))
                    .setScale(SCALE, RoundingMode.HALF_UP)
                    .negate();

            InvoiceIssueService.applyAmounts(negative, gross, storno.isVatPayer(), storno.getVatRate());
            storno.addLine(negative);

            source.setStornoedQuantity(
                    (source.getStornoedQuantity() == null ? 0 : source.getStornoedQuantity()) + qty);

            if (source.getOrderItemId() != null) {
                restockPlan.merge(source.getOrderItemId(), qty, Integer::sum);
            }
            stornoedPieces += qty;
        }

        storno.recalculateTotals();
        Invoice savedStorno = invoiceRepository.save(storno);

        // ---- Statutul facturii corectate -----------------------------------
        //
        // Calculat din linii, nu setat după intenția apelantului. Doar liniile
        // știu dacă a mai rămas ceva de stornat, iar un indicator pus manual ar
        // putea ajunge să le contrazică.
        original.setStatus(recomputeStatus(original));
        if (original.getStatus() == InvoiceStatus.CANCELLED && original.getCancelledAt() == null) {
            original.setCancelReason(cleanReason);
            original.setCancelledAt(LocalDateTime.now());
            original.setCancelledBy(savedStorno.getCancelledBy());
        }
        invoiceRepository.save(original);

        // ---- Stocul ---------------------------------------------------------

        int restocked = 0;
        Order order = original.getOrder();
        if (restock && order != null && !restockPlan.isEmpty()) {
            restocked = restockService.restock(order, restockPlan,
                    "Storno " + savedStorno.getDocumentNumber() + " · " + cleanReason);
        }

        auditService.log("INVOICE_STORNOED", "Invoice", original.getId(),
                "Storno " + savedStorno.getDocumentNumber()
                        + " pentru factura " + original.getDocumentNumber()
                        + " · " + stornoedPieces + " buc. · "
                        + savedStorno.getTotalGross() + " " + savedStorno.getCurrency()
                        + " · stoc restituit: " + (restock ? restocked + " buc." : "nu")
                        + " · motiv: " + cleanReason);

        return savedStorno;
    }

    /**
     * Ce statut are factura, judecând după cât s-a stornat din fiecare linie.
     */
    public static InvoiceStatus recomputeStatus(Invoice invoice) {
        List<InvoiceLine> lines = invoice.getLines();
        if (lines == null || lines.isEmpty()) {
            return invoice.getStatus();
        }
        boolean any = false;
        boolean all = true;
        for (InvoiceLine line : lines) {
            int stornoed = line.getStornoedQuantity() == null ? 0 : line.getStornoedQuantity();
            if (stornoed > 0) {
                any = true;
            }
            if (!line.isFullyStornoed()) {
                all = false;
            }
        }
        if (all) {
            return InvoiceStatus.CANCELLED;
        }
        return any ? InvoiceStatus.PARTIALLY_STORNOED : InvoiceStatus.ISSUED;
    }

    /**
     * Cât se mai poate storna, pe fiecare linie. Interfața cere asta ca să
     * propună implicit cantitatea rămasă și să nu lase operatorul să scrie un
     * număr pe care serverul îl va refuza oricum.
     */
    @Transactional(readOnly = true)
    public List<int[]> remainingByLine(Long invoiceId) {
        Invoice invoice = load(invoiceId);
        List<int[]> out = new ArrayList<>();
        for (InvoiceLine line : invoice.getLines()) {
            out.add(new int[]{line.getId().intValue(), line.remainingToStorno()});
        }
        return out;
    }

    private Invoice load(Long invoiceId) {
        return invoiceRepository.findWithLines(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
    }
}
