package com.electroshop.service;

import com.electroshop.dto.GoodsReceiptRequest;
import com.electroshop.dto.GoodsReceiptResultDto;
import com.electroshop.dto.ProductImportResult;
import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.CompanySettings;
import com.electroshop.model.Product;
import com.electroshop.model.Purchase;
import com.electroshop.model.PurchaseItem;
import com.electroshop.model.Supplier;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.PurchaseRepository;
import com.electroshop.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Importul unui fișier Excel ca intrare de marfă.
 *
 * <h2>Ce produce, și ce nu produce</h2>
 *
 * <p>Din fișier rezultă o <b>recepție</b>: produsele intră în catalog și în
 * stoc, iar mișcarea este consemnată într-un document {@link Purchase} legat de
 * furnizor. Documentul poartă numărul facturii furnizorului, ca referință, și
 * primește un număr propriu de NIR.</p>
 *
 * <p><b>Nu se generează nicio factură de achiziție.</b> Aceea o emite
 * furnizorul, cu seria și numerotarea lui. Dacă aplicația ar produce un
 * document numit „factură de intrare" cu numerotare proprie, ar fabrica
 * documentul altei firme — exact ce se caută la un control. Nota de
 * intrare-recepție este documentul pe care magazinul îl emite legitim la
 * primirea mărfii, și are contor separat de cel al facturilor de vânzare.</p>
 *
 * <h2>Ordinea operațiilor</h2>
 *
 * <p>Totul se validează înainte ca ceva să fie scris: fișierul, furnizorul,
 * duplicarea, și fiecare rând în parte. Un import care ar crea jumătate din
 * produse și apoi ar eșua ar lăsa catalogul într-o stare pe care nimeni nu o
 * poate reconstitui, pentru că operatorul nu știe unde s-a oprit.</p>
 */
@Service
public class GoodsReceiptService {

    private final ProductImportService importService;
    private final ProductRepository productRepository;
    private final PurchaseRepository purchaseRepository;
    private final SupplierRepository supplierRepository;
    private final CompanySettingsService companySettingsService;
    private final StockIntakeService stockIntakeService;
    private final AuditService auditService;

    public GoodsReceiptService(ProductImportService importService,
                               ProductRepository productRepository,
                               PurchaseRepository purchaseRepository,
                               SupplierRepository supplierRepository,
                               CompanySettingsService companySettingsService,
                               StockIntakeService stockIntakeService,
                               AuditService auditService) {
        this.importService = importService;
        this.productRepository = productRepository;
        this.purchaseRepository = purchaseRepository;
        this.supplierRepository = supplierRepository;
        this.companySettingsService = companySettingsService;
        this.stockIntakeService = stockIntakeService;
        this.auditService = auditService;
    }

    /**
     * Citește fișierul și înregistrează intrarea.
     *
     * @param file    fișierul Excel, în același format ca importul obișnuit
     * @param request furnizorul și datele facturii lui
     * @param dryRun  când este adevărat, nimic nu se scrie și niciun număr nu se consumă
     */
    @Transactional
    public GoodsReceiptResultDto receive(MultipartFile file, GoodsReceiptRequest request, boolean dryRun) {

        // ---- 1. Furnizorul ------------------------------------------------
        if (request == null || request.supplierId() == null) {
            throw new BadRequestException("Alege furnizorul de la care a venit livrarea.");
        }
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> new ResourceNotFoundException("Supplier", request.supplierId()));

        // ---- 2. Amprenta fișierului ---------------------------------------
        //
        // Calculată înainte de orice altceva, pentru că respingerea unui fișier
        // deja importat este mai ieftină decât parsarea lui.
        String hash = sha256(file);
        Purchase existing = purchaseRepository.findBySourceFileHash(hash).orElse(null);
        if (existing != null) {
            throw new BadRequestException(
                    "Fișierul acesta a fost deja importat pe " + existing.getReceptionIssuedAt()
                            + ", ca recepția " + existing.getReceptionNumberLabel()
                            + ". O a doua încărcare ar dubla stocul. "
                            + "Dacă a sosit o livrare nouă, exportă un fișier nou pentru ea.");
        }

        // ---- 3. Citirea -----------------------------------------------------
        ProductImportService.ParsedSheet parsed = importService.parse(file);

        if (!parsed.errors().isEmpty()) {
            throw new BadRequestException(
                    "Fișierul are " + parsed.errors().size() + " rânduri cu probleme: "
                            + describeRows(parsed.errors()));
        }
        if (parsed.rows().isEmpty()) {
            throw new BadRequestException("Fișierul nu conține niciun rând de marfă.");
        }

