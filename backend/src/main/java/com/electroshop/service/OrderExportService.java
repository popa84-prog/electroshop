package com.electroshop.service;

import com.electroshop.model.Order;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds Excel (.xlsx) and CSV exports of orders for accounting.
 */
@Service
public class OrderExportService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] HEADERS =
            {"ID", "Data", "Client", "Email", "Status", "Nr. produse", "Total (RON)", "Adresa livrare"};

    public byte[] toExcel(List<Order> orders) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Comenzi");

            CellStyle headerStyle = wb.createCellStyle();
            Font bold = wb.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);

            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(HEADERS[i]);
                c.setCellStyle(headerStyle);
            }

            int r = 1;
            for (Order o : orders) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(o.getId());
                row.createCell(1).setCellValue(o.getCreatedAt() != null ? o.getCreatedAt().format(FMT) : "");
                row.createCell(2).setCellValue(o.getUser() != null ? nz(o.getUser().getFullName()) : "");
                row.createCell(3).setCellValue(o.getUser() != null ? nz(o.getUser().getEmail()) : "");
                row.createCell(4).setCellValue(o.getStatus() != null ? o.getStatus().name() : "");
                row.createCell(5).setCellValue(o.getItems() != null ? o.getItems().size() : 0);
                row.createCell(6).setCellValue(o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0d);
                row.createCell(7).setCellValue(nz(o.getShippingAddress()));
            }

            int[] widths = {2000, 5000, 6000, 8000, 4000, 3000, 4000, 12000};
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.setColumnWidth(i, widths[i]);
            }

            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Exportul Excel a eșuat: " + e.getMessage(), e);
        }
    }

    public byte[] toCsv(List<Order> orders) {
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM so Excel opens Romanian characters correctly
        sb.append('﻿');
        sb.append(String.join(",", HEADERS)).append('\n');
        for (Order o : orders) {
            sb.append(o.getId()).append(',')
              .append(o.getCreatedAt() != null ? o.getCreatedAt().format(FMT) : "").append(',')
              .append(csv(o.getUser() != null ? o.getUser().getFullName() : "")).append(',')
              .append(csv(o.getUser() != null ? o.getUser().getEmail() : "")).append(',')
              .append(o.getStatus() != null ? o.getStatus().name() : "").append(',')
              .append(o.getItems() != null ? o.getItems().size() : 0).append(',')
              .append(o.getTotalAmount() != null ? o.getTotalAmount().toPlainString() : BigDecimal.ZERO.toPlainString()).append(',')
              .append(csv(o.getShippingAddress())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String nz(String v) {
        return v == null ? "" : v;
    }

    private String csv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
