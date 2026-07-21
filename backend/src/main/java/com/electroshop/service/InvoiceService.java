package com.electroshop.service;

import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.CompanySettings;
import com.electroshop.model.Order;
import com.electroshop.model.OrderItem;
import com.electroshop.model.OrderStatus;
import com.electroshop.repository.OrderRepository;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Generates a PDF invoice (factura) for an order (feature #9).
 *
 * <p>The seller block is filled from {@link CompanySettings} (editable in the
 * Admin panel), the buyer block from the order's user + shipping address, and
 * the lines from the order items. VAT is derived assuming product prices are
 * VAT-inclusive: base = gross / (1 + rate), vat = gross - base.</p>
 *
 * <p>Text is transliterated to ASCII so it always renders correctly with the
 * standard PDF fonts (no missing glyphs for Romanian diacritics).</p>
 */
@Service
public class InvoiceService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Color BRAND = new Color(29, 78, 216);
    private static final Color LIGHT = new Color(241, 245, 249);

    private final OrderRepository orderRepository;
    private final CompanySettingsService companySettingsService;

    public InvoiceService(OrderRepository orderRepository,
                          CompanySettingsService companySettingsService) {
        this.orderRepository = orderRepository;
        this.companySettingsService = companySettingsService;
    }

    @Transactional
    public InvoiceFile generateForOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        CompanySettings cs = companySettingsService.getEntity();

        // Assign a stable series+number once; reuse on every later download.
        if (order.getInvoiceNumber() == null) {
            String series = (cs.getInvoiceSeries() != null && !cs.getInvoiceSeries().isBlank())
                    ? cs.getInvoiceSeries() : "ELS";
            int next = cs.getInvoiceNextNumber() != null ? cs.getInvoiceNextNumber() : 1;
            order.setInvoiceSeries(series);
            order.setInvoiceNumber(next);
            order.setInvoiceIssuedAt(LocalDate.now());
            cs.setInvoiceNextNumber(next + 1);   // managed entities → flushed on commit
        }

        byte[] pdf = buildPdf(order, cs);
        String filename = "Factura_" + safe(order.getInvoiceSeries()) + "_"
                + order.getInvoiceNumber() + ".pdf";
        return new InvoiceFile(filename, pdf);
    }

    // ---------------------------------------------------------------

    private byte[] buildPdf(Order o, CompanySettings cs) {
      try {
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font white = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

        // ---- Header: title + invoice meta ----
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new int[]{6, 4});

        PdfPCell title = new PdfPCell();
        title.setBorder(0);
        title.addElement(new Paragraph("FACTURA", h1));
        String companyName = notBlank(cs.getLegalName()) ? ascii(cs.getLegalName()) : "ElectroShop";
        title.addElement(new Paragraph(companyName, h2));
        header.addCell(title);

        PdfPCell meta = new PdfPCell();
        meta.setBorder(0);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        String issued = o.getInvoiceIssuedAt() != null ? o.getInvoiceIssuedAt().format(DATE_FMT)
                : LocalDate.now().format(DATE_FMT);
        Paragraph pm = new Paragraph();
        pm.setAlignment(Element.ALIGN_RIGHT);
        pm.add(new Phrase("Seria " + safe(o.getInvoiceSeries()) + " Nr. " + o.getInvoiceNumber() + "\n", bold));
        pm.add(new Phrase("Data: " + issued + "\n", normal));
        pm.add(new Phrase("Comanda: #" + o.getId() + "\n", small));
        meta.addElement(pm);
        header.addCell(meta);
        doc.add(header);

        doc.add(spacer(10));

        // ---- Seller + Buyer ----
        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.setWidths(new int[]{1, 1});
        parties.addCell(partyCell("FURNIZOR", sellerLines(cs), h2, normal));
        parties.addCell(partyCell("CUMPARATOR", buyerLines(o), h2, normal));
        doc.add(parties);

        doc.add(spacer(14));

        // ---- Items table ----
        PdfPTable items = new PdfPTable(new float[]{0.6f, 5f, 1.2f, 1.8f, 1.8f});
        items.setWidthPercentage(100);
        addHeaderCell(items, "#", white);
        addHeaderCell(items, "Produs", white);
        addHeaderCell(items, "Cant.", white);
        addHeaderCell(items, "Pret unitar", white);
        addHeaderCell(items, "Valoare", white);

        int idx = 1;
        for (OrderItem it : o.getItems()) {
            BigDecimal lineValue = it.getUnitPrice().multiply(BigDecimal.valueOf(it.getQuantity()));
            addBodyCell(items, String.valueOf(idx++), normal, Element.ALIGN_CENTER);
            addBodyCell(items, ascii(it.getProduct().getName()), normal, Element.ALIGN_LEFT);
            addBodyCell(items, String.valueOf(it.getQuantity()), normal, Element.ALIGN_CENTER);
            addBodyCell(items, money(it.getUnitPrice()), normal, Element.ALIGN_RIGHT);
            addBodyCell(items, money(lineValue), normal, Element.ALIGN_RIGHT);
        }
        doc.add(items);

        doc.add(spacer(10));

        // ---- Totals (assume prices are VAT-inclusive) ----
        BigDecimal gross = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
        boolean vatPayer = cs.isVatPayer() && cs.getVatRate() != null
                && cs.getVatRate().compareTo(BigDecimal.ZERO) > 0;
        BigDecimal rate = vatPayer ? cs.getVatRate() : BigDecimal.ZERO;
        BigDecimal base, vat;
        if (vatPayer) {
            BigDecimal divisor = BigDecimal.ONE.add(rate.movePointLeft(2));
            base = gross.divide(divisor, 2, RoundingMode.HALF_UP);
            vat = gross.subtract(base);
        } else {
            base = gross;
            vat = BigDecimal.ZERO;
        }

        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setWidths(new int[]{3, 2});
        totalRow(totals, "Valoare fara TVA", money(base), normal, normal);
        totalRow(totals, "TVA (" + stripZeros(rate) + "%)", money(vat), normal, normal);
        totalRow(totals, "TOTAL DE PLATA", money(gross), bold, bold);
        doc.add(totals);

        doc.add(spacer(12));

        // ---- Payment status ----
        Paragraph status = new Paragraph();
        status.add(new Phrase("Stare plata: ", bold));
        status.add(new Phrase(paymentStatus(o.getStatus()), normal));
        doc.add(status);

        // ---- Notes ----
        if (notBlank(cs.getInvoiceNotes())) {
            doc.add(spacer(8));
            doc.add(new Paragraph(ascii(cs.getInvoiceNotes()), small));
        }
        doc.add(spacer(16));
        doc.add(new Paragraph(
                "Factura generata electronic, valabila fara semnatura si stampila.", small));

        doc.close();
        return baos.toByteArray();
      } catch (Exception e) {
        throw new IllegalStateException("Generarea facturii PDF a esuat: " + e.getMessage(), e);
      }
    }

    // ---- content helpers ----

    private String[] sellerLines(CompanySettings cs) {
        return new String[]{
                notBlank(cs.getCui()) ? "CUI: " + ascii(cs.getCui()) : null,
                notBlank(cs.getRegCom()) ? "Reg. Com.: " + ascii(cs.getRegCom()) : null,
                addressLine(cs),
                notBlank(cs.getIban()) ? "IBAN: " + ascii(cs.getIban()) : null,
                notBlank(cs.getBankName()) ? "Banca: " + ascii(cs.getBankName()) : null,
                notBlank(cs.getPhone()) ? "Tel: " + ascii(cs.getPhone()) : null,
                notBlank(cs.getEmail()) ? "Email: " + ascii(cs.getEmail()) : null
        };
    }

    private String addressLine(CompanySettings cs) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, cs.getAddress());
        appendPart(sb, cs.getCity());
        appendPart(sb, cs.getCounty());
        appendPart(sb, cs.getCountry());
        return sb.length() == 0 ? null : ascii(sb.toString());
    }

    private void appendPart(StringBuilder sb, String part) {
        if (notBlank(part)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(part.trim());
        }
    }

    private String[] buyerLines(Order o) {
        String name = o.getUser() != null ? o.getUser().getFullName() : null;
        String email = o.getUser() != null ? o.getUser().getEmail() : null;
        return new String[]{
                notBlank(name) ? ascii(name) : "Client",
                notBlank(email) ? "Email: " + ascii(email) : null,
                notBlank(o.getShippingAddress()) ? "Adresa: " + ascii(o.getShippingAddress()) : null
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

    private void addHeaderCell(PdfPTable t, String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(ascii(text), font));
        c.setBackgroundColor(BRAND);
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

    private String paymentStatus(OrderStatus s) {
        if (s == null) return "Neplatita";
        switch (s) {
            case PAID: return "PLATITA";
            case SHIPPED: return "PLATITA (expediata)";
            case DELIVERED: return "PLATITA (livrata)";
            case CANCELLED: return "ANULATA";
            case PENDING:
            default: return "NEPLATITA";
        }
    }

    private String money(BigDecimal v) {
        if (v == null) v = BigDecimal.ZERO;
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString() + " RON";
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
