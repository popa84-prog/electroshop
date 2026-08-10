package com.electroshop.dto;

import com.electroshop.model.Order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record OrderDto(
        Long id,
        Long userId,
        String userFullName,
        String userEmail,
        String status,
        BigDecimal totalAmount,
        String shippingAddress,
        List<OrderItemDto> items,
        LocalDateTime createdAt,

        /**
         * Seria și numărul facturii emise pentru comandă, sau {@code null}.
         *
         * <p>Adăugate odată cu modulul de facturare, pentru că emiterea a
         * devenit o acțiune explicită: interfața trebuie să poată deosebi „nu
         * are factură, deci butonul o va emite și va consuma un număr fiscal"
         * de „are deja, deci butonul doar descarcă". Fără informația asta,
         * singura cale de a afla ar fi să încerce descărcarea și să interpreteze
         * eroarea — adică să ceară confirmarea consumării unui număr abia după
         * ce operatorul a apăsat.</p>
         */
        String invoiceSeries,
        Integer invoiceNumber
) {
    public static OrderDto from(Order order) {
        return new OrderDto(
                order.getId(),
                order.getUser().getId(),
                order.getUser().getFullName(),
                order.getUser().getEmail(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getShippingAddress(),
                order.getItems().stream().map(OrderItemDto::from).collect(Collectors.toList()),
                order.getCreatedAt(),
                order.getInvoiceSeries(),
                order.getInvoiceNumber()
        );
    }
}
