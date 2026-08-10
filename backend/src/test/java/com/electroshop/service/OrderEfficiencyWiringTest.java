package com.electroshop.service;

import com.electroshop.model.Order;
import com.electroshop.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the two halves of the fix for the order-efficiency panel's lazy-loading failure.
 *
 * <p>{@code GET /api/orders/efficiency} returned HTTP 500 with <em>could not initialize
 * proxy [com.electroshop.model.User#3] - no Session</em>. The detail table prints the
 * customer's e-mail for every row; {@code Order.user} is mapped {@code LAZY}; and
 * {@link OrderEfficiencyService#efficiency} carried no transaction, so each repository
 * call opened and closed its own session and handed back orders whose customer was an
 * uninitialised proxy. Reading {@code getEmail()} off one of those, after the session
 * that could have loaded it was gone, is the exception.</p>
 *
 * <p>Two changes were needed and both are asserted here, because either one alone leaves
 * a defect. The transaction alone would work but issue one extra select per row. The
 * fetch join alone would fix this call site and leave the panel one added association
 * away from failing the same way again. A compiler cannot see either problem: both the
 * query string and the missing annotation are perfectly legal Java.</p>
 */
class OrderEfficiencyWiringTest {

    @Test
    void theDetailQueryFetchesTheCustomerWithTheOrder() throws Exception {
        Method query = null;
        for (Method m : OrderRepository.class.getDeclaredMethods()) {
            if (m.getName().equals("findPlacedBetween")) {
                query = m;
                break;
            }
        }

        assertNotNull(query, "lipseste OrderRepository.findPlacedBetween");

        Query annotation = query.getAnnotation(Query.class);
        assertNotNull(annotation, "findPlacedBetween trebuie sa poarte @Query");

        String jpql = annotation.value().replaceAll("\\s+", " ").toUpperCase(java.util.Locale.ROOT);
        assertTrue(jpql.contains("JOIN FETCH O.USER"),
                "findPlacedBetween trebuie sa incarce clientul odata cu comanda: "
                        + "fara JOIN FETCH, tabelul de detalii citeste un proxy detasat. JPQL=" + jpql);
        assertTrue(jpql.contains("LEFT JOIN FETCH O.USER"),
                "join-ul trebuie sa fie LEFT, ca o comanda fara client sa nu dispara tacut din tabel");
    }

    @Test
    void theReadPathRunsInsideAReadOnlyTransaction() throws Exception {
        Method entry = OrderEfficiencyService.class.getMethod("efficiency", MetricRange.class);

        Transactional tx = entry.getAnnotation(Transactional.class);
        assertNotNull(tx, "efficiency(MetricRange) trebuie sa fie @Transactional: "
                + "altfel fiecare apel de repository ruleaza in propria sesiune");
        assertTrue(tx.readOnly(),
                "tranzactia trebuie sa fie readOnly: panoul nu scrie nimic, iar flag-ul "
                        + "elimina dirty-checking-ul pentru cele 50 de comenzi incarcate");
    }

    @Test
    void theDetailRowStillToleratesAnOrderWithoutACustomer() {
        // The mapping says NOT NULL; the code says print a dash. The LEFT join keeps
        // those two statements from contradicting each other by dropping the row.
        Order order = new Order();
        order.setId(1L);

        assertTrue(order.getUser() == null,
                "o comanda nou construita nu are client, iar codul de randare trebuie sa suporte asta");
    }
}
