package com.electroshop.service;

import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.CompanySettings;
import com.electroshop.model.Purchase;
import com.electroshop.model.PurchaseItem;
import com.electroshop.repository.PurchaseRepository;
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

/**
 * Tipărirea notei de intrare-recepție.
 *
 * <h2>De ce acest document și nu o „factură de intrare"</h2>
 *
 * <p>Factura de achiziție o emite furnizorul, cu seria și numerotarea lui.
 * Magazinul nu are cum să o genereze: ar însemna să fabrice documentul altei
 * firme. Ce emite magazinul legitim, la primirea mărfii, este nota de
 * intrare-recepție — document intern care atestă ce a intrat, în ce cantitate
 * și la ce valoare, și care se atașează la factura primită.</p>
 *
 * <p>Din acest motiv documentul de aici poartă două numere care nu trebuie
 * confundate: al lui, din seria proprie a magazinului, și cel al facturii
 * furnizorului, tipărit ca referință.</p>
 *
 * <h2>Instantaneu, ca și facturile</h2>
 *
 * <p>Denumirea furnizorului și codul lui fiscal se citesc din
 * {@link Purchase}, unde au fost copiate la recepție, nu din rândul viu din
 * {@code suppliers}. Un furnizor redenumit peste un an nu are voie să schimbe
 * conținutul unui document deja emis. La fel, denumirile produselor vin din
 * {@link PurchaseItem}, care le păstra deja.</p>
 */
@Service
public class ReceptionNotePdfService {

    private static final Color ACCENT = new Color(13, 148, 136);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final PurchaseRepository purchaseRepository;
    private final CompanySettingsService companySettingsService;

    public ReceptionNotePdfService(PurchaseRepository purchaseRepository,
                                   CompanySettingsService companySettingsService) {
        this.purchaseRepository = purchaseRepository;
        this.companySettingsService = companySettingsService;
    }

    /**
     * Generează NIR-ul unei recepții.
     *
     * @throws BadRequestException dacă achiziția nu are număr de recepție, adică
     *                             a fost introdusă manual, înainte ca NIR-urile
     *                             să existe sau fără trecere prin import
     */
    @Transactional(readOnly = true)
    public ReceptionFile generate(Long purchaseId) {
        Purchase purchase = purchaseRepository.findById(purchaseId)
                .orElseThrow(() -> new ResourceNotFoundException("Purchase", purchaseId));

        if (purchase.getReceptionNumber() == null) {
            throw new BadRequestException(
                    "Achiziția #" + purchaseId + " nu are număr de recepție, deci nu are NIR. "
                            + "Numerele de recepție se alocă la importul unei intrări de marfă.");
        }

        byte[] pdf = buildPdf(purchase, companySettingsService.getEntity());
        String filename = "NIR_" + safe(purchase.getReceptionSeries()) + "_"
                + purchase.getReceptionNumber() + ".pdf";
        return new ReceptionFile(filename, pdf);
    }

    // ---------------------------------------------------------------

