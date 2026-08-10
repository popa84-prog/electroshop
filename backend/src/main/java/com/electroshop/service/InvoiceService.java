package com.electroshop.service;

import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.Invoice;
import com.electroshop.model.InvoiceLine;
import com.electroshop.model.InvoiceStatus;
import com.electroshop.model.InvoiceType;
import com.electroshop.model.Order;
import com.electroshop.model.OrderStatus;
import com.electroshop.repository.InvoiceRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Tipărirea documentelor fiscale.
 *
 * <h2>Ce s-a schimbat</h2>
 *
 * <p>Versiunea anterioară construia PDF-ul din comanda vie și, în plus, aloca
 * numărul de factură chiar la prima descărcare. Ambele au dispărut.</p>
 *
 * <p><b>Datele vin exclusiv din {@link Invoice}.</b> Denumirea produsului,
 * prețul unitar, datele firmei, cele ale cumpărătorului și cota de TVA sunt
 * copiile făcute la emitere. Înainte, o redenumire de produs sau o schimbare a
 * sediului firmei modifica retroactiv facturi vechi de luni de zile, iar
 * exemplarul clientului înceta să coincidă cu al magazinului. Acum nimic din ce
 * se tipărește nu mai depinde de starea curentă a bazei.</p>
 *
 * <p><b>Descărcarea nu mai alocă numere.</b> Numerotarea aparține în întregime
 * lui {@link InvoiceIssueService}, la o cerere explicită. O cerere {@code GET}
 * nu mai modifică starea fiscală.</p>
 *
 * <h2>Stornările</h2>
 *
 * <p>Același format tipărește și documentele de tip {@link InvoiceType#STORNO},
 * cu trei diferențe: titlul, rândul care indică factura corectată și motivul, și
 * faptul că valorile sunt negative — nu ca artificiu de afișare, ci pentru că
 * așa sunt stocate, astfel încât însumarea tuturor documentelor unei comenzi să
 * dea direct soldul facturat.</p>
 *
 * <p>Textul este transliterat în ASCII, ca fonturile standard PDF să nu producă
 * pătrate goale în locul diacriticelor.</p>
 */
@Service
public class InvoiceService {

    private static final Color BRAND = new Color(37, 99, 235);
    private static final Color STORNO_COLOR = new Color(190, 24, 60);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final InvoiceRepository invoiceRepository;

    public InvoiceService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Tipărește documentul cu identificatorul dat.
     */
    @Transactional(readOnly = true)
    public InvoiceFile generate(Long invoiceId) {
        Invoice invoice = invoiceRepository.findWithLines(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        return render(invoice);
    }

    /**
     * Tipărește factura emisă pentru o comandă.
     *
     * <p>Păstrată pentru ruta veche {@code GET /admin/orders/{id}/invoice}, pe
     * care interfața o folosea deja. Diferența de comportament este esențială:
     * dacă nu există factură, metoda refuză, în loc să emită una pe tăcute. O
     * cerere de descărcare nu are voie să consume un număr fiscal.</p>
     */
    @Transactional(readOnly = true)
    public InvoiceFile generateForOrder(Long orderId) {
        List<Invoice> docs = invoiceRepository.findByOrderIdWithLines(orderId);
        Invoice invoice = docs.stream()
                .filter(i -> i.getType() == InvoiceType.INVOICE)
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Comanda #" + orderId + " nu are factură emisă. "
                                + "Emite factura întâi, apoi o poți descărca."));
        return render(invoice);
    }

    private InvoiceFile render(Invoice invoice) {
        byte[] pdf = buildPdf(invoice);
        String prefix = invoice.getType() == InvoiceType.STORNO ? "Storno" : "Factura";
        String filename = prefix + "_" + safe(invoice.getSeries()) + "_"
                + invoice.getNumber() + ".pdf";
        return new InvoiceFile(filename, pdf);
    }

    // ---------------------------------------------------------------

    private byte[] buildPdf(Invoice inv) {
      try {
        boolean storno = inv.getType() == InvoiceType.STORNO;

        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Color accent = storno ? STORNO_COLOR : BRAND;
        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, accent);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font white = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        Font stornoNote = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, STORNO_COLOR);

        // ---- Antet ----
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new int[]{6, 4});

        PdfPCell title = new PdfPCell();
        title.setBorder(0);
        title.addElement(new Paragraph(storno ? "FACTURA STORNO" : "FACTURA", h1));
        title.addElement(new Paragraph(ascii(safe(inv.getSellerName())), h2));
        header.addCell(title);

        PdfPCell meta = new PdfPCell();
        meta.setBorder(0);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pm = new Paragraph();
        pm.setAlignment(Element.ALIGN_RIGHT);
        pm.add(new Phrase("Seria " + safe(inv.getSeries()) + " Nr. " + inv.getNumber() + "\n", bold));
        pm.add(new Phrase("Data: " + inv.getIssuedAt().format(DATE_FMT) + "\n", normal));
        if (inv.getOrder() != null) {
            pm.add(new Phrase("Comanda: #" + inv.getOrder().getId() + "\n", small));
        }
        meta.addElement(pm);
        header.addCell(meta);
        doc.add(header);

        // ---- Trimiterea la documentul corectat ----
        //
        // Un storno fără referință la original este o hârtie cu sume negative pe
        // care nimeni nu o poate lega de nimic. Rândul acesta este ce face
        // documentul verificabil.
        if (storno && inv.getOriginalInvoice() != null) {
            doc.add(spacer(8));
            Paragraph ref = new Paragraph();
            ref.add(new Phrase("Storneaza factura ", stornoNote));
            ref.add(new Phrase(safe(inv.getOriginalInvoice().getSeries()) + " nr. "
                    + inv.getOriginalInvoice().getNumber(), stornoNote));
            if (inv.getOriginalInvoice().getIssuedAt() != null) {
                ref.add(new Phrase(" din " + inv.getOriginalInvoice().getIssuedAt().format(DATE_FMT),
                        stornoNote));
            }
            doc.add(ref);
            if (notBlank(inv.getCancelReason())) {
                doc.add(new Paragraph("Motiv: " + ascii(inv.getCancelReason()), small));
            }
        }

        doc.add(spacer(10));

        // ---- Părți ----
        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.setWidths(new int[]{1, 1});
        parties.addCell(partyCell("FURNIZOR", sellerLines(inv), h2, normal));
        parties.addCell(partyCell("CUMPARATOR", buyerLines(inv), h2, normal));
        doc.add(parties);

        doc.add(spacer(14));

        // ---- Poziții ----
        PdfPTable items = new PdfPTable(new float[]{0.6f, 5f, 1.2f, 1.8f, 1.8f});
        items.setWidthPercentage(100);
        addHeaderCell(items, "#", white, accent);
        addHeaderCell(items, "Produs", white, accent);
        addHeaderCell(items, "Cant.", white, accent);
        addHeaderCell(items, "Pret unitar", white, accent);
        addHeaderCell(items, "Valoare", white, accent);

        int idx = 1;
        for (InvoiceLine line : inv.getLines()) {
            addBodyCell(items, String.valueOf(idx++), normal, Element.ALIGN_CENTER);
            addBodyCell(items, ascii(safe(line.getProductName())), normal, Element.ALIGN_LEFT);
            addBodyCell(items, String.valueOf(line.getQuantity()), normal, Element.ALIGN_CENTER);
            addBodyCell(items, money(line.getUnitPrice(), inv.getCurrency()), normal, Element.ALIGN_RIGHT);
            addBodyCell(items, money(line.getLineGross(), inv.getCurrency()), normal, Element.ALIGN_RIGHT);
        }
        doc.add(items);

        doc.add(spacer(10));

        // ---- Totaluri ----
        //
        // Citite din document, nu recalculate. Sunt sumele liniilor de mai sus,
        // fiecare rotunjită la emitere, deci cine adună coloana de pe hârtie
        // obține exact cifra de aici.
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setWidths(new int[]{3, 2});
        totalRow(totals, "Valoare fara TVA", money(inv.getTotalNet(), inv.getCurrency()), normal, normal);
        totalRow(totals, "TVA (" + stripZeros(inv.getVatRate()) + "%)",
                money(inv.getTotalVat(), inv.getCurrency()), normal, normal);
        totalRow(totals, storno ? "TOTAL DE RESTITUIT" : "TOTAL DE PLATA",
                money(inv.getTotalGross(), inv.getCurrency()), bold, bold);
        doc.add(totals);

        doc.add(spacer(12));

        if (!inv.isVatPayer()) {
            doc.add(new Paragraph("Neplatitor de TVA.", small));
            doc.add(spacer(6));
        }

        // ---- Starea ----
        Paragraph status = new Paragraph();
        if (storno) {
            status.add(new Phrase("Document de stornare. ", bold));
            status.add(new Phrase("Sumele de mai sus se scad din factura corectata.", normal));
        } else {
            status.add(new Phrase("Stare plata: ", bold));
            status.add(new Phrase(paymentStatus(inv), normal));
        }
        doc.add(status);

        if (notBlank(inv.getNotes()) && !storno) {
            doc.add(spacer(8));
            doc.add(new Paragraph(ascii(inv.getNotes()), small));
        }

        doc.add(spacer(16));
        doc.add(new Paragraph(
                "Document generat electronic, valabil fara semnatura si stampila.", small));

        doc.close();
        return baos.toByteArray();
      } catch (Exception e) {
        throw new IllegalStateException("Generarea documentului PDF a esuat: " + e.getMessage(), e);
      }
    }

    // ---- conținut ----

    private String[] sellerLines(Invoice inv) {
        return new String[]{
                notBlank(inv.getSellerCui()) ? "CUI: " + ascii(inv.getSellerCui()) : null,
                notBlank(inv.getSellerRegCom()) ? "Reg. Com.: " + ascii(inv.getSellerRegCom()) : null,
                notBlank(inv.getSellerAddress()) ? ascii(inv.getSellerAddress()) : null,
                notBlank(inv.getSellerIban()) ? "IBAN: " + ascii(inv.getSellerIban()) : null,
                notBlank(inv.getSellerBank()) ? "Banca: " + ascii(inv.getSellerBank()) : null
        };
    }

    private String[] buyerLines(Invoice inv) {
        return new String[]{
                notBlank(inv.getBuyerName()) ? ascii(inv.getBuyerName()) : "Client",
                notBlank(inv.getBuyerCui()) ? "CUI: " + ascii(inv.getBuyerCui()) : null,
                notBlank(inv.getBuyerRegCom()) ? "Reg. Com.: " + ascii(inv.getBuyerRegCom()) : null,
                notBlank(inv.getBuyerEmail()) ? "Email: " + ascii(inv.getBuyerEmail()) : null,
                notBlank(inv.getBuyerAddress()) ? "Adresa: " + ascii(inv.getBuyerAddress()) : null
        };
    }

    private PdfPCell partyCell(String heading, String[] lines, Font hFont, Font font) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8);
        cell.setBorderColor(new Color(203, 213, 225));
        cell.addElement(new Paragraph(heading, hFont));
        for (String line : lines) {
            if (line != null) {
                cell.addElement(new Paragraph(line, font));
            }
        }
        return cell;
    }

    private void addHeaderCell(PdfPTable t, String text, Font font, Color background) {
        PdfPCell c = new PdfPCell(new Phrase(ascii(text), font));
        c.setBackgroundColor(background);
        c.setPadding(6);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        t.addCell(c);
    }

    private void addBodyCell(PdfPTable t, String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setPadding(5);
        c.setHorizontalAlignment(align);
        t.addCell(c);
    }

    private void totalRow(PdfPTable t, String label, String value, Font lf, Font vf) {
        PdfPCell l = new PdfPCell(new Phrase(ascii(label), lf));
        l.setBorder(0);
        l.setPadding(4);
        PdfPCell v = new PdfPCell(new Phrase(value, vf));
        v.setBorder(0);
        v.setPadding(4);
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        t.addCell(l);
        t.addCell(v);
    }

    private Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(height);
        return p;
    }

    /**
     * Starea plății, citită din comandă.
     *
     * <p>Singurul lucru de pe document care se citește din starea curentă, și pe
     * bună dreptate: dacă factura a fost achitată este un fapt al zilei de azi,
     * nu unul înghețat la emitere. Restul documentului rămâne instantaneu.</p>
     */
    private String paymentStatus(Invoice inv) {
        if (inv.getStatus() == InvoiceStatus.CANCELLED) {
            return "STORNATA INTEGRAL";
        }
        if (inv.getStatus() == InvoiceStatus.PARTIALLY_STORNOED) {
            return "STORNATA PARTIAL";
        }
        Order o = inv.getOrder();
        OrderStatus s = o == null ? null : o.getStatus();
        if (s == null) {
            return "Neplatita";
        }
        switch (s) {
            case PAID: return "PLATITA";
            case SHIPPED: return "PLATITA (expediata)";
            case DELIVERED: return "PLATITA (livrata)";
            case CANCELLED: return "ANULATA";
            case RETURNED: return "RETURNATA";
            case PENDING:
            default: return "NEPLATITA";
        }
    }

    private String money(BigDecimal v, String currency) {
        if (v == null) {
            v = BigDecimal.ZERO;
        }
        String cur = (currency == null || currency.isBlank()) ? "RON" : currency;
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + cur;
    }

    private String stripZeros(BigDecimal v) {
        if (v == null) return "0";
        return v.stripTrailingZeros().toPlainString();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /** Transliterate Romanian diacritics + strip remaining marks to keep PDF glyphs safe. */
    private static String ascii(String s) {
        if (s == null) return "";
        String r = s.replace('ș', 's').replace('Ș', 'S')  // ș Ș
                    .replace('ş', 's').replace('Ş', 'S')  // ş Ş
                    .replace('ț', 't').replace('Ț', 'T')  // ț Ț
                    .replace('ţ', 't').replace('Ţ', 'T')  // ţ Ţ
                    .replace('ă', 'a').replace('Ă', 'A')  // ă Ă
                    .replace('â', 'a').replace('Â', 'A')  // â Â
                    .replace('î', 'i').replace('Î', 'I'); // î Î
        r = Normalizer.normalize(r, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return r;
    }

    public record InvoiceFile(String filename, byte[] content) {}
}
