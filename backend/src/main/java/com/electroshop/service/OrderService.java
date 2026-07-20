package com.electroshop.service;

import com.electroshop.dto.OrderDto;
import com.electroshop.dto.OrderRequest;
import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.*;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderExportService orderExportService;
    private final AuditService auditService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository, OrderExportService orderExportService,
                        AuditService auditService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderExportService = orderExportService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public byte[] exportOrders(java.time.LocalDate from, java.time.LocalDate to, String format) {
        java.time.LocalDateTime start = (from != null)
                ? from.atStartOfDay()
                : java.time.LocalDateTime.of(2000, 1, 1, 0, 0);
        java.time.LocalDateTime end = (to != null)
                ? to.plusDays(1).atStartOfDay()
                : java.time.LocalDate.now().plusDays(1).atStartOfDay();
        var orders = orderRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end);
        return "csv".equalsIgnoreCase(format)
                ? orderExportService.toCsv(orders)
                : orderExportService.toExcel(orders);
    }

    public OrderDto placeOrder(Long userId, OrderRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Order order = new Order();
        order.setUser(user);
        order.setShippingAddress(req.shippingAddress());
        order.setStatus(OrderStatus.PENDING);

        for (OrderRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", item.productId()));

            if (product.getStockQuantity() < item.quantity()) {
                throw new BadRequestException(
                        "Insufficient stock for product '" + product.getName() + "'. Available: "
                                + product.getStockQuantity());
            }
            // Decrement stock
            product.setStockQuantity(product.getStockQuantity() - item.quantity());

            OrderItem orderItem = new OrderItem();
            orderItem.setProduct(product);
            orderItem.setQuantity(item.quantity());
            orderItem.setUnitPrice(product.getPrice());
            order.addItem(orderItem);
        }

        order.recalculateTotal();
        Order saved = orderRepository.save(order);
        auditService.log("ORDER_CREATED", "Order", saved.getId(),
                "client " + user.getEmail() + " · total " + saved.getTotalAmount());
        return OrderDto.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<OrderDto> getUserOrders(Long userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(OrderDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDto getUserOrder(Long userId, Long orderId) {
        Order order = findEntity(orderId);
        if (!order.getUser().getId().equals(userId)) {
            throw new ResourceNotFoundException("Order", orderId);
        }
        return OrderDto.from(order);
    }

    // ---- Admin ----

    @Transactional(readOnly = true)
    public Page<OrderDto> getAllOrders(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            OrderStatus st = parseStatus(status);
            return orderRepository.findByStatus(st, pageable).map(OrderDto::from);
        }
        return orderRepository.findAll(pageable).map(OrderDto::from);
    }

    @Transactional(readOnly = true)
    public OrderDto getOrder(Long orderId) {
        return OrderDto.from(findEntity(orderId));
    }

    public OrderDto updateStatus(Long orderId, String status) {
        Order order = findEntity(orderId);
        OrderStatus newStatus = parseStatus(status);

        // Restock if an order is cancelled
        if (newStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            for (OrderItem item : order.getItems()) {
                Product p = item.getProduct();
                p.setStockQuantity(p.getStockQuantity() + item.getQuantity());
            }
        }
        order.setStatus(newStatus);
        OrderDto dto = OrderDto.from(orderRepository.save(order));
        auditService.log("ORDER_STATUS_CHANGED", "Order", orderId, "→ " + newStatus.name());
        return dto;
    }

    public void delete(Long orderId) {
        Order order = findEntity(orderId);
        orderRepository.delete(order);
        auditService.log("ORDER_DELETED", "Order", orderId, null);
    }

    private Order findEntity(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid order status: " + status);
        }
    }
}