    private byte[] buildPdf(Purchase p, CompanySettings cs) {
      try {
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, baos);
        doc.open();

        Font h1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, ACCENT);
        Font h2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
        Font white = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);

        // ---- Antet ----
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new int[]{6, 4});

        PdfPCell title = new PdfPCell();
        title.setBorder(0);
        title.addElement(new Paragraph("NOTA DE INTRARE-RECEPTIE", h1));
        title.addElement(new Paragraph(
                notBlank(cs.getLegalName()) ? ascii(cs.getLegalName()) : "ElectroShop", h2));
        header.addCell(title);

        PdfPCell meta = new PdfPCell();
        meta.setBorder(0);
        meta.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph pm = new Paragraph();
        pm.setAlignment(Element.ALIGN_RIGHT);
        pm.add(new Phrase("Seria " + safe(p.getReceptionSeries())
                + " Nr. " + p.getReceptionNumber() + "\n", bold));
        if (p.getReceptionIssuedAt() != null) {
            pm.add(new Phrase("Data receptiei: " + p.getReceptionIssuedAt().format(DATE_FMT) + "\n", normal));
        }
        meta.addElement(pm);
        header.addCell(meta);
        doc.add(header);

        doc.add(spacer(10));

        // ---- Furnizorul și documentul lui ----
        //
        // Cele două numere stau unul lângă altul tocmai ca să nu fie confundate:
        // sus este numărul magazinului, aici este al furnizorului.
        PdfPTable parties = new PdfPTable(2);
        parties.setWidthPercentage(100);
        parties.setWidths(new int[]{1, 1});

        parties.addCell(block("FURNIZOR", new String[]{
                notBlank(p.getSupplierName()) ? ascii(p.getSupplierName()) : "-",
                notBlank(p.getSupplierTaxId()) ? "CUI: " + ascii(p.getSupplierTaxId()) : null
        }, h2, normal));

        parties.addCell(block("DOCUMENT FURNIZOR", new String[]{
                notBlank(p.getInvoiceNumber()) ? "Factura nr. " + ascii(p.getInvoiceNumber())
                        : "Fara numar de factura",
                p.getPurchaseDate() != null ? "Data: " + p.getPurchaseDate().format(DATE_FMT) : null,
                notBlank(p.getSourceFileName()) ? "Fisier: " + ascii(p.getSourceFileName()) : null
        }, h2, normal));

        doc.add(parties);
        doc.add(spacer(14));

        // ---- Pozițiile ----
        PdfPTable items = new PdfPTable(new float[]{0.6f, 5f, 1.2f, 1.8f, 1.8f});
        items.setWidthPercentage(100);
        addHeaderCell(items, "#", white);
        addHeaderCell(items, "Produs", white);
        addHeaderCell(items, "Cant.", white);
        addHeaderCell(items, "Pret achizitie", white);
        addHeaderCell(items, "Valoare", white);

        int idx = 1;
        int totalUnits = 0;
        for (PurchaseItem item : p.getItems()) {
            int qty = item.getQuantity() == null ? 0 : item.getQuantity();
            totalUnits += qty;
            addBodyCell(items, String.valueOf(idx++), normal, Element.ALIGN_CENTER);
            addBodyCell(items, ascii(safe(item.getProductName())), normal, Element.ALIGN_LEFT);
            addBodyCell(items, String.valueOf(qty), normal, Element.ALIGN_CENTER);
            addBodyCell(items, money(item.getUnitPurchasePrice()), normal, Element.ALIGN_RIGHT);
            addBodyCell(items, money(item.getSubtotal()), normal, Element.ALIGN_RIGHT);
        }
        doc.add(items);
        doc.add(spacer(10));

        // ---- Totaluri ----
        PdfPTable totals = new PdfPTable(2);
        totals.setWidthPercentage(45);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setWidths(new int[]{3, 2});
        totalRow(totals, "Pozitii", String.valueOf(p.getItems().size()), normal, normal);
        totalRow(totals, "Total bucati", String.valueOf(totalUnits), normal, normal);
        totalRow(totals, "VALOARE RECEPTIE", money(p.getTotalAmount()), bold, bold);
        doc.add(totals);

        doc.add(spacer(12));
        if (notBlank(p.getNotes())) {
            doc.add(new Paragraph(ascii(p.getNotes()), small));
            doc.add(spacer(8));
        }

        // ---- Semnături ----
        //
        // NIR-ul este documentul care atestă că marfa a fost primită și
        // verificată. Fără spațiul pentru cine a predat și cine a primit, nu
        // atestă nimic — este doar o listă tipărită.
        PdfPTable signatures = new PdfPTable(2);
        signatures.setWidthPercentage(100);
        signatures.setWidths(new int[]{1, 1});
        signatures.addCell(signatureCell("Predat (furnizor)", normal));
        signatures.addCell(signatureCell("Primit (gestionar)", normal));
        doc.add(spacer(16));
        doc.add(signatures);

        doc.add(spacer(14));
        doc.add(new Paragraph(
                "Document intern generat electronic. Se ataseaza la factura furnizorului.", small));

        doc.close();
        return baos.toByteArray();
      } catch (Exception e) {
        throw new IllegalStateException("Generarea NIR-ului a esuat: " + e.getMessage(), e);
      }
    }

    // ---- ajutătoare de format ----

    private PdfPCell block(String heading, String[] lines, Font hFont, Font font) {
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

    private PdfPCell signatureCell(String label, Font font) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(10);
        cell.setBorder(0);
        cell.addElement(new Paragraph(label, font));
        cell.addElement(new Paragraph(" ", font));
        cell.addElement(new Paragraph("______________________", font));
        return cell;
    }

    private void addHeaderCell(PdfPTable t, String text, Font font) {
        PdfPCell c = new PdfPCell(new Phrase(ascii(text), font));
        c.setBackgroundColor(ACCENT);
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

    private String money(BigDecimal v) {
        if (v == null) {
            v = BigDecimal.ZERO;
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString() + " RON";
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /** Transliterate Romanian diacritics so the standard PDF fonts never drop a glyph. */
    private static String ascii(String s) {
        if (s == null) return "";
        String r = s.replace('ș', 's').replace('Ș', 'S')
                    .replace('ş', 's').replace('Ş', 'S')
                    .replace('ț', 't').replace('Ț', 'T')
                    .replace('ţ', 't').replace('Ţ', 'T')
                    .replace('ă', 'a').replace('Ă', 'A')
                    .replace('â', 'a').replace('Â', 'A')
                    .replace('î', 'i').replace('Î', 'I');
        r = Normalizer.normalize(r, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return r;
    }

    public record ReceptionFile(String filename, byte[] content) {}
}
