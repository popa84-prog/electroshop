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

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
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
        return OrderDto.from(orderRepository.save(order));
    }

    public void delete(Long orderId) {
        Order order = findEntity(orderId);
        orderRepository.delete(order);
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