        // ---- 4. Fiecare rând trebuie să aibă preț de achiziție și cantitate --
        //
        // Verificat pe tot fișierul, iar rezultatul respinge întregul import.
        // O intrare cu valoare zero subevaluează inventarul tăcut, iar diferența
        // se descoperă peste luni, când nu mai poate fi reconstituită din nimic.
        // Corectarea fișierului costă minute; corectarea inventarului costă un
        // inventar.
        List<String> missingCost = new ArrayList<>();
        List<String> zeroQuantity = new ArrayList<>();
        for (ProductImportService.PreparedRow row : parsed.rows()) {
            if (row.purchase == null || row.purchase.signum() <= 0) {
                missingCost.add(row.name);
            }
            if (row.stock == null || row.stock <= 0) {
                zeroQuantity.add(row.name);
            }
        }
        if (!missingCost.isEmpty()) {
            throw new BadRequestException(
                    "O intrare de marfă are nevoie de preț de achiziție pe fiecare rând. "
                            + missingCost.size() + " produse nu îl au: " + sample(missingCost)
                            + ". Completează coloana „Preț achiziție” și încarcă din nou.");
        }
        if (!zeroQuantity.isEmpty()) {
            throw new BadRequestException(
                    "O intrare de marfă are nevoie de cantitate pozitivă pe fiecare rând. "
                            + zeroQuantity.size() + " produse au cantitatea zero: "
                            + sample(zeroQuantity) + ".");
        }

        // ---- 5. Numărul de recepție ----------------------------------------
        CompanySettings cs = companySettingsService.getEntity();
        String series = receptionSeries(cs);
        int number = dryRun ? peekNextNumber(cs, series) : allocateNumber(cs, series);

        // ---- 6. Aplicarea ---------------------------------------------------
        LocalDate receptionDate = request.receptionDate() != null
                ? request.receptionDate() : LocalDate.now();

        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setSupplierName(supplier.getName());
        purchase.setSupplierTaxId(supplier.getTaxId());
        purchase.setPurchaseDate(request.invoiceDate() != null
                ? request.invoiceDate() : receptionDate);
        purchase.setInvoiceNumber(blankToNull(request.supplierInvoiceNumber()));
        purchase.setReceptionSeries(series);
        purchase.setReceptionNumber(number);
        purchase.setReceptionIssuedAt(receptionDate);
        purchase.setSourceFileName(file.getOriginalFilename());
        purchase.setSourceFileHash(hash);
        purchase.setNotes(blankToNull(request.notes()));

        List<GoodsReceiptResultDto.Line> lines = new ArrayList<>();
        int created = 0;
        int restocked = 0;
        int units = 0;

        for (ProductImportService.PreparedRow row : parsed.rows()) {
            Product product = findExisting(row);
            boolean isNew = (product == null);

            int stockBefore;
            BigDecimal costBefore;

            if (isNew) {
                stockBefore = 0;
                costBefore = null;
                if (!dryRun) {
                    product = new Product();
                    product.setName(row.name);
                    product.setCategory(row.category);
                    product.setSubcategory(row.subcategory);
                    product.setBrand(row.brand);
                    product.setDescription(row.description);
                    product.setSku(row.sku);
                    product.setPrice(row.sell);
                    // Stocul pornește de la zero și crește prin serviciul de
                    // intrare, ca produsele noi și cele existente să treacă prin
                    // exact același drum. Scris direct aici, cantitatea ar ocoli
                    // singura autoritate care ține evidența intrărilor.
                    product.setStockQuantity(0);
                    product.setPurchasePrice(null);
                    product = productRepository.save(product);
                }
                created++;
            } else {
                stockBefore = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
                costBefore = product.getPurchasePrice();
                restocked++;
            }

            int quantity = row.stock;
            BigDecimal unitCost = row.purchase;
            units += quantity;

            int stockAfter;
            BigDecimal costAfter;

            if (dryRun) {
                // Aceleași formule ca la execuția reală, fără scriere. Dacă
                // previzualizarea ar folosi alt calcul, ar fi inutilă exact în
                // cazurile în care cele două nu coincid.
                stockAfter = stockBefore + quantity;
                costAfter = StockIntakeService.weightedAverage(stockBefore, costBefore, quantity, unitCost);
            } else {
                StockIntakeService.Result result = stockIntakeService.intake(product, quantity, unitCost);
                stockAfter = result.stockAfter();
                costAfter = result.costAfter();

                PurchaseItem item = new PurchaseItem();
                item.setProduct(product);
                item.setProductName(product.getName());
                item.setQuantity(quantity);
                item.setUnitPurchasePrice(unitCost);
                purchase.addItem(item);
            }

            lines.add(new GoodsReceiptResultDto.Line(
                    row.name, isNew, quantity, unitCost,
                    unitCost.multiply(BigDecimal.valueOf(quantity)),
                    stockBefore, stockAfter, costBefore, costAfter));
        }

