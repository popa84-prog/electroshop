package com.electroshop.service;

import com.electroshop.dto.AdminProductDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Builds the stock-list export of the product catalogue.
 * <p>
 * The sheet deliberately carries only the four columns an operator needs when
 * checking merchandise against reality — name, acquisition price, selling price
 * and quantity on hand. Prices are written as real numbers (not text) with a
 * Romanian currency format, so the file can be summed and sorted in Excel
 * without any cleanup.
 */
@Service
public class ProductExportService {

    private static final String[] HEADERS = {"Produs", "Achiziție (RON)", "Preț vânzare (RON)", "Stoc"};

    /** Column widths in units of 1/256 of a character. */
    private static final int[] WIDTHS = {20000, 5000, 5500, 3000};

    /**
     * Renders the given products as an .xlsx workbook.
     *
     * @param products rows to write, already in the order they should appear
     * @return the encoded workbook
     */
    public byte[] toExcel(List<AdminProductDto> products) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Produse");

            Font boldFont = wb.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            CellStyle stockStyle = wb.createCellStyle();
            stockStyle.setAlignment(HorizontalAlignment.CENTER);

            CellStyle totalStyle = wb.createCellStyle();
            totalStyle.setFont(boldFont);

            CellStyle totalMoneyStyle = wb.createCellStyle();
            totalMoneyStyle.setFont(boldFont);
            totalMoneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            BigDecimal purchaseTotal = BigDecimal.ZERO;
            BigDecimal sellingTotal = BigDecimal.ZERO;
            long stockTotal = 0;

            int r = 1;
            for (AdminProductDto p : products) {
                Row row = sheet.createRow(r++);

                row.createCell(0).setCellValue(p.name() == null ? "" : p.name());

                Cell purchase = row.createCell(1);
                purchase.setCellStyle(moneyStyle);
                if (p.purchasePrice() != null) {
                    purchase.setCellValue(p.purchasePrice().doubleValue());
                }

                Cell price = row.createCell(2);
                price.setCellStyle(moneyStyle);
                if (p.price() != null) {
                    price.setCellValue(p.price().doubleValue());
                }

                Cell stock = row.createCell(3);
                stock.setCellStyle(stockStyle);
                int qty = p.stockQuantity() == null ? 0 : p.stockQuantity();
                stock.setCellValue(qty);

                // Totals are weighted by quantity: what the stock actually cost
                // and what it is worth at shelf price.
                BigDecimal q = BigDecimal.valueOf(qty);
                if (p.purchasePrice() != null) {
                    purchaseTotal = purchaseTotal.add(p.purchasePrice().multiply(q));
                }
                if (p.price() != null) {
                    sellingTotal = sellingTotal.add(p.price().multiply(q));
                }
                stockTotal += qty;
            }

            // Summary line: value of the stock on hand, at cost and at shelf price.
            Row totals = sheet.createRow(r + 1);
            Cell label = totals.createCell(0);
            label.setCellValue("TOTAL (valoare stoc)");
            label.setCellStyle(totalStyle);

            Cell tPurchase = totals.createCell(1);
            tPurchase.setCellValue(purchaseTotal.doubleValue());
            tPurchase.setCellStyle(totalMoneyStyle);

            Cell tSelling = totals.createCell(2);
            tSelling.setCellValue(sellingTotal.doubleValue());
            tSelling.setCellStyle(totalMoneyStyle);

            Cell tStock = totals.createCell(3);
            tStock.setCellValue(stockTotal);
            tStock.setCellStyle(totalStyle);

            for (int i = 0; i < WIDTHS.length; i++) {
                sheet.setColumnWidth(i, WIDTHS[i]);
            }
            sheet.createFreezePane(0, 1);

            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Exportul Excel a eșuat: " + e.getMessage(), e);
        }
    }

    /**
     * Same four columns as {@link #toExcel(List)}, as CSV.
     * A UTF-8 byte-order mark is prepended so Excel shows Romanian diacritics
     * correctly when the file is opened by double-click.
     */
    public byte[] toCsv(List<AdminProductDto> products) {
        StringBuilder sb = new StringBuilder();
        sb.append('﻿');
        sb.append(String.join(",", HEADERS)).append('\n');
        for (AdminProductDto p : products) {
            sb.append(csv(p.name())).append(',')
              .append(p.purchasePrice() == null ? "" : p.purchasePrice().toPlainString()).append(',')
              .append(p.price() == null ? "" : p.price().toPlainString()).append(',')
              .append(p.stockQuantity() == null ? 0 : p.stockQuantity()).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csv(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
