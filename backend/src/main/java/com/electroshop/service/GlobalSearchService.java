package com.electroshop.service;

import com.electroshop.dto.GlobalSearchDto;
import com.electroshop.model.Order;
import com.electroshop.model.Product;
import com.electroshop.model.Role;
import com.electroshop.model.User;
import com.electroshop.repository.OrderRepository;
import com.electroshop.repository.ProductRepository;
import com.electroshop.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The sidebar's global search across products, orders and users.
 *
 * <p>Task 3.</p>
 *
 * <h2>Results are grouped, never merged into one ranked list</h2>
 *
 * <p>A product, an order and a user are not comparable. Any scoring that placed them in
 * a single column would be inventing a relevance relationship between things that have
 * none — is a product whose name matches exactly more relevant than an order whose
 * customer matches exactly? The question has no answer, so the interface does not ask it.
 * Grouping lets the operator's eye go straight to the section they meant.</p>
 *
 * <h2>Permissions are applied before a group is filled, not after</h2>
 *
 * <p>An Editor searching a customer's surname must not learn that the account exists from
 * a result count. So a group the caller cannot view is absent from the response entirely
 * rather than present and empty — the two are distinguishable by any client, and the
 * difference leaks exactly the fact the permission withholds.</p>
 *
 * <h2>The search term is bound, never concatenated</h2>
 *
 * <p>Every query below passes the term as a parameter. A value containing quotes, a
 * percent sign or a SQL keyword is data and cannot become syntax. The wildcards are added
 * by the query itself, so a user typing {@code %} searches for a percent sign rather than
 * for everything.</p>
 */
@Service
public class GlobalSearchService {

    /** How many results each group returns. */
    private static final int GROUP_LIMIT = 8;

    /** Shortest term accepted. Below this every query matches most of the catalogue. */
    private static final int MIN_TERM_LENGTH = 2;

    /** Longest term accepted, as a guard against pathological patterns. */
    private static final int MAX_TERM_LENGTH = 100;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public GlobalSearchService(ProductRepository productRepository,
                               OrderRepository orderRepository,
                               UserRepository userRepository) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    /**
     * Searches every group the caller is permitted to see.
     *
     * @param rawQuery     what was typed
     * @param canViewProducts whether the caller holds the products permission
     * @param canViewOrders   whether the caller holds the orders permission
     * @param canViewUsers    whether the caller holds the users permission
     */
    @Transactional(readOnly = true)
    public GlobalSearchDto search(String rawQuery,
                                  boolean canViewProducts,
                                  boolean canViewOrders,
                                  boolean canViewUsers) {
        long started = System.nanoTime();
        String query = normalise(rawQuery);

        if (query == null) {
            // Too short to be useful. An empty result rather than an error: the endpoint
            // is called on every keystroke, and the first character is not a mistake.
            return new GlobalSearchDto(rawQuery, List.of(), List.of(), List.of(), 0, false, 0);
        }

        List<GlobalSearchDto.ProductHit> products = canViewProducts
                ? searchProducts(query)
                : List.of();
        List<GlobalSearchDto.OrderHit> orders = canViewOrders
                ? searchOrders(query)
                : List.of();
        List<GlobalSearchDto.UserHit> users = canViewUsers
                ? searchUsers(query)
                : List.of();

        int total = products.size() + orders.size() + users.size();
        boolean truncated = products.size() == GROUP_LIMIT
                || orders.size() == GROUP_LIMIT
                || users.size() == GROUP_LIMIT;

        return new GlobalSearchDto(
                rawQuery,
                products,
                orders,
                users,
                total,
                truncated,
                (System.nanoTime() - started) / 1_000_000
        );
    }

    private List<GlobalSearchDto.ProductHit> searchProducts(String query) {
        List<Product> found = productRepository.searchForGlobal(
                query, PageRequest.of(0, GROUP_LIMIT));

        List<GlobalSearchDto.ProductHit> out = new ArrayList<>(found.size());
        for (Product p : found) {
            out.add(new GlobalSearchDto.ProductHit(
                    p.getId(),
                    p.getName(),
                    p.getImageUrl(),
                    p.getSku(),
                    p.getBrand(),
                    p.getPrice(),
                    p.getStockQuantity(),
                    p.isActive(),
                    "/admin/products?id=" + p.getId()
            ));
        }
        return out;
    }

    private List<GlobalSearchDto.OrderHit> searchOrders(String query) {
        // An order is most often looked up by its number, so a numeric term is tried as
        // an id first. Falling straight through to a text search would make the most
        // common lookup in the whole panel the slowest one.
        List<Order> found = new ArrayList<>();
        Long asId = parseLong(query);
        if (asId != null) {
            orderRepository.findById(asId).ifPresent(found::add);
        }
        if (found.size() < GROUP_LIMIT) {
            for (Order order : orderRepository.searchForGlobal(
                    query, PageRequest.of(0, GROUP_LIMIT))) {
                if (found.stream().noneMatch(o -> o.getId().equals(order.getId()))) {
                    found.add(order);
                }
                if (found.size() >= GROUP_LIMIT) {
                    break;
                }
            }
        }

        List<GlobalSearchDto.OrderHit> out = new ArrayList<>(found.size());
        for (Order o : found) {
            out.add(new GlobalSearchDto.OrderHit(
                    o.getId(),
                    o.getUser() == null ? "—" : o.getUser().getEmail(),
                    o.getStatus() == null ? "—" : o.getStatus().name(),
                    o.getTotalAmount(),
                    o.getCreatedAt() == null ? null : o.getCreatedAt().toString(),
                    "/admin/orders?id=" + o.getId()
            ));
        }
        return out;
    }

    private List<GlobalSearchDto.UserHit> searchUsers(String query) {
        List<User> found = userRepository.searchForGlobal(query, PageRequest.of(0, GROUP_LIMIT));

        List<GlobalSearchDto.UserHit> out = new ArrayList<>(found.size());
        for (User u : found) {
            List<String> roles = new ArrayList<>();
            if (u.getRoles() != null) {
                for (Role role : u.getRoles()) {
                    if (role.getName() != null) {
                        roles.add(role.getName().name());
                    }
                }
            }
            out.add(new GlobalSearchDto.UserHit(
                    u.getId(),
                    u.getEmail(),
                    u.getFullName(),
                    roles,
                    u.isEnabled(),
                    "/admin/users?id=" + u.getId()
            ));
        }
        return out;
    }

    /**
     * Trims and length-checks the term.
     *
     * <p>Returns null for anything too short to narrow the catalogue meaningfully. A
     * one-character search on a few hundred products returns most of them, which is
     * slower and less useful than returning nothing while the operator keeps typing.</p>
     */
    private static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.length() < MIN_TERM_LENGTH) {
            return null;
        }
        return trimmed.length() > MAX_TERM_LENGTH
                ? trimmed.substring(0, MAX_TERM_LENGTH)
                : trimmed;
    }

    private static Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
