package com.electroshop.service;

import com.electroshop.model.Order;
import com.electroshop.model.OrderStatus;
import com.electroshop.model.OrderStatusEvent;
import com.electroshop.repository.OrderStatusEventRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Writes one row for every order status transition.
 *
 * <p>Task 15's two duration KPIs depend entirely on this. {@code Order} keeps a creation
 * timestamp and a last-touched timestamp; neither records when the order went from placed
 * to paid, or from shipped to delivered, and those two gaps are what "average processing
 * time" and "average delivery time" mean. So the transitions are captured as they
 * happen.</p>
 *
 * <h2>Why a separate collaborator rather than code inside OrderService</h2>
 *
 * <p>{@code OrderService} already carries order placement, stock movement, notifications
 * and auditing. Adding history bookkeeping to it would put a fifth concern in a class
 * that is long enough, and — more importantly — would scatter the recording across the
 * four places that change a status. One collaborator called from those four places keeps
 * the rule in one function: every status change produces exactly one row, with the same
 * shape, whoever made it.</p>
 *
 * <h2>Recording never fails a business operation</h2>
 *
 * <p>If writing the history row throws, the exception is swallowed and reported to the
 * console. Losing a measurement is a gap in a chart; failing the call would mean an order
 * cannot be marked as shipped because an analytics table is unavailable. The first is an
 * inconvenience, the second is an outage caused by a feature nobody uses on the shop
 * floor.</p>
 *
 * <h2>The actor comes from the security context</h2>
 *
 * <p>Not from a parameter, so no caller can attribute a change to somebody else, and a
 * transition made by a customer placing an order — where there is no admin — records null
 * rather than a placeholder that would look like a person.</p>
 */
@Service
public class OrderStatusRecorder {

    private final OrderStatusEventRepository repository;

    public OrderStatusRecorder(OrderStatusEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a transition.
     *
     * @param order      the order, already saved so it has an id
     * @param fromStatus the status it left, or null for the first event of an order
     * @param toStatus   the status it entered
     * @param reason     why, for returns and cancellations; null otherwise
     */
    public void record(Order order, OrderStatus fromStatus, OrderStatus toStatus, String reason) {
        if (order == null || order.getId() == null || toStatus == null) {
            return;
        }
        // A "transition" to the status it already had is not a transition. Recording it
        // would put a zero-length stage into the averages and pull every duration down
        // every time somebody re-saves an order without changing anything.
        if (fromStatus == toStatus) {
            return;
        }
        try {
            repository.save(new OrderStatusEvent(
                    order, fromStatus, toStatus, currentActor(), trim(reason)));
        } catch (RuntimeException e) {
            // See the class comment. The console is the fallback; the order still moves.
            System.err.println("[OrderStatusRecorder] tranziția nu a putut fi înregistrată pentru "
                    + "comanda " + order.getId() + ": " + e.getMessage());
        }
    }

    /** Records the first event of an order's life: nothing to PENDING. */
    public void recordCreation(Order order) {
        record(order, null, order.getStatus(), null);
    }

    /**
     * The authenticated principal's name, or null when there is none.
     *
     * <p>Null for a customer placing an order through the storefront, which is correct:
     * nobody in the admin panel did it, and writing "system" would make the history look
     * like an automated action rather than a customer action.</p>
     */
    private static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String name = auth.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? null : name;
    }

    private static String trim(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > 300 ? trimmed.substring(0, 300) : trimmed;
    }
}
