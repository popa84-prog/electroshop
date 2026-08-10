package com.electroshop.service;

import com.electroshop.dto.ProductImportResult;
import com.electroshop.dto.ProductImportResult.RowError;
import com.electroshop.exception.BadRequestException;
import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an .xlsx product-stock file, validates every row against the required
 * fields, and (unless it is a dry run) creates or updates the products.
 * Admin-only acquisition figures are stored but never exposed publicly.
 */
@Service
public class ProductImportService {

    private enum Field {
        NAME, CATEGORY, SUBCATEGORY, BRAND, STOCK,
        PURCHASE_PRICE, PA_TOTAL, SELL_PRICE, PV_TOTAL, DESCRIPTION, SKU
    }

    private final ProductRepository productRepository;
    private final ProductCategorizer categorizer;
    private final ProductBrandResolver brandResolver;

    public ProductImportService(ProductRepository productRepository, ProductCategorizer categorizer,
                                ProductBrandResolver brandResolver) {
        this.productRepository = productRepository;
        this.categorizer = categorizer;
        this.brandResolver = brandResolver;
    }

    @Transactional
    public ProductImportResult importFromExcel(MultipartFile file, boolean dryRun) {
        return importFromExcel(file, dryRun, false);
    }

    /**
     * @param restock when true, runs in "intrare marfă" (stock intake) mode:
     *   for products that already exist, the imported quantity is ADDED to the
     *   current stock and the acquisition price is recomputed as the
     *   quantity-weighted average (CMP). The selling price, category and other
     *   fields of existing products are left untouched. Brand-new products are
     *   still created in full. When false, existing products are overwritten
     *   with the Excel values (classic import).
     */
    @Transactional
    public ProductImportResult importFromExcel(MultipartFile file, boolean dryRun, boolean restock) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Fișierul este gol.");
        }

        // Parsarea este acum o metoda separata, pentru ca receptia de marfa
        // citeste exact acelasi fisier cu exact aceleasi reguli. O a doua
        // implementare a citirii ar diverge la prima coloana adaugata, iar
        // diferenta s-ar manifesta ca un import care accepta un fisier pe care
        // celalalt il refuza.
        ParsedSheet parsed = parse(file);
        List<RowError> errors = parsed.errors();
        List<String> warnings = parsed.warnings();
        List<PreparedRow> prepared = parsed.rows();
        int totalRows = parsed.totalRows();

        int created = 0;
        int updated = 0;
        int restocked = 0;
        for (PreparedRow pr : prepared) {
            Product p = null;
            if (pr.sku != null) {
                p = productRepository.findFirstBySku(pr.sku).orElse(null);
            }
            if (p == null) {
                p = productRepository.findFirstByNameIgnoreCase(pr.name).orElse(null);
            }
            boolean isNew = (p == null);

            if (isNew) {
                // Brand-new product — created in full in every mode.
                if (!dryRun) {
                    Product np = new Product();
                    np.setName(pr.name);
                    np.setCategory(pr.category);
                    np.setSubcategory(pr.subcategory);
                    np.setBrand(pr.brand);
                    np.setDescription(pr.description);
                    np.setSku(pr.sku);
                    np.setStockQuantity(pr.stock);
                    np.setPurchasePrice(pr.purchase);
                    np.setPrice(pr.sell);
                    productRepository.save(np);
                }
                created++;
            } else if (restock) {
                // Intrare marfă: adaugă la stoc + medie ponderată a prețului de achiziție.
                int existingStock = p.getStockQuantity() != null ? p.getStockQuantity() : 0;
                int incoming = pr.stock != null ? pr.stock : 0;
                int totalStock = existingStock + incoming;
                BigDecimal newPurchase = p.getPurchasePrice();
                if (pr.purchase != null) {
                    BigDecimal existingPurchase = p.getPurchasePrice();
                    if (existingPurchase == null || existingStock <= 0) {
                        // Nimic de mediat — folosește prețul nou.
                        newPurchase = pr.purchase;
                    } else if (incoming > 0) {
                        newPurchase = existingPurchase.multiply(BigDecimal.valueOf(existingStock))
                                .add(pr.purchase.multiply(BigDecimal.valueOf(incoming)))
                                .divide(BigDecimal.valueOf(totalStock), 2, RoundingMode.HALF_UP);
                    }
                }
                if (!dryRun) {
                    p.setStockQuantity(totalStock);
                    p.setPurchasePrice(newPurchase);
                    // Preț de vânzare, categorie, brand etc. rămân neschimbate.
                    productRepository.save(p);
                }
                restocked++;
                updated++;
            } else {
                // Import normal: suprascrie câmpurile existente cu valorile din Excel.
                if (!dryRun) {
                    p.setName(pr.name);
                    p.setCategory(pr.category);
                    p.setSubcategory(pr.subcategory);
                    p.setBrand(pr.brand);
                    p.setDescription(pr.description);
                    p.setSku(pr.sku);
                    p.setStockQuantity(pr.stock);
                    p.setPurchasePrice(pr.purchase);
                    p.setPrice(pr.sell);
                    productRepository.save(p);
                }
                updated++;
            }
        }
        if (restock) {
            warnings.add(0, "Mod intrare marfă: " + restocked + " produse existente au primit cantitatea adăugată la stoc și prețul de achiziție recalculat ca medie ponderată; "
                    + created + " produse noi au fost adăugate. Prețul de vânzare și categoriile produselor existente nu au fost modificate.");
        }

        return new ProductImportResult(dryRun, totalRows, prepared.size(), created, updated, errors, warnings);
    }

    /**
     * SAFE, surgical sync: reads the same .xlsx and updates ONLY the acquisition
     * price (purchase_price) of products that already exist, matched by name.
     * It never creates products, never deletes, and never touches stock, selling
     * price, category or any other field. Products in the file that are not found
     * in the shop are reported (as warnings) and left untouched — so deleted
     * products are not resurrected. Rows without a purchase price are skipped.
     */
    @Transactional
    public ProductImportResult syncPurchasePrices(MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Fișierul este gol.");
        }
        List<RowError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> missingNames = new ArrayList<>();
        int totalRows = 0;
        int withPrice = 0;
        int updated = 0;
        int notFound = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new BadRequestException("Fișierul nu conține nicio foaie de calcul.");
            }
            int headerIdx = sheet.getFirstRowNum();
            Row header = sheet.getRow(headerIdx);
            if (header == null) {
                throw new BadRequestException("Lipsește rândul de antet (prima linie).");
            }
            Map<Field, Integer> col = mapColumns(header);
            if (!col.containsKey(Field.NAME)) {
                throw new BadRequestException("Lipsește coloana 'Nume produs'.");
            }
            if (!col.containsKey(Field.PURCHASE_PRICE)) {
                throw new BadRequestException("Lipsește coloana cu prețul de achiziție.");
            }

            for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row)) continue;
                totalRows++;
                int humanRow = r + 1;

                String name = str(row, col.get(Field.NAME));
                if (name.isBlank()) continue;

                BigDecimal purchase;
                try {
                    purchase = decVal(row, col.get(Field.PURCHASE_PRICE));
                } catch (Exception e) {
                    errors.add(new RowError(humanRow, "'Preț achiziție' nu este un număr valid"));
                    continue;
                }
                if (purchase == null) continue;           // nothing to sync for this row
                if (purchase.signum() < 0) {
                    errors.add(new RowError(humanRow, "'Preț achiziție' nu poate fi negativ"));
                    continue;
                }
                withPrice++;

                Product p = productRepository.findFirstByNameIgnoreCase(name).orElse(null);
                if (p == null) {
                    notFound++;
                    missingNames.add(name);
                    continue;
                }
                if (!dryRun) {
                    p.setPurchasePrice(purchase);
                    productRepository.save(p);
                }
                updated++;
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Nu am putut citi fișierul Excel: " + e.getMessage());
        } catch (Exception e) {
            throw new BadRequestException("Fișier Excel invalid sau format neacceptat (folosește .xlsx).");
        }

        if (notFound > 0) {
            warnings.add(notFound + " produse din Excel nu au fost găsite în magazin — au fost ignorate (nimic nu s-a creat sau șters).");
            int shown = 0;
            for (String n : missingNames) {
                if (shown >= 25) {
                    warnings.add("... și încă " + (notFound - 25) + " neregăsite.");
                    break;
                }
                warnings.add("Negăsit: " + n);
                shown++;
            }
        }
        // updatedCount = câte produse au primit pretul de achizitie (matched).
        return new ProductImportResult(dryRun, totalRows, withPrice, 0, updated, errors, warnings);
    }

    // ---------------------------------------------------------------- helpers


    /**
     * Citeste si valideaza fisierul, fara sa scrie nimic.
     *
     * <p>Extrasa din {@link #importFromExcel} ca sa poata fi folosita si de
     * receptia de marfa. Cele doua trebuie sa accepte exact aceleasi fisiere:
     * daca ar avea fiecare propria citire, ar diverge la prima coloana
     * adaugata, iar utilizatorul ar descoperi ca acelasi fisier trece pe o cale
     * si pica pe cealalta.</p>
     */
    public ParsedSheet parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Fișierul este gol.");
        }

        List<RowError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<PreparedRow> prepared = new ArrayList<>();
        int totalRows = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new BadRequestException("Fișierul nu conține nicio foaie de calcul.");
            }

            int headerIdx = sheet.getFirstRowNum();
            Row header = sheet.getRow(headerIdx);
            if (header == null) {
                throw new BadRequestException("Lipsește rândul de antet (prima linie).");
            }

            Map<Field, Integer> col = mapColumns(header);

            List<String> missing = new ArrayList<>();
            if (!col.containsKey(Field.NAME)) missing.add("Nume produs");
            if (!col.containsKey(Field.STOCK)) missing.add("Cantitate în stoc");
            if (!col.containsKey(Field.SELL_PRICE)) missing.add("Preț vânzare unitar");
            if (!missing.isEmpty()) {
                throw new BadRequestException("Lipsesc coloanele obligatorii: "
                        + String.join(", ", missing) + ". Folosește șablonul furnizat.");
            }

            for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowBlank(row)) continue;
                totalRows++;
                int humanRow = r + 1;

                String name = str(row, col.get(Field.NAME));
                String category = str(row, col.get(Field.CATEGORY));
                String subcategory = str(row, col.get(Field.SUBCATEGORY));
                String brand = str(row, col.get(Field.BRAND));
                String description = str(row, col.get(Field.DESCRIPTION));
                String sku = str(row, col.get(Field.SKU));

                List<String> rowErrs = new ArrayList<>();
                // Only name, stock and selling price are truly required. Category,
                // subcategory and purchase price are optional (warned, not rejected).
                if (name.isBlank()) rowErrs.add("lipsește 'Nume produs'");

                Integer stock = null;
                try {
                    stock = intVal(row, col.get(Field.STOCK));
                } catch (Exception e) {
                    rowErrs.add("'Cantitate în stoc' nu este un număr întreg valid");
                }
                if (stock == null) rowErrs.add("lipsește 'Cantitate în stoc'");
                else if (stock < 0) rowErrs.add("'Cantitate în stoc' nu poate fi negativă");

                BigDecimal purchase = null;
                try {
                    purchase = decVal(row, col.get(Field.PURCHASE_PRICE));
                } catch (Exception e) {
                    rowErrs.add("'Preț achiziție' nu este un număr valid");
                }
                if (purchase != null && purchase.signum() < 0) {
                    rowErrs.add("'Preț achiziție' nu poate fi negativ");
                }

                BigDecimal sell = null;
                try {
                    sell = decVal(row, col.get(Field.SELL_PRICE));
                } catch (Exception e) {
                    rowErrs.add("'Preț vânzare' nu este un număr valid");
                }
                if (sell == null) rowErrs.add("lipsește 'Preț vânzare unitar'");
                else if (sell.signum() <= 0) rowErrs.add("'Preț vânzare' trebuie să fie > 0");

                if (!rowErrs.isEmpty()) {
                    errors.add(new RowError(humanRow, String.join("; ", rowErrs)));
                    continue;
                }

                // Auto-fill category / subcategory from the product name whenever the
                // Excel does not actually carry one (feature #3).
                //
                // A blank cell is not the only way a supplier sheet expresses "no
                // category". Real imports arrive with a numeric 0 (an empty NUMERIC cell
                // read back as the string "0"), with dashes, with "N/A", and with the
                // product *condition* typed into the category column ("Folosit",
                // "Resigilat"). The previous isBlank() test accepted every one of those
                // as a genuine category and stored it verbatim, which is how the catalog
                // ended up with a category literally named "0" while the subcategory next
                // to it had been auto-filled correctly. ProductCategorizer#isPlaceholder
                // recognises the whole family, so junk is now replaced instead of saved.
                boolean categoryMissing = ProductCategorizer.isPlaceholder(category);
                boolean subcategoryMissing = ProductCategorizer.isPlaceholder(subcategory);
                if (categoryMissing || subcategoryMissing) {
                    ProductCategorizer.Categorization auto = categorizer.categorize(name);
                    if (categoryMissing) category = auto.category();
                    if (subcategoryMissing) subcategory = auto.subcategory();
                    warnings.add("Rând " + humanRow + " (" + name
                            + "): categorie/subcategorie completate automat → "
                            + category + " / " + subcategory + ".");
                }

                // The two columns must also agree with each other. A sheet that names a
                // known subcategory under the wrong parent ("Casti" / "Casti",
                // "Stocare & Memorie" under "Stocare & Memorie") is repaired to the
                // canonical pair declared by the rule table, and both values are snapped
                // to their canonical spelling so the storefront facets do not split into
                // near-duplicate entries.
                String canonicalSub = categorizer.canonicalSubcategory(subcategory);
                String owner = categorizer.canonicalCategoryFor(canonicalSub);
                if (owner != null) {
                    if (!owner.equals(category)) {
                        warnings.add("Rând " + humanRow + " (" + name
                                + "): categoria „" + category + "” nu corespunde subcategoriei „"
                                + canonicalSub + "” → corectată în „" + owner + "”.");
                    }
                    category = owner;
                    subcategory = canonicalSub;
                } else {
                    // Subcategory is outside the known taxonomy — a custom value the
                    // shop owner typed deliberately. It is kept exactly as written; only
                    // the parent category is snapped to canonical spelling when it is a
                    // known one, and left untouched otherwise.
                    String canonicalCat = categorizer.canonicalCategory(category);
                    if (canonicalCat != null) {
                        category = canonicalCat;
                    }
                }

                // Auto-fill / repair the brand column from the product name.
                //
                // The same family of junk that reaches the category column reaches this
                // one: a numeric 0, a dash, "N/A", a condition word. On top of that, the
                // supplier sheets carry brands their own tooling extracted by substring
                // matching, which is how a Behringer mixer arrived labelled "Ring" and an
                // Ecowitt weather station labelled "HP" (from the model number HP2564).
                //
                // A value the operator typed deliberately is never overruled here: the
                // brand is replaced only when it is unusable, or when it is demonstrably
                // not this product's own brand — it does not appear in the name as a
                // whole word, or it appears there only as the device the product is
                // compatible with ("pentru Apple Watch"). A brand the resolver has never
                // heard of survives untouched as long as the name contains it, because an
                // unknown maker is a gap in the table, not an error in the sheet.
                if (ProductCategorizer.isPlaceholder(brand)) {
                    String derived = brandResolver.resolve(name);
                    if (derived != null) {
                        brand = derived;
                        warnings.add("Rând " + humanRow + " (" + name
                                + "): marcă completată automat → " + derived + ".");
                    } else {
                        // Nothing recognised. An unusable value is dropped rather than
                        // stored, so the storefront filter never grows a brand named "0".
                        brand = "";
                    }
                } else if (!brandResolver.mentionsAsOwnBrand(name, brand)) {
                    String derived = brandResolver.resolve(name);
                    if (derived != null) {
                        warnings.add("Rând " + humanRow + " (" + name + "): marca „" + brand
                                + "” nu identifică producătorul → corectată în „" + derived + "”.");
                        brand = derived;
                    } else {
                        warnings.add("Rând " + humanRow + " (" + name + "): marca „" + brand
                                + "” nu apare în denumire → eliminată.");
                        brand = "";
                    }
                } else {
                    // Correct brand: only its spelling is snapped to the canonical form,
                    // so "LOGITECH" and "Logitech" stop being two storefront filters.
                    String canonical = brandResolver.canonicalise(brand);
                    if (canonical != null) {
                        brand = canonical;
                    }
                }

                if (purchase == null) {
                    warnings.add("Rând " + humanRow + " (" + name
                            + "): fără preț de achiziție (poți completa mai târziu).");
                } else if (sell.compareTo(purchase) < 0) {
                    warnings.add("Rând " + humanRow + " (" + name
                            + "): preț de vânzare mai mic decât prețul de achiziție.");
                }
                checkTotal(row, col.get(Field.PA_TOTAL), purchase, stock, humanRow, "PA Total", warnings);
                checkTotal(row, col.get(Field.PV_TOTAL), sell, stock, humanRow, "PV Total", warnings);

                PreparedRow pr = new PreparedRow();
                pr.name = name;
                pr.category = category;
                pr.subcategory = subcategory;
                pr.brand = brand.isBlank() ? null : brand;
                pr.description = description.isBlank() ? null : description;
                pr.sku = sku.isBlank() ? null : sku;
                pr.stock = stock;
                pr.purchase = purchase;
                pr.sell = sell;
                prepared.add(pr);
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (IOException e) {
            throw new BadRequestException("Nu am putut citi fișierul Excel: " + e.getMessage());
        } catch (Exception e) {
            throw new BadRequestException("Fișier Excel invalid sau format neacceptat (folosește .xlsx).");
        }

        return new ParsedSheet(prepared, errors, warnings, totalRows);
    }

    /**
     * Rezultatul citirii: randurile valide, erorile pe rand, avertismentele si
     * cate randuri neblanke a avut fisierul.
     */
    public record ParsedSheet(List<PreparedRow> rows, List<RowError> errors,
                             List<String> warnings, int totalRows) {
    }

    public static class PreparedRow {
        String name;
        String category;
        String subcategory;
        String brand;
        String description;
        String sku;
        Integer stock;
        BigDecimal purchase;
        BigDecimal sell;
    }

    private Map<Field, Integer> mapColumns(Row header) {
        Map<Field, Integer> map = new EnumMap<>(Field.class);
        short last = header.getLastCellNum();
        for (int c = 0; c < last; c++) {
            Cell cell = header.getCell(c);
            if (cell == null) continue;
            String norm = normalize(cellRaw(cell));
            if (norm.isEmpty()) continue;
            Field f = matchField(norm);
            if (f != null && !map.containsKey(f)) {
                map.put(f, c);
            }
        }
        return map;
    }

    /** Match a normalized (diacritic-free, alnum-only, lowercase) header to a field. */
    private Field matchField(String n) {
        if (n.contains("subcategor")) return Field.SUBCATEGORY;
        if (n.contains("patotal") || (n.contains("achizit") && n.contains("total"))) return Field.PA_TOTAL;
        if (n.contains("pvtotal") || (n.contains("vanz") && n.contains("total"))) return Field.PV_TOTAL;
        if (n.contains("achizit")) return Field.PURCHASE_PRICE;
        if (n.contains("vanzare") || n.contains("vanz")) return Field.SELL_PRICE;
        if (n.contains("categor")) return Field.CATEGORY;
        if (n.contains("numeprodus") || n.equals("nume") || n.contains("denumire")) return Field.NAME;
        if (n.contains("cantitate") || n.contains("stoc")) return Field.STOCK;
        if (n.contains("brand") || n.contains("marca")) return Field.BRAND;
        if (n.contains("descri")) return Field.DESCRIPTION;
        if (n.contains("sku") || n.contains("cod")) return Field.SKU;
        if (n.contains("produs")) return Field.NAME;
        return null;
    }

    private String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private boolean isRowBlank(Row row) {
        short first = row.getFirstCellNum();
        if (first < 0) return true;
        short last = row.getLastCellNum();
        for (int c = first; c < last; c++) {
            Cell cell = row.getCell(c);
            if (cell != null && !cellRaw(cell).trim().isEmpty()) return false;
        }
        return true;
    }

    private String cellRaw(Cell cell) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return String.valueOf(cell.getDateCellValue());
                }
                double d = cell.getNumericCellValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return String.valueOf((long) d);
                }
                return String.valueOf(d);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    try {
                        double v = cell.getNumericCellValue();
                        if (v == Math.floor(v)) return String.valueOf((long) v);
                        return String.valueOf(v);
                    } catch (Exception ex) {
                        return "";
                    }
                }
            default:
                return "";
        }
    }

    private String str(Row row, Integer col) {
        if (col == null) return "";
        return cellRaw(row.getCell(col)).trim();
    }

    private Integer intVal(Row row, Integer col) {
        if (col == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) Math.round(cell.getNumericCellValue());
        }
        String s = cellRaw(cell).trim();
        if (s.isEmpty()) return null;
        s = s.replace(" ", "").replace(",", ".");
        return (int) Math.round(Double.parseDouble(s));
    }

    private BigDecimal decVal(Row row, Integer col) {
        if (col == null) return null;
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String s = cellRaw(cell).trim();
        if (s.isEmpty()) return null;
        s = s.replace(" ", "").replace("RON", "").replace("ron", "").replace("lei", "");
        if (s.contains(",") && s.contains(".")) {
            s = s.replace(".", "").replace(",", ".");
        } else {
            s = s.replace(",", ".");
        }
        return new BigDecimal(s);
    }

    private void checkTotal(Row row, Integer col, BigDecimal unit, Integer qty,
                            int humanRow, String label, List<String> warnings) {
        if (col == null || unit == null || qty == null) return;
        BigDecimal provided;
        try {
            provided = decVal(row, col);
        } catch (Exception e) {
            return;
        }
        if (provided == null) return;
        BigDecimal expected = unit.multiply(BigDecimal.valueOf(qty));
        if (provided.subtract(expected).abs().compareTo(new BigDecimal("0.5")) > 0) {
            warnings.add("Rând " + humanRow + ": " + label + " (" + provided.toPlainString()
                    + ") nu corespunde cu preț × cantitate (" + expected.toPlainString() + ").");
        }
    }
}
