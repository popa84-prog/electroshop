package com.electroshop.service;

import com.electroshop.model.Product;
import com.electroshop.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Media ponderată a costului și intrarea în stoc.
 *
 * <p>Formula este exact lucrul pe care un operator nu îl poate verifica din
 * cap. O intrare la un preț diferit de cel anterior mută costul mediu al
 * produsului, iar consecința nu se vede imediat: apare abia peste săptămâni, în
 * marjă și în profitul potențial, ca o deviere pe care nimeni nu o mai poate
 * lega de livrarea care a produs-o. De aceea calculul este verificat aici
 * numeric, pe cazuri alese, nu doar exersat.</p>
 *
 * <p>Serviciul are și un al doilea rol, la fel de important: este singura cale
 * prin care marfa intră în stoc. Înainte, importul din Excel scria cantitatea
 * direct, iar înregistrarea unei achiziții o scria din nou. Din momentul în
 * care un import produce și o recepție, cele două s-ar suprapune și fiecare
 * bucată ar intra de două ori — fără nicio eroare, doar cu stoc pentru marfă
 * care nu există.</p>
 */
class StockIntakeServiceTest {

    private ProductRepository productRepository;
    private StockIntakeService service;

    @BeforeEach
    void setUp() {
        productRepository = Mockito.mock(ProductRepository.class);
        service = new StockIntakeService(productRepository);
    }

    // ---- Media ponderată, verificată numeric -----------------------------

    @Test
    void costulMediuEsteMediaPonderataAStocurilor() {
        // 10 bucăți la 100 plus 10 bucăți la 200 dau exact 150.
        BigDecimal result = StockIntakeService.weightedAverage(
                10, new BigDecimal("100.00"), 10, new BigDecimal("200.00"));

        assertEquals(0, new BigDecimal("150.00").compareTo(result));
    }

    @Test
    void ponderileChiarConteaza() {
        // 90 de bucăți la 100 și doar 10 la 200 dau 110, nu 150. Media simplă ar
        // fi 150 și ar supraevalua stocul cu 40 de lei pe bucată.
        BigDecimal result = StockIntakeService.weightedAverage(
                90, new BigDecimal("100.00"), 10, new BigDecimal("200.00"));

        assertEquals(0, new BigDecimal("110.00").compareTo(result));
    }

    @Test
    void stoculZeroInseamnaCaNuExistaNimicDeMediat() {
        // Un produs epuizat care primește marfă nouă preia direct costul nou.
        // O medie cu un termen inexistent ar produce o cifră inventată.
        BigDecimal result = StockIntakeService.weightedAverage(
                0, new BigDecimal("100.00"), 5, new BigDecimal("250.00"));

        assertEquals(0, new BigDecimal("250.00").compareTo(result));
    }

    @Test
    void costulAnteriorLipsaInseamnaAcelasiLucru() {
        BigDecimal result = StockIntakeService.weightedAverage(
                7, null, 3, new BigDecimal("42.00"));

        assertEquals(0, new BigDecimal("42.00").compareTo(result));
    }

    @Test
    void intrareaFaraCostNuMiscaMediaSiNuOTrageLaZero() {
        // Tratarea costului lipsă ca zero ar trage media în jos la fiecare
        // recepție pe cantitate și ar face marja să pară tot mai bună — exact
        // inversul adevărului.
        BigDecimal result = StockIntakeService.weightedAverage(
                10, new BigDecimal("100.00"), 10, null);

        assertEquals(0, new BigDecimal("100.00").compareTo(result),
                "costul rămâne cel dinainte, nu devine 50");
    }

    @Test
    void rezultatulEsteRotunjitLaDoiBani() {
        // 3 la 10 și 1 la 10.01 dau 10.0025, care nu se poate scrie în bani.
        BigDecimal result = StockIntakeService.weightedAverage(
                3, new BigDecimal("10.00"), 1, new BigDecimal("10.01"));

        assertEquals(2, result.scale(), "exact două zecimale");
        assertEquals(0, new BigDecimal("10.00").compareTo(result));
    }

