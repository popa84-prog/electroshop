package com.electroshop.service;

import com.electroshop.dto.NotificationDto;
import com.electroshop.model.Notification;
import com.electroshop.model.Order;
import com.electroshop.model.Product;
import com.electroshop.model.User;
import com.electroshop.repository.NotificationRepository;
import com.electroshop.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Feature #8 — Notificări automate. Two kinds of notification:
 * <ul>
 *   <li><b>Event-driven</b> (new order, product deactivated, account locked): created once,
 *       right where the event happens (OrderService, ProductService, AuthService).</li>
 *   <li><b>Threshold-based</b> (low stock, no image): kept in sync by {@link #reconcile()},
 *       a periodic sweep over the current catalogue, so pre-existing conditions are caught
 *       too — not just ones crossed after this feature shipped.</li>
 * </ul>
 * Every create() call is deduplicated against the same still-unread (type, entityId) pair,
 * so re-saving a product that's already flagged low-stock doesn't spam a new row every time.
 */
@Service
@Transactional
public class NotificationService {

    /** Below this (but above zero) triggers a low-stock notification — mirrors ProductService's threshold. */
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final NotificationRepository repository;
    private final ProductRepository productRepository;

    public NotificationService(NotificationRepository repository, ProductRepository productRepository) {
        this.repository = repository;
        this.productRepository = productRepository;
    }

    // ---------- Read side ----------

    @Transactional(readOnly = true)
    public Page<NotificationDto> search(String type, boolean unreadOnly, Pageable pageable) {
        return repository.search(blankToNull(type), unreadOnly, pageable).map(NotificationDto::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByReadFalse();
    }

    public NotificationDto markRead(Long id) {
        Notification n = repository.findById(id).orElseThrow();
        n.setRead(true);
        return NotificationDto.from(repository.save(n));
    }

    public int markAllRead() {
        var page = repository.search(null, true, PageRequest.of(0, 5000, Sort.by("createdAt").descending()));
        page.forEach(n -> n.setRead(true));
        repository.saveAll(page.getContent());
        return page.getContent().size();
    }

    // ---------- Event-driven notifications ----------

    public void notifyNewOrder(Order order, User user) {
        create("NEW_ORDER",
                "Comandă nouă #" + order.getId(),
                (user != null ? user.getFullName() + " (" + user.getEmail() + ")" : "Client")
                        + " a plasat o comandă de " + formatRon(order.getTotalAmount()),
                "Order", order.getId());
    }

    public void notifyLowStock(Product p) {
        create("LOW_STOCK", "Stoc redus",
                p.getName() + " mai are doar " + p.getStockQuantity() + " bucăți în stoc.",
                "Product", p.getId());
    }

    public void notifyProductDeactivated(Product p) {
        create("PRODUCT_INACTIVE",
                "Produs dezactivat",
                p.getName() + " a fost dezactivat și nu mai apare pe site.",
                "Product", p.getId());
    }

    public void notifyAccountLocked(Long userId, String email, int attempts) {
        create("ACCOUNT_LOCKED",
                "Cont blocat",
                "Contul " + email + " a fost blocat temporar după " + attempts + " încercări eșuate de autentificare.",
                "User", userId);
    }

    // ---------- Threshold sweep ----------

    /**
     * Scans the current catalogue for products that are low-stock, imageless, or inactive
     * and makes sure each one has an open (unread) notification — runs 30s after boot, then
     * every 15 minutes. Best-effort: a failure here must never affect the request that
     * happened to trigger the schedule tick.
     */
    @Scheduled(initialDelay = 30_000, fixedRate = 15 * 60_000)
    public void reconcile() {
        try {
            for (Product p : productRepository.findLowStockActive(LOW_STOCK_THRESHOLD)) {
                notifyLowStock(p);
            }
            for (Product p : productRepository.findActiveWithNoImage()) {
                create("NO_IMAGE", "Produs fără imagine",
                        p.getName() + " nu are nicio imagine încărcată.",
                        "Product", p.getId());
            }
            for (Product p : productRepository.findByActiveFalse()) {
                create("PRODUCT_INACTIVE", "Produs inactiv",
                        p.getName() + " este dezactivat și nu apare pe site.",
                        "Product", p.getId());
            }
        } catch (Exception ignored) {
            // A sweep failure must not break request handling or app startup.
        }
    }

    // ---------- internals ----------

    private void create(String type, String title, String message, String entityType, Long entityId) {
        try {
            if (repository.existsByTypeAndEntityIdAndReadFalse(type, entityId)) {
                return; // already flagged and not yet acknowledged — don't spam
            }
            Notification n = new Notification();
            n.setType(type);
            n.setTitle(title);
            n.setMessage(message != null && message.length() > 500 ? message.substring(0, 500) : message);
            n.setEntityType(entityType);
            n.setEntityId(entityId);
            repository.save(n);
        } catch (Exception ignored) {
            // Notifications must never break the primary flow (order placement, product save, ...).
        }
    }

    private String formatRon(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount) + " RON";
    }

    private String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }
}
