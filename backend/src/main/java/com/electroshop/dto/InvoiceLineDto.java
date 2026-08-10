package com.electroshop.dto;

import com.electroshop.model.InvoiceLine;

import java.math.BigDecimal;

/**
 * O poziție de factură, aşa cum o vede interfața.
 *
 * <p>{@code remainingToStorno} este calculat pe server, nu în browser. Dialogul
 * de stornare îl foloseşte ca limită superioară a câmpului de cantitate, iar
 * dacă l-ar deduce singur din {@code quantity} minus {@code stornoedQuantity} ar
 * exista două implementări ale aceleiaşi reguli, care s-ar putea despărţi la
 * prima modificare. Serverul respinge oricum orice depăşire; câmpul acesta face
 * doar ca operatorul să nu ajungă în situaţia de a fi respins.</p>
 */
public record InvoiceLineDto(
        Long id,
        Long productId,
        Long orderItemId,
        String productName,
        String sku,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineNet,
        BigDecimal lineVat,
        BigDecimal lineGross,
        Integer stornoedQuantity,
        Integer remainingToStorno
) {
    public static InvoiceLineDto from(InvoiceLine line) {
        return new InvoiceLineDto(
                line.getId(),
                line.getProduct() == null ? null : line.getProduct().getId(),
                line.getOrderItemId(),
                line.getProductName(),
                line.getSku(),
                line.getQuantity(),
                line.getUnitPrice(),
                line.getLineNet(),
                line.getLineVat(),
                line.getLineGross(),
                line.getStornoedQuantity() == null ? 0 : line.getStornoedQuantity(),
                line.remainingToStorno()
        );
    }
}
