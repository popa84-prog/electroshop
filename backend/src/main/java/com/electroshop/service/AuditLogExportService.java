package com.electroshop.service;

import com.electroshop.dto.AuditLogDto;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds the "Jurnal de activitate" export — every column an operator needs to
   * audit who changed what and when, filtered exactly like the on-screen table
   * (feature #5 — export audit log).
   */
@Service
  public class AuditLogExportService {

    private static final String[] HEADERS = {"Data", "Autor", "Acțiune", "Entitate", "ID entitate", "Detalii"};
        private static final int[] WIDTHS = {5500, 7000, 7500, 4500, 3000, 15000};
        private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public byte[] toExcel(List<AuditLogDto> rows) {
              try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                            Sheet sheet = wb.createSheet("Jurnal");

                  Font boldFont = wb.createFont();
                            boldFont.setBold(true);
                            CellStyle headerStyle = wb.createCellStyle();
                            headerStyle.setFont(boldFont);
                            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                  Row header = sheet.createRow(0);
                            for (int i = 0; i < HEADERS.length; i++) {
                                              Cell c = header.createCell(i);
                                              c.setCellValue(HEADERS[i]);
                                              c.setCellStyle(headerStyle);
                            }

                  int r = 1;
                            for (AuditLogDto a : rows) {
                                              Row row = sheet.createRow(r++);
                                              row.createCell(0).setCellValue(a.createdAt() == null ? "" : a.createdAt().format(DATE_FORMAT));
                                              row.createCell(1).setCellValue(a.actor() == null ? "" : a.actor());
                                              row.createCell(2).setCellValue(a.action() == null ? "" : a.action());
                                              row.createCell(3).setCellValue(a.entityType() == null ? "" : a.entityType());
                                              row.createCell(4).setCellValue(a.entityId() == null ? "" : String.valueOf(a.entityId()));
                                              row.createCell(5).setCellValue(a.details() == null ? "" : a.details());
                            }

                  for (int i = 0; i < WIDTHS.length; i++) {
                                    sheet.setColumnWidth(i, WIDTHS[i]);
                  }
                            sheet.createFreezePane(0, 1);

                  wb.write(bos);
                            return bos.toByteArray();
              } catch (IOException e) {
                            throw new RuntimeException("Exportul jurnalului a eșuat: " + e.getMessage(), e);
              }
    }

    /** Same six columns as {@link #toExcel(List)}, as CSV with a UTF-8 BOM for Excel diacritics. */
    public byte[] toCsv(List<AuditLogDto> rows) {
              StringBuilder sb = new StringBuilder();
              sb.append((char) 0xFEFF);
              sb.append(String.join(",", HEADERS)).append('\n');
              for (AuditLogDto a : rows) {
                            sb.append(csv(a.createdAt() == null ? "" : a.createdAt().format(DATE_FORMAT))).append(',')
                                            .append(csv(a.actor())).append(',')
                                            .append(csv(a.action())).append(',')
                                            .append(csv(a.entityType())).append(',')
                                            .append(a.entityId() == null ? "" : a.entityId()).append(',')
                                            .append(csv(a.details())).append('\n');
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
