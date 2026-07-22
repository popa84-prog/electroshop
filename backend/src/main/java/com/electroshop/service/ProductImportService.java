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

    public ProductImportService(ProductRepository productRepository, ProductCategorizer categorizer) {
        this.productRepository = productRepository;
        this.categorizer = categorizer;
    }

    @Transactional
    public ProductImportResult importFromExcel(MultipartFile file, boolean dryRun) {
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

                // Auto-fill category / subcategory from the product name when the
                // Excel leaves them blank (feature #3). Keeps any value provided.
                if (category.isBlank() || subcategory.isBlank()) {
                    ProductCategorizer.Categorization auto = categorizer.categorize(name);
                    if (category.isBlank()) category = auto.category();
                    if (subcategory.isBlank()) subcategory = auto.subcategory();
                    warnings.add("Rând " + humanRow + " (" + name
                            + "): categorie/subcategorie completate automat → "
                            + category + " / " + subcategory + ".");
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

        int created = 0;
        int updated = 0;
        if (!dryRun) {
            for (PreparedRow pr : prepared) {
                Product p = null;
                if (pr.sku != null) {
                    p = productRepository.findFirstBySku(pr.sku).orElse(null);
                }
                if (p == null) {
                    p = productRepository.findFirstByNameIgnoreCase(pr.name).orElse(null);
                }
                boolean isNew = (p == null);
                if (isNew) p = new Product();
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
                if (isNew) created++;
                else updated++;
            }
        }

        return new ProductImportResult(dryRun, totalRows, prepared.size(), created, updated, errors, warnings);
    }

    // ---------------------------------------------------------------- helpers

    private static class PreparedRow {
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
