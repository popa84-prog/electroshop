package com.electroshop.service;

import com.electroshop.dto.OrderDto;
import com.electroshop.dto.OrderRequest;
import com.electroshop.dto.SellBatchRequest;
import com.electroshop.dto.SellProductRequest;
import com.electroshop.exception.BadRequestException;
import com.electroshop.exception.ResourceNotFoundException;
import com.electroshop.model.*;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@Transactional
public class OrderService {

    /** Below this (but above zero) triggers a low-stock notification — mirrors ProductService's threshold. */
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final OrderExportService orderExportService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    /**
     * Records every status transition, so the order-efficiency report can measure how
     * long each stage took. Without it the order carries only a creation and a
     * last-touched timestamp, from which no stage duration is derivable.
     */
    private final OrderStatusRecorder statusRecorder;
    /**
     * The one authority that puts goods back on the shelf. Cancelling an order
     * and issuing a credit note are two legitimate paths to the same effect, and
     * only a shared per-line counter keeps them from stacking.
     */
    private final OrderRestockService restockService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
                        UserRepository userRepository, OrderExportService orderExportService,
                        AuditService auditService, NotificationService notificationService,
                        OrderStatusRecorder statusRecorder, OrderRestockService restockService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.orderExportService = orderExportService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.statusRecorder = statusRecorder;
        this.restockService = restockService;
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
            // Snapshot the product's name at sale time — see OrderItem.productName.
            // Populated unconditionally, not only in anticipation of a future
            // force-delete, so it is always there once needed.
            orderItem.setProductName(product.getName());
            orderItem.setQuantity(item.quantity());
            orderItem.setUnitPrice(product.getPrice());
            // Snapshot the acquisition cost at sale time so the accounting report's
            // gross-margin figure stays accurate even if the product's purchase
            // price is edited afterwards.
            orderItem.setCostPrice(product.getPurchasePrice());
            order.addItem(orderItem);
        }

        order.recalculateTotal();
        Order saved = orderRepository.save(order);
        // The first event of the order's life: nothing to its opening status.
        // Recorded after the save because the row needs the generated id.
        statusRecorder.recordCreation(saved);
        auditService.log("ORDER_CREATED", "Order", saved.getId(),
                "client " + user.getEmail() + " · total " + saved.getTotalAmount());
        // Feature #8 — "comenzi noi" notification, feeds the admin notification center.
        notificationService.notifyNewOrder(saved, user);
        return OrderDto.from(saved);
    }

    /**
     * Feature #10 — the "VÂNDUT" popup on the admin products page: registers a
     * walk-in / in-store sale in one shot. Deliberately built on top of the same
     * Order/OrderItem tables the storefront checkout uses (rather than a separate
     * "sales" table) so it needs no bespoke wiring — every dashboard number that
     * already reads from orders (total revenue, "Comenzi" count, vânzări pe
     * zile/luni/ani, comenzi după status, top produse vândute) picks the sale up
     * automatically the next time the dashboard loads. The sale is recorded as a
     * DELIVERED order since it's a completed, already-paid-for transaction, not
     * something still working through a shipping pipeline.
     */
    public OrderDto sellProduct(Long productId, SellProductRequest req) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (req.quantity() > product.getStockQuantity()) {
            throw new BadRequestException(
                    "Stoc insuficient! Stoc disponibil: " + product.getStockQuantity() + " buc.");
        }

        User staff = currentStaffUser();

        int oldStock = product.getStockQuantity();
        product.setStockQuantity(oldStock - req.quantity());
        productRepository.save(product);

        Order order = new Order();
        order.setUser(staff);
        order.setStatus(OrderStatus.DELIVERED);
        order.setShippingAddress("Vânzare directă în magazin (înregistrată din panoul admin)");

        OrderItem item = new OrderItem();
        item.setProduct(product);
        // Snapshot the product's name at sale time — see OrderItem.productName.
        item.setProductName(product.getName());
        item.setQuantity(req.quantity());
        item.setUnitPrice(req.unitPrice());
        // Snapshot the acquisition cost at sale time — see OrderItem.costPrice.
        item.setCostPrice(product.getPurchasePrice());
        order.addItem(item);
        order.recalculateTotal();

        Order saved = orderRepository.save(order);
        // The first event of the order's life: nothing to its opening status.
        // Recorded after the save because the row needs the generated id.
        statusRecorder.recordCreation(saved);

        auditService.log("PRODUCT_SOLD", "Product", product.getId(),
                "Vândut " + req.quantity() + " bucăți din " + product.getName()
                        + " · total " + order.getTotalAmount() + " RON · comandă #" + saved.getId());

        // Feature #8 synergy — a sale that drops stock below the threshold fires the
        // same low-stock notification a manual stock edit would (see ProductService.update()).
        boolean crossedIntoLowStock = product.getStockQuantity() > 0 && product.getStockQuantity() < LOW_STOCK_THRESHOLD
                && oldStock >= LOW_STOCK_THRESHOLD;
        if (crossedIntoLowStock) {
            notificationService.notifyLowStock(product);
        }

        return OrderDto.from(saved);
    }

    /**
     * Multi-product counterpart of {@link #sellProduct} — the "VÂNDUT" popup's cart
     * mode. Registers several distinct products as ONE walk-in sale/order instead of
     * one order per product, so a customer buying "3 of this, 1 of that" shows up as
     * a single order with several line items instead of several separate orders.
     * <p>
     * Stock is validated for every line BEFORE anything is written — if any line asks
     * for more than is in stock, the whole batch is rejected and no product's stock
     * is touched. Duplicate product ids in the same batch are summed into a single
     * effective line first, so adding the same product twice behaves exactly like one
     * line with the combined quantity (keeping the price of its first occurrence).
     */
    public OrderDto sellBatch(SellBatchRequest req) {
        Map<Long, SellBatchRequest.Line> merged = new LinkedHashMap<>();
        for (SellBatchRequest.Line line : req.items()) {
            merged.merge(line.productId(), line,
                    (existing, incoming) -> new SellBatchRequest.Line(
                            existing.productId(), existing.quantity() + incoming.quantity(), existing.unitPrice()));
        }

        // Load + validate stock for every line before mutating anything, so a
        // single insufficient-stock line rejects the whole batch cleanly.
        Map<Long, Product> products = new LinkedHashMap<>();
        for (SellBatchRequest.Line line : merged.values()) {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", line.productId()));
            if (line.quantity() > product.getStockQuantity()) {
                throw new BadRequestException("Stoc insuficient pentru \"" + product.getName()
                        + "\"! Stoc disponibil: " + product.getStockQuantity() + " buc.");
            }
            products.put(line.productId(), product);
        }

        User staff = currentStaffUser();
        Order order = new Order();
        order.setUser(staff);
        order.setStatus(OrderStatus.DELIVERED);
        order.setShippingAddress("Vânzare directă în magazin (înregistrată din panoul admin)");

        for (SellBatchRequest.Line line : merged.values()) {
            Product lineProduct = products.get(line.productId());
            OrderItem item = new OrderItem();
            item.setProduct(lineProduct);
            // Snapshot the product's name at sale time — see OrderItem.productName.
            item.setProductName(lineProduct.getName());
            item.setQuantity(line.quantity());
            item.setUnitPrice(line.unitPrice());
            // Snapshot the acquisition cost at sale time — see OrderItem.costPrice.
            item.setCostPrice(lineProduct.getPurchasePrice());
            order.addItem(item);
        }
        order.recalculateTotal();
        Order saved = orderRepository.save(order);
        // The first event of the order's life: nothing to its opening status.
        // Recorded after the save because the row needs the generated id.
        statusRecorder.recordCreation(saved);

        // Apply the stock decrements and per-product audit trail now that the order
        // (and its id, used in each entry's details) exists.
        StringBuilder summary = new StringBuilder();
        for (SellBatchRequest.Line line : merged.values()) {
            Product product = products.get(line.productId());
            int oldStock = product.getStockQuantity();
            product.setStockQuantity(oldStock - line.quantity());
            productRepository.save(product);

            auditService.log("PRODUCT_SOLD", "Product", product.getId(),
                    "Vândut " + line.quantity() + " bucăți din " + product.getName()
                            + " · comandă multi-produs #" + saved.getId());

            if (summary.length() > 0) {
                summary.append(", ");
            }
            summary.append(line.quantity()).append("× ").append(product.getName());

            // Feature #8 synergy — same low-stock notification a manual stock edit would fire.
            boolean crossedIntoLowStock = product.getStockQuantity() > 0 && product.getStockQuantity() < LOW_STOCK_THRESHOLD
                    && oldStock >= LOW_STOCK_THRESHOLD;
            if (crossedIntoLowStock) {
                notificationService.notifyLowStock(product);
            }
        }

        auditService.log("PRODUCT_SOLD", "Order", saved.getId(),
                "Vânzare multi-produs: " + summary + " · total " + saved.getTotalAmount() + " RON · comandă #" + saved.getId());

        return OrderDto.from(saved);
    }

    /** The admin currently performing the sale — recorded as the order's "user" (feature #10's "User" column). */
    private User currentStaffUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = auth != null ? auth.getName() : null;
        if (email == null) {
            throw new BadRequestException("Sesiune invalidă. Reautentifică-te și încearcă din nou.");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Utilizator inexistent."));
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
        // Read before the write: after setStatus the old value is gone, and the whole
        // point of the history row is which status the order left.
        OrderStatus previousStatus = order.getStatus();

        // Restock if an order is cancelled.
        //
        // Delegated to OrderRestockService rather than done inline. Since the
        // invoicing module landed, stock can also come back through a credit
        // note, and an operator who issues the storno first and cancels the
        // order afterwards would otherwise add every quantity twice — silently,
        // producing stock for goods that do not physically exist. The service
        // keeps a per-line counter of what has already been returned and adds
        // only the remainder, so the two paths compose in any order.
        if (newStatus == OrderStatus.CANCELLED && order.getStatus() != OrderStatus.CANCELLED) {
            restockService.restockAll(order, "Anulare comandă #" + order.getId());
        }
        order.setStatus(newStatus);
        OrderDto dto = OrderDto.from(orderRepository.save(order));
        statusRecorder.record(order, previousStatus, newStatus, null);
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