        BigDecimal totalValue = lines.stream()
                .map(GoodsReceiptResultDto.Line::lineValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Long purchaseId = null;
        if (!dryRun) {
            purchase.recalculateTotal();
            purchaseId = purchaseRepository.save(purchase).getId();

            auditService.log("GOODS_RECEIPT_IMPORTED", "Purchase", purchaseId,
                    "Recepția " + series + " " + number + " de la " + supplier.getName()
                            + " · " + created + " produse noi, " + restocked + " completate"
                            + " · " + units + " buc. · " + totalValue + " RON"
                            + " · fișier: " + file.getOriginalFilename());
        }

        return new GoodsReceiptResultDto(
                dryRun, supplier.getName(), series + " " + number, purchaseId,
                parsed.totalRows(), created, restocked, units, totalValue,
                lines, parsed.warnings());
    }

    // ---- Ajutătoare ------------------------------------------------------

    /**
     * Produsul deja existent, căutat întâi după cod și apoi după denumire.
     *
     * <p>Aceeași ordine ca la importul obișnuit: codul identifică fără echivoc,
     * denumirea este a doua șansă pentru fișierele care nu îl poartă.</p>
     */
    private Product findExisting(ProductImportService.PreparedRow row) {
        if (row.sku != null) {
            Product bySku = productRepository.findFirstBySku(row.sku).orElse(null);
            if (bySku != null) {
                return bySku;
            }
        }
        return productRepository.findFirstByNameIgnoreCase(row.name).orElse(null);
    }

    private String receptionSeries(CompanySettings cs) {
        String s = cs.getReceptionSeries();
        return (s != null && !s.isBlank()) ? s.trim() : "NIR";
    }

    /**
     * Următorul număr, fără să îl consume. Folosit doar în previzualizare.
     */
    private int peekNextNumber(CompanySettings cs, String series) {
        int fromCounter = cs.getReceptionNextNumber() != null ? cs.getReceptionNextNumber() : 1;
        Integer maxUsed = purchaseRepository.maxReceptionNumber(series);
        return (maxUsed != null && maxUsed >= fromCounter) ? maxUsed + 1 : fromCounter;
    }

    /**
     * Ia următorul număr și avansează contorul.
     *
     * <p>Aceeași corecție ca la facturi: contorul din setări este sursa, dar
     * este comparat cu cel mai mare număr existent, pentru cazul în care un
     * document a fost introdus direct în bază și contorul a rămas în urmă.</p>
     */
    private int allocateNumber(CompanySettings cs, String series) {
        int next = peekNextNumber(cs, series);
        cs.setReceptionNextNumber(next + 1);
        return next;
    }

    /**
     * Amprenta conținutului fișierului.
     *
     * <p>Pe conținut, nu pe nume: un fișier redenumit este același fișier, iar
     * redenumirea este exact ce face cineva care încearcă să reîncarce o
     * livrare despre care nu mai știe sigur dacă a intrat.</p>
     */
    private static String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] out = digest.digest(file.getBytes());
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (IOException e) {
            throw new BadRequestException("Nu am putut citi fișierul: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 este obligatoriu în orice JVM; ramura există pentru compilator.
            throw new IllegalStateException("SHA-256 indisponibil", e);
        }
    }

    private static String describeRows(List<ProductImportResult.RowError> errors) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(errors.size(), 5);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append("; ");
            }
            sb.append("rândul ").append(errors.get(i).row()).append(" — ")
              .append(errors.get(i).message());
        }
        if (errors.size() > shown) {
            sb.append(" și încă ").append(errors.size() - shown).append(".");
        }
        return sb.toString();
    }

    /**
     * Primele câteva denumiri, ca mesajul de eroare să fie folositor fără să
     * devină un perete de text la un fișier cu trei sute de rânduri greșite.
     */
    private static String sample(List<String> names) {
        int shown = Math.min(names.size(), 5);
        String joined = String.join(", ", names.subList(0, shown));
        return names.size() > shown ? joined + " și încă " + (names.size() - shown) : joined;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
