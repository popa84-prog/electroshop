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
        LocalDateTime createdAt
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
                order.getCreatedAt()
        );
    }
}