    @Test
    void oSerieDeIntrariConvergeCatreCostulNou() {
        // Verificare a stabilității: costul nu oscilează și nu explodează.
        BigDecimal cost = new BigDecimal("100.00");
        int stock = 10;
        for (int i = 0; i < 20; i++) {
            cost = StockIntakeService.weightedAverage(stock, cost, 10, new BigDecimal("200.00"));
            stock += 10;
        }
        assertTrue(cost.compareTo(new BigDecimal("190.00")) > 0,
                "după 20 de intrări la 200, media se apropie de 200, nu rămâne la 100");
        assertTrue(cost.compareTo(new BigDecimal("200.00")) <= 0,
                "media nu poate depăși cel mai scump termen");
    }

    // ---- Aplicarea pe produs --------------------------------------------

    @Test
    void intrareaAdaugaCantitateaSiScrieCostulNou() {
        Product p = product(10, "100.00");

        StockIntakeService.Result r = service.intake(p, 10, new BigDecimal("200.00"));

        assertEquals(10, r.stockBefore());
        assertEquals(20, r.stockAfter());
        assertEquals(20, p.getStockQuantity().intValue());
        assertEquals(0, new BigDecimal("150.00").compareTo(p.getPurchasePrice()));
        assertEquals(10, r.applied());
        assertTrue(r.costChanged());
    }

    @Test
    void douaIntrariSuccesiveSeCumuleaza() {
        // Contrastul cu ieșirea din stoc: acolo restituirea este idempotentă,
        // pentru că aceeași marfă nu se poate întoarce de două ori. Aici fiecare
        // apel este o livrare distinctă și trebuie să se adune.
        Product p = product(0, null);

        service.intake(p, 5, new BigDecimal("100.00"));
        service.intake(p, 5, new BigDecimal("300.00"));

        assertEquals(10, p.getStockQuantity().intValue());
        assertEquals(0, new BigDecimal("200.00").compareTo(p.getPurchasePrice()));
    }

    @Test
    void cantitateaZeroNuMiscaNimic() {
        Product p = product(8, "50.00");

        StockIntakeService.Result r = service.intake(p, 0, new BigDecimal("999.00"));

        assertEquals(8, p.getStockQuantity().intValue());
        assertEquals(0, new BigDecimal("50.00").compareTo(p.getPurchasePrice()),
                "un rând cu cantitate zero nu are voie să mute costul");
        assertEquals(0, r.applied());
    }

    @Test
    void cantitateaNegativaEsteIgnorata() {
        Product p = product(8, "50.00");

        service.intake(p, -5, new BigDecimal("10.00"));

        assertEquals(8, p.getStockQuantity().intValue(),
                "intrarea nu este calea prin care se scade stoc");
    }

    @Test
    void unProdusNulNuArunca() {
        StockIntakeService.Result r = service.intake(null, 5, new BigDecimal("10.00"));
        assertEquals(0, r.applied());
        assertNull(r.costAfter());
    }

    @Test
    void produsulFaraStocInitialPorneesteDeLaZero() {
        Product p = new Product();
        p.setId(1L);
        p.setStockQuantity(null);
        p.setPurchasePrice(null);

        StockIntakeService.Result r = service.intake(p, 4, new BigDecimal("25.00"));

        assertEquals(0, r.stockBefore());
        assertEquals(4, p.getStockQuantity().intValue());
        assertEquals(0, new BigDecimal("25.00").compareTo(p.getPurchasePrice()));
    }

    private static Product product(int stock, String cost) {
        Product p = new Product();
        p.setId(1L);
        p.setName("Produs");
        p.setStockQuantity(stock);
        p.setPurchasePrice(cost == null ? null : new BigDecimal(cost));
        return p;
    }
}
